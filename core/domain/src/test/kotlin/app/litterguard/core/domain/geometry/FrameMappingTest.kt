package app.litterguard.core.domain.geometry

import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.NormalizedPoint
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource

class FrameMappingTest {
    @ParameterizedTest(name = "{0}° maps the sensor top-left corner to ({1}, {2})")
    @CsvSource("0, 0, 0", "90, 1, 0", "180, 1, 1", "270, 0, 1")
    fun `top-left corner after clockwise rotation`(
        degrees: Int,
        x: Float,
        y: Float,
    ) {
        val upright = NormalizedPoint(0f, 0f).rotated(FrameRotation.fromDegrees(degrees))
        assertThat(upright).isEqualTo(NormalizedPoint(x, y))
    }

    @ParameterizedTest
    @EnumSource(FrameRotation::class)
    fun `four quarter turns return the original point`(rotation: FrameRotation) {
        val p = NormalizedPoint(0.25f, 0.75f)
        var q = p
        repeat(4) { q = q.rotated(rotation) }
        assertThat(q.x).isWithin(1e-6f).of(p.x)
        assertThat(q.y).isWithin(1e-6f).of(p.y)
    }

    @Test
    fun `rotated box stays well-formed and keeps its area`() {
        val box = BoundingBox(0.1f, 0.2f, 0.4f, 0.3f)
        val rotated = box.rotated(FrameRotation.CW_90)
        assertThat(rotated).isEqualTo(BoundingBox(0.7f, 0.1f, 0.8f, 0.4f))
        assertThat(rotated.area).isWithin(1e-6f).of(box.area)
    }

    @Test
    fun `rotation degrees are normalised and validated`() {
        assertThat(FrameRotation.fromDegrees(-90)).isEqualTo(FrameRotation.CW_270)
        assertThat(FrameRotation.fromDegrees(450)).isEqualTo(FrameRotation.CW_90)
        assertThrows<IllegalArgumentException> { FrameRotation.fromDegrees(45) }
    }

    @Test
    fun `boxes detected inside a crop map back to frame coordinates`() {
        val crop = BoundingBox(0.5f, 0.5f, 1.0f, 1.0f)
        val inCrop = BoundingBox(0f, 0f, 0.5f, 0.5f)
        assertThat(inCrop.fromCrop(crop)).isEqualTo(BoundingBox(0.5f, 0.5f, 0.75f, 0.75f))
    }
}
