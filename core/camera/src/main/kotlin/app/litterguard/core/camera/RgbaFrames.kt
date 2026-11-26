package app.litterguard.core.camera

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Pixel conversion for CameraX `RGBA_8888` analysis frames. Pure, so it is unit-tested on the JVM. */
internal object RgbaFrames {
    private const val BYTES_PER_PIXEL = 4
    private const val GREEN_ALPHA_MASK = -0xff0100 // 0xFF00FF00
    private const val BYTE_MASK = 0xFF
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    // BT.601 luma weights scaled by 256.
    private const val RED_WEIGHT = 77
    private const val GREEN_WEIGHT = 150
    private const val BLUE_WEIGHT = 29

    /**
     * Fills [argb] and [luma] (both tightly packed, `width × height`) from a plane whose bytes are R, G, B, A and
     * whose rows start every [rowStride] bytes. The last row may be shorter than the stride.
     */
    fun convert(
        plane: ByteBuffer,
        rowStride: Int,
        width: Int,
        height: Int,
        argb: IntArray,
        luma: ByteArray,
    ) {
        require(rowStride % BYTES_PER_PIXEL == 0 && rowStride >= width * BYTES_PER_PIXEL) {
            "Unsupported row stride $rowStride for width $width"
        }
        val source = plane.duplicate()
        source.rewind()
        // Read as little-endian ints: the value is then A, B, G, R from the high byte down.
        val pixels = source.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
        val intsPerRow = rowStride / BYTES_PER_PIXEL
        for (y in 0 until height) {
            val offset = y * width
            pixels.position(y * intsPerRow)
            pixels.get(argb, offset, width)
            for (i in offset until offset + width) {
                val abgr = argb[i]
                val red = abgr and BYTE_MASK
                val green = (abgr shr GREEN_SHIFT) and BYTE_MASK
                val blue = (abgr shr RED_SHIFT) and BYTE_MASK
                argb[i] = (abgr and GREEN_ALPHA_MASK) or (red shl RED_SHIFT) or blue
                luma[i] = ((RED_WEIGHT * red + GREEN_WEIGHT * green + BLUE_WEIGHT * blue) shr GREEN_SHIFT).toByte()
            }
        }
    }
}
