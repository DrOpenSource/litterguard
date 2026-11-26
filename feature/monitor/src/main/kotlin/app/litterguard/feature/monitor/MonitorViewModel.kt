package app.litterguard.feature.monitor

import androidx.lifecycle.ViewModel
import app.litterguard.core.domain.ports.MonitoringController
import app.litterguard.core.domain.ports.MonitoringStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MonitorViewModel
    @Inject
    constructor(
        private val monitoring: MonitoringController,
    ) : ViewModel() {
        val status: StateFlow<MonitoringStatus> = monitoring.status

        fun start() {
            val current = status.value
            if (current !is MonitoringStatus.Running && current != MonitoringStatus.Starting) monitoring.start()
        }

        fun stop() = monitoring.stop()
    }
