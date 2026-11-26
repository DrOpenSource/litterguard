package app.litterguard.core.camera

import app.litterguard.core.model.MonotonicTime
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class RgbaFramesTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `converts RGBA bytes to ARGB ints and luma, skipping row padding`() {
        // 2×2 with 4 bytes of padding per row; the last row has no padding.
        val plane =
            ByteBuffer.wrap(
                bytes(
                    255,
                    0,
                    0,
                    255,
                    0,
                    255,
                    0,
                    255,
                    9,
                    9,
                    9,
                    9,
                    0,
                    0,
                    255,
                    255,
                    255,
                    255,
                    255,
                    255,
                ),
            )
        val argb = IntArray(4)
        val luma = ByteArray(4)

        RgbaFrames.convert(plane, rowStride = 12, width = 2, height = 2, argb = argb, luma = luma)

        assertThat(argb.toList())
            .containsExactly(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), -1)
            .inOrder()
        assertThat(luma.map { it.toInt() and 0xFF }).containsExactly(76, 149, 28, 255).inOrder()
    }

    @Test
    fun `leaves the caller's buffer position alone`() {
        val plane = ByteBuffer.wrap(ByteArray(8))
        plane.position(3)

        RgbaFrames.convert(plane, rowStride = 8, width = 2, height = 1, argb = IntArray(2), luma = ByteArray(2))

        assertThat(plane.position()).isEqualTo(3)
    }
}

class FramePoolTest {
    private val pool = FramePool(capacity = 2)

    @Test
    fun `runs out instead of allocating without limit`() {
        pool.acquire(4, 4)
        pool.acquire(4, 4)

        assertThat(pool.acquire(4, 4)).isNull()
    }

    @Test
    fun `closing a frame returns its buffers once`() {
        val buffers = requireNotNull(pool.acquire(4, 4))
        val frame = pool.frame(buffers, MonotonicTime(0))

        frame.close()
        frame.close()

        assertThat(pool.inUse).isEqualTo(0)
        assertThat(pool.acquire(4, 4)).isSameInstanceAs(buffers)
    }

    @Test
    fun `a resolution change discards old buffers`() {
        val old = requireNotNull(pool.acquire(4, 4))
        pool.release(old)

        val resized = requireNotNull(pool.acquire(8, 4))

        assertThat(resized.width).isEqualTo(8)
        assertThat(resized).isNotSameInstanceAs(old)
    }
}

class MountRotationTest {
    @Test
    fun `another app turning the screen upright does not turn the camera picture`() {
        // Surface.ROTATION_0..ROTATION_270 are 0..3.
        assertThat((0..3).filter(MountRotation::isLandscape)).containsExactly(1, 3).inOrder()
    }
}

class FrameThrottleTest {
    private val millis = 1_000_000L

    @Test
    fun `lets three frames a second through a thirty-fps camera`() {
        val throttle = FrameThrottle(maxFps = 3f)

        val accepted = (0 until 300).count { throttle.accept(it * 1000 * millis / 30) }

        assertThat(accepted).isIn(29..31)
    }

    @Test
    fun `the first frame always passes`() {
        assertThat(FrameThrottle(1f).accept(0)).isTrue()
    }
}
