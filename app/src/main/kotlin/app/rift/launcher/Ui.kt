package app.rift.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val PAGE_BRIEF = 0
private const val PAGE_APPS = 1

private fun safeStart(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        // The target screen does not exist on this phone. Nothing sensible to do.
    }
}

// ---------------------------------------------------------------------------
// Root: status strip, two pages (Brief, Apps), dock, labelled tab bar.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LauncherRoot(settings: SettingsState, homeSignal: Int) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var showSettings by remember { mutableStateOf(false) }
    val reload = remember { mutableIntStateOf(0) }

    // Load installed apps off the main thread, and again whenever one changes.
    LaunchedEffect(reload.intValue) {
        apps = withContext(Dispatchers.Default) {
            loadApps(context.packageManager, context.packageName)
        }
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                reload.intValue++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Pressing Home while already on the launcher returns to the Brief page.
    LaunchedEffect(homeSignal) {
        if (homeSignal > 0) {
            showSettings = false
            focusManager.clearFocus()
            pagerState.animateScrollToPage(PAGE_BRIEF)
        }
    }
    // Hide the keyboard whenever the page changes.
    LaunchedEffect(pagerState.currentPage) {
        focusManager.clearFocus()
    }

    // Back always steps "up" one level: settings, then Apps, then Brief.
    BackHandler {
        when {
            showSettings -> showSettings = false
            pagerState.currentPage != PAGE_BRIEF ->
                scope.launch { pagerState.animateScrollToPage(PAGE_BRIEF) }
            else -> Unit
        }
    }

    val launchApp: (AppInfo) -> Unit = { app ->
        safeStart(
            context,
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
    }
    val dockApps = settings.dock.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Cyber.stage)
    ) {
        GridBackdrop()
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
        ) {
            StatusStrip(onClick = { showSettings = true })
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                if (page == PAGE_BRIEF) {
                    BriefPage(
                        settings = settings,
                        onOpenApps = { scope.launch { pagerState.animateScrollToPage(PAGE_APPS) } },
                        onOpenSettings = { showSettings = true },
                    )
                } else {
                    AppsPage(
                        apps = apps,
                        settings = settings,
                        onLaunch = launchApp,
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
            Dock(dockApps = dockApps, settings = settings, onLaunch = launchApp)
            TabBar(
                current = pagerState.currentPage,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
            )
        }
        if (settings.scanlines) {
            Scanlines()
        }
    }

    if (showSettings) {
        SettingsSheet(settings = settings, onDismiss = { showSettings = false })
    }
}

// ---------------------------------------------------------------------------
// Backdrop and overlays
// ---------------------------------------------------------------------------

/** Faint 40dp grid over the near-black stage. */
@Composable
private fun GridBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val step = 40.dp.toPx()
        val line = Cyber.border.copy(alpha = 0.55f)
        var x = 0f
        while (x < size.width) {
            drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
    }
}

/** Very light CRT scanlines drawn over everything. Never intercepts touches. */
@Composable
private fun Scanlines() {
    Canvas(Modifier.fillMaxSize()) {
        val step = 4.dp.toPx()
        val line = Cyber.fg.copy(alpha = 0.035f)
        var y = 0f
        while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
    }
}

// ---------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------

/** Small uppercase monospace label, like the headers on a HUD panel. */
@Composable
fun Kicker(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        fontFamily = PlexMono,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
    )
}

@Composable
fun Plate(
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .plate(highlight)
            .padding(14.dp),
        content = content,
    )
}

/** Time on the left, RIFT in the middle, battery on the right. Tap for settings. */
@Composable
fun StatusStrip(onClick: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var battery by remember { mutableStateOf(readBattery(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            battery = readBattery(context)
            delay(15_000)
        }
    }
    val use24h = DateFormat.is24HourFormat(context)
    val timeText = now.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a"))
    val low = battery.first <= 20 && !battery.second
    val batteryText = (if (battery.second) "+" else "") + "${battery.first}%"

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = timeText,
            modifier = Modifier.weight(1f),
            fontFamily = PlexMono,
            fontSize = 12.sp,
        )
        Text(
            text = "RIFT",
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary,
            fontFamily = PlexMono,
            fontSize = 12.sp,
            letterSpacing = 4.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = batteryText,
            modifier = Modifier.weight(1f),
            color = if (low) MaterialTheme.colorScheme.secondary else Cyber.fg,
            fontFamily = PlexMono,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
        )
    }
}

