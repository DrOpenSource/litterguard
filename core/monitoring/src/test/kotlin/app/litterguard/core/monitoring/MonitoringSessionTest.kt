package app.litterguard.core.monitoring

import app.litterguard.core.domain.decision.DeterrentClip
import app.litterguard.core.domain.ports.AlertSink
import app.litterguard.core.domain.ports.AnalysisFrame
import app.litterguard.core.domain.ports.AudioRoute
import app.litterguard.core.domain.ports.Clock
import app.litterguard.core.domain.ports.Deterrent
import app.litterguard.core.domain.ports.EvidenceStore
import app.litterguard.core.domain.ports.FrameSource
import app.litterguard.core.domain.ports.IncidentRepository
import app.litterguard.core.domain.ports.MonitoringFailure
import app.litterguard.core.domain.ports.MonitoringStatus
import app.litterguard.core.domain.ports.ObjectDetector
import app.litterguard.core.domain.ports.ObjectDetectorFactory
import app.litterguard.core.domain.ports.SiteConfigRepository
import app.litterguard.core.domain.site.SiteConfig
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.DeliveryState
import app.litterguard.core.model.Detection
import app.litterguard.core.model.EvidenceFile
import app.litterguard.core.model.EvidenceKind
import app.litterguard.core.model.ImageBuffer
import app.litterguard.core.model.Incident
import app.litterguard.core.model.IncidentId
import app.litterguard.core.model.IncidentKind
import app.litterguard.core.model.LumaPlane
import app.litterguard.core.model.MonotonicTime
import app.litterguard.core.model.NormalizedPoint
import app.litterguard.core.model.ObjectCategory
import app.litterguard.core.model.Polygon
import app.litterguard.core.model.ReviewLabel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Instant
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val WIDTH = 320
private const val HEIGHT = 180
private const val FPS = 3
private val START_WALL = Instant.parse("2026-11-27T04:30:00Z")

private val ZONE =
    Polygon(
        listOf(
            NormalizedPoint(0.2f, 0.3f),
            NormalizedPoint(0.8f, 0.3f),
            NormalizedPoint(0.8f, 0.95f),
            NormalizedPoint(0.2f, 0.95f),
        ),
    )

/** A person walks across between 2 s and 8 s, leaving a bag behind from 5 s. */
private class Script {
    private val ground =
        Random(3).let { random ->
            val tiles = IntArray(60 * 40) { random.nextInt(90, 170) }
            IntArray(WIDTH * HEIGHT) { tiles[(it / WIDTH) / 6 * 60 + (it % WIDTH) / 6] }
        }

    var now = MonotonicTime(0)
    var openFrames = 0

    private fun personAt(time: Double): BoundingBox? {
        if (time < 2.0 || time > 8.0) return null
        val x = (0.02 + (time - 2.0) / 6.0 * 0.96).toFloat()
        return BoundingBox((x - 0.04f).coerceAtLeast(0f), 0.5f, (x + 0.04f).coerceAtMost(1f), 0.8f)
    }

    private fun paint(
        pixels: IntArray,
        box: BoundingBox,
        tone: Int,
    ) {
        for (y in (box.top * HEIGHT).toInt() until (box.bottom * HEIGHT).toInt()) {
            for (x in (box.left * WIDTH).toInt() until (box.right * WIDTH).toInt()) {
                pixels[y * WIDTH + x] = tone + if ((x / 4 + y / 4) % 2 == 0) 0 else 25
            }
        }
    }

    fun frames(seconds: Int): FrameSource =
        FrameSource {
            flow {
                for (index in 0..seconds * FPS) {
                    val time = index / FPS.toDouble()
                    now = MonotonicTime(0) + (index * 1000L / FPS).milliseconds
                    val grey = ground.copyOf()
                    if (time >= 5.0) paint(grey, BoundingBox(0.46f, 0.70f, 0.54f, 0.80f), 20)
                    personAt(time)?.let { paint(grey, it, 30) }
                    // Don't count a frame that cancellation would stop from ever reaching the collector.
                    currentCoroutineContext().ensureActive()
                    openFrames++
                    emit(
                        AnalysisFrame(
                            image = ImageBuffer(WIDTH, HEIGHT, grey),
                            luma = LumaPlane(WIDTH, HEIGHT, WIDTH, ByteArray(grey.size) { grey[it].toByte() }),
                            timestamp = now,
                            onClose = { openFrames-- },
                        ),
                    )
                }
            }
        }

