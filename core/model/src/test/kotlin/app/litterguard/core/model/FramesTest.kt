package app.litterguard.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FramesTest {
    @Test
    fun `monotonic time differences are durations`() {
        val t0 = MonotonicTime(1_000_000_000L)
        val t1 = t0 + 1500.milliseconds
        assertThat(t1 - t0).isEqualTo(1.5.seconds)
        assertThat(t1 > t0).isTrue()
    }

    @Test
    fun `luma plane reads unsigned values with row stride`() {
        // 2x2 image stored with a stride of 3 (one padding byte per row).
        val data = byteArrayOf(10, 200.toByte(), 0, 30, 255.toByte(), 0)
        val plane = LumaPlane(width = 2, height = 2, rowStride = 3, data = data)
        assertThat(plane[1, 0]).isEqualTo(200)
        assertThat(plane[1, 1]).isEqualTo(255)
    }

    @Test
    fun `luma copy is tightly packed and independent of the source`() {
        val data = byteArrayOf(1, 2, 9, 3, 4, 9)
        val copy = LumaPlane(2, 2, 3, data).copy()
        data[0] = 99
        assertThat(copy.rowStride).isEqualTo(2)
        assertThat(copy.data.toList()).containsExactly(1.toByte(), 2.toByte(), 3.toByte(), 4.toByte()).inOrder()
    }

    @Test
    fun `luma plane rejects a buffer that is too small`() {
        assertThrows<IllegalArgumentException> { LumaPlane(4, 4, 4, ByteArray(10)) }
    }

    @Test
    fun `image buffer indexes row-major pixels`() {
        val img = ImageBuffer(2, 2, intArrayOf(1, 2, 3, 4))
        assertThat(img[0, 1]).isEqualTo(3)
    }

    @Test
    fun `downscaling keeps every nth pixel`() {
        val img = ImageBuffer(4, 2, intArrayOf(1, 2, 3, 4, 5, 6, 7, 8))

        val half = img.downscaled(2)

        assertThat(half.width to half.height).isEqualTo(2 to 1)
        assertThat(half.pixels.toList()).containsExactly(1, 3).inOrder()
    }
}
