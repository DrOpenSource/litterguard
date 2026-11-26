package app.litterguard.core.alerts

import app.litterguard.core.alerts.telegram.GroupFinder
import app.litterguard.core.alerts.telegram.TelegramClient
import app.litterguard.core.alerts.telegram.TelegramResult
import app.litterguard.core.domain.ports.AlertChannelSetup
import app.litterguard.core.domain.ports.AlertChannelStatus
import app.litterguard.core.domain.ports.AlertSetupResult
import app.litterguard.core.domain.ports.SiteConfigRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

/** Guided Telegram setup: paste the token, add the bot to the group, and the group is found automatically. */
internal class TelegramAlertChannel(
    private val client: TelegramClient,
    private val credentials: BotCredentials,
    private val site: SiteConfigRepository,
    private val secureStorage: CoroutineDispatcher,
) : AlertChannelSetup {
    /** Ties the "add to group" link to this setup attempt. */
    private var nonce: String? = null

    override val status: Flow<AlertChannelStatus> =
        combine(credentials.botUsername, site.config) { username, config ->
            AlertChannelStatus(botUsername = username, groupTitle = config.alertChatTitle.takeIf { username != null })
        }

    override suspend fun connect(token: String): AlertSetupResult {
        val cleaned = token.trim()
        if (!TOKEN_SHAPE.matches(cleaned)) return AlertSetupResult.InvalidToken
        return when (val reply = client.getMe(cleaned)) {
            is TelegramResult.Ok -> {
                val username = reply.value.username
                if (username == null) {
                    AlertSetupResult.InvalidToken
                } else {
                    val changedBot = credentials.botUsername.value != username
                    withContext(secureStorage) { credentials.save(cleaned, username) }
                    if (changedBot) forgetGroup()
                    AlertSetupResult.Success
                }
            }

            else -> {
                reply.toSetupResult()
            }
        }
    }

    override suspend fun addToGroupLink(): String? {
        val username = credentials.botUsername.value ?: return null
        val fresh =
            UUID
                .randomUUID()
                .toString()
                .replace("-", "")
                .take(NONCE_LENGTH)
        nonce = fresh
        return "https://t.me/$username?startgroup=$fresh"
    }

    override suspend fun findGroup(): AlertSetupResult {
        val token = token()
        val username = credentials.botUsername.value
        return if (token == null || username == null) {
            AlertSetupResult.InvalidToken
        } else {
            searchForGroup(token, username)
        }
    }

    private suspend fun searchForGroup(
        token: String,
        username: String,
    ): AlertSetupResult =
        when (val reply = client.getUpdates(token)) {
            is TelegramResult.Ok -> {
                val chat = GroupFinder.find(reply.value, username, nonce)
                if (chat == null) {
                    AlertSetupResult.GroupNotFound
                } else {
                    site.update {
                        it.copy(alertChatId = chat.id.toString(), alertChatTitle = chat.title ?: "Telegram group")
                    }
                    AlertSetupResult.Success
                }
            }

            else -> {
                reply.toSetupResult()
            }
        }

    override suspend fun sendTestAlert(siteName: String): AlertSetupResult {
        val token = token()
        val chatId = site.config.first().alertChatId
        return when {
            token == null -> AlertSetupResult.InvalidToken
            chatId == null -> AlertSetupResult.GroupNotFound
            else -> sendTestTo(token, chatId, siteName)
        }
    }

    private suspend fun sendTestTo(
        token: String,
        chatId: String,
        siteName: String,
    ): AlertSetupResult {
        val text = "✅ LitterGuard test alert from $siteName. Dumping alerts will arrive in this group."
        var reply = client.sendMessage(token, chatId, text)
        if (reply is TelegramResult.ChatMoved) {
            val newId = reply.newChatId
            site.update { it.copy(alertChatId = newId) }
            reply = client.sendMessage(token, newId, text)
        }
        return reply.toSetupResult()
    }

    override suspend fun disconnect() {
        withContext(secureStorage) { credentials.clear() }
        forgetGroup()
    }

    private suspend fun token(): String? = withContext(secureStorage) { credentials.token() }

    private suspend fun forgetGroup() {
        site.update { it.copy(alertChatId = null, alertChatTitle = null) }
    }

    private fun TelegramResult<*>.toSetupResult(): AlertSetupResult =
        when (this) {
            is TelegramResult.Ok -> AlertSetupResult.Success
            TelegramResult.BadToken -> AlertSetupResult.InvalidToken
            is TelegramResult.Retryable -> AlertSetupResult.NoNetwork
            is TelegramResult.ChatMoved -> AlertSetupResult.GroupNotFound
            is TelegramResult.Rejected -> AlertSetupResult.Failed(description)
        }

    private companion object {
        /** BotFather tokens look like `1234567890:AAH…`. */
        val TOKEN_SHAPE = Regex("""\d{5,}:[A-Za-z0-9_-]{30,}""")
        const val NONCE_LENGTH = 16
    }
}
