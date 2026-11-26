package app.litterguard.core.domain.pipeline

import app.litterguard.core.domain.config.DetectionConfig
import app.litterguard.core.domain.decision.CooldownWindow
import app.litterguard.core.domain.decision.DumpingStateMachine
import app.litterguard.core.domain.decision.Effect
import app.litterguard.core.domain.decision.MachineState
import app.litterguard.core.domain.decision.Observation
import app.litterguard.core.domain.decision.Phase
import app.litterguard.core.domain.geometry.bounds
import app.litterguard.core.domain.tracking.Track
import app.litterguard.core.domain.tracking.Tracker
import app.litterguard.core.domain.vision.BaselineDiff
import app.litterguard.core.domain.vision.BaselineModel
import app.litterguard.core.domain.vision.Blob
import app.litterguard.core.domain.vision.MotionGate
import app.litterguard.core.domain.vision.MotionScore
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.Detection
import app.litterguard.core.model.IncidentId
import app.litterguard.core.model.LumaPlane
import app.litterguard.core.model.MonotonicTime
import app.litterguard.core.model.ObjectCategory
import app.litterguard.core.model.Polygon
import app.litterguard.core.model.TrackId

/** Everything the pipeline concluded from the latest frame, for the debug overlay and logs. */
data class PipelineSnapshot(
    val phase: Phase,
    val cooldown: CooldownWindow?,
    val tracks: List<Track>,
    val blobs: List<Blob>,
    val motion: MotionScore,
    val detectorRan: Boolean,
    val hasBaseline: Boolean,
    val timestamp: MonotonicTime,
)

/** @property effects what the outside world must now do, in order. Baseline housekeeping is already applied. */
data class PipelineStep(
    val effects: List<Effect>,
    val snapshot: PipelineSnapshot,
)

/**
 * The whole decision chain for one dump zone: motion gate → detector → tracker → baseline diff → state machine
 * (ARCHITECTURE §4). Deterministic: the same frames and detections give the same effects.
 * Stateful: call it from one coroutine only.
 */
class DecisionPipeline(
    roi: Polygon,
    private val config: DetectionConfig,
    newIncidentId: () -> IncidentId,
) {
    private val zoneBounds = roi.bounds
    private val motionGate = MotionGate(roi, config)
    private val tracker = Tracker(roi, config)
    private val baseline = BaselineModel(roi, config)
    private val machine = DumpingStateMachine(config, newIncidentId)
    private var state = MachineState()
    private var previousBoxes: Map<TrackId, BoundingBox> = emptyMap()

    /** [detect] is only invoked when the motion gate or an ongoing event calls for it (FR-2.2). */
    suspend fun process(
        luma: LumaPlane,
        now: MonotonicTime,
        detect: suspend () -> List<Detection>,
    ): PipelineStep {
        val motion = motionGate.update(luma)
        val needsDetector =
            !baseline.hasBaseline || motion.isMotion || tracker.hasTracks || state.phase != Phase.Idle
        val tracks =
            if (needsDetector) {
                val relevant =
                    detect().filter {
                        it.category != ObjectCategory.OTHER && it.confidence >= config.actorMinConfidence
                    }
                tracker.update(relevant, now)
            } else {
                emptyList()
            }

        val diff = if (needsComparison()) compare(luma, tracks) else null
        val sceneMoving = motion.isMotion && motionGate.hasMotionOutside(departedBoxes(tracks))
        previousBoxes = tracks.associate { it.id to it.box }
        val transition = machine.step(state, observe(now, sceneMoving, tracks, diff))
        state = transition.state

        val external = mutableListOf<Effect>()
        for (effect in transition.effects) {
            if (effect is Effect.BaselineEffect) apply(effect, luma, now) else external += effect
        }

        if (state.phase == Phase.Idle && !motion.isMotion && isQuietAround(tracks)) baseline.offerQuiet(luma, now)

        return PipelineStep(
            effects = external,
            snapshot =
                PipelineSnapshot(
                    phase = state.phase,
                    cooldown = state.cooldown,
                    tracks = tracks,
                    blobs = (diff as? BaselineDiff.Compared)?.blobs.orEmpty(),
                    motion = motion,
                    detectorRan = needsDetector,
                    hasBaseline = baseline.hasBaseline,
                    timestamp = now,
                ),
        )
    }

    /**
     * Whether nothing near the zone could still be about to change it. Someone who has long stood still outside it
     * is scenery: waiting for them to leave could mean never learning what the empty zone looks like.
     */
    private fun isQuietAround(tracks: List<Track>): Boolean =
        tracks.none {
            it.box.intersectionArea(zoneBounds) > 0f && (it.inZone || it.stationaryFor < config.sceneryAfter)
        }

    /** Where people and vehicles that are no longer in the zone are now, and were a frame ago. */
    private fun departedBoxes(tracks: List<Track>): List<BoundingBox> {
        val stillInZone = tracks.filter { it.inZone }.map { it.id }.toSet()
        val current = tracks.filter { it.id !in stillInZone }.map { it.box }
        return current + previousBoxes.filterKeys { it !in stillInZone }.values
    }

    private fun needsComparison(): Boolean =
        when (state.phase) {
            is Phase.Settling, is Phase.ObjectPending -> true
            is Phase.ActorPresent -> config.preWarningEnabled
            is Phase.AnimalVisit, Phase.Idle -> false
        }

    private fun compare(
        luma: LumaPlane,
        tracks: List<Track>,
    ): BaselineDiff = baseline.compare(luma, tracks.filter { it.seenThisFrame }.map { it.box })

    private fun observe(
        now: MonotonicTime,
        sceneMoving: Boolean,
        tracks: List<Track>,
        diff: BaselineDiff?,
    ): Observation {
        val inZone = tracks.filter { it.inZone }
        return Observation(
            now = now,
            motion = sceneMoving,
            actorsInZone = inZone.filter { it.category.isActor }.map { it.category }.toSet(),
            animalInZone = inZone.any { it.category == ObjectCategory.ANIMAL },
            occupantsLastSeenAt = inZone.mapNotNull { it.lastSeenInZoneAt }.maxOrNull(),
            diff = diff,
        )
    }

    private fun apply(
        effect: Effect.BaselineEffect,
        luma: LumaPlane,
        now: MonotonicTime,
    ) {
        when (effect) {
            Effect.FreezeBaseline -> baseline.freeze()
            Effect.UnfreezeBaseline -> baseline.unfreeze()
            Effect.Rebaseline, Effect.AbsorbIntoBaseline -> baseline.rebaseline(luma, now)
        }
    }
}
