package app.litterguard.feature.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.litterguard.core.domain.ports.LiveView
import app.litterguard.core.domain.ports.MonitoringFailure
import app.litterguard.core.domain.ports.MonitoringStatus
import app.litterguard.core.domain.tracking.Track
import app.litterguard.core.model.ObjectCategory
import app.litterguard.core.ui.CameraFrameView
import app.litterguard.core.ui.OverlayColours
import app.litterguard.core.ui.drawLabelledBox
import app.litterguard.core.ui.drawZone
import java.util.Locale

/** Live view while watching: what the app sees and what it has concluded. */
@Composable
fun MonitorRoute(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MonitorViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    // Start once per visit, so pressing Stop in the notification isn't undone by a redraw.
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            viewModel.start()
        }
    }
    KeepScreenOn()
    MonitorScreen(
        status = status,
        onStop = {
            viewModel.stop()
            onClose()
        },
        onRetry = viewModel::start,
        onBack = onClose,
        modifier = modifier,
    )
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
internal fun MonitorScreen(
    status: MonitoringStatus,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxSize().safeDrawingPadding()) {
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            when (status) {
                is MonitoringStatus.Running -> LivePicture(status.view, Modifier.fillMaxSize())
                MonitoringStatus.Starting -> Waiting()
                MonitoringStatus.Stopped -> Text(stringResource(R.string.monitor_stopped))
                is MonitoringStatus.Failed -> Text(failureText(status.reason), Modifier.padding(16.dp))
            }
        }
        Column(
            Modifier
                .width(220.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val full = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            if (status is MonitoringStatus.Running) Details(status.view)
            if (status is MonitoringStatus.Stopped || status is MonitoringStatus.Failed) {
                Button(onClick = onRetry, modifier = full) { Text(stringResource(R.string.monitor_start)) }
            } else {
                Button(onClick = onStop, modifier = full) { Text(stringResource(R.string.monitor_stop)) }
            }
            OutlinedButton(onClick = onBack, modifier = full) { Text(stringResource(R.string.monitor_back)) }
        }
    }
}

@Composable
private fun Waiting() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Text(stringResource(R.string.monitor_starting), Modifier.padding(8.dp))
    }
}

@Composable
private fun LivePicture(
    view: LiveView,
    modifier: Modifier = Modifier,
) {
    val text = rememberTextMeasurer()
    val newObject = stringResource(R.string.box_new_object)
    val names = ObjectCategory.entries.associateWith { stringResource(nameOf(it)) }
    CameraFrameView(view.frame, modifier) {
        drawZone(view.zone.vertices)
        view.snapshot.tracks.filter { it.seenThisFrame }.forEach { track ->
            drawLabelledBox(track.box, trackLabel(track, names), colourOf(track.category), text)
        }
        view.snapshot.blobs.forEach { drawLabelledBox(it.box, newObject, OverlayColours.newObject, text) }
    }
}

private fun trackLabel(
    track: Track,
    names: Map<ObjectCategory, String>,
): String {
    val percent = (track.confidence * PERCENT).toInt()
    // A star marks someone standing inside the zone, so colour isn't the only signal.
    val mark = if (track.inZone) "★ " else ""
    return "$mark${names.getValue(track.category)} $percent%"
}

@Composable
private fun Details(view: LiveView) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val headline = headlineOf(view.snapshot)
        val colour =
            if (headline == Headline.ALERT_RAISED || headline == Headline.NEW_OBJECT) {
                OverlayColours.newObject
            } else {
                MaterialTheme.colorScheme.primary
            }
        Text(
            headlineText(headline, pendingSeconds(view.snapshot)),
            color = colour,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(stringResource(R.string.monitor_confirmed_count, view.dumpingConfirmed))
        val inference = view.inferenceMillis?.let { stringResource(R.string.monitor_inference, it) }
        val speed = String.format(Locale.ENGLISH, "%.1f", view.framesPerSecond)
        Text(
            listOfNotNull(stringResource(R.string.monitor_speed, speed), inference).joinToString("\n"),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(view.detectorDescription, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun headlineText(
    headline: Headline,
    seconds: Int?,
): String =
    when (headline) {
        Headline.LEARNING_SCENE -> stringResource(R.string.headline_learning)
        Headline.WATCHING -> stringResource(R.string.headline_watching)
        Headline.PERSON_IN_ZONE -> stringResource(R.string.headline_person)
        Headline.ANIMAL_IN_ZONE -> stringResource(R.string.headline_animal)
        Headline.CHECKING -> stringResource(R.string.headline_checking)
        Headline.NEW_OBJECT -> stringResource(R.string.headline_new_object, seconds ?: 0)
        Headline.ALERT_RAISED -> stringResource(R.string.headline_alert)
    }

@Composable
private fun failureText(reason: MonitoringFailure): String =
    when (reason) {
        MonitoringFailure.NoZone -> stringResource(R.string.failure_no_zone)
        is MonitoringFailure.Error -> stringResource(R.string.failure_error, reason.detail)
    }

private fun colourOf(category: ObjectCategory): Color =
    when (category) {
        ObjectCategory.PERSON -> OverlayColours.person
        ObjectCategory.VEHICLE -> OverlayColours.vehicle
        ObjectCategory.ANIMAL, ObjectCategory.OTHER -> OverlayColours.animal
    }

private fun nameOf(category: ObjectCategory): Int =
    when (category) {
        ObjectCategory.PERSON -> R.string.box_person
        ObjectCategory.VEHICLE -> R.string.box_vehicle
        ObjectCategory.ANIMAL -> R.string.box_animal
        ObjectCategory.OTHER -> R.string.box_other
    }

private const val PERCENT = 100
