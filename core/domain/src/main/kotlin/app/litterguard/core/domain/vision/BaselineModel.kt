package app.litterguard.core.domain.vision

import app.litterguard.core.domain.config.DetectionConfig
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.LumaPlane
import app.litterguard.core.model.MonotonicTime
import app.litterguard.core.model.Polygon
import kotlin.math.abs

/** A region of the dump zone that differs from the "empty" baseline: a candidate abandoned object. */
data class Blob(
    val box: BoundingBox,
    /** Area as a share of the whole frame. */
    val frameFraction: Float,
)

/** Result of comparing the current frame with the baseline. */
sealed interface BaselineDiff {
    /** No baseline has been captured yet, so nothing can be said about this frame. */
    data object Unavailable : BaselineDiff

    /**
     * @property changedFraction share of the visible zone that differs (used by the illumination abort).
     * @property blobs candidate objects, largest first.
     * @property occluders boxes of live people, vehicles and animals that were hiding part of the picture.
     * @property hiddenFraction share of the zone hidden by [occluders].
     */
    data class Compared(
        val changedFraction: Float,
        val blobs: List<Blob>,
        val occluders: List<BoundingBox> = emptyList(),
        val hiddenFraction: Float = 0f,
    ) : BaselineDiff
}

/**
 * Class-agnostic abandoned-object detection (FR-2.6, PRD §6.2, ADR-0002): remembers what the empty zone looks like
 * and reports what is different. It does not care what the new thing is. Stateful: not thread-safe.
 */
class BaselineModel(
    roi: Polygon,
    private val config: DetectionConfig,
) {
    private val raster = RoiRaster(roi, GRID_WIDTH, GRID_HEIGHT)
    private val history = ArrayDeque<IntArray>()
    private var baseline: IntArray? = null
    private var lastRefresh: MonotonicTime? = null

    val hasBaseline: Boolean get() = baseline != null

    /** While frozen (an actor visit is in progress) quiet frames are ignored: the baseline is the "before" picture. */
    var isFrozen: Boolean = false
        private set

    /**
     * Offers a frame of the **quiet, empty** zone. At most one frame per [DetectionConfig.baselineRefresh] is kept,
     * and the baseline is the per-cell median of the last few, which absorbs slow lighting drift and one-off glitches.
     */
    fun offerQuiet(
        luma: LumaPlane,
        now: MonotonicTime,
    ) {
        if (isFrozen) return
        val last = lastRefresh
        if (last != null && now - last < config.baselineRefresh) return
        history.addLast(raster.sample(luma))
        while (history.size > config.baselineHistory) history.removeFirst()
        lastRefresh = now
        baseline = median(history)
    }

    fun freeze() {
        isFrozen = true
    }

    fun unfreeze() {
        isFrozen = false
    }

    /** Throws the old baseline away and takes [luma] as the new one (after an abort, or to absorb a new object). */
    fun rebaseline(
        luma: LumaPlane,
        now: MonotonicTime,
    ) {
        val sample = raster.sample(luma)
        history.clear()
        history.addLast(sample)
        baseline = sample
        lastRefresh = now
        isFrozen = false
    }

    /**
     * Compares [luma] with the baseline. Cells under [occupied] boxes (live people, vehicles, animals) don't count
     * towards the changed fraction, and blobs mostly hidden by them are dropped.
     */
    fun compare(
        luma: LumaPlane,
        occupied: List<BoundingBox> = emptyList(),
    ): BaselineDiff {
        val before = baseline ?: return BaselineDiff.Unavailable
        val current = raster.sample(luma)
        val hidden = hiddenCells(occupied)
        val offset = brightnessOffset(before, current, hidden)
        val adjusted = IntArray(current.size) { current[it] - offset }

        val edgesBefore = GridOps.gradient(before, raster.width, raster.height)
        val edgesNow = GridOps.gradient(adjusted, raster.width, raster.height)
        val edgeChanged =
            BooleanArray(current.size) { abs(edgesNow[it] - edgesBefore[it]) > config.objectEdgeThreshold }

        val raw =
            BooleanArray(current.size) { i ->
                if (!raster.mask[i]) {
                    false
                } else {
                    val delta = abs(adjusted[i] - before[i])
                    delta > config.objectLumaThreshold ||
                        (edgeChanged[i] && delta > config.objectLumaThreshold / 2)
                }
            }
        val changed = GridOps.openThenClose(raw, raster.width, raster.height)

        var visible = 0
        var visibleChanged = 0
        for (i in changed.indices) {
            if (raster.mask[i] && !hidden[i]) {
                visible++
                if (changed[i]) visibleChanged++
            }
        }
        val changedFraction = if (visible == 0) 0f else visibleChanged.toFloat() / visible

        val blobs =
            GridOps
                .components(changed, raster.width, raster.height)
                .filter { isCandidate(it, edgeChanged, hidden) }
                .map { component ->
                    Blob(
                        box = raster.cellRangeBox(component.minX, component.minY, component.maxX, component.maxY),
                        frameFraction = component.cells.size * raster.cellFrameFraction,
                    )
                }.sortedByDescending { it.frameFraction }
        val hiddenCount = changed.indices.count { raster.mask[it] && hidden[it] }
        val hiddenFraction = if (raster.maskCount == 0) 0f else hiddenCount.toFloat() / raster.maskCount
        return BaselineDiff.Compared(changedFraction, blobs, occupied, hiddenFraction)
    }

    private fun isCandidate(
        component: GridOps.Component,
        edgeChanged: BooleanArray,
        hidden: BooleanArray,
    ): Boolean {
        val size = component.cells.size
        val bigEnough = size * raster.cellFrameFraction >= config.objectMinFrameFraction
        val mostlyVisible = component.cells.count { hidden[it] } <= size * config.actorOverlapDiscardFraction
        // A real object brings new outlines or hides old ones. A soft shadow or patch of light does neither.
        val hasOutlines = component.cells.count { edgeChanged[it] } >= size * config.objectMinEdgeSupport
        return bigEnough && mostlyVisible && hasOutlines
    }

    private fun hiddenCells(occupied: List<BoundingBox>): BooleanArray =
        BooleanArray(raster.width * raster.height) { i ->
            val x = raster.cellCenterX(i % raster.width)
            val y = raster.cellCenterY(i / raster.width)
            occupied.any { it.covers(x, y) }
        }

    /** Median brightness shift over the visible zone, so a cloud or auto-exposure step doesn't look like change. */
    private fun brightnessOffset(
        before: IntArray,
        current: IntArray,
        hidden: BooleanArray,
    ): Int {
        val histogram = IntArray(DIFF_BINS)
        var total = 0
        for (i in current.indices) {
            if (raster.mask[i] && !hidden[i]) {
                histogram[current[i] - before[i] + DetectionConfig.MAX_LUMA]++
                total++
            }
        }
        var seen = 0
        for (bin in histogram.indices) {
            seen += histogram[bin]
            if (total > 0 && seen * 2 >= total) return bin - DetectionConfig.MAX_LUMA
        }
        return 0
    }

    private fun median(samples: Collection<IntArray>): IntArray {
        val frames = samples.toList()
        val column = IntArray(frames.size)
        return IntArray(frames.first().size) { cell ->
            for (f in frames.indices) column[f] = frames[f][cell]
            column.sort()
            column[column.size / 2]
        }
    }

    private companion object {
        const val GRID_WIDTH = 320
        const val GRID_HEIGHT = 240
        const val DIFF_BINS = 2 * DetectionConfig.MAX_LUMA + 1
    }
}
