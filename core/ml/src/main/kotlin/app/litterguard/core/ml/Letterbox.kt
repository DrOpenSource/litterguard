package app.litterguard.core.ml

import app.litterguard.core.domain.geometry.fromCrop
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.ImageBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Fits a rectangular crop of a frame into the model's square input without distorting it, padding the rest with the
 * same grey Ultralytics uses in training. Also maps the model's boxes back to full-frame coordinates.
 */
internal class Letterbox(
    private val crop: BoundingBox,
    frameWidth: Int,
    frameHeight: Int,
    private val size: Int,
) {
    private val cropLeft = crop.left * frameWidth
    private val cropTop = crop.top * frameHeight
    private val cropWidth = max(1f, crop.width * frameWidth)
    private val cropHeight = max(1f, crop.height * frameHeight)
    private val scale = min(size / cropWidth, size / cropHeight)

    private val contentWidth = (cropWidth * scale).roundToInt().coerceIn(1, size)
    private val contentHeight = (cropHeight * scale).roundToInt().coerceIn(1, size)
    private val padX = (size - contentWidth) / 2
    private val padY = (size - contentHeight) / 2

    /**
     * Writes the crop into [out] as planar RGB (`[3, size, size]`, values 0..1), sampling bilinearly.
     */
    fun writeNchw(
        image: ImageBuffer,
        out: FloatArray,
    ) {
        require(out.size == CHANNELS * size * size) { "Input array must hold 3×$size×$size values" }
        out.fill(PAD_VALUE)
        val plane = size * size
        val stepX = cropWidth / contentWidth
        val stepY = cropHeight / contentHeight
        for (y in 0 until contentHeight) {
            val sourceY = (cropTop + (y + HALF) * stepY - HALF).coerceIn(0f, image.height - 1f)
            val y0 = sourceY.toInt()
            val y1 = min(y0 + 1, image.height - 1)
            val fy = sourceY - y0
            for (x in 0 until contentWidth) {
                val sourceX = (cropLeft + (x + HALF) * stepX - HALF).coerceIn(0f, image.width - 1f)
                val x0 = sourceX.toInt()
                val x1 = min(x0 + 1, image.width - 1)
                val fx = sourceX - x0
                val p00 = image[x0, y0]
                val p10 = image[x1, y0]
                val p01 = image[x0, y1]
                val p11 = image[x1, y1]
                val index = (y + padY) * size + (x + padX)
                for (channel in 0 until CHANNELS) {
                    val shift = RED_SHIFT - channel * BITS_PER_CHANNEL
                    val top = lerp((p00 shr shift) and BYTE_MASK, (p10 shr shift) and BYTE_MASK, fx)
                    val bottom = lerp((p01 shr shift) and BYTE_MASK, (p11 shr shift) and BYTE_MASK, fx)
                    out[channel * plane + index] = (top + (bottom - top) * fy) / MAX_BYTE
                }
            }
        }
    }

    /** Maps a box in input coordinates normalised to `[0, 1]` back to normalised full-frame coordinates. */
    fun toFrame(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): BoundingBox {
        fun cropX(v: Float) = ((v * size - padX) / contentWidth).coerceIn(0f, 1f)

        fun cropY(v: Float) = ((v * size - padY) / contentHeight).coerceIn(0f, 1f)

        val l = cropX(left)
        val t = cropY(top)
        return BoundingBox(l, t, max(l, cropX(right)), max(t, cropY(bottom))).fromCrop(crop)
    }

    private fun lerp(
        a: Int,
        b: Int,
        share: Float,
    ): Float = a + (b - a) * share

    companion object {
        private const val CHANNELS = 3
        private const val HALF = 0.5f
        private const val RED_SHIFT = 16
        private const val BITS_PER_CHANNEL = 8
        private const val BYTE_MASK = 0xFF
        private const val MAX_BYTE = 255f

        /** Ultralytics letterbox padding: grey 114. */
        const val PAD_VALUE = 114f / 255f
    }
}
