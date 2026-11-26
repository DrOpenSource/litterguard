package app.litterguard.core.domain.ports

import app.litterguard.core.domain.site.SiteConfig
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.DeliveryState
import app.litterguard.core.model.EvidenceFile
import app.litterguard.core.model.EvidenceKind
import app.litterguard.core.model.ImageBuffer
import app.litterguard.core.model.Incident
import app.litterguard.core.model.IncidentId
import app.litterguard.core.model.ReviewLabel
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface IncidentRepository {
    /** Newest first. */
    fun observeAll(): Flow<List<Incident>>

    suspend fun find(id: IncidentId): Incident?

    /** Inserts the incident together with its evidence rows, atomically. */
    suspend fun add(incident: Incident)

    suspend fun recordMerge(id: IncidentId)

    suspend fun setDeliveryState(
        id: IncidentId,
        state: DeliveryState,
    )

    suspend fun setReviewLabel(
        id: IncidentId,
        label: ReviewLabel,
    )
}

/** Evidence images in app-private storage; nothing goes to the shared gallery. */
interface EvidenceStore {
    /**
     * Encodes [image] as JPEG and returns its record. [highlight], if given, is outlined on the picture so people
     * can see at a glance what was left behind. [index] distinguishes several images of one kind.
     */
    suspend fun save(
        id: IncidentId,
        kind: EvidenceKind,
        index: Int,
        image: ImageBuffer,
        capturedAt: Instant,
        highlight: BoundingBox? = null,
    ): EvidenceFile

    /** Absolute file-system path of a stored image. */
    fun absolutePath(file: EvidenceFile): String
}

interface SiteConfigRepository {
    val config: Flow<SiteConfig>

    suspend fun update(transform: (SiteConfig) -> SiteConfig)
}
