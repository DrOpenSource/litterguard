package app.litterguard.core.domain.tracking

import app.litterguard.core.domain.config.DetectionConfig
import app.litterguard.core.domain.scenario.SCENE_ZONE
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.Detection
import app.litterguard.core.model.MonotonicTime
import app.litterguard.core.model.ObjectCategory
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TrackerTest {
    private val tracker = Tracker(SCENE_ZONE, DetectionConfig())
    private var frame = 0

    private fun step(vararg detections: Detection): List<Track> =
        tracker.update(detections.toList(), MonotonicTime(0) + (333 * frame++).milliseconds)

    /** A box whose ground-contact point is ([x], [y]). */
    private fun at(
        x: Float,
        y: Float = 0.8f,
        category: ObjectCategory = ObjectCategory.PERSON,
        width: Float = 0.1f,
        height: Float = 0.3f,
    ) = Detection(BoundingBox(x - width / 2, y - height, x + width / 2, y), category, category.name, 0.9f)

    @Test
    fun `an actor counts as entered only after two frames in the zone`() {
        assertThat(step(at(0.5f)).single().inZone).isFalse()
        assertThat(step(at(0.51f)).single().inZone).isTrue()
    }

    @Test
    fun `the ground contact point decides what is in the zone`() {
        // The box overlaps the zone heavily, but the feet are below it.
        repeat(3) { step(at(0.5f, y = 0.99f)) }

        assertThat(step(at(0.5f, y = 0.99f)).single().inZone).isFalse()
    }

    @Test
    fun `a walking person keeps one id`() {
        val ids = (0..8).map { step(at(0.25f + it * 0.06f)).single().id }

        assertThat(ids.toSet()).hasSize(1)
    }

    @Test
    fun `a fast mover without box overlap is still followed`() {
        val first = step(at(0.30f, width = 0.06f)).single().id
        val second = step(at(0.40f, width = 0.06f)).single().id

        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `a gap of one or two frames does not end the track or the visit`() {
        step(at(0.5f))
        val id = step(at(0.5f)).single().id
        step()
        val coasting = step().single()

        assertThat(coasting.inZone).isTrue()
        assertThat(coasting.seenThisFrame).isFalse()
        assertThat(step(at(0.5f)).single().id).isEqualTo(id)
    }

    @Test
    fun `three missed frames end the track`() {
        step(at(0.5f))
        step(at(0.5f))
        step()
        step()

        assertThat(step()).isEmpty()
        assertThat(tracker.hasTracks).isFalse()
    }

    @Test
    fun `walking out of the zone ends the visit after three frames`() {
        step(at(0.70f))
        step(at(0.74f))

        val states = listOf(0.83f, 0.86f, 0.89f).map { step(at(it)).single().inZone }

        assertThat(states).containsExactly(true, true, false).inOrder()
    }

    @Test
    fun `two people crossing keep their own ids`() {
        val start = step(at(0.35f, y = 0.70f), at(0.65f, y = 0.90f))
        val left = start[0].id
        val right = start[1].id
        var last = start
        for (i in 1..6) {
            last = step(at(0.35f + i * 0.05f, y = 0.70f), at(0.65f - i * 0.05f, y = 0.90f))
        }

        assertThat(
            last
                .single { it.id == left }
                .box.center.x,
        ).isGreaterThan(0.6f)
        assertThat(
            last
                .single { it.id == right }
                .box.center.x,
        ).isLessThan(0.4f)
    }

    @Test
    fun `a one-frame misclassification does not change the category`() {
        repeat(3) { step(at(0.5f)) }

        val track = step(at(0.5f, category = ObjectCategory.ANIMAL)).single()

        assertThat(track.category).isEqualTo(ObjectCategory.PERSON)
    }

    @Test
    fun `a parked vehicle accumulates standing-still time`() {
        val parked = at(0.5f, category = ObjectCategory.VEHICLE, width = 0.3f)
        var track = step(parked).single()
        repeat(12) { track = step(parked).single() }

        assertThat(track.stationaryFor).isAtLeast(3.seconds)
    }

    @Test
    fun `moving resets the standing-still time`() {
        repeat(6) { step(at(0.3f)) }

        val moved = step(at(0.36f)).single()

        assertThat(moved.stationaryFor).isEqualTo(0.seconds)
    }
}
