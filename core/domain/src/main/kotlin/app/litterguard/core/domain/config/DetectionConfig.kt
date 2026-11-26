package app.litterguard.core.domain.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Every tunable of the detection pipeline, in one place (PRD §6.4). Nothing else in the code base may hard-code
 * these values. When a default changes here, update the PRD table in the same change.
 *
 * Fractions are of the ROI area unless stated otherwise; luma values are 8-bit (0–255).
 */
data class DetectionConfig(
    /** Frames per second fed to the pipeline. Lowered at runtime by the thermal policy (FR-8.4). */
    val analysisFps: Float = 3f,
    /** Per-pixel luma change that counts as motion, on the downscaled ROI crop. */
    val motionLumaThreshold: Int = 25,
    /** Share of ROI pixels that must change before the frame counts as "motion". */
    val motionMinFraction: Float = 0.005f,
    /** Minimum detector confidence for a person/vehicle/animal to be tracked. */
    val actorMinConfidence: Float = 0.45f,
    /** Consecutive in-ROI frames before an actor counts as "entered" (hysteresis). */
    val actorEnterFrames: Int = 2,
    /** Consecutive missing frames before an actor counts as "left" (hysteresis). */
    val actorExitFrames: Int = 3,
    /** Overlap with its previous box that lets a detection continue a track. */
    val trackMatchIou: Float = 0.2f,
    /** Without overlap, how far (in box sizes) a detection may move and still continue a same-category track. */
    val trackMatchReach: Float = 1.0f,
    /** A track whose box keeps at least this overlap with where it stopped counts as standing still. */
    val trackStationaryIou: Float = 0.8f,
    /** Quiet time after the last actor leaves before comparing against the baseline. */
    val settleTime: Duration = 1.seconds,
    /** Smallest new-object blob, as a share of the **whole frame** area. */
    val objectMinFrameFraction: Float = 0.0015f,
    /** Luma change from the baseline that marks a cell as "different" (after brightness normalisation). */
    val objectLumaThreshold: Int = 30,
    /** Change in edge strength that marks a new or hidden outline. */
    val objectEdgeThreshold: Int = 24,
    /** Share of a blob's cells that must show changed edges; rejects soft shadows and patches of light. */
    val objectMinEdgeSupport: Float = 0.05f,
    /**
     * A changed region whose outline was this much stronger **before** than now is something that was taken
     * away, not left behind.
     */
    val removedObjectEdgeRatio: Float = 1.4f,
    /** A blob with more than this share of its cells under a live actor or animal box is ignored. */
    val actorOverlapDiscardFraction: Float = 0.30f,
    /** Quiet-zone snapshots whose per-cell median forms the baseline. */
    val baselineHistory: Int = 3,
    /** Longest wait for motion to stop after the last actor leaves, so swaying leaves can't stall the decision. */
    val maxSettleWait: Duration = 3.seconds,
    /** Longest wait while a person or vehicle blocks the view of where something may have been left. */
    val maxOccludedWait: Duration = 30.seconds,
    /** Frames a candidate object may be missing (noise, someone passing in front) before it is dropped. */
    val candidateMissTolerance: Int = 2,
    /** How long a new object must stay static before dumping is confirmed. */
    val dwellTime: Duration = 5.seconds,
    /** IoU a candidate blob must keep with its first appearance to count as static. */
    val objectStabilityIou: Float = 0.6f,
    /** If the baseline diff covers more of the ROI than this, assume a lighting change or shake and abort. */
    val illuminationAbortFraction: Float = 0.35f,
    /** No new siren for this long after an alert; detections in the window merge into the same incident. */
    val cooldown: Duration = 60.seconds,
    /** How often the empty-ROI baseline is refreshed while IDLE. */
    val baselineRefresh: Duration = 30.seconds,
    /** An actor static for longer than this is treated as parked (stays tracked, stops consuming captures). */
    val parkedActorTimeout: Duration = 180.seconds,
    /** Best analysis frames kept per vehicle track for evidence. */
    val evidenceFramesPerTrack: Int = 3,
    /** Kept evidence pictures are at least this far apart in time, so they don't all show the same moment. */
    val evidenceMinSpacing: Duration = 1.seconds,
    /** Speculative high-resolution captures allowed per vehicle track. */
    val speculativeCapturesPerTrack: Int = 2,
    /** Margin added around the ROI bounds for ROI-crop inference, as a share of the ROI's width/height. */
    val roiCropMargin: Float = 0.10f,
    /**
     * How far above the zone's top edge the detector also looks, as a share of the **frame** height. The zone is
     * drawn on the ground and people stand up from it, so without this their upper bodies would be cut off.
     */
    val roiCropHeadroom: Float = 0.40f,
    /**
     * Someone standing still **outside** the zone this long counts as scenery when the baseline refreshes, so a
     * bystander can't stop the app from ever learning what the empty zone looks like.
     */
    val sceneryAfter: Duration = 10.seconds,
    /** Play the "area under surveillance" notice while an actor is present (FR-3.5). Off by owner decision D2. */
    val preWarningEnabled: Boolean = false,
) {
    init {
        require(analysisFps in MIN_FPS..MAX_FPS) { "analysisFps must be in $MIN_FPS..$MAX_FPS, was $analysisFps" }
        require(motionLumaThreshold in 1..MAX_LUMA) { "motionLumaThreshold must be in 1..$MAX_LUMA" }
        requireFraction(motionMinFraction, "motionMinFraction")
        requireFraction(actorMinConfidence, "actorMinConfidence")
        require(actorEnterFrames >= 1) { "actorEnterFrames must be >= 1" }
        require(actorExitFrames >= 1) { "actorExitFrames must be >= 1" }
        requireFraction(trackMatchIou, "trackMatchIou")
        require(trackMatchReach >= 0f) { "trackMatchReach must not be negative" }
        requireFraction(trackStationaryIou, "trackStationaryIou")
        require(!settleTime.isNegative()) { "settleTime must not be negative" }
        requireFraction(objectMinFrameFraction, "objectMinFrameFraction")
        require(dwellTime.isPositive()) { "dwellTime must be positive" }
        requireFraction(objectStabilityIou, "objectStabilityIou")
        requireFraction(illuminationAbortFraction, "illuminationAbortFraction")
        require(objectLumaThreshold in 1..MAX_LUMA) { "objectLumaThreshold must be in 1..$MAX_LUMA" }
        require(objectEdgeThreshold >= 1) { "objectEdgeThreshold must be >= 1" }
        requireFraction(objectMinEdgeSupport, "objectMinEdgeSupport")
        require(removedObjectEdgeRatio >= 1f) { "removedObjectEdgeRatio must be at least 1" }
        requireFraction(actorOverlapDiscardFraction, "actorOverlapDiscardFraction")
        require(baselineHistory >= 1) { "baselineHistory must be >= 1" }
        require(maxSettleWait >= settleTime) { "maxSettleWait must be at least settleTime" }
        require(maxOccludedWait >= maxSettleWait) { "maxOccludedWait must be at least maxSettleWait" }
        require(candidateMissTolerance >= 0) { "candidateMissTolerance must be >= 0" }
        require(!cooldown.isNegative()) { "cooldown must not be negative" }
        require(baselineRefresh.isPositive()) { "baselineRefresh must be positive" }
        require(parkedActorTimeout > dwellTime) { "parkedActorTimeout must exceed dwellTime" }
        require(evidenceFramesPerTrack >= 1) { "evidenceFramesPerTrack must be >= 1" }
        require(!evidenceMinSpacing.isNegative()) { "evidenceMinSpacing must not be negative" }
        require(speculativeCapturesPerTrack >= 0) { "speculativeCapturesPerTrack must be >= 0" }
        require(roiCropMargin in 0f..1f) { "roiCropMargin must be in [0, 1]" }
        require(roiCropHeadroom in 0f..1f) { "roiCropHeadroom must be in [0, 1]" }
        require(sceneryAfter.isPositive()) { "sceneryAfter must be positive" }
    }

    /**
     * Worst-case time from "last actor left the ROI" to the siren starting, per PRD §6.5:
     * settle + dwell + one frame interval + audio start-up. J2 requires this to stay under 8 s.
     */
    val worstCaseTriggerLatency: Duration
        get() = settleTime + dwellTime + (1.0 / analysisFps).seconds + AUDIO_START_BUDGET

    companion object {
        const val MIN_FPS = 0.5f
        const val MAX_FPS = 5f
        const val MAX_LUMA = 255

        /** PRD §6.5 budget for the deterrent to start playing after confirmation. */
        val AUDIO_START_BUDGET: Duration = 0.5.seconds

        /** J2 acceptance limit for [worstCaseTriggerLatency]. */
        val MAX_TRIGGER_LATENCY: Duration = 8.seconds

        /** Operator-facing presets (FR-1.6). All presets keep [worstCaseTriggerLatency] under 8 s. */
        fun forSensitivity(sensitivity: Sensitivity): DetectionConfig =
            when (sensitivity) {
                Sensitivity.LOW -> {
                    DetectionConfig(
                        actorMinConfidence = 0.55f,
                        objectMinFrameFraction = 0.0025f,
                        dwellTime = 6.seconds,
                    )
                }

                Sensitivity.NORMAL -> {
                    DetectionConfig()
                }

                Sensitivity.HIGH -> {
                    DetectionConfig(
                        actorMinConfidence = 0.35f,
                        objectMinFrameFraction = 0.0010f,
                        dwellTime = 4.seconds,
                    )
                }
            }

        private fun requireFraction(
            value: Float,
            name: String,
        ) {
            require(value in 0f..1f) { "$name must be in [0, 1], was $value" }
        }
    }
}

/** Operator-facing sensitivity. LOW = fewer false alarms, may miss small objects; HIGH = the opposite. */
enum class Sensitivity { LOW, NORMAL, HIGH }
