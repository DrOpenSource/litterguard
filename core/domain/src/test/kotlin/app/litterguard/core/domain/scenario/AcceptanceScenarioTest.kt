package app.litterguard.core.domain.scenario

import app.litterguard.core.domain.config.DetectionConfig
import app.litterguard.core.model.IncidentKind
import app.litterguard.core.model.ObjectCategory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Synthetic versions of the PRD field journeys J2 (must trigger) and J3 (must stay silent). Task P1.13. */
class AcceptanceScenarioTest {
    // J2: true positives.

    @Test
    fun `person on foot drops a bag and leaves`() =
        runTest {
            val scene =
                SyntheticScene(
                    listOf(
                        person(
                            Key(3.0, 0.02f, 0.80f),
                            Key(7.0, 0.50f, 0.80f),
                            Key(8.0, 0.50f, 0.80f),
                            Key(12.0, 0.98f, 0.80f),
                        ),
                        bag(0.50f, 0.80f, from = 7.5),
                    ),
                )

            val result = scene.run(seconds = 30.0)

            assertSingleDumping(result, expectedCategories = setOf(ObjectCategory.PERSON))
            assertTriggeredInTime(
                result,
                leftZoneAt = lastTimeInZone(from = 8.0, to = 12.0, startX = 0.50f, endX = 0.98f),
            )
        }

    @Test
    fun `rider on a scooter stops, drops a bag and rides off`() =
        runTest {
            val ride =
                arrayOf(Key(3.0, 0.05f, 0.85f), Key(5.0, 0.45f, 0.85f), Key(8.0, 0.45f, 0.85f), Key(10.0, 0.98f, 0.85f))
            val rider =
                arrayOf(Key(3.0, 0.05f, 0.80f), Key(5.0, 0.45f, 0.80f), Key(8.0, 0.45f, 0.80f), Key(10.0, 0.98f, 0.80f))
            val scene = SyntheticScene(listOf(scooter(*ride), person(*rider), bag(0.30f, 0.90f, from = 7.0)))

            val result = scene.run(seconds = 28.0)

            assertSingleDumping(result, setOf(ObjectCategory.PERSON, ObjectCategory.VEHICLE))
            assertTriggeredInTime(
                result,
                leftZoneAt = lastTimeInZone(from = 8.0, to = 10.0, startX = 0.45f, endX = 0.98f),
            )
        }

    @Test
    fun `car pulls up, leaves a bag and drives away`() =
        runTest {
            val scene =
                SyntheticScene(
                    listOf(
                        bag(0.55f, 0.88f, from = 8.0),
                        car(
                            Key(3.0, 0.02f, 0.85f),
                            Key(6.0, 0.50f, 0.85f),
                            Key(11.0, 0.50f, 0.85f),
                            Key(13.0, 0.98f, 0.85f),
                        ),
                    ),
                )

            val result = scene.run(seconds = 32.0)

            assertSingleDumping(result, setOf(ObjectCategory.VEHICLE))
            assertTriggeredInTime(
                result,
                leftZoneAt = lastTimeInZone(from = 11.0, to = 13.0, startX = 0.50f, endX = 0.98f),
            )
        }

    @Test
    fun `stepping just outside the zone and standing in front of the bag does not hide the dumping`() =
        runTest {
            val scene =
                SyntheticScene(
                    listOf(
                        bag(0.77f, 0.80f, from = 5.0),
                        // Walks in, drops the bag at the zone edge, then stands just outside, still covering it.
                        person(
                            Key(3.0, 0.50f, 0.99f),
                            Key(5.0, 0.77f, 0.82f),
                            Key(6.0, 0.805f, 0.82f),
                            Key(14.0, 0.805f, 0.82f),
                            Key(15.0, 0.98f, 0.82f),
                        ),
                    ),
                )

            val result = scene.run(seconds = 36.0)

            assertSingleDumping(result, setOf(ObjectCategory.PERSON))
        }

    @Test
    fun `someone passing in front of the dumped bag outside the zone does not cancel it`() =
        runTest {
            val scene =
                SyntheticScene(
                    listOf(
                        person(Key(3.0, 0.98f, 0.80f), Key(5.0, 0.75f, 0.80f), Key(6.0, 0.98f, 0.80f)),
                        bag(0.75f, 0.80f, from = 5.0),
                        // Walks past below the zone, close to the camera, covering the bag for a moment.
                        Sprite(
                            ObjectCategory.PERSON,
                            width = 0.12f,
                            height = 0.40f,
                            tone = 60,
                            listOf(Key(8.0, 0.55f, 0.99f), Key(11.0, 0.98f, 0.99f)),
                        ),
                    ),
                )

            val result = scene.run(seconds = 34.0)

            assertThat(result.sirens).hasSize(1)
        }

    // J3: must stay silent.

