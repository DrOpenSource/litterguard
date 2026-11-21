package app.litterguard.core.domain.geometry

import app.litterguard.core.model.NormalizedPoint
import app.litterguard.core.model.Polygon

/** Why an operator-drawn ROI is or isn't usable. The setup screen maps each case to a plain-language message. */
sealed interface RoiValidation {
    data class Valid(
        val roi: Polygon,
    ) : RoiValidation

    data class TooFewPoints(
        val count: Int,
        val min: Int,
    ) : RoiValidation

    data class TooManyPoints(
        val count: Int,
        val max: Int,
    ) : RoiValidation

    data object OutsideFrame : RoiValidation

    data object SelfIntersecting : RoiValidation

    data class TooSmall(
        val areaFraction: Float,
        val minAreaFraction: Float,
    ) : RoiValidation
}

/** Rules for the dump-zone polygon the operator taps on the preview (FR-1.2). */
object RoiRules {
    const val MIN_POINTS = 4
    const val MAX_POINTS = 12

    /** The zone must cover at least this share of the frame, or detection becomes unreliable. */
    const val MIN_AREA_FRACTION = 0.005f

    fun validate(points: List<NormalizedPoint>): RoiValidation {
        // Built lazily: only evaluated once the point count is known to be valid.
        val polygon by lazy { Polygon(points) }
        return when {
            points.size < MIN_POINTS -> RoiValidation.TooFewPoints(points.size, MIN_POINTS)
            points.size > MAX_POINTS -> RoiValidation.TooManyPoints(points.size, MAX_POINTS)
            points.any { it.x !in 0f..1f || it.y !in 0f..1f } -> RoiValidation.OutsideFrame
            !polygon.isSimple -> RoiValidation.SelfIntersecting
            polygon.area < MIN_AREA_FRACTION -> RoiValidation.TooSmall(polygon.area, MIN_AREA_FRACTION)
            else -> RoiValidation.Valid(polygon)
        }
    }
}
