package app.litterguard.core.domain.ports

import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.Detection
import app.litterguard.core.model.ImageBuffer

/** A loaded detection model. Create one per monitoring session and close it at the end. */
interface ObjectDetector : AutoCloseable {
    /** Model name, version and the accelerator actually in use, for logs and incident records. */
    val description: String

    /**
     * Finds people, vehicles and animals inside [region] of [image] (FR-2.3). Boxes are returned in normalised
     * **full-frame** coordinates.
     */
    suspend fun detect(
        image: ImageBuffer,
        region: BoundingBox,
    ): List<Detection>
}

fun interface ObjectDetectorFactory {
    suspend fun open(): ObjectDetector
}
