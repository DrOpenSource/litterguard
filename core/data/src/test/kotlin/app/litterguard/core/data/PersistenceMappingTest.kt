package app.litterguard.core.data

import app.litterguard.core.data.db.IncidentWithEvidence
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.DeliveryState
import app.litterguard.core.model.EvidenceFile
import app.litterguard.core.model.EvidenceKind
import app.litterguard.core.model.Incident
import app.litterguard.core.model.IncidentId
import app.litterguard.core.model.IncidentKind
import app.litterguard.core.model.NormalizedPoint
import app.litterguard.core.model.ObjectCategory
import app.litterguard.core.model.Polygon
import app.litterguard.core.model.ReviewLabel
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class PersistenceMappingTest {
    private val time = Instant.ofEpochMilli(1_790_000_000_000)

    private val incident =
        Incident(
            id = IncidentId("abc"),
            siteName = "Gate 2",
            kind = IncidentKind.CONFIRMED_DUMPING,
            actorCategories = setOf(ObjectCategory.PERSON, ObjectCategory.VEHICLE),
            actorEnteredAt = time.minusSeconds(20),
            actorLeftAt = time.minusSeconds(6),
            confirmedAt = time,
            objectBox = BoundingBox(0.1f, 0.2f, 0.3f, 0.4f),
            deliveryState = DeliveryState.PENDING,
            reviewLabel = ReviewLabel.FALSE_ALARM,
            detectorDescription = "yolo",
            mergedCount = 2,
            evidence = listOf(EvidenceFile(EvidenceKind.SCENE, "abc/scene_0.jpg", 1280, 720, time)),
        )

    @Test
    fun `an incident survives a round trip through its rows`() {
        val rows = IncidentWithEvidence(incident.toEntity(), incident.evidence.map { it.toEntity(incident.id) })

        assertThat(rows.toDomain()).isEqualTo(incident)
    }

    @Test
    fun `missing optional values survive too`() {
        val sparse =
            incident.copy(
                actorCategories = emptySet(),
                actorEnteredAt = null,
                actorLeftAt = null,
                objectBox = null,
                evidence = emptyList(),
            )

        assertThat(IncidentWithEvidence(sparse.toEntity(), emptyList()).toDomain()).isEqualTo(sparse)
    }
}

class ZoneCodecTest {
    private val zone =
        Polygon(
            listOf(
                NormalizedPoint(0.1f, 0.1f),
                NormalizedPoint(0.9f, 0.1f),
                NormalizedPoint(0.9f, 0.9f),
                NormalizedPoint(0.1f, 0.9f),
            ),
        )

    @Test
    fun `a zone survives a round trip`() {
        assertThat(ZoneCodec.decode(ZoneCodec.encode(zone))).isEqualTo(zone)
    }

    @Test
    fun `bad stored data is treated as no zone`() {
        val bad =
            listOf(
                "not json",
                "[]",
                "[[0.1]]",
                "[[0.1,0.1],[0.9,0.1],[0.9,0.9]]",
                "[[0.1,0.1],[0.9,0.9],[0.9,0.1],[0.1,0.9]]",
                "[[0.1,0.1],[5.0,0.1],[0.9,0.9],[0.1,0.9]]",
            )

        for (text in bad) assertThat(ZoneCodec.decode(text)).isNull()
    }
}
