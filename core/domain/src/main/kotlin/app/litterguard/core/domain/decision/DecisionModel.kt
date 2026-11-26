package app.litterguard.core.domain.decision

import app.litterguard.core.domain.vision.BaselineDiff
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.IncidentId
import app.litterguard.core.model.IncidentKind
import app.litterguard.core.model.MonotonicTime
import app.litterguard.core.model.ObjectCategory

/** What the state machine knows about the visit it is currently judging. */
data class VisitContext(
    val enteredAt: MonotonicTime,
    /** When a person, vehicle or animal was last actually seen in the zone. */
    val lastSeenAt: MonotonicTime,
    /** Person/vehicle categories seen during this visit. Empty for an animal-only visit. */
    val actorCategories: Set<ObjectCategory> = emptySet(),
    val noticePlayed: Boolean = false,
) {
    val hasActor: Boolean get() = actorCategories.isNotEmpty()
}

/** Where the machine is (PRD §6.3). The cooldown runs alongside, see [MachineState.cooldown]. */
sealed interface Phase {
    /** Nothing is happening; the baseline may refresh. */
    data object Idle : Phase

    /** At least one person or vehicle is in the zone. The baseline is frozen. */
    data class ActorPresent(
        val visit: VisitContext,
    ) : Phase

    /** Only animals are in the zone. They are never treated as actors (FR-3.2). */
    data class AnimalVisit(
        val visit: VisitContext,
    ) : Phase

    /** Everyone has left; waiting for motion to stop before comparing with the baseline. */
    data class Settling(
        val visit: VisitContext,
        val since: MonotonicTime,
        val quietSince: MonotonicTime?,
    ) : Phase

    /** Something new is there. It must stay put for the dwell time. */
    data class ObjectPending(
        val visit: VisitContext,
        val firstBox: BoundingBox,
        val box: BoundingBox,
        val since: MonotonicTime,
        val misses: Int = 0,
    ) : Phase
}

/** After an alert, further confirmations are merged into [incidentId] until [until] (FR-3.3). */
data class CooldownWindow(
    val incidentId: IncidentId,
    val until: MonotonicTime,
)

data class MachineState(
    val phase: Phase = Phase.Idle,
    val cooldown: CooldownWindow? = null,
)

/** Everything one frame tells the state machine. */
data class Observation(
    val now: MonotonicTime,
    /** Motion in the zone that isn't just someone who has already left it. */
    val motion: Boolean,
    /** Categories of the people/vehicles currently in the zone (after hysteresis). */
    val actorsInZone: Set<ObjectCategory> = emptySet(),
    val animalInZone: Boolean = false,
    /** Latest time any of those actors or animals was actually detected in the zone. */
    val occupantsLastSeenAt: MonotonicTime? = null,
    /** Baseline comparison, or null when it was not computed for this frame. */
    val diff: BaselineDiff? = null,
)

enum class DeterrentClip {
    /** The "illegal dumping detected" warning (FR-4.1). */
    WARNING,

    /** The short "area under surveillance" notice (FR-3.5, off by default). */
    NOTICE,

    /** A tone for the operator's "Test siren" button. */
    TEST_TONE,
}

/** A new incident as the decision logic sees it. Adapters add wall-clock times, evidence and device details. */
data class IncidentDraft(
    val id: IncidentId,
    val kind: IncidentKind,
    val actorCategories: Set<ObjectCategory>,
    val actorEnteredAt: MonotonicTime?,
    val actorLeftAt: MonotonicTime?,
    val confirmedAt: MonotonicTime,
    val objectBox: BoundingBox?,
)

/** An instruction from the pure state machine to the outside world. Effects must be executed in list order. */
sealed interface Effect {
    /** Handled by the pipeline itself, which owns the baseline. */
    sealed interface BaselineEffect : Effect

    data object FreezeBaseline : BaselineEffect

    data object UnfreezeBaseline : BaselineEffect

    /** The scene changed wholesale; start the baseline again from the current frame. */
    data object Rebaseline : BaselineEffect

    /** Make the current frame the baseline so a reported object doesn't trigger again (FR-3.4). */
    data object AbsorbIntoBaseline : BaselineEffect

    data class PlayDeterrent(
        val clip: DeterrentClip,
    ) : Effect

    data class CommitIncident(
        val draft: IncidentDraft,
    ) : Effect

    data class MergeIntoIncident(
        val incidentId: IncidentId,
        val objectBox: BoundingBox,
        val at: MonotonicTime,
    ) : Effect

    data class EnqueueAlert(
        val incidentId: IncidentId,
    ) : Effect

    /** The visit ended without needing its buffered evidence. */
    data object DiscardEvidence : Effect
}

data class Transition(
    val state: MachineState,
    val effects: List<Effect> = emptyList(),
)
