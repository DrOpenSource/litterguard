package app.litterguard.core.domain.vision

import app.litterguard.core.domain.config.DetectionConfig
import app.litterguard.core.domain.scenario.SCENE_ZONE
import app.litterguard.core.domain.scenario.SyntheticScene
import app.litterguard.core.domain.scenario.bag
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.LumaPlane
import app.litterguard.core.model.MonotonicTime
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class MotionGateTest {
    private val gate = MotionGate(SCENE_ZONE, DetectionConfig())

    @Test
    fun `the first frame is not motion`() {
        assertThat(gate.update(SyntheticScene(emptyList()).lumaAt(0.0)).isMotion).isFalse()
    }

    @Test
    fun `sensor noise is not motion`() {
        val scene = SyntheticScene(emptyList())
        gate.update(scene.lumaAt(0.0))

        assertThat(gate.update(scene.lumaAt(1.0)).isMotion).isFalse()
    }

    @Test
    fun `something appearing in the zone is motion`() {
        val scene = SyntheticScene(listOf(bag(0.5f, 0.7f, from = 1.0)))
        gate.update(scene.lumaAt(0.0))

        assertThat(gate.update(scene.lumaAt(1.0)).isMotion).isTrue()
    }

    @Test
    fun `changes outside the zone are ignored`() {
        val scene = SyntheticScene(listOf(bag(0.08f, 0.2f, from = 1.0)))
        gate.update(scene.lumaAt(0.0))

        assertThat(gate.update(scene.lumaAt(1.0)).isMotion).isFalse()
    }

    @Test
    fun `motion explained by a departed actor can be discounted`() {
        val scene = SyntheticScene(listOf(bag(0.5f, 0.7f, from = 1.0)))
        gate.update(scene.lumaAt(0.0))
        gate.update(scene.lumaAt(1.0))

        assertThat(gate.hasMotionOutside(emptyList())).isTrue()
        assertThat(gate.hasMotionOutside(listOf(BoundingBox(0.4f, 0.5f, 0.6f, 0.75f)))).isFalse()
    }
}

class BaselineModelTest {
    private val config = DetectionConfig()
    private val model = BaselineModel(SCENE_ZONE, config)
    private val start = MonotonicTime(0)
    private val empty = SyntheticScene(emptyList())

    private fun compared(
        luma: LumaPlane,
        occupied: List<BoundingBox> = emptyList(),
    ) = model.compare(luma, occupied) as BaselineDiff.Compared

    private fun capture() = model.offerQuiet(empty.lumaAt(0.0), start)

    @Test
    fun `nothing can be said before a baseline exists`() {
        assertThat(model.compare(empty.lumaAt(0.0))).isEqualTo(BaselineDiff.Unavailable)
    }

    @Test
    fun `an unchanged scene has no blobs`() {
        capture()

        assertThat(compared(empty.lumaAt(5.0)).blobs).isEmpty()
    }

    @Test
    fun `a new object is found where it is`() {
        capture()

        val blobs = compared(SyntheticScene(listOf(bag(0.5f, 0.7f, from = 0.0))).lumaAt(5.0)).blobs

        assertThat(blobs).hasSize(1)
        assertThat(blobs.single().box.iou(BoundingBox(0.465f, 0.61f, 0.535f, 0.70f))).isGreaterThan(0.7f)
    }

    @Test
    fun `a global brightness change is normalised away`() {
        capture()

        val diff = compared(SyntheticScene(emptyList(), brightness = { 45 }).lumaAt(5.0))

        assertThat(diff.blobs).isEmpty()
        assertThat(diff.changedFraction).isLessThan(0.01f)
    }

    @Test
    fun `a soft-edged shadow is not an object`() {
        capture()
        val source = empty.lumaAt(0.0)
        // Darkness that ramps up gently over many pixels, so no new outline appears anywhere.
        val shaded =
            ByteArray(source.data.size) { i ->
                val x = i % source.width
                val y = i / source.width
                val reach = maxOf(kotlin.math.abs(x - 320) / 140f, kotlin.math.abs(y - 220) / 100f)
                val darkness = ((1f - reach).coerceIn(0f, 1f) * 70).toInt()
                (source[x, y] - darkness).coerceIn(0, 255).toByte()
            }

        val diff = compared(LumaPlane(source.width, source.height, source.width, shaded))

        assertThat(diff.blobs).isEmpty()
    }

    @Test
    fun `camera shake changes too much of the zone to judge`() {
        capture()

        val diff = compared(SyntheticScene(emptyList(), shake = { 6 }).lumaAt(5.0))

        assertThat(diff.changedFraction).isGreaterThan(config.illuminationAbortFraction)
    }

    @Test
    fun `a tiny speck is below the minimum object size`() {
        capture()
        val speck =
            app.litterguard.core.domain.scenario.Sprite(
                category = null,
                width = 0.015f,
                height = 0.02f,
                tone = 20,
                path =
                    listOf(
                        app.litterguard.core.domain.scenario
                            .Key(0.0, 0.5f, 0.7f),
                        app.litterguard.core.domain.scenario
                            .Key(9.0, 0.5f, 0.7f),
                    ),
            )

        assertThat(compared(SyntheticScene(listOf(speck)).lumaAt(5.0)).blobs).isEmpty()
    }

    @Test
    fun `a blob under a live actor box is ignored`() {
        capture()
        val frame = SyntheticScene(listOf(bag(0.5f, 0.7f, from = 0.0))).lumaAt(5.0)

        val diff = compared(frame, listOf(BoundingBox(0.40f, 0.40f, 0.60f, 0.75f)))

        assertThat(diff.blobs).isEmpty()
        assertThat(diff.hiddenFraction).isGreaterThan(0f)
    }

    @Test
    fun `absorbing an object stops it being reported again`() {
        capture()
        val withBag = SyntheticScene(listOf(bag(0.5f, 0.7f, from = 0.0)))

        model.rebaseline(withBag.lumaAt(5.0), start + 5.seconds)

        assertThat(compared(withBag.lumaAt(6.0)).blobs).isEmpty()
    }

    @Test
    fun `a frozen baseline ignores quiet frames`() {
        capture()
        model.freeze()
        val withBag = SyntheticScene(listOf(bag(0.5f, 0.7f, from = 0.0)))

        model.offerQuiet(withBag.lumaAt(40.0), start + 40.seconds)

        assertThat(compared(withBag.lumaAt(41.0)).blobs).hasSize(1)
    }

    @Test
    fun `one odd snapshot is outvoted by the median`() {
        capture()
        model.offerQuiet(empty.lumaAt(31.0), start + 31.seconds)
        val withBag = SyntheticScene(listOf(bag(0.5f, 0.7f, from = 0.0)))
        model.offerQuiet(withBag.lumaAt(62.0), start + 62.seconds)

        assertThat(compared(empty.lumaAt(63.0)).blobs).isEmpty()
    }

    @Test
    fun `quiet frames are only kept every refresh interval`() {
        capture()
        val withBag = SyntheticScene(listOf(bag(0.5f, 0.7f, from = 0.0)))

        model.offerQuiet(withBag.lumaAt(10.0), start + 10.seconds)

        assertThat(compared(withBag.lumaAt(11.0)).blobs).hasSize(1)
    }
}
