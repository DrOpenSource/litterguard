package app.litterguard.core.domain.geometry

import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.NormalizedPoint

/**
 * Maps a normalised point in the **sensor** frame to the **upright** frame after rotating the image clockwise by
 * [rotation]. The domain only ever works in upright coordinates; adapters call this once per frame.
 */
fun NormalizedPoint.rotated(rotation: FrameRotation): NormalizedPoint =
    when (rotation) {
        FrameRotation.NONE -> this
        FrameRotation.CW_90 -> NormalizedPoint(1f - y, x)
        FrameRotation.CW_180 -> NormalizedPoint(1f - x, 1f - y)
        FrameRotation.CW_270 -> NormalizedPoint(y, 1f - x)
    }

/** Box version of [NormalizedPoint.rotated]; the result is re-normalised so left <= right and top <= bottom. */
fun BoundingBox.rotated(rotation: FrameRotation): BoundingBox {
    val a = NormalizedPoint(left, top).rotated(rotation)
    val b = NormalizedPoint(right, bottom).rotated(rotation)
    return BoundingBox(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))
}

/**
 * Maps a box expressed relative to a crop ([crop], itself in frame coordinates) back to full-frame coordinates.
 * Used after ROI-crop inference: the detector sees only the crop, and the domain needs frame coordinates.
 */
fun BoundingBox.fromCrop(crop: BoundingBox): BoundingBox =
    BoundingBox(
        left = crop.left + left * crop.width,
        top = crop.top + top * crop.height,
        right = crop.left + right * crop.width,
        bottom = crop.top + bottom * crop.height,
    )
