package app.litterguard.feature.setup

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.litterguard.core.domain.ports.AlertChannelSetup
import app.litterguard.core.domain.ports.AlertChannelStatus
import app.litterguard.core.domain.ports.AlertSetupResult
import app.litterguard.core.domain.ports.SiteConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which action the latest result belongs to, so the screen can say the right thing about it. */
enum class AlertAction { CONNECT, FIND_GROUP, TEST }

@Immutable
data class AlertsUiState(
    val status: AlertChannelStatus = AlertChannelStatus(),
    val busy: Boolean = false,
    val lastResult: Pair<AlertAction, AlertSetupResult>? = null,
)

@HiltViewModel
class AlertsViewModel
    @Inject
    constructor(
        private val channel: AlertChannelSetup,
        private val sites: SiteConfigRepository,
    ) : ViewModel() {
        private data class Progress(
            val busy: Boolean = false,
            val lastResult: Pair<AlertAction, AlertSetupResult>? = null,
        )

        private val progress = MutableStateFlow(Progress())

        val state: StateFlow<AlertsUiState> =
            combine(channel.status, progress) { status, current ->
                AlertsUiState(status, current.busy, current.lastResult)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AlertsUiState())

        fun connect(token: String) = run(AlertAction.CONNECT) { channel.connect(token) }

        fun findGroup() = run(AlertAction.FIND_GROUP) { channel.findGroup() }

        fun sendTest() = run(AlertAction.TEST) { channel.sendTestAlert(sites.config.first().siteName) }

        fun openGroupPicker(open: (String) -> Unit) {
            viewModelScope.launch { channel.addToGroupLink()?.let(open) }
        }

        fun disconnect() {
            viewModelScope.launch {
                channel.disconnect()
                progress.value = Progress()
            }
        }

        private fun run(
            action: AlertAction,
            work: suspend () -> AlertSetupResult,
        ) {
            if (progress.value.busy) return
            progress.value = Progress(busy = true)
            viewModelScope.launch {
                var result: AlertSetupResult? = null
                try {
                    result = work()
                } finally {
                    progress.update { Progress(busy = false, lastResult = result?.let { action to it }) }
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
