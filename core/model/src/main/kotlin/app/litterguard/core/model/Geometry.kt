package app.litterguard.core.model

import kotlin.math.max
import kotlin.math.min

/**
 * A point in normalised frame coordinates.
 *
 * Origin is the top-left corner of the upright frame, x grows to the right, y grows downwards.
 * Values inside the frame are in `[0, 1]`; values slightly outside are allowed (detector boxes may overhang).
 */
data class NormalizedPoint(
    val x: Float,
    val y: Float,
) {
    init {
        require(x.isFinite() && y.isFinite()) { "Point coordinates must be finite: ($x, $y)" }
    }
}

/** Axis-aligned box in normalised frame coordinates (see [NormalizedPoint] for conventions). */
data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) {
            "Box coordinates must be finite: $this"
        }
        require(left <= right && top <= bottom) { "Box must have left <= right and top <= bottom: $this" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height
    val center: NormalizedPoint get() = NormalizedPoint((left + right) / 2f, (top + bottom) / 2f)

    /** Ground-contact point of an upright actor; used to decide whether an actor stands inside the ROI. */
    val bottomCenter: NormalizedPoint get() = NormalizedPoint((left + right) / 2f, bottom)

    fun intersectionArea(other: BoundingBox): Float {
        val w = min(right, other.right) - max(left, other.left)
        val h = min(bottom, other.bottom) - max(top, other.top)
        return if (w <= 0f || h <= 0f) 0f else w * h
    }

    /** Intersection over union in `[0, 1]`. Two zero-area boxes have IoU 0. */
    fun iou(other: BoundingBox): Float {
        val intersection = intersectionArea(other)
        val union = area + other.area - intersection
        return if (union <= 0f) 0f else intersection / union
    }

    /** Clamps the box to the unit square. A box entirely outside collapses onto the nearest edge. */
    fun clampToUnit(): BoundingBox {
        val l = left.coerceIn(0f, 1f)
        val t = top.coerceIn(0f, 1f)
        return BoundingBox(l, t, right.coerceIn(l, 1f), bottom.coerceIn(t, 1f))
    }

    /** Grows the box by [fraction] of its own width/height on every side (negative values shrink it). */
    fun expandedBy(fraction: Float): BoundingBox {
        val dx = width * fraction
        val dy = height * fraction
        return BoundingBox(left - dx, top - dy, max(left - dx, right + dx), max(top - dy, bottom + dy))
    }
}

/**
 * A closed polygon in normalised coordinates; the last vertex connects back to the first.
 * Validity rules for an operator-drawn ROI (point count, simplicity, minimum area) live in the domain layer.
 */
data class Polygon(
    val vertices: List<NormalizedPoint>,
) {
    init {
        require(
            vertices.size >= MIN_VERTICES,
        ) { "A polygon needs at least $MIN_VERTICES vertices, got ${vertices.size}" }
    }

    /** Edges as consecutive vertex pairs, including the closing edge (last → first). */
    val edges: List<Pair<NormalizedPoint, NormalizedPoint>>
        get() = vertices.indices.map { i -> vertices[i] to vertices[(i + 1) % vertices.size] }

    companion object {
        const val MIN_VERTICES = 3
    }
}
