package app.litterguard.feature.incidents

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.litterguard.core.model.DeliveryState
import app.litterguard.core.model.EvidenceFile
import app.litterguard.core.model.Incident
import app.litterguard.core.model.IncidentId
import app.litterguard.core.model.IncidentKind
import app.litterguard.core.model.ReviewLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Past events with their pictures, and the operator's verdict on each (FR-6.2). */
@Composable
fun IncidentsRoute(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IncidentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected = state.selected
    BackHandler(enabled = selected != null) { viewModel.open(null) }
    if (selected == null) {
        IncidentList(state.incidents, viewModel::pathOf, viewModel::open, onClose, modifier)
    } else {
        IncidentDetail(selected, viewModel::pathOf, viewModel::review, { viewModel.open(null) }, modifier)
    }
}

@Composable
private fun IncidentList(
    incidents: List<Incident>,
    pathOf: (EvidenceFile) -> String,
    onOpen: (IncidentId) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onClose) { Text(stringResource(R.string.incidents_back)) }
                Text(stringResource(R.string.incidents_title), style = MaterialTheme.typography.headlineSmall)
            }
        }
        if (incidents.isEmpty()) item { Text(stringResource(R.string.incidents_empty)) }
        items(incidents, key = { it.id.value }) { incident ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(incident.id) }) {
                Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    incident.evidence.firstOrNull()?.let {
                        EvidenceImage(pathOf(it), THUMBNAIL_SAMPLE, Modifier.width(128.dp))
                    }
                    Summary(incident)
                }
            }
        }
    }
}

@Composable
private fun IncidentDetail(
    incident: Incident,
    pathOf: (EvidenceFile) -> String,
    onReview: (IncidentId, ReviewLabel) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.incidents_back)) }
            Summary(incident)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val tall = Modifier.heightIn(min = 48.dp)
                Button(onClick = { onReview(incident.id, ReviewLabel.CONFIRMED) }, modifier = tall) {
                    Text(stringResource(R.string.review_confirm))
                }
                OutlinedButton(onClick = { onReview(incident.id, ReviewLabel.FALSE_ALARM) }, modifier = tall) {
                    Text(stringResource(R.string.review_false_alarm))
                }
            }
        }
        items(incident.evidence, key = { it.relativePath }) { file ->
            EvidenceImage(pathOf(file), FULL_SAMPLE, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Summary(incident: Incident) {
    val formatter = DateTimeFormatter.ofPattern("d MMM, h:mm:ss a", Locale.ENGLISH).withZone(ZoneId.systemDefault())
    Column {
        Text(stringResource(kindText(incident.kind)), style = MaterialTheme.typography.titleMedium)
        Text(formatter.format(incident.confirmedAt))
        if (incident.deliveryState != DeliveryState.NOT_REQUIRED) {
            Text(stringResource(deliveryText(incident.deliveryState)))
        }
        if (incident.reviewLabel != ReviewLabel.NONE) Text(stringResource(reviewText(incident.reviewLabel)))
    }
}

@Composable
private fun EvidenceImage(
    path: String,
    sampleSize: Int,
    modifier: Modifier = Modifier,
) {
    val image by produceState<ImageBitmap?>(initialValue = null, path, sampleSize) {
        value =
            withContext(Dispatchers.IO) {
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeFile(path, options)?.asImageBitmap()
            }
    }
    image?.let {
        Image(
            bitmap = it,
            contentDescription = stringResource(R.string.evidence_picture),
            modifier = modifier.aspectRatio(it.width.toFloat() / it.height),
        )
    }
}

private fun kindText(kind: IncidentKind): Int =
    when (kind) {
        IncidentKind.CONFIRMED_DUMPING -> R.string.kind_dumping
        IncidentKind.UNATTRIBUTED_OBJECT -> R.string.kind_unattributed
        IncidentKind.INCONCLUSIVE -> R.string.kind_inconclusive
    }

private fun deliveryText(state: DeliveryState): Int =
    when (state) {
        DeliveryState.NOT_REQUIRED, DeliveryState.PENDING -> R.string.delivery_pending
        DeliveryState.DELIVERED -> R.string.delivery_sent
        DeliveryState.FAILED -> R.string.delivery_failed
    }

private fun reviewText(label: ReviewLabel): Int =
    when (label) {
        ReviewLabel.NONE, ReviewLabel.CONFIRMED -> R.string.reviewed_confirmed
        ReviewLabel.FALSE_ALARM -> R.string.reviewed_false_alarm
    }

private const val THUMBNAIL_SAMPLE = 8
private const val FULL_SAMPLE = 1
