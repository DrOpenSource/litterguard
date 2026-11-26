package app.litterguard.core.alerts.telegram

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

/** What came back from one Bot API call. No variant ever contains the token or the request URL. */
internal sealed interface TelegramResult<out T> {
    data class Ok<T>(
        val value: T,
    ) : TelegramResult<T>

    /** The token was rejected. */
    data object BadToken : TelegramResult<Nothing>

    /** Couldn't reach Telegram, or Telegram had a temporary problem: try again later. */
    data class Retryable(
        val retryAfterSeconds: Int? = null,
        val httpCode: Int? = null,
    ) : TelegramResult<Nothing>

    /** The group was upgraded to a supergroup and has a new ID. */
    data class ChatMoved(
        val newChatId: String,
    ) : TelegramResult<Nothing>

    /** Retrying won't help. */
    data class Rejected(
        val httpCode: Int,
        val description: String,
    ) : TelegramResult<Nothing>
}

/** The few Bot API calls LitterGuard needs. The token is only ever placed in the request path, never logged. */
internal class TelegramClient(
    private val http: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val dispatcher: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getMe(token: String): TelegramResult<TelegramUser> =
        call(token, "getMe", FormBody.Builder().build(), TelegramUser.serializer())

    /** The latest 100 messages and "bot was added" events. Telegram drops older ones, which setup doesn't need. */
    suspend fun getUpdates(token: String): TelegramResult<List<TelegramUpdate>> {
        val body =
            FormBody
                .Builder()
                .add("timeout", "0")
                .add("limit", "100")
                .add("offset", "-100")
                .add("allowed_updates", """["message","my_chat_member"]""")
                .build()
        return call(token, "getUpdates", body, ListSerializer(TelegramUpdate.serializer()))
    }

    suspend fun sendMessage(
        token: String,
        chatId: String,
        text: String,
    ): TelegramResult<Unit> {
        val body =
            FormBody
                .Builder()
                .add("chat_id", chatId)
                .add("text", text)
                .build()
        return call(token, "sendMessage", body, JsonElement.serializer()).discard()
    }

    /** Sends one photo, or several as an album with [caption] under the first (FR-7.1). */
    suspend fun sendPhotos(
        token: String,
        chatId: String,
        photos: List<File>,
        caption: String,
    ): TelegramResult<Unit> {
        require(photos.isNotEmpty() && photos.size <= MAX_ALBUM) { "Telegram albums hold 1 to $MAX_ALBUM photos" }
        val form = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("chat_id", chatId)
        val method =
            if (photos.size == 1) {
                form.addFormDataPart("caption", caption)
                form.addFormDataPart("photo", photos.single().name, photos.single().asRequestBody(JPEG))
                "sendPhoto"
            } else {
                val media =
                    buildJsonArray {
                        photos.indices.forEach { index -> add(albumEntry(index, caption)) }
                    }
                form.addFormDataPart("media", media.toString())
                photos.forEachIndexed { index, file ->
                    form.addFormDataPart("photo$index", file.name, file.asRequestBody(JPEG))
                }
                "sendMediaGroup"
            }
        return call(token, method, form.build(), JsonElement.serializer()).discard()
    }

    private fun albumEntry(
        index: Int,
        caption: String,
    ): JsonObject =
        buildJsonObject {
            put("type", "photo")
            put("media", "attach://photo$index")
            if (index == 0) put("caption", caption)
        }

    private suspend fun <T> call(
        token: String,
        method: String,
        body: RequestBody,
        resultSerializer: KSerializer<T>,
    ): TelegramResult<T> =
        withContext(dispatcher) {
            val url =
                baseUrl
                    .newBuilder()
                    .addPathSegment("bot$token")
                    .addPathSegment(method)
                    .build()
            try {
                http
                    .newCall(
                        Request
                            .Builder()
                            .url(url)
                            .post(body)
                            .build(),
                    ).execute()
                    .use { response ->
                        interpret(response.code, response.body.string(), resultSerializer)
                    }
            } catch (expected: IOException) {
                // The exception text can contain the URL, and with it the token, so it is dropped on purpose.
                TelegramResult.Retryable()
            }
        }

    private fun <T> interpret(
        code: Int,
        text: String,
        resultSerializer: KSerializer<T>,
    ): TelegramResult<T> {
        val envelope =
            try {
                json.decodeFromString(ApiEnvelope.serializer(), text)
            } catch (expected: SerializationException) {
                // Not a Bot API reply (for example a gateway error page); the HTTP code decides what to do.
                null
            }
        val result = envelope?.result
        val movedTo = envelope?.parameters?.migrateToChatId
        return when {
            envelope?.ok == true && result != null -> decodeResult(code, result, resultSerializer)

            movedTo != null -> TelegramResult.ChatMoved(movedTo.toString())

            code == HTTP_TOO_MANY -> TelegramResult.Retryable(envelope?.parameters?.retryAfter, code)

            code >= HTTP_SERVER_ERROR || envelope == null -> TelegramResult.Retryable(httpCode = code)

            // Telegram answers 401 for a revoked token and 404 for a malformed one.
            code == HTTP_UNAUTHORIZED || code == HTTP_NOT_FOUND -> TelegramResult.BadToken

            else -> TelegramResult.Rejected(code, envelope.description.orEmpty())
        }
    }

    private fun <T> decodeResult(
        code: Int,
        result: JsonElement,
        serializer: KSerializer<T>,
    ): TelegramResult<T> =
        try {
            TelegramResult.Ok(json.decodeFromJsonElement(serializer, result))
        } catch (expected: SerializationException) {
            TelegramResult.Rejected(code, "Unexpected reply from Telegram")
        }

    private fun <T> TelegramResult<T>.discard(): TelegramResult<Unit> =
        when (this) {
            is TelegramResult.Ok -> TelegramResult.Ok(Unit)
            is TelegramResult.BadToken -> this
            is TelegramResult.Retryable -> this
            is TelegramResult.ChatMoved -> this
            is TelegramResult.Rejected -> this
        }

    companion object {
        const val MAX_ALBUM = 10
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY = 429
        private const val HTTP_SERVER_ERROR = 500
        private val JPEG = "image/jpeg".toMediaType()
    }
}
