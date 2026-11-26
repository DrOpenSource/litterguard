package app.litterguard.core.domain.ports

import app.litterguard.core.model.IncidentId
import kotlinx.coroutines.flow.Flow

/** Queues an alert for an already persisted incident. Delivery survives restarts and offline periods (FR-7.2). */
fun interface AlertSink {
    fun enqueue(id: IncidentId)
}

/** The Telegram connection as far as the setup screen needs to know. The token itself is never exposed. */
data class AlertChannelStatus(
    /** The bot's @username once a token has been accepted. */
    val botUsername: String? = null,
    val groupTitle: String? = null,
) {
    val isReady: Boolean get() = botUsername != null && groupTitle != null
}

sealed interface AlertSetupResult {
    data object Success : AlertSetupResult

    /** Telegram rejected the token. */
    data object InvalidToken : AlertSetupResult

    data object NoNetwork : AlertSetupResult

    /** No fresh message from a group was found; the operator needs to repeat the "add to group" step. */
    data object GroupNotFound : AlertSetupResult

    /** Anything else; [detail] is safe to show (it never contains the token). */
    data class Failed(
        val detail: String,
    ) : AlertSetupResult
}

/** Guided Telegram setup (FR-1.5): paste the token, add the bot to the group, and the group is found automatically. */
interface AlertChannelSetup {
    val status: Flow<AlertChannelStatus>

    /** Checks the token with Telegram and stores it encrypted. */
    suspend fun connect(token: String): AlertSetupResult

    /** A link that opens Telegram to pick the group, or null before [connect] succeeds. */
    suspend fun addToGroupLink(): String?

    suspend fun findGroup(): AlertSetupResult

    suspend fun sendTestAlert(siteName: String): AlertSetupResult

    suspend fun disconnect()
}
