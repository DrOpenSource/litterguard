package app.litterguard.core.monitoring

import android.content.Context
import app.litterguard.core.domain.ports.AlertSink
import app.litterguard.core.domain.ports.Clock
import app.litterguard.core.domain.ports.Deterrent
import app.litterguard.core.domain.ports.EvidenceStore
import app.litterguard.core.domain.ports.FrameSource
import app.litterguard.core.domain.ports.IncidentRepository
import app.litterguard.core.domain.ports.MonitoringController
import app.litterguard.core.domain.ports.MonitoringStatus
import app.litterguard.core.domain.ports.ObjectDetectorFactory
import app.litterguard.core.domain.ports.SiteConfigRepository
import app.litterguard.core.model.MonotonicTime
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.time.Instant
import javax.inject.Singleton

internal object SystemClockSource : Clock {
    override fun monotonic(): MonotonicTime = MonotonicTime(System.nanoTime())

    override fun wallClock(): Instant = Instant.now()
}

/** What the screens use: starting goes through the foreground service, which owns the session. */
internal class ServiceMonitoringController(
    private val context: Context,
    private val session: MonitoringSession,
) : MonitoringController {
    override val status: StateFlow<MonitoringStatus> = session.status

    override fun start() = MonitoringService.start(context)

    override fun stop() {
        session.stop()
        MonitoringService.stop(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object MonitoringModule {
    @Provides
    @Singleton
    fun clock(): Clock = SystemClockSource

    @Provides
    @Singleton
    fun recorder(
        incidents: IncidentRepository,
        evidence: EvidenceStore,
        alerts: AlertSink,
        clock: Clock,
    ): IncidentRecorder {
        // One failed save or enqueue is logged and must not stop later ones, or the camera.
        val onFailure = CoroutineExceptionHandler { _, error -> Timber.e(error, "Could not record an incident") }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1) + onFailure)
        return IncidentRecorder(incidents, evidence, alerts, clock, scope)
    }

    @Provides
    @Singleton
    fun session(
        camera: FrameSource,
        detectors: ObjectDetectorFactory,
        deterrent: Deterrent,
        recorder: IncidentRecorder,
        sites: SiteConfigRepository,
        clock: Clock,
    ): MonitoringSession =
        MonitoringSession(
            camera = camera,
            detectors = detectors,
            deterrent = deterrent,
            recorder = recorder,
            sites = sites,
            clock = clock,
            // The decision pipeline is confined to one coroutine at a time (BEST_PRACTICES §2).
            dispatcher = Dispatchers.Default.limitedParallelism(1),
        )

    @Provides
    @Singleton
    fun controller(
        @ApplicationContext context: Context,
        session: MonitoringSession,
    ): MonitoringController = ServiceMonitoringController(context, session)
}
