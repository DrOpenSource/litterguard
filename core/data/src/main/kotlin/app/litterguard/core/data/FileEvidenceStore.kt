package app.litterguard.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import app.litterguard.core.domain.ports.EvidenceStore
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.EvidenceFile
import app.litterguard.core.model.EvidenceKind
import app.litterguard.core.model.ImageBuffer
import app.litterguard.core.model.IncidentId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Evidence JPEGs under `filesDir/evidence/<incidentId>/`: app-private, no location tags (PRD §8–9). */
@Singleton
internal class FileEvidenceStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : EvidenceStore {
        private val root: File get() = File(context.filesDir, FOLDER)

        override suspend fun save(
            id: IncidentId,
            kind: EvidenceKind,
            index: Int,
            image: ImageBuffer,
            capturedAt: Instant,
            highlight: BoundingBox?,
        ): EvidenceFile =
            withContext(Dispatchers.IO) {
                val name = "${kind.name.lowercase(Locale.ROOT)}_$index.jpg"
                val relativePath = "${id.value}/$name"
                val file = File(root, relativePath)
                val folder = requireNotNull(file.parentFile)
                if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Could not create evidence folder")

                val bitmap = toBitmap(image, highlight)
                try {
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                } finally {
                    bitmap.recycle()
                }
                EvidenceFile(kind, relativePath, image.width, image.height, capturedAt)
            }

        override fun absolutePath(file: EvidenceFile): String = File(root, file.relativePath).absolutePath

        private fun toBitmap(
            image: ImageBuffer,
            highlight: BoundingBox?,
        ): Bitmap {
            val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(image.pixels, 0, image.width, 0, 0, image.width, image.height)
            if (highlight != null) {
                val paint =
                    Paint().apply {
                        color = Color.RED
                        style = Paint.Style.STROKE
                        strokeWidth = image.width * HIGHLIGHT_STROKE
                    }
                // Grow the outline a little so it doesn't cover the object itself.
                val box = highlight.expandedBy(HIGHLIGHT_MARGIN).clampToUnit()
                Canvas(bitmap).drawRect(
                    box.left * image.width,
                    box.top * image.height,
                    box.right * image.width,
                    box.bottom * image.height,
                    paint,
                )
            }
            return bitmap
        }

        private companion object {
            const val FOLDER = "evidence"
            const val JPEG_QUALITY = 85
            const val HIGHLIGHT_STROKE = 0.004f
            const val HIGHLIGHT_MARGIN = 0.15f
        }
    }
