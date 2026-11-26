package app.litterguard.core.monitoring

import app.litterguard.core.domain.config.DetectionConfig
import app.litterguard.core.domain.decision.Effect
import app.litterguard.core.domain.decision.Phase
import app.litterguard.core.domain.evidence.EvidenceBuffer
import app.litterguard.core.domain.geometry.cropRect
import app.litterguard.core.domain.pipeline.DecisionPipeline
import app.litterguard.core.domain.pipeline.PipelineStep
import app.litterguard.core.domain.ports.AnalysisFrame
import app.litterguard.core.domain.ports.Clock
import app.litterguard.core.domain.ports.Deterrent
import app.litterguard.core.domain.ports.FrameSource
import app.litterguard.core.domain.ports.LiveView
import app.litterguard.core.domain.ports.MonitoringFailure
import app.litterguard.core.domain.ports.MonitoringStatus
import app.litterguard.core.domain.ports.ObjectDetector
import app.litterguard.core.domain.ports.ObjectDetectorFactory
import app.litterguard.core.domain.ports.SiteConfigRepository
import app.litterguard.core.domain.site.SiteConfig
import app.litterguard.core.model.IncidentId
import app.litterguard.core.model.IncidentKind
import app.litterguard.core.model.MonotonicTime
import app.litterguard.core.model.Polygon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

private const val PREVIEW_DOWNSCALE = 2

/**
 * One watch of the dump zone: camera frames go through the decision pipeline, and its effects are carried out
 * (ARCHITECTURE §4). Everything runs on [dispatcher], which must be single-threaded.
 */
internal class MonitoringSession(
    private val camera: FrameSource,
    private val detectors: ObjectDetectorFactory,
    private val deterrent: Deterrent,
    private val recorder: IncidentRecorder,
    private val sites: SiteConfigRepository,
    private val clock: Clock,
    private val dispatcher: CoroutineDispatcher,
) {
    private val state = MutableStateFlow<MonitoringStatus>(MonitoringStatus.Stopped)

    @Volatile private var job: Job? = null

    val status: StateFlow<MonitoringStatus> = state.asStateFlow()

    val isRunning: Boolean get() = job?.isActive == true

    /** Starts watching inside [scope]; does nothing if already watching. */
    fun start(scope: CoroutineScope) {
        if (isRunning) return
        state.value = MonitoringStatus.Starting
        val onCrash =
            CoroutineExceptionHandler { _, error ->
                Timber.e(error, "Monitoring failed")
                state.value =
                    MonitoringStatus.Failed(MonitoringFailure.Error(error.message ?: error.javaClass.simpleName))
            }
        val started = scope.launch(dispatcher + onCrash) { watch() }
        job = started
        // Cancellation takes effect later: a frame already being processed can still publish after stop().
        started.invokeOnCompletion { cause ->
            if (cause is CancellationException && (job == null || job === started)) markStopped()
        }
    }

    /** Stops the camera, the model and any siren. A failure stays visible. */
    fun stop() {
        job?.cancel()
        job = null
        markStopped()
    }

    private fun markStopped() {
        if (state.value !is MonitoringStatus.Failed) state.value = MonitoringStatus.Stopped
    }

    private suspend fun watch() {
        val site = sites.config.first()
        val zone = site.zone
        if (zone == null) {
            state.value = MonitoringStatus.Failed(MonitoringFailure.NoZone)
            return
        }
        detectors.open().use { detector -> Watch(site, zone, detector).run() }
        state.value = MonitoringStatus.Stopped
    }

    /** The state of one run, so nothing leaks from one session into the next. */
    private inner class Watch(
        private val site: SiteConfig,
        private val zone: Polygon,
        private val detector: ObjectDetector,
    ) {
        private val config = DetectionConfig.forSensitivity(site.sensitivity)
        private val region = zone.cropRect(config.roiCropMargin, config.roiCropHeadroom)
        private val pipeline = DecisionPipeline(zone, config) { IncidentId(UUID.randomUUID().toString()) }
        private val evidence = EvidenceBuffer(config)
        private val rate = RateMeter()
        private var confirmed = 0

        /** Sirens are children of this scope: stopping the session silences them. */
        suspend fun run() =
            coroutineScope {
                Timber.i("Watching '%s' with %s", site.siteName, detector.description)
                camera.frames(config.analysisFps).collect { frame -> frame.use { process(it, this) } }
            }

        private suspend fun process(
            frame: AnalysisFrame,
            sirens: CoroutineScope,
        ) {
            var inferenceMillis: Long? = null
            val step =
                pipeline.process(frame.luma, frame.timestamp) {
                    val started = clock.monotonic()
                    detector.detect(frame.image, region).also {
                        inferenceMillis = (clock.monotonic() - started).inWholeMilliseconds
                    }
                }
            if (step.snapshot.phase is Phase.ActorPresent) {
                evidence.offerFrame(frame.image, frame.luma, step.snapshot.tracks, frame.timestamp)
            }
            step.effects.forEach { execute(it, frame, sirens) }
            publish(frame, step, inferenceMillis)
        }

        private fun execute(
            effect: Effect,
            frame: AnalysisFrame,
            sirens: CoroutineScope,
        ) {
            when (effect) {
                is Effect.PlayDeterrent -> {
                    sirens.launch { deterrent.play(effect.clip) }
                }

                is Effect.CommitIncident -> {
                    if (effect.draft.kind == IncidentKind.CONFIRMED_DUMPING) {
                        confirmed++
                        logLatency(effect.draft.actorLeftAt)
                    }
                    recorder.commit(
                        IncidentCapture(
                            draft = effect.draft,
                            siteName = site.siteName,
                            detectorDescription = detector.description,
                            scene = frame.image.copy(),
                            actorFrames = evidence.best(),
                        ),
                    )
                }

                is Effect.EnqueueAlert -> {
                    recorder.alert(effect.incidentId)
                }

                is Effect.MergeIntoIncident -> {
                    recorder.merge(effect.incidentId)
                }

                Effect.DiscardEvidence -> {
                    evidence.clear()
                }

                // Handled inside the pipeline, which owns the baseline.
                is Effect.BaselineEffect -> {}
            }
        }

        /** The number hypothesis H2 is judged by (PRD §6.5). */
        private fun logLatency(actorLeftAt: MonotonicTime?) {
            if (actorLeftAt != null) {
                val delay = clock.monotonic() - actorLeftAt
                Timber.i("Dumping confirmed %d ms after the actor left the zone", delay.inWholeMilliseconds)
            }
        }

        private fun publish(
            frame: AnalysisFrame,
            step: PipelineStep,
            inferenceMillis: Long?,
        ) {
            state.value =
                MonitoringStatus.Running(
                    LiveView(
                        frame = frame.image.downscaled(PREVIEW_DOWNSCALE),
                        zone = zone,
                        snapshot = step.snapshot,
                        detectorDescription = detector.description,
                        inferenceMillis = inferenceMillis,
                        framesPerSecond = rate.mark(frame.timestamp),
                        dumpingConfirmed = confirmed,
                    ),
                )
        }
    }
}

/** Frames per second over the last few frames. */
internal class RateMeter {
    private val recent = ArrayDeque<MonotonicTime>()

    fun mark(time: MonotonicTime): Float {
        recent.addLast(time)
        if (recent.size > WINDOW) recent.removeFirst()
        val span = (recent.last() - recent.first()).inWholeMilliseconds
        return if (span <= 0) 0f else (recent.size - 1) * MILLIS_PER_SECOND / span
    }

    private companion object {
        const val WINDOW = 10
        const val MILLIS_PER_SECOND = 1000f
    }
}
