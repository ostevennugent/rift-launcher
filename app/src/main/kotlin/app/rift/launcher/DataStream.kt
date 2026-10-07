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
fun DataStream(modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontFamily = PlexMono, fontSize = 10.sp, color = Cyber.fg)
    val layouts = remember(measurer) {
        GLYPHS.map { measurer.measure(AnnotatedString(it.toString()), style) }
    }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(90)
            tick++
        }
    }
    val accent = MaterialTheme.colorScheme.primary

    Canvas(modifier.fillMaxSize()) {
        val t = tick
        val rowH = 13.dp.toPx()
        val rows = (size.height / rowH).toInt()
        if (rows <= 0) return@Canvas
        val trail = 12
        val glyphW = layouts.first().size.width.toFloat()
        val speeds = floatArrayOf(0.45f, 0.8f, 1.15f)
        val offsets = intArrayOf(3, 19, 41)
        for (side in 0..1) {
            val x = if (side == 0) 1.dp.toPx() else size.width - glyphW - 1.dp.toPx()
            for (s in speeds.indices) {
                val span = rows + trail
                val head = ((t * speeds[s] + offsets[s] * (side + 1)) % span).toInt()
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
                        topLeft = Offset(x, row * rowH),
                        alpha = if (isHead) 0.85f else fade * fade * 0.5f,
                    )
                }
            }
        }
    }
}
