package app.litterguard.core.alerts.telegram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class ApiEnvelope(
    val ok: Boolean,
    val result: JsonElement? = null,
    val description: String? = null,
    val parameters: ResponseParameters? = null,
)

@Serializable
internal data class ResponseParameters(
    @SerialName("retry_after") val retryAfter: Int? = null,
    @SerialName("migrate_to_chat_id") val migrateToChatId: Long? = null,
)

@Serializable
internal data class TelegramUser(
    val id: Long,
    val username: String? = null,
)

@Serializable
internal data class TelegramChat(
    val id: Long,
    val type: String,
    val title: String? = null,
) {
    val isGroup: Boolean get() = type == "group" || type == "supergroup"
}

@Serializable
internal data class TelegramMessage(
    val chat: TelegramChat,
    val text: String? = null,
)

@Serializable
internal data class ChatMember(
    val status: String,
)

@Serializable
internal data class ChatMemberUpdate(
    val chat: TelegramChat,
    @SerialName("new_chat_member") val newMember: ChatMember,
)

@Serializable
internal data class TelegramUpdate(
    @SerialName("update_id") val updateId: Long,
    val message: TelegramMessage? = null,
    @SerialName("my_chat_member") val myChatMember: ChatMemberUpdate? = null,
)
