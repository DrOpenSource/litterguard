package app.litterguard.feature.incidents

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.litterguard.core.domain.ports.EvidenceStore
import app.litterguard.core.domain.ports.IncidentRepository
import app.litterguard.core.model.EvidenceFile
import app.litterguard.core.model.Incident
import app.litterguard.core.model.IncidentId
import app.litterguard.core.model.ReviewLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class IncidentsUiState(
    val incidents: List<Incident> = emptyList(),
    val selected: Incident? = null,
)

@HiltViewModel
class IncidentsViewModel
    @Inject
    constructor(
        private val repository: IncidentRepository,
        private val evidence: EvidenceStore,
    ) : ViewModel() {
        private val selectedId = MutableStateFlow<IncidentId?>(null)

        val state: StateFlow<IncidentsUiState> =
            combine(repository.observeAll(), selectedId) { incidents, id ->
                IncidentsUiState(incidents, incidents.firstOrNull { it.id == id })
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), IncidentsUiState())

        fun open(id: IncidentId?) {
            selectedId.value = id
        }

        fun review(
            id: IncidentId,
            label: ReviewLabel,
        ) {
            viewModelScope.launch { repository.setReviewLabel(id, label) }
        }

        fun pathOf(file: EvidenceFile): String = evidence.absolutePath(file)

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