    inner class ScriptedDetector : ObjectDetector {
        var closed = false
        val regions = mutableListOf<BoundingBox>()
        override val description = "scripted"

        override suspend fun detect(
            image: ImageBuffer,
            region: BoundingBox,
        ): List<Detection> {
            regions += region
            val time = (now - MonotonicTime(0)).inWholeMilliseconds / 1000.0
            return listOfNotNull(personAt(time)?.let { Detection(it, ObjectCategory.PERSON, "person", 0.9f) })
        }

        override fun close() {
            closed = true
        }
    }

    val detector = ScriptedDetector()

    val clock =
        object : Clock {
            override fun monotonic() = now

            override fun wallClock(): Instant = START_WALL.plusNanos(now.nanos)
        }
}

private class Recording :
    IncidentRepository,
    EvidenceStore,
    AlertSink,
    Deterrent {
    val saved = mutableListOf<Incident>()
    val images = mutableListOf<Pair<EvidenceKind, BoundingBox?>>()
    val played = mutableListOf<DeterrentClip>()
    val events = mutableListOf<String>()
    var failImages = false

    override fun observeAll(): Flow<List<Incident>> = MutableStateFlow(saved)

    override suspend fun find(id: IncidentId) = saved.firstOrNull { it.id == id }

    override suspend fun add(incident: Incident) {
        saved += incident
        events += "saved"
    }

    override suspend fun recordMerge(id: IncidentId) = Unit

    override suspend fun setDeliveryState(
        id: IncidentId,
        state: DeliveryState,
    ) = Unit

    override suspend fun setReviewLabel(
        id: IncidentId,
        label: ReviewLabel,
    ) = Unit

    override suspend fun save(
        id: IncidentId,
        kind: EvidenceKind,
        index: Int,
        image: ImageBuffer,
        capturedAt: Instant,
        highlight: BoundingBox?,
    ): EvidenceFile {
        if (failImages) throw IOException("disk full")
        images += kind to highlight
        return EvidenceFile(kind, "${id.value}/${kind.name}_$index.jpg", image.width, image.height, capturedAt)
    }

    override fun absolutePath(file: EvidenceFile) = file.relativePath

    override fun enqueue(id: IncidentId) {
        events += "alert"
    }

    override fun currentRoute(): AudioRoute = AudioRoute.PhoneSpeaker

    override suspend fun play(clip: DeterrentClip) {
        played += clip
    }
}

private class Sites(
    zone: Polygon?,
) : SiteConfigRepository {
    override val config = MutableStateFlow(SiteConfig(siteName = "Gate 2", zone = zone))

    override suspend fun update(transform: (SiteConfig) -> SiteConfig) {
        config.value = transform(config.value)
    }
}

/** The whole loop with fake camera, model, speaker and storage: the closest thing to the phone that runs on a PC. */
@OptIn(ExperimentalCoroutinesApi::class)
class MonitoringSessionTest {
    private val script = Script()
    private val recording = Recording()

    private fun TestScope.session(
        zone: Polygon? = ZONE,
        camera: FrameSource = script.frames(seconds = 18),
        detectors: ObjectDetectorFactory = ObjectDetectorFactory { script.detector },
    ): MonitoringSession {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val recorder =
            IncidentRecorder(recording, recording, recording, script.clock, CoroutineScope(dispatcher))
        return MonitoringSession(camera, detectors, recording, recorder, Sites(zone), script.clock, dispatcher)
    }