    @Test
    fun `walking through while carrying a bag is not dumping`() =
        runTest {
            val scene =
                SyntheticScene(
                    listOf(
                        person(Key(3.0, 0.02f, 0.80f), Key(10.0, 0.98f, 0.80f)),
                        carriedBag(Key(3.0, 0.06f, 0.78f), Key(10.0, 1.02f, 0.78f)),
                    ),
                )

            assertSilent(scene.run(seconds = 30.0))
        }

    @Test
    fun `stopping briefly with a bag and taking it along is not dumping`() =
        runTest {
            val scene =
                SyntheticScene(
                    listOf(
                        person(
                            Key(3.0, 0.02f, 0.80f),
                            Key(6.0, 0.45f, 0.80f),
                            Key(10.0, 0.45f, 0.80f),
                            Key(13.0, 0.98f, 0.80f),
                        ),
                        bag(0.52f, 0.80f, from = 6.0, until = 10.0),
                        carriedBag(Key(10.1, 0.49f, 0.78f), Key(13.0, 1.02f, 0.78f)),
                    ),
                )

            assertSilent(scene.run(seconds = 32.0))
        }

    @Test
    fun `coming back for the bag before the dwell time cancels the event`() =
        runTest {
            val scene =
                SyntheticScene(
                    listOf(
                        person(Key(3.0, 0.02f, 0.80f), Key(5.0, 0.45f, 0.80f), Key(7.0, 0.98f, 0.80f)),
                        person(Key(9.0, 0.98f, 0.80f), Key(11.0, 0.50f, 0.80f), Key(13.0, 0.98f, 0.80f)),
                        bag(0.45f, 0.80f, from = 5.0, until = 11.0),
                    ),
                )

            assertSilent(scene.run(seconds = 32.0))
        }

    @Test
    fun `a dog wandering through is not dumping`() =
        runTest {
            val scene =
                SyntheticScene(
                    listOf(
                        dog(
                            Key(3.0, 0.02f, 0.85f),
                            Key(8.0, 0.50f, 0.70f),
                            Key(10.0, 0.50f, 0.70f),
                            Key(15.0, 0.98f, 0.85f),
                        ),
                    ),
                )

            val result = scene.run(seconds = 32.0)

            assertSilent(result)
            assertThat(result.incidents).isEmpty()
        }

    @Test
    fun `a cow wandering through is not dumping`() =
        runTest {
            val scene =
                SyntheticScene(
                    listOf(
                        cow(
                            Key(3.0, 0.02f, 0.80f),
                            Key(9.0, 0.40f, 0.80f),
                            Key(14.0, 0.40f, 0.80f),
                            Key(20.0, 0.98f, 0.80f),
                        ),
                    ),
                )

            val result = scene.run(seconds = 38.0)

            assertSilent(result)
            assertThat(result.incidents).isEmpty()
        }

    @Test
    fun `passing clouds do not raise anything`() =
        runTest {
            val scene =
                SyntheticScene(emptyList(), brightness = { t ->
                    if (t in
                        5.0..15.0
                    ) {
                        -((t - 5.0) * 4).toInt()
                    } else {
                        0
                    }
                })

            val result = scene.run(seconds = 30.0)

            assertSilent(result)
            assertThat(result.incidents).isEmpty()
        }

    @Test
    fun `a lighting change while someone walks through is not dumping`() =
        runTest {
            val scene =
                SyntheticScene(
                    listOf(person(Key(3.0, 0.02f, 0.80f), Key(10.0, 0.98f, 0.80f))),
                    brightness = { t -> if (t > 6.0) -35 else 0 },
                )

            assertSilent(scene.run(seconds = 30.0))
        }

    @Test
    fun `a car that parks and later leaves is not dumping`() =
        runTest {
            val scene =
                SyntheticScene(
                    listOf(
                        car(
                            Key(3.0, 0.02f, 0.85f),
                            Key(6.0, 0.50f, 0.85f),
                            Key(40.0, 0.50f, 0.85f),
                            Key(43.0, 0.98f, 0.85f),
                        ),
                    ),
                )

            assertSilent(scene.run(seconds = 60.0))
        }

    @Test
    fun `a car already parked at start-up that drives away is not dumping`() =
        runTest {
            val scene =
                SyntheticScene(
                    listOf(car(Key(0.0, 0.50f, 0.85f), Key(12.0, 0.50f, 0.85f), Key(15.0, 0.98f, 0.85f))),
                )

            assertSilent(scene.run(seconds = 35.0))
        }

    @Test
    fun `someone lingering in the zone and leaving empty-handed is not dumping`() =
        runTest {
            val scene =
                SyntheticScene(
                    listOf(
                        person(
                            Key(3.0, 0.02f, 0.80f),
                            Key(6.0, 0.40f, 0.80f),
                            Key(20.0, 0.60f, 0.75f),
                            Key(35.0, 0.35f, 0.85f),
                            Key(45.0, 0.98f, 0.80f),
                        ),
                    ),
                )

            assertSilent(scene.run(seconds = 62.0))
        }

