package app.litterguard.feature.setup

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.litterguard.core.domain.geometry.RoiRules
import app.litterguard.core.domain.geometry.RoiValidation
import app.litterguard.core.domain.ports.FrameSource
import app.litterguard.core.domain.ports.MonitoringController
import app.litterguard.core.domain.ports.SiteConfigRepository
import app.litterguard.core.model.ImageBuffer
import app.litterguard.core.model.NormalizedPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.hypot

@Immutable
data class ZoneUiState(
    val points: List<NormalizedPoint> = emptyList(),
    /** Why the last save was refused, if it was. */
    val problem: RoiValidation? = null,
    val cameraFailed: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class ZoneViewModel
    @Inject
    constructor(
        camera: FrameSource,
        private val sites: SiteConfigRepository,
        monitoring: MonitoringController,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(ZoneUiState())
        val state: StateFlow<ZoneUiState> = mutableState.asStateFlow()

        /** Live pictures from the very stream the detector uses. The camera is open only while this is collected. */
        val preview: Flow<ImageBuffer> =
            camera
                .frames(PREVIEW_FPS)
                .map { frame -> frame.use { it.image.downscaled(PREVIEW_DOWNSCALE) } }
                .catch { mutableState.update { it.copy(cameraFailed = true) } }

        private var dragged: Int? = null

        init {
            // Only one thing can use the camera, and a zone must not change under a running session.
            monitoring.stop()
            viewModelScope.launch {
                val existing =
                    sites.config
                        .first()
                        .zone
                        ?.vertices
                        .orEmpty()
                mutableState.update { if (it.points.isEmpty()) it.copy(points = existing) else it }
            }
        }

        fun addPoint(point: NormalizedPoint) = edit { if (it.size < RoiRules.MAX_POINTS) it + point else it }

        /** Picks up the corner nearest to [at], if one is close enough. */
        fun startDrag(at: NormalizedPoint) {
            val points = mutableState.value.points
            dragged =
                points.indices
                    .minByOrNull { distance(points[it], at) }
                    ?.takeIf { distance(points[it], at) <= DRAG_REACH }
        }

        fun dragTo(to: NormalizedPoint) {
            val index = dragged ?: return
            edit { points -> points.mapIndexed { i, point -> if (i == index) to else point } }
        }

        fun endDrag() {
            dragged = null
        }

        fun undo() = edit { it.dropLast(1) }

        fun clear() = edit { emptyList() }

        fun save() {
            when (val result = RoiRules.validate(mutableState.value.points)) {
                is RoiValidation.Valid -> {
                    viewModelScope.launch {
                        sites.update { it.copy(zone = result.roi) }
                        mutableState.update { it.copy(saved = true, problem = null) }
                    }
                }

                else -> {
                    mutableState.update { it.copy(problem = result) }
                }
            }
        }

        private fun edit(change: (List<NormalizedPoint>) -> List<NormalizedPoint>) {
            mutableState.update { it.copy(points = change(it.points), problem = null) }
        }

        private fun distance(
            a: NormalizedPoint,
            b: NormalizedPoint,
        ) = hypot(a.x - b.x, a.y - b.y)

        private companion object {
            const val PREVIEW_FPS = 5f
            const val PREVIEW_DOWNSCALE = 2
            const val DRAG_REACH = 0.07f
        }
    }
