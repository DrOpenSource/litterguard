package app.litterguard.core.domain.geometry

import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.NormalizedPoint
import app.litterguard.core.model.Polygon
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Tolerance for "on the edge" and collinearity tests, in normalised units (~0.001 px at 1280 px). */
internal const val GEOMETRY_EPSILON = 1e-6

/**
 * Whether [point] lies inside the polygon. Points exactly on an edge or vertex count as **inside**, so an actor
 * standing on the drawn boundary is treated consistently (FR-2.4).
 */
operator fun Polygon.contains(point: NormalizedPoint): Boolean {
    if (edges.any { (a, b) -> isOnSegment(point, a, b) }) return true
    // Crossing-number (ray casting) test with a horizontal ray towards +x.
    var inside = false
    for ((a, b) in edges) {
        val crosses = (a.y > point.y) != (b.y > point.y)
        if (crosses) {
            val xAtY = a.x + (point.y - a.y) * (b.x - a.x) / (b.y - a.y)
            if (point.x < xAtY) inside = !inside
        }
    }
    return inside
}

/** Absolute area of the polygon (shoelace formula), in normalised units, so 1.0 is the whole frame. */
val Polygon.area: Float
    get() {
        var twiceArea = 0.0
        for ((a, b) in edges) twiceArea += a.x.toDouble() * b.y - b.x.toDouble() * a.y
        return (abs(twiceArea) / 2.0).toFloat()
    }

/** Axis-aligned bounds of the polygon. */
val Polygon.bounds: BoundingBox
    get() =
        BoundingBox(
            left = vertices.minOf { it.x },
            top = vertices.minOf { it.y },
            right = vertices.maxOf { it.x },
            bottom = vertices.maxOf { it.y },
        )

/**
 * True when no two non-adjacent edges touch or cross. Operator-drawn ROIs must be simple, or the inside test is
 * ill-defined (FR-1.2).
 */
val Polygon.isSimple: Boolean
    get() {
        val e = edges
        val n = e.size
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val adjacent = j == i + 1 || (i == 0 && j == n - 1)
                if (!adjacent && segmentsIntersect(e[i].first, e[i].second, e[j].first, e[j].second)) return false
            }
        }
        return true
    }

/**
 * The region the detector should see for ROI-crop inference (ARCHITECTURE §4): the ROI bounds grown by [margin]
 * (a share of the ROI's own width/height) and clamped to the frame.
 */
fun Polygon.cropRect(margin: Float): BoundingBox = bounds.expandedBy(margin).clampToUnit()

private fun cross(
    o: NormalizedPoint,
    a: NormalizedPoint,
    b: NormalizedPoint,
): Double = (a.x.toDouble() - o.x) * (b.y.toDouble() - o.y) - (a.y.toDouble() - o.y) * (b.x.toDouble() - o.x)

private fun isOnSegment(
    p: NormalizedPoint,
    a: NormalizedPoint,
    b: NormalizedPoint,
): Boolean =
    abs(cross(a, b, p)) <= GEOMETRY_EPSILON &&
        p.x >= min(a.x, b.x) - GEOMETRY_EPSILON && p.x <= max(a.x, b.x) + GEOMETRY_EPSILON &&
        p.y >= min(a.y, b.y) - GEOMETRY_EPSILON && p.y <= max(a.y, b.y) + GEOMETRY_EPSILON

private fun segmentsIntersect(
    p1: NormalizedPoint,
    p2: NormalizedPoint,
    q1: NormalizedPoint,
    q2: NormalizedPoint,
): Boolean {
    val d1 = cross(q1, q2, p1)
    val d2 = cross(q1, q2, p2)
    val d3 = cross(p1, p2, q1)
    val d4 = cross(p1, p2, q2)
    val properCrossing =
        ((d1 > GEOMETRY_EPSILON && d2 < -GEOMETRY_EPSILON) || (d1 < -GEOMETRY_EPSILON && d2 > GEOMETRY_EPSILON)) &&
            ((d3 > GEOMETRY_EPSILON && d4 < -GEOMETRY_EPSILON) || (d3 < -GEOMETRY_EPSILON && d4 > GEOMETRY_EPSILON))
    return properCrossing ||
        isOnSegment(p1, q1, q2) || isOnSegment(p2, q1, q2) ||
        isOnSegment(q1, p1, p2) || isOnSegment(q2, p1, p2)
}
