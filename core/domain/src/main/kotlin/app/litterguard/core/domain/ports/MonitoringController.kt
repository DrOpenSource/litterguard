package app.litterguard.core.domain.ports

import app.litterguard.core.domain.pipeline.PipelineSnapshot
import app.litterguard.core.model.ImageBuffer
import app.litterguard.core.model.Polygon
import kotlinx.coroutines.flow.StateFlow

/** What the monitor screen draws for the latest processed frame. */
data class LiveView(
    /** A private copy of the frame, safe to keep. */
    val frame: ImageBuffer,
    val zone: Polygon,
    val snapshot: PipelineSnapshot,
    val detectorDescription: String,
    /** Duration of this frame's detector run in milliseconds, or null when the motion gate skipped it. */
    val inferenceMillis: Long?,
    val framesPerSecond: Float,
    val dumpingConfirmed: Int,
)

sealed interface MonitoringStatus {
    data object Stopped : MonitoringStatus

    data object Starting : MonitoringStatus

    data class Running(
        val view: LiveView,
    ) : MonitoringStatus

    data class Failed(
        val reason: MonitoringFailure,
    ) : MonitoringStatus
}

sealed interface MonitoringFailure {
    data object NoZone : MonitoringFailure

    data class Error(
        val detail: String,
    ) : MonitoringFailure
}

/** Starts and stops watching the dump zone. */
interface MonitoringController {
    val status: StateFlow<MonitoringStatus>

    fun start()

    fun stop()
}
