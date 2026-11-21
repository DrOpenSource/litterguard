package app.litterguard.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GeometryTest {
    private fun box(
        l: Float,
        t: Float,
        r: Float,
        b: Float,
    ) = BoundingBox(l, t, r, b)

    @Test
    fun `area, center and bottom-centre are derived from the edges`() {
        val b = box(0.2f, 0.1f, 0.6f, 0.5f)
        assertThat(b.area).isWithin(1e-6f).of(0.16f)
        assertThat(b.center).isEqualTo(NormalizedPoint(0.4f, 0.3f))
        assertThat(b.bottomCenter).isEqualTo(NormalizedPoint(0.4f, 0.5f))
    }

    @Test
    fun `iou of identical boxes is 1`() {
        val b = box(0.1f, 0.1f, 0.3f, 0.4f)
        assertThat(b.iou(b)).isWithin(1e-6f).of(1f)
    }

    @Test
    fun `iou of disjoint and edge-touching boxes is 0`() {
        val a = box(0f, 0f, 0.2f, 0.2f)
        assertThat(a.iou(box(0.5f, 0.5f, 0.7f, 0.7f))).isEqualTo(0f)
        assertThat(a.iou(box(0.2f, 0f, 0.4f, 0.2f))).isEqualTo(0f)
    }

    @Test
    fun `iou of half-overlapping boxes is one third`() {
        val a = box(0f, 0f, 0.2f, 0.2f)
        val b = box(0.1f, 0f, 0.3f, 0.2f)
        assertThat(a.iou(b)).isWithin(1e-5f).of(1f / 3f)
    }

    @Test
    fun `iou of zero-area boxes is 0 rather than NaN`() {
        val point = box(0.5f, 0.5f, 0.5f, 0.5f)
        assertThat(point.iou(point)).isEqualTo(0f)
    }

    @Test
    fun `clampToUnit keeps an overhanging box inside the frame`() {
        assertThat(box(-0.1f, 0.9f, 0.2f, 1.3f).clampToUnit()).isEqualTo(box(0f, 0.9f, 0.2f, 1f))
    }

    @Test
    fun `expandedBy grows each side by a share of the box size`() {
        val grown = box(0.4f, 0.4f, 0.6f, 0.5f).expandedBy(0.5f)
        assertThat(grown.left).isWithin(1e-6f).of(0.3f)
        assertThat(grown.top).isWithin(1e-6f).of(0.35f)
        assertThat(grown.right).isWithin(1e-6f).of(0.7f)
        assertThat(grown.bottom).isWithin(1e-6f).of(0.55f)
    }

    @Test
    fun `inverted or non-finite boxes are rejected`() {
        assertThrows<IllegalArgumentException> { box(0.5f, 0f, 0.4f, 0.1f) }
        assertThrows<IllegalArgumentException> { box(0f, 0f, Float.NaN, 0.1f) }
    }

    @Test
    fun `polygon edges include the closing edge`() {
        val p = Polygon(listOf(NormalizedPoint(0f, 0f), NormalizedPoint(1f, 0f), NormalizedPoint(0f, 1f)))
        assertThat(p.edges).hasSize(3)
        assertThat(p.edges.last()).isEqualTo(NormalizedPoint(0f, 1f) to NormalizedPoint(0f, 0f))
    }

    @Test
    fun `polygon needs at least three vertices`() {
        assertThrows<IllegalArgumentException> { Polygon(listOf(NormalizedPoint(0f, 0f), NormalizedPoint(1f, 1f))) }
    }

    @Test
    fun `only people and vehicles are actors`() {
        assertThat(ObjectCategory.entries.filter { it.isActor })
            .containsExactly(ObjectCategory.PERSON, ObjectCategory.VEHICLE)
    }

    @Test
    fun `detection confidence must be a probability`() {
        assertThrows<IllegalArgumentException> {
            Detection(box(0f, 0f, 0.1f, 0.1f), ObjectCategory.PERSON, "person", 1.2f)
        }
    }
}
