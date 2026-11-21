package app.litterguard.core.domain.geometry

import app.litterguard.core.model.NormalizedPoint
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RoiValidationTest {
    private fun pts(vararg xy: Float) = xy.toList().chunked(2) { (x, y) -> NormalizedPoint(x, y) }

    @Test
    fun `a normal quadrilateral is valid`() {
        val result = RoiRules.validate(pts(0.2f, 0.5f, 0.7f, 0.5f, 0.8f, 0.9f, 0.1f, 0.9f))
        assertThat(result).isInstanceOf(RoiValidation.Valid::class.java)
    }

    @Test
    fun `three points are too few`() {
        assertThat(RoiRules.validate(pts(0.1f, 0.1f, 0.9f, 0.1f, 0.5f, 0.9f)))
            .isEqualTo(RoiValidation.TooFewPoints(3, RoiRules.MIN_POINTS))
    }

    @Test
    fun `thirteen points are too many`() {
        val ring =
            (0 until 13).map { i ->
                val a = 2 * Math.PI * i / 13
                NormalizedPoint((0.5 + 0.3 * Math.cos(a)).toFloat(), (0.5 + 0.3 * Math.sin(a)).toFloat())
            }
        assertThat(RoiRules.validate(ring)).isEqualTo(RoiValidation.TooManyPoints(13, RoiRules.MAX_POINTS))
    }

    @Test
    fun `points outside the frame are rejected`() {
        assertThat(RoiRules.validate(pts(-0.1f, 0.5f, 0.7f, 0.5f, 0.8f, 0.9f, 0.1f, 0.9f)))
            .isEqualTo(RoiValidation.OutsideFrame)
    }

    @Test
    fun `a crossed (bow-tie) zone is rejected`() {
        assertThat(RoiRules.validate(pts(0.2f, 0.2f, 0.8f, 0.8f, 0.8f, 0.2f, 0.2f, 0.8f)))
            .isEqualTo(RoiValidation.SelfIntersecting)
    }

    @Test
    fun `a tiny zone is rejected`() {
        val result = RoiRules.validate(pts(0.5f, 0.5f, 0.55f, 0.5f, 0.55f, 0.55f, 0.5f, 0.55f))
        assertThat(result).isInstanceOf(RoiValidation.TooSmall::class.java)
    }
}
