package app.litterguard.core.ml

import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.ImageBuffer
import app.litterguard.core.model.ObjectCategory
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

private const val SIZE = 416
private val FULL = BoundingBox(0f, 0f, 1f, 1f)

private fun assertBox(
    actual: BoundingBox,
    expected: BoundingBox,
) {
    assertThat(actual.left).isWithin(0.005f).of(expected.left)
    assertThat(actual.top).isWithin(0.005f).of(expected.top)
    assertThat(actual.right).isWithin(0.005f).of(expected.right)
    assertThat(actual.bottom).isWithin(0.005f).of(expected.bottom)
}

class LetterboxTest {
    // A 16:9 frame in a square input is padded above and below: content is 416×234, padding 91 px.
    private val wide = Letterbox(FULL, 1280, 720, SIZE)

    @Test
    fun `maps input boxes back through the padding`() {
        val mapped = wide.toFrame(0f, 91f / SIZE, 1f, (91f + 234f) / SIZE)

        assertBox(mapped, FULL)
    }

    @Test
    fun `maps through a crop into frame coordinates`() {
        val crop = BoundingBox(0.25f, 0.25f, 0.75f, 0.75f)
        val letterbox = Letterbox(crop, 1280, 720, SIZE)

        val mapped = letterbox.toFrame(0.5f, 0.5f, 0.5f, 0.5f)

        assertBox(mapped, BoundingBox(0.5f, 0.5f, 0.5f, 0.5f))
    }

    @Test
    fun `boxes in the padding are clamped to the crop`() {
        assertBox(wide.toFrame(0f, 0f, 1f, 1f), FULL)
    }

    @Test
    fun `writes planar RGB with grey padding`() {
        val orange = 0xFFFF8000.toInt()
        val image = ImageBuffer(1280, 720, IntArray(1280 * 720) { orange })
        val out = FloatArray(3 * SIZE * SIZE)

        wide.writeNchw(image, out)

        val centre = 208 * SIZE + 208
        assertThat(out[centre]).isWithin(0.001f).of(1f)
        assertThat(out[SIZE * SIZE + centre]).isWithin(0.001f).of(128f / 255f)
        assertThat(out[2 * SIZE * SIZE + centre]).isWithin(0.001f).of(0f)
        assertThat(out[10 * SIZE + 208]).isEqualTo(Letterbox.PAD_VALUE)
    }

    @Test
    fun `samples only inside the crop`() {
        // Left half black, right half white; a crop of the right half must be all white.
        val image = ImageBuffer(100, 100, IntArray(100 * 100) { if (it % 100 < 50) 0 else -1 })
        val out = FloatArray(3 * SIZE * SIZE)

        Letterbox(BoundingBox(0.52f, 0f, 1f, 0.48f), 100, 100, SIZE).writeNchw(image, out)

        assertThat(out[208 * SIZE + 208]).isWithin(0.001f).of(1f)
        assertThat(out[208 * SIZE + 2]).isWithin(0.001f).of(1f)
    }
}

class YoloDecoderTest {
    private val anchors = 4
    private val decoder = YoloDecoder(anchors, confidenceFloor = 0.25f, nmsIou = 0.45f)
    private val square = Letterbox(FULL, SIZE, SIZE, SIZE)
    private val output = FloatArray(84 * anchors)

    private fun put(
        anchor: Int,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        vararg scores: Pair<String, Float>,
    ) {
        listOf(cx, cy, w, h).forEachIndexed { channel, value -> output[channel * anchors + anchor] = value }
        for ((label, score) in scores) output[(4 + CocoClasses.names.indexOf(label)) * anchors + anchor] = score
    }

    @Test
    fun `reads a channels-first box and its best class`() {
        put(1, 0.5f, 0.6f, 0.2f, 0.4f, "person" to 0.9f, "dog" to 0.3f)

        val detection = decoder.decode(output, square).single()

        assertThat(detection.label).isEqualTo("person")
        assertThat(detection.category).isEqualTo(ObjectCategory.PERSON)
        assertThat(detection.confidence).isEqualTo(0.9f)
        assertBox(detection.box, BoundingBox(0.4f, 0.4f, 0.6f, 0.8f))
    }

    @Test
    fun `ignores weak scores and classes that don't matter`() {
        put(0, 0.5f, 0.5f, 0.2f, 0.2f, "person" to 0.2f)
        put(1, 0.5f, 0.5f, 0.2f, 0.2f, "chair" to 0.95f)

        assertThat(decoder.decode(output, square)).isEmpty()
    }

    @Test
    fun `one vehicle seen as car and truck is reported once`() {
        put(0, 0.5f, 0.5f, 0.4f, 0.3f, "car" to 0.8f)
        put(1, 0.51f, 0.5f, 0.4f, 0.3f, "truck" to 0.6f)

        assertThat(decoder.decode(output, square).map { it.label }).containsExactly("car")
    }

    @Test
    fun `a rider and their motorcycle are both kept`() {
        put(0, 0.5f, 0.45f, 0.2f, 0.4f, "person" to 0.8f)
        put(1, 0.5f, 0.5f, 0.25f, 0.35f, "motorcycle" to 0.7f)

        assertThat(decoder.decode(output, square).map { it.label }).containsExactly("person", "motorcycle")
    }

    @Test
    fun `separate people are both kept`() {
        put(0, 0.2f, 0.5f, 0.1f, 0.3f, "person" to 0.8f)
        put(1, 0.8f, 0.5f, 0.1f, 0.3f, "person" to 0.7f)

        assertThat(decoder.decode(output, square)).hasSize(2)
    }
}

class ModelManifestTest {
    private val manifest = ModelManifest.parse(File("models.json").readText())

    @Test
    fun `the committed manifest parses and is self-consistent`() {
        val detector = manifest.detector
        val size = detector.input.size
        val strides = listOf(8, 16, 32)

        assertThat(detector.output.anchors).isEqualTo(strides.sumOf { (size / it) * (size / it) })
        assertThat(detector.output.channels).isEqualTo(4 + CocoClasses.names.size)
        assertThat(detector.input.layout).isEqualTo("NCHW")
    }

    @Test
    fun `variants are listed in the order ADR-0003 chose`() {
        assertThat(manifest.detector.variants.map { it.id to it.accelerator })
            .containsExactly("int8" to "CPU", "fp32" to "GPU")
            .inOrder()
    }

    @Test
    fun `class names match their count`() {
        assertThat(CocoClasses.names).hasSize(80)
        assertThat(CocoClasses.category("cow")).isEqualTo(ObjectCategory.ANIMAL)
        assertThat(CocoClasses.category("motorcycle")).isEqualTo(ObjectCategory.VEHICLE)
        assertThat(CocoClasses.category("handbag")).isEqualTo(ObjectCategory.OTHER)
    }
}
