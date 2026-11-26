package app.litterguard.core.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * A point on a monotonic clock (camera sensor timestamps or `System.nanoTime`), never wall-clock time.
 * Only differences between two [MonotonicTime] values are meaningful.
 */
@JvmInline
value class MonotonicTime(
    val nanos: Long,
) : Comparable<MonotonicTime> {
    operator fun minus(other: MonotonicTime): Duration = (nanos - other.nanos).nanoseconds

    operator fun plus(duration: Duration): MonotonicTime = MonotonicTime(nanos + duration.inWholeNanoseconds)

    operator fun minus(duration: Duration): MonotonicTime = MonotonicTime(nanos - duration.inWholeNanoseconds)

    override fun compareTo(other: MonotonicTime): Int = nanos.compareTo(other.nanos)
}

/**
 * An 8-bit grayscale image plane (for example the Y plane of a camera frame, or a downscaled copy).
 *
 * Not a data class: it wraps a mutable, possibly pooled array. Callers must not keep a reference beyond the call it
 * was passed to unless they copy it ([copy]).
 */
class LumaPlane(
    val width: Int,
    val height: Int,
    val rowStride: Int,
    val data: ByteArray,
) {
    init {
        require(width > 0 && height > 0) { "LumaPlane must be non-empty: ${width}x$height" }
        require(rowStride >= width) { "rowStride ($rowStride) must be >= width ($width)" }
        require(data.size >= rowStride * (height - 1) + width) { "data too small for ${width}x$height @ $rowStride" }
    }

    /** Luma value in `[0, 255]` at pixel ([x], [y]). */
    operator fun get(
        x: Int,
        y: Int,
    ): Int = data[y * rowStride + x].toInt() and BYTE_MASK

    /** A tightly packed (rowStride == width) deep copy, safe to retain. */
    fun copy(): LumaPlane {
        val packed = ByteArray(width * height)
        for (y in 0 until height) System.arraycopy(data, y * rowStride, packed, y * width, width)
        return LumaPlane(width, height, width, packed)
    }

    private companion object {
        const val BYTE_MASK = 0xFF
    }
}

/**
 * A platform-neutral colour image: packed ARGB_8888 pixels, row-major, tightly packed.
 * Same retention rules as [LumaPlane].
 */
class ImageBuffer(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
) {
    init {
        require(width > 0 && height > 0) { "ImageBuffer must be non-empty: ${width}x$height" }
        require(pixels.size >= width * height) { "pixels too small for ${width}x$height" }
    }

    /** ARGB pixel at ([x], [y]). */
    operator fun get(
        x: Int,
        y: Int,
    ): Int = pixels[y * width + x]

    /** A tightly packed deep copy, safe to retain. */
    fun copy(): ImageBuffer = ImageBuffer(width, height, pixels.copyOf(width * height))

    /** A new image keeping every [factor]-th pixel in both directions: cheap, and good enough for previews. */
    fun downscaled(factor: Int): ImageBuffer {
        require(factor >= 1) { "factor must be >= 1" }
        val newWidth = maxOf(1, width / factor)
        val newHeight = maxOf(1, height / factor)
        val out = IntArray(newWidth * newHeight) { this[(it % newWidth) * factor, (it / newWidth) * factor] }
        return ImageBuffer(newWidth, newHeight, out)
    }
}
