package app.litterguard.core.domain.ports

import app.litterguard.core.model.ImageBuffer
import app.litterguard.core.model.LumaPlane
import app.litterguard.core.model.MonotonicTime
import kotlinx.coroutines.flow.Flow

/**
 * One upright camera frame. [image] and [luma] are pooled: they are only valid until [close], which the collector
 * must call exactly once on every path. Copy anything that has to outlive the frame.
 */
class AnalysisFrame(
    val image: ImageBuffer,
    val luma: LumaPlane,
    val timestamp: MonotonicTime,
    private val onClose: () -> Unit = {},
) : AutoCloseable {
    private var closed = false

    override fun close() {
        if (closed) return
        closed = true
        onClose()
    }
}

fun interface FrameSource {
    /**
     * Collecting opens the camera; cancelling releases it. At most [maxFps] frames per second are emitted, and
     * frames that arrive while the collector is busy are dropped, never queued (FR-2.1).
     */
    fun frames(maxFps: Float): Flow<AnalysisFrame>
}