    @Test
    fun `a dropped bag sounds the siren, saves the evidence, then queues the alert`() =
        runTest {
            val session = session()

            session.start(this)
            advanceUntilIdle()

            assertThat(recording.played).containsExactly(DeterrentClip.WARNING)
            assertThat(recording.events).containsExactly("saved", "alert").inOrder()
            val incident = recording.saved.single()
            assertThat(incident.kind).isEqualTo(IncidentKind.CONFIRMED_DUMPING)
            assertThat(incident.siteName).isEqualTo("Gate 2")
            assertThat(incident.deliveryState).isEqualTo(DeliveryState.PENDING)
            assertThat(incident.evidence.first().kind).isEqualTo(EvidenceKind.SCENE)
            assertThat(incident.evidence.count { it.kind == EvidenceKind.ACTOR }).isAtLeast(1)
        }

    @Test
    fun `the scene picture outlines the object and times are wall-clock`() =
        runTest {
            session().start(this)
            advanceUntilIdle()

            val incident = recording.saved.single()
            assertThat(recording.images.first { it.first == EvidenceKind.SCENE }.second).isEqualTo(incident.objectBox)
            val delay = incident.confirmedAt.toEpochMilli() - requireNotNull(incident.actorLeftAt).toEpochMilli()
            assertThat(delay).isIn(5.seconds.inWholeMilliseconds..7.seconds.inWholeMilliseconds)
            assertThat(incident.confirmedAt).isGreaterThan(START_WALL)
        }

    @Test
    fun `the detector is shown enough of the picture above the zone to see a whole person`() =
        runTest {
            session().start(this)
            advanceUntilIdle()

            // The zone's far edge is at 0.30; someone standing there reaches far above it.
            assertThat(
                script.detector.regions
                    .first()
                    .top,
            ).isAtMost(0.01f)
        }

    @Test
    fun `every frame is released and the model is closed`() =
        runTest {
            session().start(this)
            advanceUntilIdle()

            assertThat(script.openFrames).isEqualTo(0)
            assertThat(script.detector.closed).isTrue()
        }

    @Test
    fun `a full disk still records the incident and alerts`() =
        runTest {
            recording.failImages = true

            session().start(this)
            advanceUntilIdle()

            assertThat(recording.saved.single().evidence).isEmpty()
            assertThat(recording.events).containsExactly("saved", "alert").inOrder()
        }

    @Test
    fun `pressing stop while a frame is being processed still ends as stopped`() =
        runTest {
            var session: MonitoringSession? = null
            var calls = 0
            val stopsMidFrame =
                object : ObjectDetector {
                    override val description = "stops"

                    override suspend fun detect(
                        image: ImageBuffer,
                        region: BoundingBox,
                    ): List<Detection> {
                        if (++calls == 3) requireNotNull(session).stop()
                        return emptyList()
                    }

                    override fun close() = Unit
                }
            val started = session(detectors = ObjectDetectorFactory { stopsMidFrame })
            session = started

            started.start(this)
            advanceUntilIdle()

            assertThat(calls).isEqualTo(3)
            assertThat(started.status.value).isEqualTo(MonitoringStatus.Stopped)
            assertThat(script.openFrames).isEqualTo(0)
        }

    @Test
    fun `without a dump zone it says so and never opens the model`() =
        runTest {
            var opened = false
            val session =
                session(
                    zone = null,
                    detectors =
                        ObjectDetectorFactory {
                            opened = true
                            script.detector
                        },
                )

            session.start(this)
            advanceUntilIdle()

            assertThat(session.status.value).isEqualTo(MonitoringStatus.Failed(MonitoringFailure.NoZone))
            assertThat(opened).isFalse()
        }

    @Test
    fun `a model that can't load is reported, not thrown`() =
        runTest {
            val session = session(detectors = ObjectDetectorFactory { error("no model") })

            session.start(CoroutineScope(coroutineContext + kotlinx.coroutines.SupervisorJob()))
            advanceUntilIdle()

            assertThat(session.status.value)
                .isEqualTo(MonitoringStatus.Failed(MonitoringFailure.Error("no model")))
        }
}

class RateMeterTest {
    @Test
    fun `measures frames per second`() {
        val meter = RateMeter()

        val rate = (0..20).map { meter.mark(MonotonicTime(0) + (it * 333).milliseconds) }.last()

        assertThat(rate).isWithin(0.1f).of(3f)
    }
}
