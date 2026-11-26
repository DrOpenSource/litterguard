package app.litterguard.core.camera

import app.litterguard.core.domain.ports.AnalysisFrame
import app.litterguard.core.model.ImageBuffer
import app.litterguard.core.model.LumaPlane
import app.litterguard.core.model.MonotonicTime

/**
 * Reusable frame buffers, so steady-state analysis doesn't allocate. When every buffer is still held by the
 * collector, [acquire] returns null and the camera frame is dropped. Thread-safe.
 */
internal class FramePool(
    private val capacity: Int,
) {
    class Buffers(
        val width: Int,
        val height: Int,
    ) {
        val argb = IntArray(width * height)
        val luma = ByteArray(width * height)
    }

    private val free = ArrayDeque<Buffers>()
    private var width = 0
    private var height = 0
    private var lent = 0

    /** Buffers currently held by the collector; a steadily growing number would mean a leak. */
    @get:Synchronized
    val inUse: Int get() = lent

    @Synchronized
    fun acquire(
        frameWidth: Int,
        frameHeight: Int,
    ): Buffers? {
        if (frameWidth != width || frameHeight != height) {
            width = frameWidth
            height = frameHeight
            free.clear()
        }
        if (lent >= capacity) return null
        lent++
        return free.removeLastOrNull() ?: Buffers(frameWidth, frameHeight)
    }

    @Synchronized
    fun release(buffers: Buffers) {
        lent--
        if (buffers.width == width && buffers.height == height) free.addLast(buffers)
    }

    fun frame(
        buffers: Buffers,
        timestamp: MonotonicTime,
    ) = AnalysisFrame(
        image = ImageBuffer(buffers.width, buffers.height, buffers.argb),
        luma = LumaPlane(buffers.width, buffers.height, buffers.width, buffers.luma),
        timestamp = timestamp,
        onClose = { release(buffers) },
    )
}

/** Lets through at most `maxFps` frames per second, judged by their timestamps (FR-2.1). Single-threaded. */
internal class FrameThrottle(
    maxFps: Float,
) {
    private val minGapNanos = (NANOS_PER_SECOND / maxFps).toLong() - JITTER_NANOS
    private var last: Long? = null

    fun accept(nowNanos: Long): Boolean {
        val previous = last
        if (previous != null && nowNanos - previous < minGapNanos) return false
        last = nowNanos
        return true
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0

        /** Sensor frames arrive a few milliseconds early or late; don't let that cost a whole frame interval. */
        const val JITTER_NANOS = 10_000_000L
    }
}
