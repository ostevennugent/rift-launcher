package app.rift.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Fixed cyberpunk palette, ported from the original RIFT web prototype. */
object Cyber {
    val stage = Color(0xFF05070A)
    val bg = Color(0xFF080B10)
    val surface = Color(0xFF10161E)
    val surface2 = Color(0xFF18212C)
    val fg = Color(0xFFE7F2F5)
    val muted = Color(0xFF93A4B5)
    val border = Color(0xFF263140)
}

/** A neon scheme: [primary] is the main glow, [hot] is the second neon used for alerts. */
data class Accent(val name: String, val primary: Color, val hot: Color)

val Accents = listOf(
    Accent("Teal", Color(0xFF2EE6C7), Color(0xFFFF4D7A)),
    Accent("Signal", Color(0xFFFF4D7A), Color(0xFF2EE6C7)),
    Accent("Amber", Color(0xFFE6B15A), Color(0xFFFF6A3D)),
    Accent("Acid", Color(0xFFC6E85A), Color(0xFF2EE6C7)),
)

val Oxanium = FontFamily(
    Font(R.font.oxanium_regular, FontWeight.Normal),
    Font(R.font.oxanium_medium, FontWeight.Medium),
    Font(R.font.oxanium_semibold, FontWeight.SemiBold),
)

val PlexMono = FontFamily(
    Font(R.font.plexmono_regular, FontWeight.Normal),
    Font(R.font.plexmono_medium, FontWeight.Medium),
)

private fun Typography.withFont(family: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

@Composable
fun RiftTheme(accent: Accent, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent.primary,
            onPrimary = Cyber.stage,
            secondary = accent.hot,
            onSecondary = Cyber.stage,
            background = Cyber.stage,
            onBackground = Cyber.fg,
            surface = Cyber.surface,
            onSurface = Cyber.fg,
            surfaceVariant = Cyber.surface2,
            onSurfaceVariant = Cyber.muted,
            outline = Cyber.border,
        ),
        typography = Typography().withFont(Oxanium),
    ) {
        CompositionLocalProvider(LocalContentColor provides Cyber.fg) {
            content()
        }
    }
}

/**
 * A flat dark "plate" with a sharp 1dp border and a small neon bracket in the
 * top-left corner. Set [highlight] to light the whole border in the accent color.
 */
@Composable
fun Modifier.plate(highlight: Boolean = false): Modifier {
    val primary = MaterialTheme.colorScheme.primary
    val edge = if (highlight) primary else Cyber.border
    return this
        .background(Cyber.surface)
        .border(1.dp, edge)
        .drawWithContent {
            drawContent()
            val length = 8.dp.toPx()
            val thickness = 2.dp.toPx()
            drawLine(primary, Offset(0f, 0f), Offset(length, 0f), strokeWidth = thickness)
            drawLine(primary, Offset(0f, 0f), Offset(0f, length), strokeWidth = thickness)
        }
}
