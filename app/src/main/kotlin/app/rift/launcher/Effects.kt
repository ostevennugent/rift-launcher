package app.rift.launcher

import android.app.ActivityOptions
import android.os.Bundle
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/** Where the last tapped icon was on screen, so the open animation can grow out of it. */
object LaunchOrigin {
    var rect: Rect? = null
}

/** Android's own window animation for the app that opens. Style: 0 system, 1 zoom, 2 reveal, 3 fade. */
fun launchOptions(view: View, style: Int, origin: Rect?): Bundle? {
    if (style == 0) return null
    val r = origin ?: Rect(view.width / 2f - 1f, view.height / 2f - 1f, view.width / 2f + 1f, view.height / 2f + 1f)
    return try {
        when (style) {
            1 -> ActivityOptions.makeScaleUpAnimation(
                view, r.left.toInt(), r.top.toInt(), r.width.toInt().coerceAtLeast(1), r.height.toInt().coerceAtLeast(1),
            )
            2 -> ActivityOptions.makeClipRevealAnimation(
                view, r.left.toInt(), r.top.toInt(), r.width.toInt().coerceAtLeast(1), r.height.toInt().coerceAtLeast(1),
            )
            3 -> ActivityOptions.makeCustomAnimation(view.context, R.anim.rift_in, R.anim.rift_out)
            else -> null
        }?.toBundle()
    } catch (_: Exception) {
        null
    }
}

/** One neon flash that grows from the tapped icon to the whole screen. */
class LaunchFlash(val rect: Rect, val id: Int)

@Composable
fun LaunchFlashOverlay(flash: LaunchFlash, accent: Color, onDone: () -> Unit) {
    val progress = remember(flash.id) { Animatable(0f) }
    LaunchedEffect(flash.id) {
        progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        onDone()
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        val full = Rect(0f, 0f, size.width, size.height)
        val left = lerp(flash.rect.left, full.left, p)
        val top = lerp(flash.rect.top, full.top, p)
        val right = lerp(flash.rect.right, full.right, p)
        val bottom = lerp(flash.rect.bottom, full.bottom, p)
        val fade = 1f - p * 0.6f
        // The screen darkens and a neon frame blooms out of the icon.
        drawRect(color = Color.Black.copy(alpha = 0.55f * p))
        drawRect(
            color = accent.copy(alpha = 0.22f * fade),
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
        )
        drawRect(
            color = accent.copy(alpha = fade),
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            style = Stroke(width = 3.dp.toPx()),
        )
        // Three scan lines sweep down at different speeds.
        for ((i, speed) in listOf(1f, 0.72f, 0.45f).withIndex()) {
            val y = lerp(flash.rect.top, size.height, (p * speed).coerceAtMost(1f))
            drawLine(
                color = accent.copy(alpha = (0.85f - i * 0.2f) * fade),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = (2.5f - i * 0.5f).dp.toPx(),
            )
        }
    }
}
