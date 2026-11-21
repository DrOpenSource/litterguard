package app.litterguard.core.model

/** What kind of thing a detection is, as far as the dumping logic cares. */
enum class ObjectCategory {
    PERSON,
    VEHICLE,
    ANIMAL,
    OTHER,
    ;

    /** Only people and vehicles can be "actors" that dump waste. Animals are tracked but never attributed (FR-3.2). */
    val isActor: Boolean get() = this == PERSON || this == VEHICLE
}

/**
 * One object reported by a detector for one frame.
 *
 * @property label the model's raw class name (e.g. "motorcycle"), kept for debugging and evidence.
 * @property confidence detector score in `[0, 1]`.
 */
data class Detection(
    val box: BoundingBox,
    val category: ObjectCategory,
    val label: String,
    val confidence: Float,
) {
    init {
        require(confidence in 0f..1f) { "Confidence must be in [0, 1], got $confidence" }
    }
}

/** Stable identity of a tracked object across frames within one monitoring session. */
@JvmInline
value class TrackId(
    val value: Int,
)

/** Identity of a persisted incident (a UUID string). */
@JvmInline
value class IncidentId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "IncidentId must not be blank" }
    }
}
