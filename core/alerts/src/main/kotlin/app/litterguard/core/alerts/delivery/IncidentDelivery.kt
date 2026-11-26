package app.litterguard.core.alerts.delivery

import app.litterguard.core.alerts.BotCredentials
import app.litterguard.core.alerts.telegram.TelegramClient
import app.litterguard.core.alerts.telegram.TelegramResult
import app.litterguard.core.domain.ports.EvidenceStore
import app.litterguard.core.domain.ports.IncidentRepository
import app.litterguard.core.domain.ports.SiteConfigRepository
import app.litterguard.core.model.DeliveryState
import app.litterguard.core.model.EvidenceKind
import app.litterguard.core.model.Incident
import app.litterguard.core.model.IncidentId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

internal enum class DeliveryOutcome { DONE, RETRY, FAILED }

/** Sends one persisted incident to the Telegram group and records what happened (FR-7.1, FR-7.2). */
internal class IncidentDelivery(
    private val client: TelegramClient,
    private val credentials: BotCredentials,
    private val site: SiteConfigRepository,
    private val incidents: IncidentRepository,
    private val evidence: EvidenceStore,
    private val captions: AlertCaption,
    private val io: CoroutineDispatcher,
) {
    suspend fun deliver(id: IncidentId): DeliveryOutcome {
        val incident = incidents.find(id)
        // Nothing to send, or an earlier attempt already succeeded: never post the same alert twice.
        return if (incident == null || incident.deliveryState == DeliveryState.DELIVERED) {
            DeliveryOutcome.DONE
        } else {
            deliverNow(incident)
        }
    }

    private suspend fun deliverNow(incident: Incident): DeliveryOutcome {
        val token = withContext(io) { credentials.token() }
        val chatId = site.config.first().alertChatId
        if (token == null || chatId == null) {
            Timber.w("Telegram isn't set up; alert for %s can't be sent", incident.id.value)
            return fail(incident.id)
        }
        return record(incident.id, sendFollowingMoves(token, chatId, incident))
    }

    private suspend fun sendFollowingMoves(
        token: String,
        chatId: String,
        incident: Incident,
    ): TelegramResult<Unit> {
        val photos = photosOf(incident)
        val reply = send(token, chatId, incident, photos)
        if (reply !is TelegramResult.ChatMoved) return reply
        site.update { it.copy(alertChatId = reply.newChatId) }
        return send(token, reply.newChatId, incident, photos)
    }

    private suspend fun record(
        id: IncidentId,
        reply: TelegramResult<Unit>,
    ): DeliveryOutcome =
        when (reply) {
            is TelegramResult.Ok -> {
                incidents.setDeliveryState(id, DeliveryState.DELIVERED)
                Timber.i("Alert for %s delivered", id.value)
                DeliveryOutcome.DONE
            }

            is TelegramResult.Retryable -> {
                Timber.w("Alert for %s will be retried (HTTP %s)", id.value, reply.httpCode)
                DeliveryOutcome.RETRY
            }

            TelegramResult.BadToken, is TelegramResult.ChatMoved, is TelegramResult.Rejected -> {
                Timber.e("Alert for %s was rejected: %s", id.value, reply)
                fail(id)
            }
        }

    private suspend fun send(
        token: String,
        chatId: String,
        incident: Incident,
        photos: List<File>,
    ): TelegramResult<Unit> {
        val caption = captions.of(incident)
        return if (photos.isEmpty()) {
            client.sendMessage(token, chatId, caption)
        } else {
            client.sendPhotos(token, chatId, photos, caption)
        }
    }

    /** The scene (what was left) first, then the best pictures of who was there. */
    private suspend fun photosOf(incident: Incident): List<File> =
        withContext(io) {
            val (scenes, others) = incident.evidence.partition { it.kind == EvidenceKind.SCENE }
            (scenes + others.filter { it.kind == EvidenceKind.ACTOR })
                .map { File(evidence.absolutePath(it)) }
                .filter { it.isFile }
                .take(MAX_PHOTOS)
        }

    private suspend fun fail(id: IncidentId): DeliveryOutcome {
        incidents.setDeliveryState(id, DeliveryState.FAILED)
        return DeliveryOutcome.FAILED
    }

    private companion object {
        const val MAX_PHOTOS = 3
    }
}
