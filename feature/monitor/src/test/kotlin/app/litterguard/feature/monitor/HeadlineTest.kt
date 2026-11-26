package app.litterguard.feature.monitor

import app.litterguard.core.domain.decision.CooldownWindow
import app.litterguard.core.domain.decision.Phase
import app.litterguard.core.domain.decision.VisitContext
import app.litterguard.core.domain.pipeline.PipelineSnapshot
import app.litterguard.core.domain.vision.MotionScore
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.IncidentId
import app.litterguard.core.model.MonotonicTime
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class HeadlineTest {
    private val start = MonotonicTime(0)
    private val box = BoundingBox(0f, 0f, 1f, 1f)

    private fun snapshot(
        phase: Phase,
        cooldown: CooldownWindow? = null,
        hasBaseline: Boolean = true,
    ) = PipelineSnapshot(
        phase = phase,
        cooldown = cooldown,
        tracks = emptyList(),
        blobs = emptyList(),
        motion = MotionScore(0f, false),
        detectorRan = false,
        hasBaseline = hasBaseline,
        timestamp = start + 10.seconds,
    )

    @Test
    fun `idle reads differently while learning, watching and just after an alert`() {
        val cooldown = CooldownWindow(IncidentId("a"), start + 60.seconds)

        assertThat(headlineOf(snapshot(Phase.Idle, hasBaseline = false))).isEqualTo(Headline.LEARNING_SCENE)
        assertThat(headlineOf(snapshot(Phase.Idle))).isEqualTo(Headline.WATCHING)
        assertThat(headlineOf(snapshot(Phase.Idle, cooldown))).isEqualTo(Headline.ALERT_RAISED)
    }

    @Test
    fun `a pending object counts the seconds it has stayed`() {
        val pending = Phase.ObjectPending(VisitContext(start, start), box, box, since = start + 7.seconds)

        assertThat(headlineOf(snapshot(pending))).isEqualTo(Headline.NEW_OBJECT)
        assertThat(pendingSeconds(snapshot(pending))).isEqualTo(3)
        assertThat(pendingSeconds(snapshot(Phase.Idle))).isNull()
    }
}
