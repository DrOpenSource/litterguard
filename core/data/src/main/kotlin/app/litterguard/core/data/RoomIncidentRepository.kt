package app.litterguard.core.data

import app.litterguard.core.data.db.EvidenceFileEntity
import app.litterguard.core.data.db.IncidentDao
import app.litterguard.core.data.db.IncidentEntity
import app.litterguard.core.data.db.IncidentWithEvidence
import app.litterguard.core.domain.ports.IncidentRepository
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.DeliveryState
import app.litterguard.core.model.EvidenceFile
import app.litterguard.core.model.EvidenceKind
import app.litterguard.core.model.Incident
import app.litterguard.core.model.IncidentId
import app.litterguard.core.model.IncidentKind
import app.litterguard.core.model.ObjectCategory
import app.litterguard.core.model.ReviewLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RoomIncidentRepository
    @Inject
    constructor(
        private val dao: IncidentDao,
    ) : IncidentRepository {
        override fun observeAll(): Flow<List<Incident>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

        override suspend fun find(id: IncidentId): Incident? = dao.find(id.value)?.toDomain()

        override suspend fun add(incident: Incident) {
            dao.insertWithEvidence(incident.toEntity(), incident.evidence.map { it.toEntity(incident.id) })
        }

        override suspend fun recordMerge(id: IncidentId) = dao.incrementMergedCount(id.value)

        override suspend fun setDeliveryState(
            id: IncidentId,
            state: DeliveryState,
        ) = dao.setDeliveryState(id.value, state.name)

        override suspend fun setReviewLabel(
            id: IncidentId,
            label: ReviewLabel,
        ) = dao.setReviewLabel(id.value, label.name)
    }

internal fun Incident.toEntity() =
    IncidentEntity(
        id = id.value,
        siteName = siteName,
        kind = kind.name,
        actorCategories = actorCategories.joinToString(",") { it.name },
        actorEnteredAt = actorEnteredAt?.toEpochMilli(),
        actorLeftAt = actorLeftAt?.toEpochMilli(),
        confirmedAt = confirmedAt.toEpochMilli(),
        boxLeft = objectBox?.left,
        boxTop = objectBox?.top,
        boxRight = objectBox?.right,
        boxBottom = objectBox?.bottom,
        deliveryState = deliveryState.name,
        reviewLabel = reviewLabel.name,
        detectorDescription = detectorDescription,
        mergedCount = mergedCount,
    )

internal fun EvidenceFile.toEntity(id: IncidentId) =
    EvidenceFileEntity(
        incidentId = id.value,
        kind = kind.name,
        relativePath = relativePath,
        width = width,
        height = height,
        capturedAt = capturedAt.toEpochMilli(),
    )

internal fun IncidentWithEvidence.toDomain() =
    Incident(
        id = IncidentId(incident.id),
        siteName = incident.siteName,
        kind = IncidentKind.valueOf(incident.kind),
        actorCategories =
            incident.actorCategories
                .split(",")
                .filter { it.isNotEmpty() }
                .map(ObjectCategory::valueOf)
                .toSet(),
        actorEnteredAt = incident.actorEnteredAt?.let(Instant::ofEpochMilli),
        actorLeftAt = incident.actorLeftAt?.let(Instant::ofEpochMilli),
        confirmedAt = Instant.ofEpochMilli(incident.confirmedAt),
        objectBox = incident.box(),
        deliveryState = DeliveryState.valueOf(incident.deliveryState),
        reviewLabel = ReviewLabel.valueOf(incident.reviewLabel),
        detectorDescription = incident.detectorDescription,
        mergedCount = incident.mergedCount,
        evidence =
            evidence.sortedBy { it.rowId }.map {
                EvidenceFile(
                    kind = EvidenceKind.valueOf(it.kind),
                    relativePath = it.relativePath,
                    width = it.width,
                    height = it.height,
                    capturedAt = Instant.ofEpochMilli(it.capturedAt),
                )
            },
    )

private fun IncidentEntity.box(): BoundingBox? {
    val left = boxLeft ?: return null
    return BoundingBox(left, requireNotNull(boxTop), requireNotNull(boxRight), requireNotNull(boxBottom))
}
