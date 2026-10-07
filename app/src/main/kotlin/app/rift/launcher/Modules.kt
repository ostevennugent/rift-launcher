package app.rift.launcher

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
private fun Mono(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = Cyber.muted) {
    Text(text = text, modifier = modifier, color = color, fontFamily = PlexMono, fontSize = 12.sp)
}

@Composable
fun WeatherModule(info: InfoController, onOpenConfig: () -> Unit) {
    val w = info.weather
    if (w == null) {
        if (info.weatherError != null) {
            Plate {
                Kicker("Weather")
                Mono("Unavailable: ${info.weatherError}", Modifier.padding(top = 4.dp))
            }
        } else {
            NoticePlate("Weather", "Add your city", "Tap here, then enter a city under Information.", onOpenConfig)
        }
        return
    }
    Plate {
        Kicker("Weather · ${w.place}")
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(text = "${w.temp}°${w.unit}", fontSize = 40.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            Text(text = w.summary, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
        }
        Mono(
            "H ${w.high}° · L ${w.low}° · Feels ${w.feelsLike}° · Wind ${w.wind} ${w.windUnit}",
            Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun AgendaModule(info: InfoController, onRequestCalendar: () -> Unit) {
    if (!info.calendarGranted) {
        NoticePlate("Agenda", "Show your calendar", "Tap to allow RIFT to read upcoming events.", onRequestCalendar)
        return
    }
    Plate {
        Kicker("Agenda")
        if (info.agenda.isEmpty()) {
            Mono("Nothing coming up in the next week.", Modifier.padding(top = 4.dp))
        } else {
            info.agenda.forEach { e ->
                Column(Modifier.padding(top = 8.dp)) {
                    Text(text = e.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Mono(e.whenText + if (e.place.isNotBlank()) " · ${e.place}" else "")
                }
            }
        }
    }
}

@Composable
fun CommsModule(info: InfoController) {
    val context = LocalContext.current
    if (!info.notificationAccess) {
        NoticePlate(
            "Notifications",
            "Show notifications here",
            "Tap to grant notification access. See Config > Access if the switch is greyed out.",
        ) { safeStart(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        return
    }
    val items = NotificationHub.items
    Plate {
        Kicker("Notifications · ${items.size}")
        if (items.isEmpty()) {
            Mono("All clear.", Modifier.padding(top = 4.dp))
        } else {
            items.take(4).forEach { n -> NoteRow(n, onDone = {}) }
            if (items.size > 4) Mono("+${items.size - 4} more in the shade", Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun NoteRow(n: NoteItem, onDone: () -> Unit) {
    Row(
        Modifier
            .clickable {
                try {
                    n.intent?.send()
                } catch (_: Exception) {
                    // Intent was cancelled.
                }
                onDone()
            }
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Kicker(n.app, color = Cyber.muted)
            Text(text = n.title.ifBlank { n.text }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (n.title.isNotBlank() && n.text.isNotBlank()) {
                Mono(n.text.replace("\n", " "), Modifier)
            }
        }
        if (n.clearable) {
            Box(
                Modifier.size(40.dp).clickable { NotificationHub.dismiss(n.key) },
                contentAlignment = Alignment.Center,
            ) { Text("✕", color = Cyber.muted) }
        }
    }
}

/** Now playing with transport controls. Draws nothing when no media session is active. */
@Composable
fun MediaModule(info: InfoController) {
    val context = LocalContext.current
    var np by remember { mutableStateOf<NowPlaying?>(null) }
    LaunchedEffect(info.notificationAccess) {
        while (true) {
            np = if (info.notificationAccess) {
                withContext(Dispatchers.Default) { readNowPlaying(context) }
            } else {
                null
            }
            delay(2_000)
        }
    }
    val current = np ?: return
    val accent = MaterialTheme.colorScheme.primary
    Plate {
        Kicker("Now playing")
        Text(text = current.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        if (current.artist.isNotBlank()) Mono(current.artist)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val controls = current.controller.transportControls
            listOf(
                "⏮" to { controls.skipToPrevious() },
                (if (current.playing) "⏸" else "▶") to {
                    if (current.playing) controls.pause() else controls.play()
                },
                "⏭" to { controls.skipToNext() },
            ).forEach { (symbol, action) ->
                Box(
                    Modifier
                        .size(48.dp)
                        .background(Cyber.surface2)
                        .border(1.dp, Cyber.border)
                        .clickable {
                            try {
                                action()
                            } catch (_: Exception) {
                                // Session ended.
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Text(symbol, color = accent) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeadlinesModule(info: InfoController) {
    var expanded by remember { mutableStateOf(false) }
    Plate(Modifier.clickable { expanded = !expanded }) {
        Kicker("Headlines")
        when {
            info.headlines.isEmpty() ->
                Mono(info.headlinesError?.let { "Unavailable: $it" } ?: "Loading…", Modifier.padding(top = 4.dp))
            expanded -> info.headlines.take(8).forEach {
                Text(text = it, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
            else -> Text(
                text = info.headlines.take(8).joinToString("   ◆   "),
                maxLines = 1,
                softWrap = false,
                fontFamily = PlexMono,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp).basicMarquee(iterations = Int.MAX_VALUE),
            )
        }
    }
}

@Composable
fun VitalsModule() {
    val context = LocalContext.current
    var v by remember { mutableStateOf<Vitals?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            v = withContext(Dispatchers.Default) { InfoFetcher.vitals(context) }
            delay(10_000)
        }
    }
    val vit = v ?: return
    Plate {
        Kicker("Device vitals")
        Mono(
            "Storage ${vit.storageUsedPct}% used (" + "%.1f".format(vit.storageFreeGb) + " GB free)",
            Modifier.padding(top = 4.dp), Cyber.fg,
        )
        Mono("Memory ${vit.memUsedPct}% used · Network ${vit.network}", Modifier.padding(top = 2.dp), Cyber.fg)
        if (vit.tempC > 0f) Mono("Battery temp %.1f°C".format(vit.tempC), Modifier.padding(top = 2.dp), Cyber.fg)
    }
}
