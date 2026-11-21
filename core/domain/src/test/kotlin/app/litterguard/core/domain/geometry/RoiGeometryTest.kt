package app.litterguard.core.domain.geometry

import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.NormalizedPoint
import app.litterguard.core.model.Polygon
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class RoiGeometryTest {
    private fun poly(vararg xy: Float) = Polygon(xy.toList().chunked(2) { (x, y) -> NormalizedPoint(x, y) })

    private val square = poly(0.2f, 0.2f, 0.8f, 0.2f, 0.8f, 0.8f, 0.2f, 0.8f)

    /** A "U" shape: the notch between the arms (around x = 0.5, y = 0.3) is outside. */
    private val concaveU =
        poly(
            0.1f,
            0.1f,
            0.3f,
            0.1f,
            0.3f,
            0.6f,
            0.7f,
            0.6f,
            0.7f,
            0.1f,
            0.9f,
            0.1f,
            0.9f,
            0.9f,
            0.1f,
            0.9f,
        )

    @ParameterizedTest(name = "square contains ({0}, {1}) = {2}")
    @CsvSource(
        "0.5, 0.5, true", // centre
        "0.2, 0.5, true", // on the left edge
        "0.8, 0.8, true", // on a vertex
        "0.5, 0.2, true", // on the top edge
        "0.1, 0.5, false", // left of the square
        "0.5, 0.9, false", // below
        "0.81, 0.5, false", // just outside the right edge
    )
    fun `square containment, with edges counting as inside`(
        x: Float,
        y: Float,
        expected: Boolean,
    ) {
        assertThat(NormalizedPoint(x, y) in square).isEqualTo(expected)
    }

    @ParameterizedTest(name = "U contains ({0}, {1}) = {2}")
    @CsvSource(
        "0.2, 0.3, true", // left arm
        "0.8, 0.3, true", // right arm
        "0.5, 0.8, true", // base
        "0.5, 0.3, false", // inside the notch
        "0.5, 0.6, true", // on the notch floor edge
    )
    fun `concave containment`(
        x: Float,
        y: Float,
        expected: Boolean,
    ) {
        assertThat(NormalizedPoint(x, y) in concaveU).isEqualTo(expected)
    }

    @Test
    fun `area of the unit-fraction square and the U shape`() {
        assertThat(square.area).isWithin(1e-6f).of(0.36f)
        // 0.8 x 0.8 outer square minus the 0.4 x 0.5 notch
        assertThat(concaveU.area).isWithin(1e-6f).of(0.64f - 0.20f)
    }

    @Test
    fun `area does not depend on winding direction`() {
        val reversed = Polygon(square.vertices.reversed())
        assertThat(reversed.area).isWithin(1e-6f).of(square.area)
    }

    @Test
    fun `bounds of a polygon`() {
        assertThat(concaveU.bounds).isEqualTo(BoundingBox(0.1f, 0.1f, 0.9f, 0.9f))
    }

    @Test
    fun `convex and concave simple polygons are simple`() {
        assertThat(square.isSimple).isTrue()
        assertThat(concaveU.isSimple).isTrue()
    }

    @Test
    fun `a bow-tie polygon is not simple`() {
        val bowTie = poly(0.2f, 0.2f, 0.8f, 0.8f, 0.8f, 0.2f, 0.2f, 0.8f)
        assertThat(bowTie.isSimple).isFalse()
    }

    @Test
    fun `a polygon whose edge touches a non-adjacent vertex is not simple`() {
        // Vertex (0.5, 0.2) lies on the edge from (0.2, 0.2) to (0.8, 0.2) of a later loop-back.
        val touching = poly(0.2f, 0.2f, 0.8f, 0.2f, 0.8f, 0.8f, 0.5f, 0.2f, 0.2f, 0.8f)
        assertThat(touching.isSimple).isFalse()
    }

    @Test
    fun `crop rect adds margin and stays inside the frame`() {
        val crop = poly(0.0f, 0.5f, 0.4f, 0.5f, 0.4f, 1.0f, 0.0f, 1.0f).cropRect(margin = 0.1f)
        assertThat(crop.left).isEqualTo(0f) // clamped
        assertThat(crop.right).isWithin(1e-6f).of(0.44f)
        assertThat(crop.top).isWithin(1e-6f).of(0.45f)
        assertThat(crop.bottom).isEqualTo(1f) // clamped
    }
}
