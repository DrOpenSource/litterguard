package app.litterguard.core.domain.decision

import app.litterguard.core.domain.config.DetectionConfig
import app.litterguard.core.domain.vision.BaselineDiff
import app.litterguard.core.domain.vision.Blob
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.IncidentId
import app.litterguard.core.model.IncidentKind
import app.litterguard.core.model.MonotonicTime
import app.litterguard.core.model.ObjectCategory
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DumpingStateMachineTest {
    private val config = DetectionConfig()
    private var ids = 0
    private val machine = DumpingStateMachine(config) { IncidentId("incident-${++ids}") }

    private val start = MonotonicTime(0)
    private val now = start + 20.seconds
    private val bagBox = BoundingBox(0.40f, 0.60f, 0.48f, 0.70f)
    private val otherBox = BoundingBox(0.70f, 0.60f, 0.78f, 0.70f)
    private val bag = Blob(bagBox, 0.008f)

    private val actorVisit =
        VisitContext(enteredAt = start, lastSeenAt = start + 10.seconds, actorCategories = setOf(ObjectCategory.PERSON))
    private val animalVisit = VisitContext(enteredAt = start, lastSeenAt = start + 10.seconds)

    private val actorPresent = Phase.ActorPresent(actorVisit)
    private val animalPhase = Phase.AnimalVisit(animalVisit)
    private val settling = Phase.Settling(actorVisit, since = start + 10.seconds, quietSince = start + 10.seconds)
    private val pendingEarly = Phase.ObjectPending(actorVisit, bagBox, bagBox, since = now - 2.seconds)
    private val pendingDue = Phase.ObjectPending(actorVisit, bagBox, bagBox, since = now - config.dwellTime)

    private enum class Input {
        NOTHING,
        ACTOR,
        ANIMAL,
        EMPTY_DIFF,
        BLOB,
        SCENE_CHANGE,
        NO_BASELINE,
    }

    private fun observation(
        input: Input,
        at: MonotonicTime = now,
    ): Observation {
        val base = Observation(now = at, motion = false)
        return when (input) {
            Input.NOTHING -> base
            Input.ACTOR -> base.copy(actorsInZone = setOf(ObjectCategory.PERSON), occupantsLastSeenAt = at)
            Input.ANIMAL -> base.copy(animalInZone = true, occupantsLastSeenAt = at)
            Input.EMPTY_DIFF -> base.copy(diff = BaselineDiff.Compared(0f, emptyList()))
            Input.BLOB -> base.copy(diff = BaselineDiff.Compared(0.02f, listOf(bag)))
            Input.SCENE_CHANGE -> base.copy(diff = BaselineDiff.Compared(0.6f, listOf(bag)))
            Input.NO_BASELINE -> base.copy(diff = BaselineDiff.Unavailable)
        }
    }

    private data class Row(
        val from: Phase,
        val input: Input,
        val to: String,
        val effects: List<String> = emptyList(),
    )

    private val discard = listOf("UnfreezeBaseline", "DiscardEvidence")
    private val aborted = listOf("CommitIncident", "Rebaseline", "DiscardEvidence")
    private val confirmed =
        listOf("PlayDeterrent", "CommitIncident", "EnqueueAlert", "AbsorbIntoBaseline", "DiscardEvidence")

    private val table =
        listOf(
            Row(Phase.Idle, Input.NOTHING, "Idle"),
            Row(Phase.Idle, Input.ACTOR, "ActorPresent", listOf("FreezeBaseline")),
            Row(Phase.Idle, Input.ANIMAL, "AnimalVisit", listOf("FreezeBaseline")),
            Row(Phase.Idle, Input.EMPTY_DIFF, "Idle"),
            Row(Phase.Idle, Input.BLOB, "Idle"),
            Row(Phase.Idle, Input.SCENE_CHANGE, "Idle"),
            Row(Phase.Idle, Input.NO_BASELINE, "Idle"),
            Row(actorPresent, Input.NOTHING, "Settling"),
            Row(actorPresent, Input.ACTOR, "ActorPresent"),
            Row(actorPresent, Input.ANIMAL, "Settling"),
            Row(actorPresent, Input.EMPTY_DIFF, "Settling"),
            Row(actorPresent, Input.BLOB, "Settling"),
            Row(actorPresent, Input.SCENE_CHANGE, "Settling"),
            Row(actorPresent, Input.NO_BASELINE, "Settling"),
            Row(animalPhase, Input.NOTHING, "Settling"),
            Row(animalPhase, Input.ACTOR, "ActorPresent"),
            Row(animalPhase, Input.ANIMAL, "AnimalVisit"),
            Row(animalPhase, Input.EMPTY_DIFF, "Settling"),
            Row(animalPhase, Input.BLOB, "Settling"),
            Row(animalPhase, Input.SCENE_CHANGE, "Settling"),
            Row(animalPhase, Input.NO_BASELINE, "Settling"),
            // "NOTHING" has no comparison yet, so the machine waits for one.
            Row(settling, Input.NOTHING, "Settling"),
            Row(settling, Input.ACTOR, "ActorPresent"),
            Row(settling, Input.ANIMAL, "Settling"),
            Row(settling, Input.EMPTY_DIFF, "Idle", discard),
            Row(settling, Input.BLOB, "ObjectPending"),
            Row(settling, Input.SCENE_CHANGE, "Idle", aborted),
            Row(settling, Input.NO_BASELINE, "Idle", discard),
            Row(pendingEarly, Input.NOTHING, "ObjectPending"),
            Row(pendingEarly, Input.ACTOR, "ActorPresent"),
            Row(pendingEarly, Input.ANIMAL, "ObjectPending"),
            Row(pendingEarly, Input.EMPTY_DIFF, "ObjectPending"),
            Row(pendingEarly, Input.BLOB, "ObjectPending"),
            Row(pendingEarly, Input.SCENE_CHANGE, "Idle", aborted),
            Row(pendingEarly, Input.NO_BASELINE, "ObjectPending"),
            Row(pendingDue, Input.BLOB, "Idle", confirmed),
        )

    @TestFactory
    fun `transition table`() =
        table.map { row ->
            DynamicTest.dynamicTest("${row.from.name()} + ${row.input} -> ${row.to}") {
                val result = machine.step(MachineState(row.from), observation(row.input))

                assertThat(result.state.phase.name()).isEqualTo(row.to)
                assertThat(result.effects.map { it.javaClass.simpleName })
                    .containsExactlyElementsIn(row.effects)
                    .inOrder()
            }
        }

    @Test
    fun `the table covers every phase and input pair`() {
        val phases = listOf("Idle", "ActorPresent", "AnimalVisit", "Settling", "ObjectPending")
        for (phase in phases) {
            val covered = table.filter { it.from.name() == phase }.map { it.input }.toSet()
            assertWithMessage(phase).that(covered).containsExactlyElementsIn(Input.entries)
        }
    }

    @Test
    fun `confirmation starts a cooldown carrying the incident id`() {
        val result = machine.step(MachineState(pendingDue), observation(Input.BLOB))

        val record =
            result.effects
                .filterIsInstance<Effect.CommitIncident>()
                .single()
                .draft
        assertThat(record.kind).isEqualTo(IncidentKind.CONFIRMED_DUMPING)
        assertThat(record.objectBox).isEqualTo(bagBox)
        assertThat(record.actorLeftAt).isEqualTo(actorVisit.lastSeenAt)
        assertThat(result.state.cooldown).isEqualTo(CooldownWindow(record.id, now + config.cooldown))
    }

    @Test
    fun `a confirmation during the cooldown merges instead of alerting again`() {
        val cooling = MachineState(pendingDue, CooldownWindow(IncidentId("first"), now + 30.seconds))

        val result = machine.step(cooling, observation(Input.BLOB))

        assertThat(result.effects.first()).isEqualTo(Effect.MergeIntoIncident(IncidentId("first"), bagBox, now))
        assertThat(result.effects.filterIsInstance<Effect.PlayDeterrent>()).isEmpty()
        assertThat(result.effects.filterIsInstance<Effect.EnqueueAlert>()).isEmpty()
        assertThat(result.state.cooldown).isEqualTo(cooling.cooldown)
    }

    @Test
    fun `the cooldown expires`() {
        val expired = MachineState(pendingDue, CooldownWindow(IncidentId("old"), now))

        val result = machine.step(expired, observation(Input.BLOB))

        assertThat(result.effects).contains(Effect.PlayDeterrent(DeterrentClip.WARNING))
    }

    @Test
    fun `an object nobody can be blamed for is logged without siren or alert`() {
        val pending = Phase.ObjectPending(animalVisit, bagBox, bagBox, since = now - config.dwellTime)

        val result = machine.step(MachineState(pending), observation(Input.BLOB))

        val record =
            result.effects
                .filterIsInstance<Effect.CommitIncident>()
                .single()
                .draft
        assertThat(record.kind).isEqualTo(IncidentKind.UNATTRIBUTED_OBJECT)
        assertThat(result.effects.filterIsInstance<Effect.PlayDeterrent>()).isEmpty()
        assertThat(result.effects.filterIsInstance<Effect.EnqueueAlert>()).isEmpty()
        assertThat(result.state.cooldown).isNull()
    }

    @Test
    fun `an animal returning to an animal-only visit goes back to AnimalVisit`() {
        val waiting = Phase.Settling(animalVisit, since = start + 10.seconds, quietSince = null)

        val result = machine.step(MachineState(waiting), observation(Input.ANIMAL))

        assertThat(result.state.phase).isInstanceOf(Phase.AnimalVisit::class.java)
    }

    @Test
    fun `settling waits for quiet`() {
        val justLeft = Phase.Settling(actorVisit, since = now - 500.milliseconds, quietSince = null)

        val result = machine.step(MachineState(justLeft), observation(Input.BLOB))

        assertThat(result.state.phase).isEqualTo(justLeft.copy(quietSince = now))
    }

    @Test
    fun `endless motion cannot stall settling`() {
        val windy = Phase.Settling(actorVisit, since = now - config.maxSettleWait, quietSince = null)

        val result = machine.step(MachineState(windy), observation(Input.BLOB).copy(motion = true))

        assertThat(result.state.phase).isInstanceOf(Phase.ObjectPending::class.java)
    }

    @Test
    fun `a blocked view postpones the verdict, but not forever`() {
        val blocked = BaselineDiff.Compared(0f, emptyList(), listOf(bagBox), hiddenFraction = 0.1f)
        val early = Phase.Settling(actorVisit, since = now - 5.seconds, quietSince = now - 5.seconds)
        val late = Phase.Settling(actorVisit, since = now - config.maxOccludedWait, quietSince = null)

        val stillWaiting = machine.step(MachineState(early), observation(Input.NOTHING).copy(diff = blocked))
        val gaveUp = machine.step(MachineState(late), observation(Input.NOTHING).copy(diff = blocked))

        assertThat(stillWaiting.state.phase).isInstanceOf(Phase.Settling::class.java)
        assertThat(gaveUp.state.phase).isEqualTo(Phase.Idle)
    }

    @Test
    fun `a hidden candidate does not count as missing`() {
        val hidden = BaselineDiff.Compared(0f, emptyList(), listOf(bagBox), hiddenFraction = 0.1f)

        val result = machine.step(MachineState(pendingEarly), observation(Input.NOTHING).copy(diff = hidden))

        assertThat(result.state.phase).isEqualTo(pendingEarly)
    }

    @Test
    fun `a candidate missing for longer than the tolerance ends the event`() {
        var state = MachineState(pendingEarly)
        repeat(config.candidateMissTolerance) { state = machine.step(state, observation(Input.EMPTY_DIFF)).state }
        assertThat(state.phase).isInstanceOf(Phase.ObjectPending::class.java)

        val result = machine.step(state, observation(Input.EMPTY_DIFF))

        assertThat(result.state.phase).isEqualTo(Phase.Idle)
    }

    @Test
    fun `a candidate that moves restarts the dwell timer`() {
        val moved = BaselineDiff.Compared(0.02f, listOf(Blob(otherBox, 0.008f)))
        val due = pendingDue.copy(misses = config.candidateMissTolerance)

        val result = machine.step(MachineState(due), observation(Input.NOTHING).copy(diff = moved))

        val phase = result.state.phase as Phase.ObjectPending
        assertThat(phase.firstBox).isEqualTo(otherBox)
        assertThat(phase.since).isEqualTo(now)
        assertThat(result.effects).isEmpty()
    }

    @Test
    fun `the pre-warning is off by default`() {
        val result = machine.step(MachineState(actorPresent), observation(Input.ACTOR).copy(diff = blobDiff()))

        assertThat(result.effects).isEmpty()
    }

    @Test
    fun `the pre-warning plays once when enabled`() {
        val warning = DumpingStateMachine(config.copy(preWarningEnabled = true)) { IncidentId("x") }
        val seen = observation(Input.ACTOR).copy(diff = blobDiff())

        val first = warning.step(MachineState(actorPresent), seen)
        val second = warning.step(first.state, seen)

        assertThat(first.effects).containsExactly(Effect.PlayDeterrent(DeterrentClip.NOTICE))
        assertThat(second.effects).isEmpty()
    }

    @Test
    fun `a visit remembers every kind of actor seen`() {
        val joined = observation(Input.ACTOR).copy(actorsInZone = setOf(ObjectCategory.VEHICLE))

        val result = machine.step(MachineState(actorPresent), joined)

        val visit = (result.state.phase as Phase.ActorPresent).visit
        assertThat(visit.actorCategories).containsExactly(ObjectCategory.PERSON, ObjectCategory.VEHICLE)
        assertThat(visit.lastSeenAt).isEqualTo(now)
    }

    private fun blobDiff() = BaselineDiff.Compared(0.02f, listOf(bag))

    private fun Phase.name(): String = javaClass.simpleName
}