    // Other PRD behaviour.

    @Test
    fun `an object left after an animal visit is logged but not alerted`() =
        runTest {
            val scene =
                SyntheticScene(
                    listOf(
                        dog(
                            Key(3.0, 0.02f, 0.80f),
                            Key(6.0, 0.40f, 0.80f),
                            Key(8.0, 0.40f, 0.80f),
                            Key(11.0, 0.98f, 0.80f),
                        ),
                        bag(0.55f, 0.82f, from = 7.0),
                    ),
                )

            val result = scene.run(seconds = 30.0)

            assertSilent(result)
            assertThat(result.incidents.map { it.kind }).containsExactly(IncidentKind.UNATTRIBUTED_OBJECT)
        }

    @Test
    fun `a second bag during the cooldown is merged without a second siren`() =
        runTest {
            val scene =
                SyntheticScene(
                    listOf(
                        person(Key(3.0, 0.02f, 0.80f), Key(5.0, 0.35f, 0.80f), Key(7.0, 0.98f, 0.80f)),
                        bag(0.35f, 0.80f, from = 5.0),
                        person(Key(18.0, 0.02f, 0.80f), Key(20.0, 0.65f, 0.80f), Key(22.0, 0.98f, 0.80f)),
                        bag(0.65f, 0.80f, from = 20.0),
                    ),
                )

            val result = scene.run(seconds = 40.0)

            assertThat(result.sirens).hasSize(1)
            assertThat(result.alerts).hasSize(1)
            assertThat(result.merges).hasSize(1)
            assertThat(result.incidentsOfKind(IncidentKind.CONFIRMED_DUMPING)).hasSize(1)
        }

    @Test
    fun `a camera knocked out of place is inconclusive, not dumping`() =
        runTest {
            val scene =
                SyntheticScene(
                    listOf(person(Key(3.0, 0.02f, 0.80f), Key(5.0, 0.45f, 0.80f), Key(7.0, 0.98f, 0.80f))),
                    shake = { t -> if (t > 6.0) 6 else 0 },
                )

            val result = scene.run(seconds = 30.0)

            assertSilent(result)
            assertThat(result.incidents.map { it.kind }).containsExactly(IncidentKind.INCONCLUSIVE)
        }

    @Test
    fun `an empty quiet scene barely runs the detector`() =
        runTest {
            val result = SyntheticScene(emptyList()).run(seconds = 20.0)

            assertThat(result.detectorRuns).isEqualTo(1)
            assertThat(result.snapshots.last().hasBaseline).isTrue()
        }

    @Test
    fun `every sensitivity preset catches a bag dropped on foot`() =
        runTest {
            val scene =
                SyntheticScene(
                    listOf(
                        person(Key(3.0, 0.02f, 0.80f), Key(6.0, 0.50f, 0.80f), Key(10.0, 0.98f, 0.80f)),
                        bag(0.50f, 0.80f, from = 6.0),
                    ),
                )

            for (preset in app.litterguard.core.domain.config.Sensitivity.entries) {
                val result = scene.run(seconds = 30.0, config = DetectionConfig.forSensitivity(preset))
                assertThat(result.sirens).hasSize(1)
            }
        }

    private fun assertSingleDumping(
        result: SceneResult,
        expectedCategories: Set<ObjectCategory>,
    ) {
        assertThat(result.incidents.map { it.kind }).containsExactly(IncidentKind.CONFIRMED_DUMPING)
        assertThat(result.sirens).hasSize(1)
        assertThat(result.alerts).hasSize(1)
        assertThat(result.incidents.single().actorCategories).isEqualTo(expectedCategories)
        assertThat(result.incidents.single().objectBox).isNotNull()
    }

    /** PRD §6.5: the decision must come early enough that the siren starts within 8 s (modelled limit 6.8 s). */
    private fun assertTriggeredInTime(
        result: SceneResult,
        leftZoneAt: Double,
    ) {
        val delay = result.sirens.single().time - leftZoneAt
        assertThat(delay).isAtLeast(DetectionConfig().dwellTime.inWholeMilliseconds / 1000.0)
        assertThat(delay).isAtMost(MAX_DECISION_DELAY)
    }

    /** When a ground point moving linearly from [startX] to [endX] crosses the zone's right edge. */
    private fun lastTimeInZone(
        from: Double,
        to: Double,
        startX: Float,
        endX: Float,
    ): Double = from + (to - from) * (ZONE_RIGHT - startX) / (endX - startX)

    private fun assertSilent(result: SceneResult) {
        assertThat(result.sirens).isEmpty()
        assertThat(result.alerts).isEmpty()
        assertThat(result.incidentsOfKind(IncidentKind.CONFIRMED_DUMPING)).isEmpty()
    }

    private companion object {
        const val ZONE_RIGHT = 0.80f

        /** 6.8 s budget minus the 0.5 s reserved for audio start-up. */
        const val MAX_DECISION_DELAY = 6.3
    }
}
