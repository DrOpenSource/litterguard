package app.litterguard.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colours =
    darkColorScheme(
        primary = Color(0xFF7BD88F),
        onPrimary = Color(0xFF07350F),
        secondary = Color(0xFF8AB4F8),
        onSecondary = Color(0xFF0B2545),
        background = Color(0xFF101411),
        onBackground = Color(0xFFE6EBE6),
        surface = Color(0xFF101411),
        onSurface = Color(0xFFE6EBE6),
        surfaceVariant = Color(0xFF1D241F),
        onSurfaceVariant = Color(0xFFC2CBC3),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
    )

/** Colours for marks drawn over camera pictures. Each kind of mark also has a text label or its own shape. */
object OverlayColours {
    val zone = Color(0xFFFFFFFF)
    val person = Color(0xFF3DDC84)
    val vehicle = Color(0xFF4DA3FF)
    val animal = Color(0xFFFFD54F)
    val newObject = Color(0xFFFF5252)
    val labelBackground = Color(0xCC000000)
}

@Composable
fun LitterGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colours, content = content)
}
