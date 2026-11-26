package app.litterguard.feature.setup

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.litterguard.core.domain.ports.AlertSetupResult

/** Guided Telegram setup (FR-1.5): no chat IDs to type. */
@Composable
fun AlertsRoute(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlertsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.alerts_title), style = MaterialTheme.typography.headlineSmall)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.lastResult?.let { (action, result) -> ResultText(action, result) }
        val bot = state.status.botUsername
        if (bot == null) {
            TokenStep(enabled = !state.busy, onConnect = viewModel::connect)
        } else {
            Text(stringResource(R.string.alerts_bot_connected, bot))
            GroupStep(
                state,
                GroupActions(
                    openGroupPicker = viewModel::openGroupPicker,
                    findGroup = viewModel::findGroup,
                    sendTest = viewModel::sendTest,
                    disconnect = viewModel::disconnect,
                ),
            )
        }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.back))
        }
    }
}

@Composable
private fun TokenStep(
    enabled: Boolean,
    onConnect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Deliberately not saved across process death: the token is only ever stored encrypted.
        val token = remember { TextFieldState() }
        Text(stringResource(R.string.alerts_token_help))
        OutlinedSecureTextField(
            state = token,
            label = { Text(stringResource(R.string.alerts_token_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onConnect(token.text.toString()) },
            enabled = enabled && token.text.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.alerts_connect))
        }
    }
}

private data class GroupActions(
    val openGroupPicker: ((String) -> Unit) -> Unit,
    val findGroup: () -> Unit,
    val sendTest: () -> Unit,
    val disconnect: () -> Unit,
)

@Composable
private fun GroupStep(
    state: AlertsUiState,
    actions: GroupActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val uriHandler = LocalUriHandler.current
        var noTelegram by remember { mutableStateOf(false) }
        val full = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        val group = state.status.groupTitle

        Text(
            if (group != null) {
                stringResource(R.string.alerts_group_found, group)
            } else {
                stringResource(R.string.alerts_group_help)
            },
        )
        OutlinedButton(
            onClick = {
                actions.openGroupPicker { link ->
                    try {
                        uriHandler.openUri(link)
                        noTelegram = false
                    } catch (expected: ActivityNotFoundException) {
                        noTelegram = true
                    } catch (expected: IllegalArgumentException) {
                        noTelegram = true
                    }
                }
            },
            enabled = !state.busy,
            modifier = full,
        ) {
            Text(stringResource(R.string.alerts_open_telegram))
        }
        if (noTelegram) Text(stringResource(R.string.alerts_no_telegram), color = MaterialTheme.colorScheme.error)
        Button(onClick = actions.findGroup, enabled = !state.busy, modifier = full) {
            Text(stringResource(R.string.alerts_find_group))
        }
        if (group != null) {
            Button(onClick = actions.sendTest, enabled = !state.busy, modifier = full) {
                Text(stringResource(R.string.alerts_send_test))
            }
        }
        OutlinedButton(onClick = actions.disconnect, enabled = !state.busy, modifier = full) {
            Text(stringResource(R.string.alerts_disconnect))
        }
    }
}

@Composable
private fun ResultText(
    action: AlertAction,
    result: AlertSetupResult,
) {
    val message =
        when (result) {
            AlertSetupResult.Success -> {
                stringResource(
                    when (action) {
                        AlertAction.CONNECT -> R.string.result_connected
                        AlertAction.FIND_GROUP -> R.string.result_group_found
                        AlertAction.TEST -> R.string.result_test_sent
                    },
                )
            }

            AlertSetupResult.InvalidToken -> {
                stringResource(R.string.result_bad_token)
            }

            AlertSetupResult.NoNetwork -> {
                stringResource(R.string.result_no_network)
            }

            AlertSetupResult.GroupNotFound -> {
                stringResource(R.string.result_no_group)
            }

            is AlertSetupResult.Failed -> {
                stringResource(R.string.result_failed, result.detail)
            }
        }
    val colour =
        if (result == AlertSetupResult.Success) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        }
    Text(message, color = colour, style = MaterialTheme.typography.titleMedium)
}
