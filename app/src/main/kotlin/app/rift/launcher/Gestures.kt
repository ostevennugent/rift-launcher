package app.rift.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/** What a gesture can do. The first item of each pair is what is stored. */
val GestureActions = listOf(
    "none" to "Nothing",
    "shade" to "Open the shade",
    "search" to "Search apps",
    "apps" to "Go to Apps",
    "deck" to "Go to Deck",
    "brief" to "Go to Brief",
    "config" to "Open Config",
)

/** The gestures the user can remap: stored key, label, default action. */
val GestureSlots = listOf(
    Triple(Keys.G_DOWN, "Swipe down", "shade"),
    Triple(Keys.G_UP, "Swipe up", "search"),
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
