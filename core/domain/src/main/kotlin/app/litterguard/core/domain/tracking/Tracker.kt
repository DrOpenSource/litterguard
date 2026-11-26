package app.litterguard.core.domain.tracking

import app.litterguard.core.domain.config.DetectionConfig
import app.litterguard.core.domain.geometry.contains
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.Detection
import app.litterguard.core.model.MonotonicTime
import app.litterguard.core.model.ObjectCategory
import app.litterguard.core.model.Polygon
import app.litterguard.core.model.TrackId
import kotlin.math.hypot
import kotlin.math.max
import kotlin.time.Duration

/** The state of one tracked person, vehicle or animal after a frame. */
data class Track(
    val id: TrackId,
    /** The most frequent category over recent frames, so a one-frame misclassification doesn't flip it. */
    val category: ObjectCategory,
    val label: String,
    val box: BoundingBox,
    val confidence: Float,
    /** In the dump zone, with enter/exit hysteresis, judged by the bottom-centre of the box (FR-2.4). */
    val inZone: Boolean,
    /** False while the track is coasting through a missed detection. */
    val seenThisFrame: Boolean,
    val lastSeenInZoneAt: MonotonicTime?,
    val stationaryFor: Duration,
)

/**
 * A small IoU tracker with a centroid fallback (FR-2.5). It gives actors stable IDs for as long as they are visible.
 * It never re-identifies anyone who left: no appearance features are stored (AGENTS.md hard rule 6).
 * Stateful: not thread-safe.
 */
class Tracker(
    private val roi: Polygon,
    private val config: DetectionConfig,
) {
    private val tracks = mutableListOf<State>()
    private var nextId = 1

    val hasTracks: Boolean get() = tracks.isNotEmpty()

    fun update(
        detections: List<Detection>,
        now: MonotonicTime,
    ): List<Track> {
        val assignment = associate(detections)
        val iterator = tracks.iterator()
        while (iterator.hasNext()) {
            val track = iterator.next()
            val match = assignment[track]
            if (match != null) {
                track.matched(match, now)
            } else {
                track.missed()
                if (track.misses >= config.actorExitFrames) iterator.remove()
            }
        }
        val claimed = assignment.values.toSet()
        detections.filter { detection -> claimed.none { it === detection } }.forEach { detection ->
            tracks += State(TrackId(nextId++), detection, now).also { it.matched(detection, now) }
        }
        return tracks.map { it.snapshot(now) }
    }

    /** Greedy best-first matching of existing tracks to this frame's detections. */
    private fun associate(detections: List<Detection>): Map<State, Detection> {
        val pairs =
            tracks.flatMap { track ->
                detections.indices.mapNotNull { index ->
                    matchScore(track, detections[index])?.let { Triple(track, index, it) }
                }
            }
        val result = mutableMapOf<State, Detection>()
        val taken = mutableSetOf<Int>()
        for ((track, index, _) in pairs.sortedByDescending { it.third }) {
            if (track !in result && index !in taken) {
                result[track] = detections[index]
                taken += index
            }
        }
        return result
    }

    /** Overlap wins over proximity; a matching category breaks ties. Null means "cannot be the same object". */
    private fun matchScore(
        track: State,
        detection: Detection,
    ): Float? {
        val sameCategory = if (detection.category == track.category) SAME_CATEGORY_BONUS else 0f
        val iou = track.box.iou(detection.box)
        if (iou >= config.trackMatchIou) return OVERLAP_BASE + iou + sameCategory

        val a = track.box.center
        val b = detection.box.center
        val size = max(max(track.box.width, track.box.height), max(detection.box.width, detection.box.height))
        val reach = size * config.trackMatchReach
        val distance = hypot(a.x - b.x, a.y - b.y)
        val smaller = minOf(track.box.area, detection.box.area)
        val larger = maxOf(track.box.area, detection.box.area)
        val similarSize = larger > 0f && smaller / larger >= MIN_AREA_RATIO
        return if (distance <= reach && similarSize && detection.category == track.category) {
            1f - distance / reach
        } else {
            null
        }
    }

    private inner class State(
        val id: TrackId,
        first: Detection,
        now: MonotonicTime,
    ) {
        var box = first.box
        var label = first.label
        var confidence = first.confidence
        var category = first.category
        var misses = 0
        private var inStreak = 0
        private var outStreak = 0
        private var inZone = false
        private var seen = true
        private var lastSeenInZoneAt: MonotonicTime? = null
        private var anchor = first.box
        private var anchorSince = now
        private val votes = ArrayDeque<ObjectCategory>()

        fun matched(
            detection: Detection,
            now: MonotonicTime,
        ) {
            box = detection.box
            label = detection.label
            confidence = detection.confidence
            misses = 0
            seen = true
            votes.addLast(detection.category)
            if (votes.size > CATEGORY_VOTES) votes.removeFirst()
            category = majorityCategory(detection.category)

            if (box.bottomCenter in roi) {
                inStreak++
                outStreak = 0
                if (inStreak >= config.actorEnterFrames) inZone = true
                if (inZone) lastSeenInZoneAt = now
            } else {
                leaving()
            }
            if (anchor.iou(box) < config.trackStationaryIou) {
                anchor = box
                anchorSince = now
            }
        }

        fun missed() {
            misses++
            seen = false
            leaving()
        }

        private fun leaving() {
            inStreak = 0
            outStreak++
            if (outStreak >= config.actorExitFrames) inZone = false
        }

        private fun majorityCategory(latest: ObjectCategory): ObjectCategory {
            val counts = votes.groupingBy { it }.eachCount()
            val best = counts.values.max()
            return if (counts[latest] == best) latest else counts.entries.first { it.value == best }.key
        }

        fun snapshot(now: MonotonicTime) =
            Track(id, category, label, box, confidence, inZone, seen, lastSeenInZoneAt, now - anchorSince)
    }

    private companion object {
        const val OVERLAP_BASE = 1f
        const val SAME_CATEGORY_BONUS = 0.1f
        const val MIN_AREA_RATIO = 0.33f
        const val CATEGORY_VOTES = 10
    }
}
