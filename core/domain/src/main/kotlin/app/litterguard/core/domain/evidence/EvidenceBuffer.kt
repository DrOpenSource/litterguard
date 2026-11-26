package app.litterguard.core.domain.evidence

import app.litterguard.core.domain.config.DetectionConfig
import app.litterguard.core.domain.tracking.Track
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.ImageBuffer
import app.litterguard.core.model.LumaPlane
import app.litterguard.core.model.MonotonicTime
import kotlin.math.hypot
import kotlin.math.max

/** A kept picture of an actor. [image] is a private copy. */
class EvidenceFrame(
    val image: ImageBuffer,
    val box: BoundingBox,
    val capturedAt: MonotonicTime,
    val score: Float,
)

/** How useful a picture of an actor is: big, sharp and central is best (FR-5.1). */
object EvidenceScorer {
    private const val MAX_SAMPLES_PER_AXIS = 96
    private const val CENTRALITY_FLOOR = 0.5f
    private const val HALF = 0.5f
    private const val LAPLACIAN_CENTRE_WEIGHT = 4
    private const val MIN_SIDE = 3

    fun score(
        luma: LumaPlane,
        box: BoundingBox,
    ): Float {
        val clamped = box.clampToUnit()
        val offCentre = hypot(clamped.center.x - HALF, clamped.center.y - HALF) / hypot(HALF, HALF)
        val centrality = CENTRALITY_FLOOR + (1f - CENTRALITY_FLOOR) * (1f - offCentre)
        return clamped.area * sharpness(luma, clamped) * centrality
    }

    /** Variance of the Laplacian inside [box]: higher means crisper. */
    fun sharpness(
        luma: LumaPlane,
        box: BoundingBox,
    ): Float {
        if (luma.width < MIN_SIDE || luma.height < MIN_SIDE) return 0f
        val x0 = (box.left * luma.width).toInt().coerceIn(1, luma.width - 2)
        val x1 = (box.right * luma.width).toInt().coerceIn(x0, luma.width - 2)
        val y0 = (box.top * luma.height).toInt().coerceIn(1, luma.height - 2)
        val y1 = (box.bottom * luma.height).toInt().coerceIn(y0, luma.height - 2)

        val stepX = max(1, (x1 - x0) / MAX_SAMPLES_PER_AXIS)
        val stepY = max(1, (y1 - y0) / MAX_SAMPLES_PER_AXIS)
        var count = 0
        var sum = 0.0
        var sumOfSquares = 0.0
        for (y in y0..y1 step stepY) {
            for (x in x0..x1 step stepX) {
                val neighbours = luma[x - 1, y] + luma[x + 1, y] + luma[x, y - 1] + luma[x, y + 1]
                val laplacian = LAPLACIAN_CENTRE_WEIGHT * luma[x, y] - neighbours
                count++
                sum += laplacian
                sumOfSquares += laplacian.toDouble() * laplacian
            }
        }
        val mean = sum / count
        return max(0.0, sumOfSquares / count - mean * mean).toFloat()
    }
}

/**
 * Keeps the best few pictures taken **while** actors are in the zone, because by the time dumping is confirmed they
 * have left (ADR-0002). Pictures are spread out in time so they don't all show the same moment.
 * Stateful: not thread-safe.
 */
class EvidenceBuffer(
    private val config: DetectionConfig,
) {
    private val kept = mutableListOf<EvidenceFrame>()

    /**
     * Offers a frame with its tracks. Only people and vehicles that are in the zone, visible and not long parked are
     * worth picturing; the most useful of them decides this frame's score.
     */
    fun offerFrame(
        image: ImageBuffer,
        luma: LumaPlane,
        tracks: List<Track>,
        now: MonotonicTime,
    ) {
        val best =
            tracks
                .filter { it.category.isActor && it.inZone && it.seenThisFrame }
                .filter { it.stationaryFor < config.parkedActorTimeout }
                .maxByOrNull { EvidenceScorer.score(luma, it.box) }
        if (best != null) offer(image, luma, best.box, now)
    }

    /** Offers a frame; [image] is copied only if it is worth keeping. */
    fun offer(
        image: ImageBuffer,
        luma: LumaPlane,
        box: BoundingBox,
        now: MonotonicTime,
    ) {
        val score = EvidenceScorer.score(luma, box)
        val sameMoment = kept.firstOrNull { (now - it.capturedAt).absoluteValue < config.evidenceMinSpacing }
        val isFull = kept.size >= config.evidenceFramesPerTrack
        val rival = sameMoment ?: if (isFull) kept.minBy { it.score } else null
        if (rival != null) {
            if (score <= rival.score) return
            kept.remove(rival)
        }
        kept += EvidenceFrame(image.copy(), box, now, score)
    }

    /** Best first. */
    fun best(): List<EvidenceFrame> = kept.sortedByDescending { it.score }

    fun clear() = kept.clear()
}
