package app.litterguard.core.domain.vision

import app.litterguard.core.domain.geometry.bounds
import app.litterguard.core.domain.geometry.contains
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.LumaPlane
import app.litterguard.core.model.NormalizedPoint
import app.litterguard.core.model.Polygon
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A fixed [width] × [height] grid laid over the bounding rectangle of the dump zone. Frames of any resolution are
 * sampled into it, so the motion gate and the baseline diff work in one stable coordinate system. [mask] marks the
 * cells whose centre lies inside the zone polygon; everything outside is ignored.
 */
internal class RoiRaster(
    roi: Polygon,
    val width: Int,
    val height: Int,
) {
    val bounds: BoundingBox = roi.bounds.clampToUnit()

    val mask: BooleanArray =
        BooleanArray(width * height) { i ->
            NormalizedPoint(cellCenterX(i % width), cellCenterY(i / width)) in roi
        }

    val maskCount: Int = mask.count { it }

    /** Share of the whole frame covered by one grid cell. */
    val cellFrameFraction: Float = bounds.area / (width * height)

    fun cellCenterX(x: Int): Float = bounds.left + (x + HALF) * bounds.width / width

    fun cellCenterY(y: Int): Float = bounds.top + (y + HALF) * bounds.height / height

    /** Normalised frame box covering the inclusive cell range. */
    fun cellRangeBox(
        minX: Int,
        minY: Int,
        maxX: Int,
        maxY: Int,
    ): BoundingBox =
        BoundingBox(
            left = bounds.left + minX * bounds.width / width,
            top = bounds.top + minY * bounds.height / height,
            right = bounds.left + (maxX + 1) * bounds.width / width,
            bottom = bounds.top + (maxY + 1) * bounds.height / height,
        )

    /** Area-averages [luma] into a new grid of `width × height` values in 0..255. */
    fun sample(luma: LumaPlane): IntArray {
        val xRanges = IntArray(width * 2)
        for (x in 0 until width) {
            val start = pixelStart(bounds.left + x * bounds.width / width, luma.width)
            xRanges[x * 2] = start
            xRanges[x * 2 + 1] = pixelEnd(bounds.left + (x + 1) * bounds.width / width, luma.width, start)
        }
        val out = IntArray(width * height)
        for (y in 0 until height) {
            val y0 = pixelStart(bounds.top + y * bounds.height / height, luma.height)
            val y1 = pixelEnd(bounds.top + (y + 1) * bounds.height / height, luma.height, y0)
            for (x in 0 until width) {
                out[y * width + x] = average(luma, xRanges[x * 2], xRanges[x * 2 + 1], y0, y1)
            }
        }
        return out
    }

    private fun average(
        luma: LumaPlane,
        x0: Int,
        x1: Int,
        y0: Int,
        y1: Int,
    ): Int {
        // Large cells are sub-sampled: a handful of pixels per axis is enough to suppress sensor noise.
        val stepX = max(1, (x1 - x0) / SAMPLES_PER_AXIS)
        val stepY = max(1, (y1 - y0) / SAMPLES_PER_AXIS)
        var sum = 0
        var count = 0
        for (py in y0 until y1 step stepY) {
            for (px in x0 until x1 step stepX) {
                sum += luma[px, py]
                count++
            }
        }
        return sum / count
    }

    private fun pixelStart(
        fraction: Float,
        size: Int,
    ): Int = min(size - 1, max(0, floor(fraction * size).toInt()))

    private fun pixelEnd(
        fraction: Float,
        size: Int,
        start: Int,
    ): Int = min(size, max(start + 1, ceil(fraction * size).toInt()))

    private companion object {
        const val HALF = 0.5f
        const val SAMPLES_PER_AXIS = 4
    }
}
