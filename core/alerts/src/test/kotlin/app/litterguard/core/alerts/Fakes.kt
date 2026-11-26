package app.litterguard.core.alerts

import app.litterguard.core.domain.ports.EvidenceStore
import app.litterguard.core.domain.ports.IncidentRepository
import app.litterguard.core.domain.ports.SiteConfigRepository
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.Instant

/** A made-up token in the real shape; not a credential. */
const val FAKE_TOKEN = "123456789:AAFakeTokenForTestsOnly_0123456789abc"

internal class FakeCredentials(
    private var stored: String? = null,
    username: String? = null,
) : BotCredentials {
    override val botUsername: MutableStateFlow<String?> = MutableStateFlow(username)

    override fun token(): String? = stored

    override fun save(
        token: String,
        botUsername: String,
    ) {
        stored = token
        this.botUsername.value = botUsername
    }

    override fun clear() {
        stored = null
        botUsername.value = null
    }
}

internal class FakeSiteConfig(
    initial: SiteConfig = SiteConfig(),
) : SiteConfigRepository {
    override val config: MutableStateFlow<SiteConfig> = MutableStateFlow(initial)

    val current: StateFlow<SiteConfig> get() = config

    override suspend fun update(transform: (SiteConfig) -> SiteConfig) {
        config.value = transform(config.value)
    }
}

internal class FakeIncidents(
    vararg initial: Incident,
) : IncidentRepository {
    private val rows = MutableStateFlow(initial.toList())

    fun byId(id: String): Incident = rows.value.single { it.id.value == id }

    override fun observeAll(): Flow<List<Incident>> = rows

    override suspend fun find(id: IncidentId): Incident? = rows.value.firstOrNull { it.id == id }

    override suspend fun add(incident: Incident) {
        rows.value += incident
    }

    override suspend fun recordMerge(id: IncidentId) = change(id) { it.copy(mergedCount = it.mergedCount + 1) }

    override suspend fun setDeliveryState(
        id: IncidentId,
        state: DeliveryState,
    ) = change(id) { it.copy(deliveryState = state) }

    override suspend fun setReviewLabel(
        id: IncidentId,
        label: ReviewLabel,
    ) = change(id) { it.copy(reviewLabel = label) }

    private fun change(
        id: IncidentId,
        transform: (Incident) -> Incident,
    ) {
        rows.value = rows.value.map { if (it.id == id) transform(it) else it }
    }
}

internal class FolderEvidenceStore(
    private val root: File,
) : EvidenceStore {
    override suspend fun save(
        id: IncidentId,
        kind: EvidenceKind,
        index: Int,
        image: ImageBuffer,
        capturedAt: Instant,
        highlight: BoundingBox?,
    ): EvidenceFile = error("Not used in these tests")

    override fun absolutePath(file: EvidenceFile): String = File(root, file.relativePath).absolutePath
}
