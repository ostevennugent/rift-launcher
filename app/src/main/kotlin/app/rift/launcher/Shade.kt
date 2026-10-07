package app.rift.launcher

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Overlay over the home screen: quick toggles, media and notifications. Swipe up or tap outside to close. */
@Composable
fun Shade(open: Boolean, info: InfoController, onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler(enabled = open) { onClose() }
    val threshold = with(LocalDensity.current) { 60.dp.toPx() }
    val edge = remember(threshold) { EdgeSwipe(threshold, onDown = null, onUp = onClose) }

    AnimatedVisibility(
        visible = open,
        enter = fadeIn() + slideInVertically { -it / 6 },
        exit = fadeOut() + slideOutVertically { -it / 6 },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xF005070A))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClose() }
                .systemBarsPadding()
                .pointerInput(Unit) { detectTapGestures { } }
                .nestedScroll(edge)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Kicker("Shade", modifier = Modifier.weight(1f))
                Text(
                    text = "CLOSE ▲",
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { onClose() }
                        .padding(8.dp),
                )
            }
            Plate {
                Kicker("Quick toggles")
                Spacer(Modifier.height(10.dp))
                QuickToggles()
            }
            MediaModule(info)
            if (!info.notificationAccess) {
                NoticePlate(
                    "Notifications",
                    "Show notifications here",
                    "Tap to grant notification access.",
                ) { safeStart(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            } else {
                val items = NotificationHub.items
                Plate {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Kicker("Notifications · ${items.size}", modifier = Modifier.weight(1f))
                        if (items.isNotEmpty()) {
                            Text(
                                text = "CLEAR ALL",
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clickable { NotificationHub.clearAll() }
                                    .padding(8.dp),
                            )
                        }
                    }
                    if (items.isEmpty()) {
                        Text("All clear.", color = Cyber.muted, fontFamily = PlexMono, fontSize = 12.sp)
                    }
                    items.forEach { NoteRow(it, onDone = onClose) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
