package app.litterguard.core.domain.decision

import app.litterguard.core.domain.config.DetectionConfig
import app.litterguard.core.domain.vision.BaselineDiff
import app.litterguard.core.domain.vision.Blob
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.IncidentId
import app.litterguard.core.model.IncidentKind
import app.litterguard.core.model.MonotonicTime
import kotlin.time.Duration

/**
 * The dumping decision (FR-3.1–3.5, PRD §6.3): dumping is confirmed only when an actor was present, a new object
 * remains after they leave, and it stays put for the dwell time.
 *
 * Pure: `(state, observation) → (state, effects)`. It reads no clock and does no I/O; the only impurity,
 * [newIncidentId], is injected.
 */
class DumpingStateMachine(
    private val config: DetectionConfig,
    private val newIncidentId: () -> IncidentId,
) {
    fun step(
        state: MachineState,
        observation: Observation,
    ): Transition {
        val current = state.copy(cooldown = state.cooldown?.takeIf { observation.now < it.until })
        return when (val phase = current.phase) {
            Phase.Idle -> onIdle(current, observation)
            is Phase.ActorPresent -> onActorPresent(current, phase, observation)
            is Phase.AnimalVisit -> onAnimalVisit(current, phase, observation)
            is Phase.Settling -> onSettling(current, phase, observation)
            is Phase.ObjectPending -> onObjectPending(current, phase, observation)
        }
    }

    private fun onIdle(
        state: MachineState,
        observation: Observation,
    ): Transition {
        val seenAt = observation.occupantsLastSeenAt ?: observation.now
        val visit = VisitContext(enteredAt = observation.now, lastSeenAt = seenAt)
        return when {
            observation.actorsInZone.isNotEmpty() -> {
                state.moveTo(Phase.ActorPresent(visit.with(observation)), Effect.FreezeBaseline)
            }

            observation.animalInZone -> {
                state.moveTo(Phase.AnimalVisit(visit), Effect.FreezeBaseline)
            }

            else -> {
                Transition(state)
            }
        }
    }

    private fun onActorPresent(
        state: MachineState,
        phase: Phase.ActorPresent,
        observation: Observation,
    ): Transition {
        if (observation.actorsInZone.isEmpty()) return startSettling(state, phase.visit, observation)

        val visit = phase.visit.with(observation)
        val somethingNew = (observation.diff as? BaselineDiff.Compared)?.blobs?.isNotEmpty() == true
        return if (config.preWarningEnabled && !visit.noticePlayed && somethingNew) {
            state.moveTo(
                Phase.ActorPresent(visit.copy(noticePlayed = true)),
                Effect.PlayDeterrent(DeterrentClip.NOTICE),
            )
        } else {
            state.moveTo(Phase.ActorPresent(visit))
        }
    }

    private fun onAnimalVisit(
        state: MachineState,
        phase: Phase.AnimalVisit,
        observation: Observation,
    ): Transition =
        when {
            observation.actorsInZone.isNotEmpty() -> {
                state.moveTo(Phase.ActorPresent(phase.visit.with(observation)))
            }

            observation.animalInZone -> {
                state.moveTo(Phase.AnimalVisit(phase.visit.with(observation)))
            }

            else -> {
                startSettling(state, phase.visit, observation)
            }
        }

    private fun startSettling(
        state: MachineState,
        visit: VisitContext,
        observation: Observation,
    ): Transition {
        // Time already spent in the tracker's exit hysteresis counts towards settling (latency budget, PRD §6.5).
        val quietSince = if (observation.motion) null else visit.lastSeenAt
        return state.moveTo(Phase.Settling(visit, since = visit.lastSeenAt, quietSince = quietSince))
    }

    /** Someone is (back) in the zone while we were judging it: carry the same visit on. */
    private fun reoccupied(
        state: MachineState,
        visit: VisitContext,
        observation: Observation,
    ): Transition? =
        when {
            observation.actorsInZone.isNotEmpty() -> {
                state.moveTo(Phase.ActorPresent(visit.with(observation)))
            }

            observation.animalInZone && !visit.hasActor -> {
                state.moveTo(Phase.AnimalVisit(visit.with(observation)))
            }

            else -> {
                null
            }
        }

    private fun onSettling(
        state: MachineState,
        phase: Phase.Settling,
        observation: Observation,
    ): Transition = reoccupied(state, phase.visit, observation) ?: awaitVerdict(state, phase, observation)

    private fun awaitVerdict(
        state: MachineState,
        phase: Phase.Settling,
        observation: Observation,
    ): Transition {
        val quietSince = if (observation.motion) null else phase.quietSince ?: observation.now
        val waiting = phase.copy(quietSince = quietSince)
        val waited = observation.now - phase.since
        val quietLongEnough = quietSince != null && observation.now - quietSince >= config.settleTime
        val settled = quietLongEnough || waited >= config.maxSettleWait
        return when (val diff = observation.diff.takeIf { settled }) {
            null -> state.moveTo(waiting)
            BaselineDiff.Unavailable -> endVisit(state)
            is BaselineDiff.Compared -> judge(state, waiting, diff, observation.now)
        }
    }

    private fun judge(
        state: MachineState,
        phase: Phase.Settling,
        diff: BaselineDiff.Compared,
        now: MonotonicTime,
    ): Transition =
        when {
            diff.isSceneWideChange() -> inconclusive(state, phase.visit, now)

            diff.blobs.isNotEmpty() -> state.moveTo(pending(phase.visit, diff.blobs.first(), now))

            // "Nothing there" can't be trusted while someone is blocking the view.
            diff.isViewBlocked(now - phase.since) -> state.moveTo(phase)

            else -> endVisit(state)
        }

    private fun onObjectPending(
        state: MachineState,
        phase: Phase.ObjectPending,
        observation: Observation,
    ): Transition = reoccupied(state, phase.visit, observation) ?: followCandidate(state, phase, observation)

    private fun followCandidate(
        state: MachineState,
        phase: Phase.ObjectPending,
        observation: Observation,
    ): Transition {
        val diff = observation.diff as? BaselineDiff.Compared ?: return Transition(state)
        val match =
            diff.blobs
                .filter { it.box.iou(phase.firstBox) >= config.objectStabilityIou }
                .maxByOrNull { it.box.iou(phase.firstBox) }
        return when {
            diff.isSceneWideChange() -> {
                inconclusive(state, phase.visit, observation.now)
            }

            match == null -> {
                candidateMissing(state, phase, diff, observation.now)
            }

            observation.now - phase.since >= config.dwellTime -> {
                confirm(state, phase.visit, match, observation.now)
            }

            else -> {
                state.moveTo(phase.copy(box = match.box, misses = 0))
            }
        }
    }

    private fun candidateMissing(
        state: MachineState,
        phase: Phase.ObjectPending,
        diff: BaselineDiff.Compared,
        now: MonotonicTime,
    ): Transition =
        when {
            diff.isHidden(phase.firstBox, now - phase.since) -> Transition(state)
            phase.misses < config.candidateMissTolerance -> state.moveTo(phase.copy(misses = phase.misses + 1))
            diff.blobs.isEmpty() -> endVisit(state)
            else -> state.moveTo(pending(phase.visit, diff.blobs.first(), now))
        }

    private fun confirm(
        state: MachineState,
        visit: VisitContext,
        blob: Blob,
        now: MonotonicTime,
    ): Transition {
        val cooldown = state.cooldown
        return when {
            !visit.hasActor -> {
                val record = draft(IncidentKind.UNATTRIBUTED_OBJECT, visit, now).copy(objectBox = blob.box)
                Transition(
                    state.copy(phase = Phase.Idle),
                    listOf(Effect.CommitIncident(record), Effect.AbsorbIntoBaseline, Effect.DiscardEvidence),
                )
            }

            cooldown != null -> {
                Transition(
                    state.copy(phase = Phase.Idle),
                    listOf(
                        Effect.MergeIntoIncident(cooldown.incidentId, blob.box, now),
                        Effect.AbsorbIntoBaseline,
                        Effect.DiscardEvidence,
                    ),
                )
            }

            else -> {
                val record = draft(IncidentKind.CONFIRMED_DUMPING, visit, now).copy(objectBox = blob.box)
                Transition(
                    MachineState(Phase.Idle, CooldownWindow(record.id, now + config.cooldown)),
                    listOf(
                        // The siren comes first: it must start within 500 ms (FR-4.1).
                        Effect.PlayDeterrent(DeterrentClip.WARNING),
                        Effect.CommitIncident(record),
                        Effect.EnqueueAlert(record.id),
                        Effect.AbsorbIntoBaseline,
                        Effect.DiscardEvidence,
                    ),
                )
            }
        }
    }

    private fun inconclusive(
        state: MachineState,
        visit: VisitContext,
        now: MonotonicTime,
    ): Transition =
        Transition(
            state.copy(phase = Phase.Idle),
            listOf(
                Effect.CommitIncident(draft(IncidentKind.INCONCLUSIVE, visit, now)),
                Effect.Rebaseline,
                Effect.DiscardEvidence,
            ),
        )

    private fun endVisit(state: MachineState): Transition =
        Transition(state.copy(phase = Phase.Idle), listOf(Effect.UnfreezeBaseline, Effect.DiscardEvidence))

    private fun draft(
        kind: IncidentKind,
        visit: VisitContext,
        now: MonotonicTime,
    ) = IncidentDraft(
        id = newIncidentId(),
        kind = kind,
        actorCategories = visit.actorCategories,
        actorEnteredAt = visit.enteredAt,
        actorLeftAt = visit.lastSeenAt,
        confirmedAt = now,
        objectBox = null,
    )

    private fun pending(
        visit: VisitContext,
        blob: Blob,
        now: MonotonicTime,
    ) = Phase.ObjectPending(visit, firstBox = blob.box, box = blob.box, since = now)

    private fun BaselineDiff.Compared.isSceneWideChange() = changedFraction > config.illuminationAbortFraction

    private fun BaselineDiff.Compared.isViewBlocked(waited: Duration) =
        hiddenFraction > 0f && waited < config.maxOccludedWait

    private fun BaselineDiff.Compared.isHidden(
        box: BoundingBox,
        waited: Duration,
    ) = waited < config.maxOccludedWait &&
        occluders.any { it.intersectionArea(box) > box.area * config.actorOverlapDiscardFraction }

    private fun VisitContext.with(observation: Observation) =
        copy(
            actorCategories = actorCategories + observation.actorsInZone,
            lastSeenAt = maxOf(lastSeenAt, observation.occupantsLastSeenAt ?: observation.now),
        )

    private fun MachineState.moveTo(
        phase: Phase,
        vararg effects: Effect,
    ) = Transition(copy(phase = phase), effects.toList())
}
