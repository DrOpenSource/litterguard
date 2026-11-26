package app.litterguard.feature.setup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.litterguard.core.domain.config.Sensitivity
import app.litterguard.core.domain.ports.AudioRoute
import app.litterguard.core.ui.LitterGuardTheme

/** Where the home screen can take the operator. */
data class HomeNavigation(
    val drawZone: () -> Unit,
    val setUpAlerts: () -> Unit,
    val watch: () -> Unit,
    val incidents: () -> Unit,
)

@Composable
fun HomeRoute(
    navigation: HomeNavigation,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasCamera by remember { mutableStateOf(context.hasCameraPermission()) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            hasCamera = context.hasCameraPermission()
            denied = !hasCamera
        }
    // The operator may have changed permissions or paired a speaker while away.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasCamera = context.hasCameraPermission()
        viewModel.refreshAudioRoute()
    }
    HomeScreen(
        state = state,
        camera =
            CameraAccess(
                granted = hasCamera,
                denied = denied,
                request = { launcher.launch(wantedPermissions()) },
                openSettings = { context.openAppSettings() },
            ),
        actions =
            HomeActions(
                toggleSiren = viewModel::toggleTestSiren,
                siteName = viewModel::setSiteName,
                sensitivity = viewModel::setSensitivity,
            ),
        navigation = navigation,
        modifier = modifier,
    )
}

private fun Context.hasCameraPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun wantedPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.CAMERA)
    }

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

internal data class CameraAccess(
    val granted: Boolean,
    val denied: Boolean,
    val request: () -> Unit,
    val openSettings: () -> Unit,
)

internal data class HomeActions(
    val toggleSiren: () -> Unit,
    val siteName: (String) -> Unit,
    val sensitivity: (Sensitivity) -> Unit,
)

@Composable
internal fun HomeScreen(
    state: HomeUiState,
    camera: CameraAccess,
    actions: HomeActions,
    navigation: HomeNavigation,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
        CameraStep(camera)
        ZoneStep(state, camera.granted, navigation.drawZone)
        SirenStep(state, actions.toggleSiren)
        AlertsStep(state, navigation.setUpAlerts)
        SettingsCard(state, actions)
        val ready = camera.granted && state.site.zone != null
        Button(
            onClick = navigation.watch,
            enabled = ready,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        ) {
            Text(
                stringResource(if (state.isWatching) R.string.home_view_live else R.string.home_start),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        if (!ready) Text(stringResource(R.string.home_not_ready))
        OutlinedButton(onClick = navigation.incidents, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.home_incidents))
        }
    }
}

@Composable
private fun CameraStep(camera: CameraAccess) {
    Step(
        title = stringResource(R.string.step_camera),
        detail =
            stringResource(
                when {
                    camera.granted -> R.string.camera_allowed
                    camera.denied -> R.string.camera_denied
                    else -> R.string.camera_why
                },
            ),
    ) {
        if (!camera.granted) {
            Button(onClick = camera.request) { Text(stringResource(R.string.camera_allow)) }
            if (camera.denied) {
                OutlinedButton(onClick = camera.openSettings) { Text(stringResource(R.string.open_settings)) }
            }
        }
    }
}

@Composable
private fun ZoneStep(
    state: HomeUiState,
    enabled: Boolean,
    onDraw: () -> Unit,
) {
    val drawn = state.site.zone != null
    Step(
        title = stringResource(R.string.step_zone),
        detail = stringResource(if (drawn) R.string.zone_saved else R.string.zone_missing),
    ) {
        Button(onClick = onDraw, enabled = enabled) {
            Text(stringResource(if (drawn) R.string.zone_redraw else R.string.zone_draw))
        }
    }
}

@Composable
private fun SirenStep(
    state: HomeUiState,
    onToggle: () -> Unit,
) {
    val route =
        when (val current = state.audioRoute) {
            AudioRoute.PhoneSpeaker -> stringResource(R.string.route_phone)
            is AudioRoute.Bluetooth -> stringResource(R.string.route_bluetooth, current.deviceName)
        }
    Step(title = stringResource(R.string.step_siren), detail = route) {
        Button(onClick = onToggle) {
            Text(stringResource(if (state.soundPlaying) R.string.siren_stop else R.string.siren_test))
        }
    }
}

@Composable
private fun AlertsStep(
    state: HomeUiState,
    onSetUp: () -> Unit,
) {
    val group = state.alerts.groupTitle
    Step(
        title = stringResource(R.string.step_alerts),
        detail =
            if (group != null) {
                stringResource(R.string.alerts_ready, group)
            } else {
                stringResource(R.string.alerts_missing)
            },
    ) {
        Button(onClick = onSetUp) { Text(stringResource(R.string.alerts_set_up)) }
    }
}

@Composable
private fun SettingsCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    // Edited locally so typing is never interrupted by the value being saved.
    var name by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name ?: state.site.siteName,
                onValueChange = {
                    name = it
                    actions.siteName(it)
                },
                label = { Text(stringResource(R.string.site_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.sensitivity))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sensitivity.entries.forEach { option ->
                    FilterChip(
                        selected = state.site.sensitivity == option,
                        onClick = { actions.sensitivity(option) },
                        label = { Text(stringResource(option.label())) },
                    )
                }
            }
        }
    }
}

private fun Sensitivity.label(): Int =
    when (this) {
        Sensitivity.LOW -> R.string.sensitivity_low
        Sensitivity.NORMAL -> R.string.sensitivity_normal
        Sensitivity.HIGH -> R.string.sensitivity_high
    }

@Composable
private fun Step(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    buttons: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail)
            buttons()
        }
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    LitterGuardTheme {
        HomeScreen(
            state = HomeUiState(),
            camera = CameraAccess(granted = false, denied = false, request = {}, openSettings = {}),
            actions = HomeActions(toggleSiren = {}, siteName = {}, sensitivity = {}),
            navigation = HomeNavigation({}, {}, {}, {}),
        )
    }
}
