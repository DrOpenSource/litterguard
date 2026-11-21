package app.litterguard.core.domain.config

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.time.Duration.Companion.seconds

class DetectionConfigTest {
    @Test
    fun `defaults match the PRD parameter table`() {
        val c = DetectionConfig()
        assertThat(c.analysisFps).isEqualTo(3f)
        assertThat(c.dwellTime).isEqualTo(5.seconds)
        assertThat(c.cooldown).isEqualTo(60.seconds)
        assertThat(c.actorEnterFrames).isEqualTo(2)
        assertThat(c.actorExitFrames).isEqualTo(3)
    }

    @Test
    fun `pre-warning is off by default (owner decision D2)`() {
        assertThat(DetectionConfig().preWarningEnabled).isFalse()
    }

    @ParameterizedTest
    @EnumSource(Sensitivity::class)
    fun `every preset meets the 8 second J2 trigger-latency budget`(sensitivity: Sensitivity) {
        val config = DetectionConfig.forSensitivity(sensitivity)
        assertThat(config.worstCaseTriggerLatency).isLessThan(DetectionConfig.MAX_TRIGGER_LATENCY)
    }

    @Test
    fun `default worst-case latency is about 6_8 seconds`() {
        // settle 1 s + dwell 5 s + one frame at 3 FPS (0.33 s) + audio 0.5 s
        val latency = DetectionConfig().worstCaseTriggerLatency.inWholeMilliseconds
        assertThat(latency).isIn(6_830L..6_834L)
    }

    @Test
    fun `low sensitivity is stricter than high sensitivity`() {
        val low = DetectionConfig.forSensitivity(Sensitivity.LOW)
        val high = DetectionConfig.forSensitivity(Sensitivity.HIGH)
        assertThat(low.actorMinConfidence).isGreaterThan(high.actorMinConfidence)
        assertThat(low.objectMinFrameFraction).isGreaterThan(high.objectMinFrameFraction)
        assertThat(low.dwellTime).isGreaterThan(high.dwellTime)
    }

    @Test
    fun `out-of-range values are rejected`() {
        assertThrows<IllegalArgumentException> { DetectionConfig(analysisFps = 30f) }
        assertThrows<IllegalArgumentException> { DetectionConfig(motionMinFraction = 1.5f) }
        assertThrows<IllegalArgumentException> { DetectionConfig(dwellTime = 0.seconds) }
        assertThrows<IllegalArgumentException> { DetectionConfig(parkedActorTimeout = 2.seconds) }
        assertThrows<IllegalArgumentException> { DetectionConfig(motionLumaThreshold = 0) }
    }
}
