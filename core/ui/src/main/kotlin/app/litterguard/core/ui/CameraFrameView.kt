package app.litterguard.core.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.litterguard.core.model.BoundingBox
import app.litterguard.core.model.ImageBuffer
import app.litterguard.core.model.NormalizedPoint

/**
 * Shows an analysis frame at its true aspect ratio with marks drawn on top. Because the picture **is** the frame the
 * detector sees, anything placed on it is already in the pipeline's normalised coordinates (FR-1.3).
 *
 * @param gestureModifier applied to a layer that exactly covers the picture.
 * @param overlay draws on the picture; use [toOffset] to place normalised coordinates.
 */
@Composable
fun CameraFrameView(
    frame: ImageBuffer,
    modifier: Modifier = Modifier,
    gestureModifier: Modifier = Modifier,
    overlay: DrawScope.() -> Unit = {},
) {
    val picture =
        remember(frame) {
            Bitmap
                .createBitmap(frame.pixels, 0, frame.width, frame.width, frame.height, Bitmap.Config.ARGB_8888)
                .asImageBitmap()
        }
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.aspectRatio(frame.width.toFloat() / frame.height)) {
            Canvas(Modifier.fillMaxSize().then(gestureModifier)) {
                drawImage(
                    image = picture,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(picture.width, picture.height),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                )
                overlay()
            }
        }
    }
}

fun DrawScope.toOffset(point: NormalizedPoint) = Offset(point.x * size.width, point.y * size.height)

/** The dump zone outline; closed and lightly filled once it has at least three corners. */
fun DrawScope.drawZone(
    points: List<NormalizedPoint>,
    colour: Color = OverlayColours.zone,
    showCorners: Boolean = false,
) {
    if (points.isEmpty()) return
    val path = Path()
    points.forEachIndexed { index, point ->
        val at = toOffset(point)
        if (index == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
    }
    if (points.size > 2) {
        path.close()
        drawPath(path, colour.copy(alpha = ZONE_FILL))
    }
    drawPath(path, colour, style = Stroke(width = 2.dp.toPx()))
    if (showCorners) points.forEach { drawCircle(colour, radius = 7.dp.toPx(), center = toOffset(it)) }
}

/** A labelled box over something the app has seen. */
fun DrawScope.drawLabelledBox(
    box: BoundingBox,
    label: String,
    colour: Color,
    text: TextMeasurer,
) {
    val topLeft = Offset(box.left * size.width, box.top * size.height)
    drawRect(
        color = colour,
        topLeft = topLeft,
        size = Size(box.width * size.width, box.height * size.height),
        style = Stroke(width = 3.dp.toPx()),
    )
    val measured = text.measure(label, TextStyle(color = colour, fontSize = 13.sp))
    val labelTop = (topLeft.y - measured.size.height).coerceAtLeast(0f)
    val labelLeft = topLeft.x.coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f))
    drawRect(
        color = OverlayColours.labelBackground,
        topLeft = Offset(labelLeft, labelTop),
        size = Size(measured.size.width.toFloat(), measured.size.height.toFloat()),
    )
    drawText(measured, topLeft = Offset(labelLeft, labelTop))
}

private const val ZONE_FILL = 0.12f
