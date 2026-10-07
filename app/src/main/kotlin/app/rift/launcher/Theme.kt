package app.rift.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color

val Accents = listOf(
    Color(0xFF4DD0E1), // cyan
    Color(0xFFB388FF), // violet
    Color(0xFFFF8A80), // rose
    Color(0xFFB2FF59), // lime
    Color(0xFFFFD180), // amber
)

val SheetColor = Color(0xFF14181F)

@Composable
fun RiftTheme(accent: Color, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            onPrimary = Color.Black,
            surface = SheetColor,
            onSurface = Color.White,
            background = Color.Transparent,
            onBackground = Color.White,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            content()
        }
    }
}
