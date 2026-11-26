package app.litterguard.feature.setup

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.litterguard.core.domain.config.Sensitivity
import app.litterguard.core.domain.decision.DeterrentClip
import app.litterguard.core.domain.ports.AlertChannelSetup
import app.litterguard.core.domain.ports.AlertChannelStatus
import app.litterguard.core.domain.ports.AudioRoute
import app.litterguard.core.domain.ports.Deterrent
import app.litterguard.core.domain.ports.MonitoringController
import app.litterguard.core.domain.ports.MonitoringStatus
import app.litterguard.core.domain.ports.SiteConfigRepository
import app.litterguard.core.domain.site.SiteConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class HomeUiState(
    val site: SiteConfig = SiteConfig(),
    val alerts: AlertChannelStatus = AlertChannelStatus(),
    val soundPlaying: Boolean = false,
    val audioRoute: AudioRoute = AudioRoute.PhoneSpeaker,
    val isWatching: Boolean = false,
)

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val sites: SiteConfigRepository,
        private val deterrent: Deterrent,
        alertSetup: AlertChannelSetup,
        monitoring: MonitoringController,
    ) : ViewModel() {
        private data class Sound(
            val playing: Boolean = false,
            val route: AudioRoute = AudioRoute.PhoneSpeaker,
        )

        private val sound = MutableStateFlow(Sound())
        private var siren: Job? = null

        private val watching =
            monitoring.status
                .map { it is MonitoringStatus.Running || it is MonitoringStatus.Starting }
                .distinctUntilChanged()

        val state: StateFlow<HomeUiState> =
            combine(sites.config, alertSetup.status, sound, watching) { site, alerts, current, isWatching ->
                HomeUiState(site, alerts, current.playing, current.route, isWatching)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

        fun refreshAudioRoute() {
            sound.update { it.copy(route = deterrent.currentRoute()) }
        }

        /** Plays the real warning, or stops it if it is already playing (FR-1.4). */
        fun toggleTestSiren() {
            val playing = siren
            if (playing?.isActive == true) {
                playing.cancel()
                return
            }
            refreshAudioRoute()
            siren =
                viewModelScope.launch {
                    sound.update { it.copy(playing = true) }
                    try {
                        deterrent.play(DeterrentClip.WARNING)
                    } finally {
                        sound.update { it.copy(playing = false) }
                    }
                }
        }

        fun setSiteName(name: String) {
            viewModelScope.launch { sites.update { it.copy(siteName = name.take(MAX_NAME_LENGTH)) } }
        }

        fun setSensitivity(sensitivity: Sensitivity) {
            viewModelScope.launch { sites.update { it.copy(sensitivity = sensitivity) } }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val MAX_NAME_LENGTH = 60
        }
    }