// ---------------------------------------------------------------------------
// Brief page: glowing clock and a few plates.
// ---------------------------------------------------------------------------

@Composable
fun BriefPage(settings: SettingsState, onOpenApps: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val hot = MaterialTheme.colorScheme.secondary
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var battery by remember { mutableStateOf(readBattery(context)) }
    var alarm by remember { mutableStateOf(readNextAlarm(context)) }
    var isDefault by remember { mutableStateOf(isDefaultHome(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            battery = readBattery(context)
            alarm = readNextAlarm(context)
            isDefault = isDefaultHome(context)
            delay(10_000)
        }
    }

    val use24h = DateFormat.is24HourFormat(context)
    val timeText = now.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm"))
    val dateText = now.format(DateTimeFormatter.ofPattern("EEEE, MMM d")).uppercase()
    val greeting = when {
        now.hour < 5 -> "Up late"
        now.hour < 12 -> "Good morning"
        now.hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = timeText,
            fontSize = 72.sp,
            lineHeight = 76.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-2).sp,
            style = TextStyle(
                shadow = if (settings.glow) {
                    Shadow(color = primary.copy(alpha = 0.6f), blurRadius = 36f)
                } else {
                    null
                },
            ),
        )
        Text(
            text = dateText,
            color = Cyber.muted,
            fontFamily = PlexMono,
            fontSize = 12.sp,
            letterSpacing = 3.sp,
        )
        Spacer(Modifier.height(4.dp))

        if (!isDefault) {
            Plate(
                modifier = Modifier.clickable {
                    safeStart(context, Intent(Settings.ACTION_HOME_SETTINGS))
                },
                highlight = true,
            ) {
                Kicker("Action", color = hot)
                Text(
                    text = "Make RIFT your home screen",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = "Tap here, then pick RIFT under Home app.",
                    color = Cyber.muted,
                    fontFamily = PlexMono,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Plate {
            Kicker("Status")
            Text(
                text = greeting,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            val batteryText = "Power ${battery.first}%" + if (battery.second) " (charging)" else ""
            Text(
                text = batteryText,
                color = Cyber.muted,
                fontFamily = PlexMono,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = alarm?.let { "Next alarm $it" } ?: "No alarm set",
                color = Cyber.muted,
                fontFamily = PlexMono,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Plate(modifier = Modifier.clickable { onOpenApps() }) {
            Kicker("Apps")
            Text(
                text = "Tap here, or swipe left, to see everything installed.",
                color = Cyber.muted,
                fontFamily = PlexMono,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Plate(modifier = Modifier.clickable { onOpenSettings() }) {
            Kicker("Settings")
            Text(
                text = "Accent color, grid size, effects, dock.",
                color = Cyber.muted,
                fontFamily = PlexMono,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------
// Apps page: search on top, A-Z grid below.
// ---------------------------------------------------------------------------

@Composable
fun AppsPage(
    apps: List<AppInfo>,
    settings: SettingsState,
    onLaunch: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }
    val accent = MaterialTheme.colorScheme.primary

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(fontFamily = PlexMono, fontSize = 14.sp),
                placeholder = {
                    Text(
                        text = "SEARCH_",
                        color = Cyber.muted,
                        fontFamily = PlexMono,
                        fontSize = 14.sp,
                        letterSpacing = 2.sp,
                    )
                },
                singleLine = true,
                shape = RectangleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = Cyber.border,
                    focusedContainerColor = Cyber.surface,
                    unfocusedContainerColor = Cyber.surface,
                    cursorColor = accent,
                    focusedTextColor = Cyber.fg,
                    unfocusedTextColor = Cyber.fg,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { filtered.firstOrNull()?.let { onLaunch(it) } }
                ),
            )
            Box(
                Modifier
                    .size(56.dp)
                    .background(Cyber.surface)
                    .border(1.dp, Cyber.border)
                    .clickable { onOpenSettings() },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "⚙", fontSize = 22.sp, color = accent)
            }
        }

        Kicker(
            text = "Apps · ${filtered.size}",
            color = Cyber.muted,
            modifier = Modifier.padding(top = 14.dp),
        )

        if (filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (apps.isEmpty()) "LOADING…" else "NO MATCH FOR \"${query.trim()}\"",
                    color = Cyber.muted,
                    fontFamily = PlexMono,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.columns),
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    AppIcon(
                        app = app,
                        settings = settings,
                        onLaunch = onLaunch,
                        showLabel = settings.showLabels,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Icons, dock, tabs
// ---------------------------------------------------------------------------

/** One app on a dark tile. Tap launches it; long-press opens the actions menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppInfo,
    settings: SettingsState,
    onLaunch: (AppInfo) -> Unit,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val pinned = app.packageName in settings.dock

    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .combinedClickable(
                    onClick = { onLaunch(app) },
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(60.dp)
                    .background(Cyber.surface2)
                    .border(1.dp, Cyber.border),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(44.dp),
                )
            }
            if (showLabel) {
                Text(
                    text = app.label,
                    color = Cyber.muted,
                    fontFamily = PlexMono,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier
                .background(Cyber.surface2)
                .border(1.dp, Cyber.border),
        ) {
            DropdownMenuItem(
                text = { Text(if (pinned) "Remove from dock" else "Pin to dock") },
                onClick = {
                    menuOpen = false
                    settings.toggleDock(app.packageName)
                },
            )
            DropdownMenuItem(
                text = { Text("App info") },
                onClick = {
                    menuOpen = false
                    safeStart(
                        context,
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${app.packageName}"),
                        ),
                    )
                },
            )
            DropdownMenuItem(
                text = { Text("Uninstall") },
                onClick = {
                    menuOpen = false
                    safeStart(
                        context,
                        Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")),
                    )
                },
            )
        }
    }
}

/** Pinned favourites, always visible on both pages. */
@Composable
fun Dock(dockApps: List<AppInfo>, settings: SettingsState, onLaunch: (AppInfo) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .plate()
    ) {
        if (dockApps.isEmpty()) {
            Text(
                text = "LONG-PRESS ANY APP TO PIN IT HERE",
                color = Cyber.muted,
                fontFamily = PlexMono,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
            )
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                dockApps.forEach { app ->
                    AppIcon(
                        app = app,
                        settings = settings,
                        onLaunch = onLaunch,
                        showLabel = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Labelled page switcher so it is always obvious where you are. */
@Composable
fun TabBar(current: Int, onSelect: (Int) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Cyber.border)
        )
        Row(Modifier.fillMaxWidth()) {
            listOf("Brief", "Apps").forEachIndexed { index, label ->
                val selected = index == current
                Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clickable { onSelect(index) }
                        .drawBehind {
                            if (selected) {
                                drawLine(
                                    accent,
                                    Offset(0f, 0f),
                                    Offset(size.width, 0f),
                                    strokeWidth = 3.dp.toPx(),
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label.uppercase(),
                        color = if (selected) accent else Cyber.muted,
                        fontFamily = PlexMono,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        fontSize = 13.sp,
                        letterSpacing = 3.sp,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Cyber.stage,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Cyber.muted,
                uncheckedTrackColor = Cyber.surface2,
                uncheckedBorderColor = Cyber.border,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(settings: SettingsState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Cyber.bg,
        shape = RectangleShape,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Kicker("Settings")

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Kicker("Neon scheme", color = Cyber.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Accents.forEachIndexed { index, scheme ->
                        val selected = index == settings.accentIndex
                        Column(
                            Modifier.clickable { settings.setAccent(index) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .background(scheme.primary)
                                    .then(
                                        if (selected) Modifier.border(3.dp, Cyber.fg)
                                        else Modifier.border(1.dp, Cyber.border)
                                    )
                            )
                            Text(
                                text = scheme.name.uppercase(),
                                color = if (selected) Cyber.fg else Cyber.muted,
                                fontFamily = PlexMono,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Kicker("Apps per row", color = Cyber.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(3, 4, 5).forEach { count ->
                        val selected = count == settings.columns
                        Box(
                            Modifier
                                .background(if (selected) accent else Cyber.surface)
                                .border(1.dp, if (selected) accent else Cyber.border)
                                .clickable { settings.updateColumns(count) }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = "$count",
                                color = if (selected) Cyber.stage else Cyber.fg,
                                fontFamily = PlexMono,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            SettingToggle("Show app names", settings.showLabels) { settings.updateShowLabels(it) }
            SettingToggle("Scanlines", settings.scanlines) { settings.updateScanlines(it) }
            SettingToggle("Clock glow", settings.glow) { settings.updateGlow(it) }

            Button(
                onClick = { safeStart(context, Intent(Settings.ACTION_HOME_SETTINGS)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
            ) {
                Text(
                    text = "CHOOSE DEFAULT HOME APP",
                    fontFamily = PlexMono,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                )
            }
        }
    }
}
