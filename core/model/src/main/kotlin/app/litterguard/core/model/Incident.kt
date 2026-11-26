package app.litterguard.core.model

import java.time.Instant

/** What the pipeline concluded about an event (PRD §6). Only [CONFIRMED_DUMPING] sounds the siren and alerts. */
enum class IncidentKind {
    CONFIRMED_DUMPING,

    /** A new object with no person or vehicle to attribute it to, e.g. after an animal visit (FR-3.2). */
    UNATTRIBUTED_OBJECT,

    /** The scene changed too much to judge: lighting change or camera shake (PRD §6.2). */
    INCONCLUSIVE,
}

/** Where an alert is on its way to the group chat. */
enum class DeliveryState {
    /** This incident is never alerted (unattributed or inconclusive). */
    NOT_REQUIRED,
    PENDING,
    DELIVERED,

    /** Permanently rejected (for example a wrong token); needs the operator. */
    FAILED,
}

/** The operator's verdict (FR-6.2). */
enum class ReviewLabel { NONE, CONFIRMED, FALSE_ALARM }

enum class EvidenceKind {
    /** The frame showing the object that was left behind. */
    SCENE,

    /** A frame showing the person or vehicle while they were in the zone. */
    ACTOR,
    PLATE_CROP,
    HIGH_RES,
}

/** One stored image belonging to an incident. [relativePath] is relative to the app-private evidence folder. */
data class EvidenceFile(
    val kind: EvidenceKind,
    val relativePath: String,
    val width: Int,
    val height: Int,
    val capturedAt: Instant,
)

/** The persisted record of an event (PRD §8). Times are wall-clock. */
data class Incident(
    val id: IncidentId,
    val siteName: String,
    val kind: IncidentKind,
    val actorCategories: Set<ObjectCategory>,
    val actorEnteredAt: Instant?,
    val actorLeftAt: Instant?,
    val confirmedAt: Instant,
    val objectBox: BoundingBox?,
    val deliveryState: DeliveryState,
    val reviewLabel: ReviewLabel = ReviewLabel.NONE,
    val detectorDescription: String = "",
    /** Further confirmations folded into this incident during the cooldown (FR-3.3). */
    val mergedCount: Int = 0,
    val evidence: List<EvidenceFile> = emptyList(),
)
