package app.litterguard.core.ml

import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.Detection
import app.litterguard.core.model.ObjectCategory

/**
 * Turns the raw YOLO head `[1, 4 + classes, anchors]` into detections. Channels come first: value `c` of anchor `a`
 * is at `c * anchors + a`. Channels 0–3 are the box centre and size normalised to the model input; the rest are
 * class scores that already have the sigmoid applied.
 */
internal class YoloDecoder(
    private val anchors: Int,
    private val confidenceFloor: Float,
    private val nmsIou: Float,
    private val classNames: List<String> = CocoClasses.names,
) {
    /** Only people, vehicles and animals matter; other classes are skipped. */
    private val relevant: List<Pair<Int, ObjectCategory>> =
        classNames.indices
            .map { it to CocoClasses.category(classNames[it]) }
            .filter { it.second != ObjectCategory.OTHER }

    fun decode(
        output: FloatArray,
        letterbox: Letterbox,
    ): List<Detection> {
        require(output.size == (BOX_CHANNELS + classNames.size) * anchors) {
            "Unexpected output size ${output.size} for ${classNames.size} classes and $anchors anchors"
        }
        val candidates = mutableListOf<Detection>()
        for (anchor in 0 until anchors) {
            var bestScore = confidenceFloor
            var bestClass = -1
            var bestCategory = ObjectCategory.OTHER
            for ((index, category) in relevant) {
                val score = output[(BOX_CHANNELS + index) * anchors + anchor]
                if (score >= bestScore) {
                    bestScore = score
                    bestClass = index
                    bestCategory = category
                }
            }
            if (bestClass >= 0) {
                candidates +=
                    Detection(
                        box = boxAt(output, anchor, letterbox),
                        category = bestCategory,
                        label = classNames[bestClass],
                        confidence = bestScore.coerceIn(0f, 1f),
                    )
            }
        }
        return nonMaximumSuppression(candidates)
    }

    private fun boxAt(
        output: FloatArray,
        anchor: Int,
        letterbox: Letterbox,
    ): BoundingBox {
        val cx = output[anchor]
        val cy = output[anchors + anchor]
        val halfWidth = output[WIDTH_CHANNEL * anchors + anchor] / 2f
        val halfHeight = output[HEIGHT_CHANNEL * anchors + anchor] / 2f
        return letterbox.toFrame(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight)
    }

    /** Suppression works per category, so one vehicle seen as both "car" and "truck" is counted once. */
    private fun nonMaximumSuppression(candidates: List<Detection>): List<Detection> {
        val kept = mutableListOf<Detection>()
        for (candidate in candidates.sortedByDescending { it.confidence }) {
            val duplicate = kept.any { it.category == candidate.category && it.box.iou(candidate.box) > nmsIou }
            if (!duplicate && candidate.box.area > 0f) kept += candidate
        }
        return kept
    }

    private companion object {
        const val BOX_CHANNELS = 4
        const val WIDTH_CHANNEL = 2
        const val HEIGHT_CHANNEL = 3
    }
}
