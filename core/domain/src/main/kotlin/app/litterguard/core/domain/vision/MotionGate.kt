package app.litterguard.core.domain.vision

import app.litterguard.core.domain.config.DetectionConfig
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.LumaPlane
import app.litterguard.core.model.Polygon
import kotlin.math.abs

/** @property changedFraction share of zone cells that changed since the previous frame. */
data class MotionScore(
    val changedFraction: Float,
    val isMotion: Boolean,
)

/**
 * Stage 0 of the pipeline (FR-2.2): a cheap frame difference over a 160×120 grayscale grid of the dump zone. The
 * object detector only runs when this reports motion (or something is already going on). Stateful: not thread-safe.
 */
class MotionGate(
    roi: Polygon,
    private val config: DetectionConfig,
) {
    private val raster = RoiRaster(roi, GRID_WIDTH, GRID_HEIGHT)
    private var previous: IntArray? = null
    private val changed = BooleanArray(GRID_WIDTH * GRID_HEIGHT)

    /** Scores [luma] against the previous frame. The first frame never counts as motion. */
    fun update(luma: LumaPlane): MotionScore {
        val current = raster.sample(luma)
        val before = previous
        previous = current
        changed.fill(false)
        if (before == null || raster.maskCount == 0) return MotionScore(0f, false)

        var count = 0
        for (i in current.indices) {
            if (raster.mask[i] && abs(current[i] - before[i]) > config.motionLumaThreshold) {
                changed[i] = true
                count++
            }
        }
        val fraction = count.toFloat() / raster.maskCount
        return MotionScore(fraction, fraction >= config.motionMinFraction)
    }

    /**
     * Whether the latest frame still shows motion once the cells under [explained] boxes are ignored. Used while
     * settling: someone driving away from the zone shouldn't postpone judging what they left in it.
     */
    fun hasMotionOutside(explained: List<BoundingBox>): Boolean {
        var count = 0
        for (i in changed.indices) {
            if (!changed[i]) continue
            val x = raster.cellCenterX(i % GRID_WIDTH)
            val y = raster.cellCenterY(i / GRID_WIDTH)
            if (explained.none { it.covers(x, y) }) count++
        }
        return raster.maskCount > 0 && count.toFloat() / raster.maskCount >= config.motionMinFraction
    }

    private companion object {
        const val GRID_WIDTH = 160
        const val GRID_HEIGHT = 120
    }
}
