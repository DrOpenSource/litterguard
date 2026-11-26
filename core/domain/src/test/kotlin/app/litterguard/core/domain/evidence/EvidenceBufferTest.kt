package app.litterguard.core.domain.evidence

import app.litterguard.core.domain.config.DetectionConfig
import app.litterguard.core.domain.tracking.Track
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.ImageBuffer
import app.litterguard.core.model.LumaPlane
import app.litterguard.core.model.MonotonicTime
import app.litterguard.core.model.ObjectCategory
import app.litterguard.core.model.TrackId
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class EvidenceBufferTest {
    private val config = DetectionConfig()
    private val buffer = EvidenceBuffer(config)
    private val image = ImageBuffer(64, 64, IntArray(64 * 64))
    private val start = MonotonicTime(0)

    private val flat = LumaPlane(64, 64, 64, ByteArray(64 * 64) { 100 })
    private val crisp =
        Random(1).let { random -> LumaPlane(64, 64, 64, ByteArray(64 * 64) { random.nextInt(256).toByte() }) }

    private fun box(size: Float) = BoundingBox(0.5f - size / 2, 0.5f - size / 2, 0.5f + size / 2, 0.5f + size / 2)

    @Test
    fun `a flat picture has no sharpness`() {
        assertThat(EvidenceScorer.sharpness(flat, box(0.5f))).isEqualTo(0f)
        assertThat(EvidenceScorer.sharpness(crisp, box(0.5f))).isGreaterThan(0f)
    }

    @Test
    fun `bigger and more central scores higher`() {
        val corner = BoundingBox(0f, 0f, 0.3f, 0.3f)

        assertThat(EvidenceScorer.score(crisp, box(0.8f))).isGreaterThan(EvidenceScorer.score(crisp, box(0.3f)))
        assertThat(EvidenceScorer.score(crisp, box(0.3f))).isGreaterThan(EvidenceScorer.score(crisp, corner))
    }

    @Test
    fun `only the best few are kept, best first`() {
        listOf(0.2f, 0.6f, 0.3f, 0.8f, 0.1f).forEachIndexed { i, size ->
            buffer.offer(image, crisp, box(size), start + (i * 2).seconds)
        }

        assertThat(buffer.best().map { it.box }).containsExactly(box(0.8f), box(0.6f), box(0.3f)).inOrder()
    }

    @Test
    fun `pictures from the same moment replace each other instead of piling up`() {
        buffer.offer(image, crisp, box(0.3f), start)
        buffer.offer(image, crisp, box(0.5f), start + 300.milliseconds)
        buffer.offer(image, crisp, box(0.2f), start + 600.milliseconds)

        assertThat(buffer.best().map { it.box }).containsExactly(box(0.5f))
    }

    @Test
    fun `kept pictures are private copies`() {
        val source = ImageBuffer(2, 2, intArrayOf(1, 2, 3, 4))
        buffer.offer(source, crisp, box(0.5f), start)

        source.pixels[0] = 99

        assertThat(
            buffer
                .best()
                .single()
                .image.pixels[0],
        ).isEqualTo(1)
    }

    private fun track(
        category: ObjectCategory = ObjectCategory.PERSON,
        inZone: Boolean = true,
        seen: Boolean = true,
        stationaryFor: Duration = 0.seconds,
        box: BoundingBox = box(0.5f),
    ) = Track(TrackId(1), category, "x", box, 0.9f, inZone, seen, start, stationaryFor)

    @Test
    fun `only visible actors in the zone are pictured`() {
        val useless =
            listOf(
                track(category = ObjectCategory.ANIMAL),
                track(inZone = false),
                track(seen = false),
                track(stationaryFor = config.parkedActorTimeout),
            )

        buffer.offerFrame(image, crisp, useless, start)

        assertThat(buffer.best()).isEmpty()
    }

    @Test
    fun `the most useful actor decides what a frame is worth`() {
        buffer.offerFrame(image, crisp, listOf(track(box = box(0.2f)), track(box = box(0.7f))), start)

        assertThat(buffer.best().single().box).isEqualTo(box(0.7f))
    }

    @Test
    fun `clearing empties the buffer`() {
        buffer.offer(image, crisp, box(0.5f), start)

        buffer.clear()

        assertThat(buffer.best()).isEmpty()
    }
}
