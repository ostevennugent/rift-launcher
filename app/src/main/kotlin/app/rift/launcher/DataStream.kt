package app.rift.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val GLYPHS = "0123456789ABCDEF<>/\\|=+*ｱｲｳｴｵｶｷｸｹｺ"

/**
 * Streams of hex and katakana running down the far left and right edges of the screen,
 * like a terminal feed. Purely decorative; it never takes touches and draws behind the content.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun DataStream(settings: SettingsState, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontFamily = PlexMono, fontSize = 10.sp, color = Cyber.fg)
    val layouts = remember(measurer) {
        GLYPHS.map { measurer.measure(AnnotatedString(it.toString()), style) }
    }
    var tick by remember { mutableIntStateOf(0) }
    val frameMs = longArrayOf(130L, 90L, 55L)[settings.int(Keys.STREAM_SPEED, 1).coerceIn(0, 2)]
    LaunchedEffect(frameMs) {
        while (true) {
            delay(frameMs)
            tick++
        }
    }
    val accent = MaterialTheme.colorScheme.primary
    val trail = intArrayOf(16, 32, 52)[settings.int(Keys.STREAM_TRAIL, 1).coerceIn(0, 2)]
    val streams = intArrayOf(4, 8, 12)[settings.int(Keys.STREAM_DENSITY, 1).coerceIn(0, 2)]
    val edge = settings.int(Keys.STREAM_EDGE, 0).coerceIn(0, 2)

    Canvas(modifier.fillMaxSize()) {
        val t = tick
        val rowH = 12.dp.toPx()
        val rows = (size.height / rowH).toInt()
        if (rows <= 0) return@Canvas
        val glyphW = layouts.first().size.width.toFloat()
        val speeds = floatArrayOf(0.4f, 0.7f, 1.0f, 1.4f, 0.55f, 0.9f, 1.2f, 0.8f)
        val sides = when (edge) {
            1 -> intArrayOf(0)
            2 -> intArrayOf(1)
            else -> intArrayOf(0, 1)
        }
        for (side in sides) {
            for (s in 0 until streams) {
                val col = s % 2
                val inner = if (side == 0) 1.dp.toPx() + col * (glyphW + 1.dp.toPx())
                else size.width - glyphW - 1.dp.toPx() - col * (glyphW + 1.dp.toPx())
                val span = rows + trail
                val offset = Math.floorMod(s * 37 + side * 53 + 11, span)
                val head = ((t * speeds[s % speeds.size] + offset) % span).toInt()
                for (k in 0 until trail) {
                    val row = head - k
                    if (row < 0 || row >= rows) continue
                    val seed = row * 31 + side * 7 + s * 13 + if (k == 0) t else t / 6
                    val glyph = layouts[Math.floorMod(seed * 2654435761L.toInt(), layouts.size)]
                    val fade = 1f - k / trail.toFloat()
                    val isHead = k == 0
                    drawText(
                        textLayoutResult = glyph,
                        color = if (isHead) Cyber.fg else accent,
                        topLeft = Offset(inner, row * rowH),
                        alpha = if (isHead) 1f else (0.15f + 0.65f * fade * fade),
                    )
                }
            }
        }
    }
}
