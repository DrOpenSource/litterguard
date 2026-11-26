package app.litterguard.feature.setup

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.litterguard.core.domain.geometry.RoiValidation
import app.litterguard.core.model.ImageBuffer
import app.litterguard.core.model.NormalizedPoint
import app.litterguard.core.ui.CameraFrameView
import app.litterguard.core.ui.drawZone

/** Draw the dump zone by tapping on a live picture; corners can be dragged (FR-1.2). */
@Composable
fun ZoneRoute(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ZoneViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Collecting with the lifecycle closes the camera as soon as the screen is no longer visible.
    val preview = remember(viewModel) { viewModel.preview }
    val frame by preview.collectAsStateWithLifecycle(initialValue = null)
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(state.saved) { if (state.saved) currentOnDone() }
    val actions =
        remember(viewModel) {
            ZoneActions(
                addPoint = viewModel::addPoint,
                startDrag = viewModel::startDrag,
                dragTo = viewModel::dragTo,
                endDrag = viewModel::endDrag,
                undo = viewModel::undo,
                clear = viewModel::clear,
                save = viewModel::save,
            )
        }

    Row(modifier.fillMaxSize().safeDrawingPadding()) {
        val pictureArea = Modifier.weight(1f).fillMaxHeight()
        if (state.cameraFailed) {
            Text(stringResource(R.string.zone_camera_failed), pictureArea.padding(16.dp))
        } else {
            ZonePicture(frame, state.points, actions, pictureArea)
        }
        ZoneControls(state, actions, onDone, Modifier.width(220.dp).fillMaxHeight())
    }
}

private data class ZoneActions(
    val addPoint: (NormalizedPoint) -> Unit,
    val startDrag: (NormalizedPoint) -> Unit,
    val dragTo: (NormalizedPoint) -> Unit,
    val endDrag: () -> Unit,
    val undo: () -> Unit,
    val clear: () -> Unit,
    val save: () -> Unit,
)

@Composable
private fun ZonePicture(
    frame: ImageBuffer?,
    points: List<NormalizedPoint>,
    actions: ZoneActions,
    modifier: Modifier = Modifier,
) {
    if (frame == null) {
        Column(modifier, Arrangement.Center, Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(stringResource(R.string.zone_opening_camera), Modifier.padding(8.dp))
        }
        return
    }
    val gestures =
        Modifier
            .pointerInput(actions) { detectTapGestures { actions.addPoint(normalised(it)) } }
            .pointerInput(actions) {
                detectDragGestures(
                    onDragStart = { actions.startDrag(normalised(it)) },
                    onDrag = { change, _ -> actions.dragTo(normalised(change.position)) },
                    onDragEnd = actions.endDrag,
                    onDragCancel = actions.endDrag,
                )
            }
    CameraFrameView(frame, modifier, gestures) { drawZone(points, showCorners = true) }
}

private fun PointerInputScope.normalised(position: Offset) =
    NormalizedPoint(
        (position.x / size.width).coerceIn(0f, 1f),
        (position.y / size.height).coerceIn(0f, 1f),
    )

@Composable
private fun ZoneControls(
    state: ZoneUiState,
    actions: ZoneActions,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.zone_instructions))
        Text(stringResource(R.string.zone_points, state.points.size))
        state.problem?.let { Text(problemText(it), color = MaterialTheme.colorScheme.error) }
        val full = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        Button(onClick = actions.save, modifier = full) { Text(stringResource(R.string.zone_save)) }
        OutlinedButton(onClick = actions.undo, modifier = full) { Text(stringResource(R.string.zone_undo)) }
        OutlinedButton(onClick = actions.clear, modifier = full) { Text(stringResource(R.string.zone_clear)) }
        OutlinedButton(onClick = onCancel, modifier = full) { Text(stringResource(R.string.back)) }
    }
}

@Composable
private fun problemText(problem: RoiValidation): String =
    when (problem) {
        is RoiValidation.TooFewPoints -> stringResource(R.string.zone_too_few, problem.min)
        is RoiValidation.TooManyPoints -> stringResource(R.string.zone_too_many, problem.max)
        is RoiValidation.TooSmall -> stringResource(R.string.zone_too_small)
        RoiValidation.SelfIntersecting -> stringResource(R.string.zone_crossing)
        RoiValidation.OutsideFrame -> stringResource(R.string.zone_outside)
        is RoiValidation.Valid -> ""
    }
