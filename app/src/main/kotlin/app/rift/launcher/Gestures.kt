package app.rift.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/** What a gesture can do. The first item of each pair is what is stored. */
val GestureActions = listOf(
    "none" to "Nothing",
    "shade" to "Open the shade",
    "search" to "Search apps (drawer + keyboard)",
    "apps" to "Open the app drawer",
    "home" to "Go to Home grid",
    "brief" to "Go to Brief",
    "config" to "Open Config",
    "sysshade" to "System notification shade (Shizuku)",
    "sysqs" to "System quick settings (Shizuku)",
)

/** The gestures the user can remap: stored key, label, default action. */
val GestureSlots = listOf(
    Triple(Keys.G_DOWN, "Swipe down", "shade"),
    Triple(Keys.G_UP, "Swipe up", "apps"),
    Triple(Keys.G_LONG, "Long-press empty space", "config"),
    Triple(Keys.G_DOUBLE, "Double-tap empty space", "none"),
)

/**
 * Fires when a vertical drag has nowhere left to scroll, so a swipe on a scrolling page still
 * scrolls first and only becomes a gesture once the content hits its end.
 */
class EdgeSwipe(
    private val thresholdPx: Float,
    private val onDown: (() -> Unit)?,
    private val onUp: (() -> Unit)?,
) : NestedScrollConnection {
    private var acc = 0f
    private var fired = false

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.Drag) return Offset.Zero
        if (consumed.y != 0f) {
            acc = 0f
            return Offset.Zero
        }
        acc += available.y
        if (!fired) {
            if (acc > thresholdPx && onDown != null) {
                fired = true
                onDown.invoke()
            } else if (acc < -thresholdPx && onUp != null) {
                fired = true
                onUp.invoke()
            }
        }
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        acc = 0f
        fired = false
        return Velocity.Zero
    }
}

/** Closes something when it is dragged far enough in one direction, wherever the drag starts on it. */
fun Modifier.swipeDismiss(up: Boolean, thresholdPx: Float, onClose: () -> Unit): Modifier =
    pointerInput(up, thresholdPx) {
        var total = 0f
        var fired = false
        detectVerticalDragGestures(
            onDragStart = {
                total = 0f
                fired = false
            },
            onDragEnd = {},
            onDragCancel = {},
        ) { _, dy ->
            total += dy
            if (!fired && ((up && total < -thresholdPx) || (!up && total > thresholdPx))) {
                fired = true
                onClose()
            }
        }
    }

/**
 * Swipes that start in the top or bottom strip of the screen always count, even over a page that
 * scrolls. Down from the top strip runs [onTopDown]; up from the bottom strip runs [onBottomUp].
 * It only watches the touches; the page underneath still scrolls normally.
 */
fun Modifier.edgeZones(
    topZonePx: Float,
    bottomZonePx: Float,
    thresholdPx: Float,
    onTopDown: () -> Unit,
    onBottomUp: () -> Unit,
): Modifier = pointerInput(topZonePx, bottomZonePx, thresholdPx) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val zone = when {
            down.position.y <= topZonePx -> 1
            down.position.y >= size.height - bottomZonePx -> 2
            else -> 0
        }
        if (zone == 0) return@awaitEachGesture
        var dx = 0f
        var dy = 0f
        var fired = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val delta = change.positionChange()
            dx += delta.x
            dy += delta.y
            if (!fired && Math.abs(dy) > thresholdPx && Math.abs(dy) > 2f * Math.abs(dx)) {
                if (zone == 1 && dy > 0f) {
                    fired = true
                    onTopDown()
                } else if (zone == 2 && dy < 0f) {
                    fired = true
                    onBottomUp()
                }
            }
        }
    }
}
