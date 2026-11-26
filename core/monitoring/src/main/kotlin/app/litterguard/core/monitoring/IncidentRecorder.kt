package app.litterguard.core.monitoring

import app.litterguard.core.domain.decision.IncidentDraft
import app.litterguard.core.domain.evidence.EvidenceFrame
import app.litterguard.core.domain.ports.AlertSink
import app.litterguard.core.domain.ports.Clock
import app.litterguard.core.domain.ports.EvidenceStore
import app.litterguard.core.domain.ports.IncidentRepository
import app.litterguard.core.model.DeliveryState
import app.litterguard.core.model.EvidenceFile
import app.litterguard.core.model.EvidenceKind
import app.litterguard.core.model.ImageBuffer
import app.litterguard.core.model.Incident
import app.litterguard.core.model.IncidentId
import app.litterguard.core.model.IncidentKind
import app.litterguard.core.model.MonotonicTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.time.Instant
import kotlin.time.toJavaDuration

/** Everything needed to persist an incident. All images are private copies. */
internal class IncidentCapture(
    val draft: IncidentDraft,
    val siteName: String,
    val detectorDescription: String,
    /** The frame in which the incident was decided: it shows what was left behind. */
    val scene: ImageBuffer,
    val actorFrames: List<EvidenceFrame>,
)

/**
 * Persists incidents and queues alerts, strictly in the order requested and away from the camera loop. Its [scope]
 * outlives a monitoring session, so pressing Stop never loses an incident that was just confirmed.
 * Call it from one thread.
 */
internal class IncidentRecorder(
    private val incidents: IncidentRepository,
    private val evidence: EvidenceStore,
    private val alerts: AlertSink,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private var tail: Job? = null

    /** Saves the evidence files first, then the database row, so a crash never leaves a row without its files. */
    fun commit(capture: IncidentCapture) {
        // Convert to wall-clock time now, while the two clocks still describe the same moment.
        val now = clock.monotonic()
        val wallNow = clock.wallClock()

        fun wall(time: MonotonicTime): Instant = wallNow.minus((now - time).toJavaDuration())

        enqueue {
            val draft = capture.draft
            val confirmedAt = wall(draft.confirmedAt)
            val files = saveEvidence(capture, confirmedAt, ::wall)
            incidents.add(
                Incident(
                    id = draft.id,
                    siteName = capture.siteName,
                    kind = draft.kind,
                    actorCategories = draft.actorCategories,
                    actorEnteredAt = draft.actorEnteredAt?.let(::wall),
                    actorLeftAt = draft.actorLeftAt?.let(::wall),
                    confirmedAt = confirmedAt,
                    objectBox = draft.objectBox,
                    deliveryState =
                        if (draft.kind == IncidentKind.CONFIRMED_DUMPING) {
                            DeliveryState.PENDING
                        } else {
                            DeliveryState.NOT_REQUIRED
                        },
                    detectorDescription = capture.detectorDescription,
                    evidence = files,
                ),
            )
            Timber.i("Incident %s saved (%s, %d images)", draft.id.value, draft.kind, files.size)
        }
    }

    fun alert(id: IncidentId) = enqueue { alerts.enqueue(id) }

    fun merge(id: IncidentId) = enqueue { incidents.recordMerge(id) }

    private suspend fun saveEvidence(
        capture: IncidentCapture,
        confirmedAt: Instant,
        wall: (MonotonicTime) -> Instant,
    ): List<EvidenceFile> {
        val id = capture.draft.id
        val scene =
            saveOne {
                evidence.save(id, EvidenceKind.SCENE, 0, capture.scene, confirmedAt, capture.draft.objectBox)
            }
        val actors =
            capture.actorFrames.mapIndexed { index, frame ->
                saveOne { evidence.save(id, EvidenceKind.ACTOR, index, frame.image, wall(frame.capturedAt)) }
            }
        return listOfNotNull(scene) + actors.filterNotNull()
    }

    /** A full disk must not lose the incident record or the alert, only that picture. */
    private suspend fun saveOne(save: suspend () -> EvidenceFile): EvidenceFile? =
        try {
            save()
        } catch (e: IOException) {
            Timber.e(e, "Could not save an evidence image")
            null
        }

    private fun enqueue(work: suspend () -> Unit) {
        val previous = tail
        tail =
            scope.launch {
                previous?.join()
                work()
            }
    }
}
