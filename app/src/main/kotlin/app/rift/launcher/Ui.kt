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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.MaterialTheme
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
private const val PAGE_CONFIG = 2

private const val SIX_HOURS_MS = 6 * 60 * 60 * 1000L

fun safeStart(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        // The target screen does not exist on this phone. Nothing sensible to do.
    }
}

// ---------------------------------------------------------------------------
// Root: status strip, three pages (Brief, Apps, Config), dock, tab bar.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherRoot(settings: SettingsState, homeSignal: Int, configSignal: Int) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = settings.int(Keys.START_PAGE, 0).coerceIn(0, 1),
        pageCount = { 3 },
    )
    val updates = remember { UpdateController(context.applicationContext, settings, scope) }

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
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

    // On start: clear any old downloaded update, then check GitHub at most every 6 hours.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { Updater.cleanup(context) }
        val last = settings.long(Keys.LAST_CHECK, 0L)
        if (settings.autoUpdate && System.currentTimeMillis() - last > SIX_HOURS_MS) {
            updates.check()
        }
    }

    // Pressing Home while already on the launcher returns to the Brief page.
    LaunchedEffect(homeSignal) {
        if (homeSignal > 0) {
            focusManager.clearFocus()
            pagerState.animateScrollToPage(PAGE_BRIEF)
        }
    }
    // The "Config" app shortcut jumps straight to the Config page.
    LaunchedEffect(configSignal) {
        if (configSignal > 0) {
            pagerState.animateScrollToPage(PAGE_CONFIG)
        }
    }
    // Hide the keyboard whenever the page changes.
    LaunchedEffect(pagerState.currentPage) {
        focusManager.clearFocus()
    }

    // Back steps "up" to Brief from the other pages.
    BackHandler {
        if (pagerState.currentPage != PAGE_BRIEF) {
            scope.launch { pagerState.animateScrollToPage(PAGE_BRIEF) }
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
    val openConfig: () -> Unit = {
        scope.launch { pagerState.animateScrollToPage(PAGE_CONFIG) }
    }

    val hidden = settings.hiddenApps
    val visibleApps = remember(apps, hidden) { apps.filter { it.packageName !in hidden } }
    val dockApps = settings.dock.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Cyber.stage)
    ) {
        if (settings.showGrid) {
            GridBackdrop()
        }
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
        ) {
            StatusStrip(settings = settings, onOpenConfig = openConfig)
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    PAGE_BRIEF -> BriefPage(
                        settings = settings,
                        apps = apps,
                        updates = updates,
                        onLaunch = launchApp,
                        onOpenConfig = openConfig,
                    )
                    PAGE_APPS -> AppsPage(
                        apps = visibleApps,
                        settings = settings,
                        onLaunch = launchApp,
                        onOpenConfig = openConfig,
                    )
                    else -> ConfigPage(settings = settings, apps = apps, updates = updates)
                }
            }
            if (settings.showDock) {
                Dock(dockApps = dockApps, settings = settings, onLaunch = launchApp)
            }
            if (settings.showTabBar) {
                TabBar(
                    current = pagerState.currentPage,
                    onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                )
            }
        }
        if (settings.scanlines) {
            Scanlines()
        }
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

/** A tappable highlighted plate used for notices (updates, set-as-home). */
@Composable
private fun NoticePlate(kicker: String, title: String, body: String, onClick: () -> Unit) {
    Plate(modifier = Modifier.clickable { onClick() }, highlight = true) {
        Kicker(kicker, color = MaterialTheme.colorScheme.secondary)
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = body,
            color = Cyber.muted,
            fontFamily = PlexMono,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Time on the left, title in the middle, battery and the gear on the right.
 * Each part can be hidden in Config, but the gear is always there so Config
 * can always be reached. Tapping anywhere on the strip opens Config too.
 */
@Composable
fun StatusStrip(settings: SettingsState, onOpenConfig: () -> Unit) {
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
    val use24h = when (settings.int(Keys.CLOCK_MODE, 0)) {
        1 -> false
        2 -> true
        else -> DateFormat.is24HourFormat(context)
    }
    val timeText = now.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a"))
    val low = battery.first <= 20 && !battery.second
    val batteryText = (if (battery.second) "+" else "") + "${battery.first}%"
    val title = settings.str(Keys.TITLE_TEXT, "RIFT").ifBlank { "RIFT" }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenConfig() }
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (settings.bool(Keys.STRIP_TIME, true)) {
            Text(
                text = timeText,
                modifier = Modifier.weight(1f),
                fontFamily = PlexMono,
                fontSize = 12.sp,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (settings.bool(Keys.STRIP_TITLE, true)) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary,
                fontFamily = PlexMono,
                fontSize = 12.sp,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (settings.bool(Keys.STRIP_BATTERY, true)) {
                Text(
                    text = batteryText,
                    color = if (low) MaterialTheme.colorScheme.secondary else Cyber.fg,
                    fontFamily = PlexMono,
                    fontSize = 12.sp,
                )
            }
            Box(
                Modifier
                    .size(48.dp)
                    .clickable { onOpenConfig() },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "⚙", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Brief page: whatever modules you turned on, in the order you chose.
// ---------------------------------------------------------------------------

@Composable
fun BriefPage(
    settings: SettingsState,
    apps: List<AppInfo>,
    updates: UpdateController,
    onLaunch: (AppInfo) -> Unit,
    onOpenConfig: () -> Unit,
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val showSeconds = settings.bool(Keys.CLOCK_SECONDS, false)

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var battery by remember { mutableStateOf(readBattery(context)) }
    var alarm by remember { mutableStateOf(readNextAlarm(context)) }
    var isDefault by remember { mutableStateOf(isDefaultHome(context)) }

    LaunchedEffect(showSeconds) {
        while (true) {
            now = LocalDateTime.now()
            delay(if (showSeconds) 1_000L else 10_000L)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            battery = readBattery(context)
            alarm = readNextAlarm(context)
            isDefault = isDefaultHome(context)
            delay(10_000)
        }
    }

    val use24h = when (settings.int(Keys.CLOCK_MODE, 0)) {
        1 -> false
        2 -> true
        else -> DateFormat.is24HourFormat(context)
    }
    val pattern = (if (use24h) "HH:mm" else "h:mm") + (if (showSeconds) ":ss" else "")
    val timeText = now.format(DateTimeFormatter.ofPattern(pattern))
    val baseSize = listOf(56, 72, 92)[settings.int(Keys.CLOCK_SIZE, 1).coerceIn(0, 2)]
    val clockSize = baseSize * (if (showSeconds) 0.72f else 1f)
    val dateText = now.format(DateTimeFormatter.ofPattern("EEEE, MMM d")).uppercase()
    val greeting = when {
        now.hour < 5 -> "Up late"
        now.hour < 12 -> "Good morning"
        now.hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    val modules = settings.moduleOrder.filter { it !in settings.hiddenModules }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        when (val st = updates.status) {
            is UpdateStatus.Available -> NoticePlate(
                "Update",
                "A new build is available",
                "Build ${st.info.sha.take(7)}. Tap to review in Config.",
                onOpenConfig,
            )
            is UpdateStatus.Downloading -> NoticePlate(
                "Update",
                "Downloading update",
                "${(st.progress * 100).toInt()}% done.",
                onOpenConfig,
            )
            is UpdateStatus.Ready -> NoticePlate(
                "Update",
                "Update downloaded",
                "Tap to finish installing in Config.",
                onOpenConfig,
            )
            else -> Unit
        }

        if (settings.bool(Keys.DEFAULT_REMINDER, true) && !isDefault) {
            NoticePlate(
                "Action",
                "Make RIFT your home screen",
                "Tap here, then pick RIFT under Home app.",
            ) { safeStart(context, Intent(Settings.ACTION_HOME_SETTINGS)) }
        }

        modules.forEach { id ->
            when (id) {
                "clock" -> Text(
                    text = timeText,
                    fontSize = clockSize.sp,
                    lineHeight = (clockSize * 1.05f).sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-2).sp,
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(
                        shadow = if (settings.glow) {
                            Shadow(color = primary.copy(alpha = 0.6f), blurRadius = 36f)
                        } else {
                            null
                        },
                    ),
                )
                "date" -> Text(
                    text = dateText,
                    color = Cyber.muted,
                    fontFamily = PlexMono,
                    fontSize = 12.sp,
                    letterSpacing = 3.sp,
                )
                "status" -> Plate {
                    Kicker("Status")
                    if (settings.bool(Keys.ST_GREETING, true)) {
                        Text(
                            text = greeting,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (settings.bool(Keys.ST_BATTERY, true)) {
                        val text = "Power ${battery.first}%" + if (battery.second) " (charging)" else ""
                        Text(
                            text = text,
                            color = Cyber.muted,
                            fontFamily = PlexMono,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (settings.bool(Keys.ST_ALARM, true)) {
                        Text(
                            text = alarm?.let { "Next alarm $it" } ?: "No alarm set",
                            color = Cyber.muted,
                            fontFamily = PlexMono,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                "quick" -> QuickApps(settings = settings, apps = apps, onLaunch = onLaunch)
                else -> Unit
            }
        }

        if (modules.isEmpty()) {
            NoticePlate(
                "Empty",
                "Nothing on the Brief page",
                "Tap here to open Config and switch modules on.",
                onOpenConfig,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** The apps you pinned with "Pin to home", as a grid of your chosen width. */
@Composable
private fun QuickApps(settings: SettingsState, apps: List<AppInfo>, onLaunch: (AppInfo) -> Unit) {
    val columns = settings.int(Keys.QUICK_COLUMNS, 4).coerceIn(3, 6)
    val quickApps = settings.quick.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
    if (quickApps.isEmpty()) {
        Plate {
            Kicker("Quick apps")
            Text(
                text = "Long-press any app on the Apps page and choose Pin to home.",
                color = Cyber.muted,
                fontFamily = PlexMono,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    } else {
        Column {
            Kicker("Quick apps", color = Cyber.muted)
            Spacer(Modifier.height(4.dp))
            quickApps.chunked(columns).forEach { rowApps ->
                Row(Modifier.fillMaxWidth()) {
                    rowApps.forEach { app ->
                        AppIcon(
                            app = app,
                            settings = settings,
                            onLaunch = onLaunch,
                            showLabel = settings.showLabels,
                            iconDp = settings.iconDp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - rowApps.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Apps page: search on top, A-Z grid below.
// ---------------------------------------------------------------------------

@Composable
private fun riftFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = Cyber.border,
    focusedContainerColor = Cyber.surface,
    unfocusedContainerColor = Cyber.surface,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedTextColor = Cyber.fg,
    unfocusedTextColor = Cyber.fg,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = Cyber.muted,
)

@Composable
fun AppsPage(
    apps: List<AppInfo>,
    settings: SettingsState,
    onLaunch: (AppInfo) -> Unit,
    onOpenConfig: () -> Unit,
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
                colors = riftFieldColors(),
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
                    .clickable { onOpenConfig() },
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
                        iconDp = settings.iconDp,
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
    iconDp: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val onDock = app.packageName in settings.dock
    val onHome = app.packageName in settings.quick

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
                    .size((iconDp + 16).dp)
                    .background(Cyber.surface2)
                    .border(1.dp, Cyber.border),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(iconDp.dp),
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
                text = { Text(if (onDock) "Remove from dock" else "Pin to dock") },
                onClick = {
                    menuOpen = false
                    settings.toggleDock(app.packageName)
                },
            )
            DropdownMenuItem(
                text = { Text(if (onHome) "Remove from home" else "Pin to home") },
                onClick = {
                    menuOpen = false
                    settings.toggleQuick(app.packageName)
                },
            )
            DropdownMenuItem(
                text = { Text("Hide app") },
                onClick = {
                    menuOpen = false
                    settings.toggleHiddenApp(app.packageName)
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

/** Pinned favourites, visible on every page. */
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
                        iconDp = minOf(settings.iconDp, 40),
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
            listOf("Brief", "Apps", "Config").forEachIndexed { index, label ->
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
// Config page
// ---------------------------------------------------------------------------

@Composable
private fun riftSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Cyber.stage,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = Cyber.muted,
    uncheckedTrackColor = Cyber.surface2,
    uncheckedBorderColor = Cyber.border,
)

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Plate {
        Kicker(title)
        Column(
            Modifier.padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, colors = riftSwitchColors())
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .background(if (selected) accent else Cyber.surface)
            .border(1.dp, if (selected) accent else Cyber.border)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = text.uppercase(),
            color = if (selected) Cyber.stage else Cyber.fg,
            fontFamily = PlexMono,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Kicker(label, color = Cyber.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, text ->
                Chip(text = text, selected = index == selected) { onSelect(index) }
            }
        }
    }
}

@Composable
private fun RiftButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier, shape = RectangleShape) {
        Text(
            text = text.uppercase(),
            fontFamily = PlexMono,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun MoveButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .border(1.dp, Cyber.border)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            color = if (enabled) MaterialTheme.colorScheme.primary else Cyber.border,
        )
    }
}

@Composable
private fun ModuleRow(
    label: String,
    shown: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = shown, onCheckedChange = { onToggle() }, colors = riftSwitchColors())
        Text(text = label, modifier = Modifier.weight(1f).padding(start = 12.dp))
        MoveButton("▲", canMoveUp, onUp)
        Spacer(Modifier.width(6.dp))
        MoveButton("▼", canMoveDown, onDown)
    }
}

@Composable
private fun MonoNote(text: String) {
    Text(text = text, color = Cyber.muted, fontFamily = PlexMono, fontSize = 12.sp)
}

@Composable
fun ConfigPage(settings: SettingsState, apps: List<AppInfo>, updates: UpdateController) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    var confirmReset by remember { mutableStateOf(false) }
    val order = settings.moduleOrder
    val hiddenModules = settings.hiddenModules
    val hiddenApps = apps.filter { it.packageName in settings.hiddenApps }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Everything you see on the home screen can be changed here.",
            color = Cyber.muted,
            fontFamily = PlexMono,
            fontSize = 12.sp,
        )

        Section("Brief page") {
            MonoNote("Switch modules on or off and use the arrows to reorder them.")
            order.forEachIndexed { index, id ->
                val label = BriefModules.firstOrNull { it.first == id }?.second ?: id
                ModuleRow(
                    label = label,
                    shown = id !in hiddenModules,
                    canMoveUp = index > 0,
                    canMoveDown = index < order.lastIndex,
                    onToggle = { settings.toggleModuleHidden(id) },
                    onUp = { settings.moveModule(id, -1) },
                    onDown = { settings.moveModule(id, 1) },
                )
            }
            ChoiceRow(
                label = "Quick apps per row",
                options = listOf("3", "4", "5", "6"),
                selected = settings.int(Keys.QUICK_COLUMNS, 4).coerceIn(3, 6) - 3,
            ) { settings.putInt(Keys.QUICK_COLUMNS, it + 3) }
        }

        Section("Clock and status") {
            ChoiceRow(
                label = "Clock format",
                options = listOf("System", "12h", "24h"),
                selected = settings.int(Keys.CLOCK_MODE, 0).coerceIn(0, 2),
            ) { settings.putInt(Keys.CLOCK_MODE, it) }
            ChoiceRow(
                label = "Clock size",
                options = listOf("Small", "Medium", "Large"),
                selected = settings.int(Keys.CLOCK_SIZE, 1).coerceIn(0, 2),
            ) { settings.putInt(Keys.CLOCK_SIZE, it) }
            ToggleRow("Show seconds", settings.bool(Keys.CLOCK_SECONDS, false)) {
                settings.putBool(Keys.CLOCK_SECONDS, it)
            }
            ToggleRow("Greeting line", settings.bool(Keys.ST_GREETING, true)) {
                settings.putBool(Keys.ST_GREETING, it)
            }
            ToggleRow("Battery line", settings.bool(Keys.ST_BATTERY, true)) {
                settings.putBool(Keys.ST_BATTERY, it)
            }
            ToggleRow("Next alarm line", settings.bool(Keys.ST_ALARM, true)) {
                settings.putBool(Keys.ST_ALARM, it)
            }
        }

        Section("Top strip") {
            ToggleRow("Show time", settings.bool(Keys.STRIP_TIME, true)) {
                settings.putBool(Keys.STRIP_TIME, it)
            }
            ToggleRow("Show title", settings.bool(Keys.STRIP_TITLE, true)) {
                settings.putBool(Keys.STRIP_TITLE, it)
            }
            OutlinedTextField(
                value = settings.str(Keys.TITLE_TEXT, "RIFT"),
                onValueChange = { settings.putStr(Keys.TITLE_TEXT, it.take(14)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title text") },
                textStyle = TextStyle(fontFamily = PlexMono, fontSize = 14.sp),
                singleLine = true,
                shape = RectangleShape,
                colors = riftFieldColors(),
            )
            ToggleRow("Show battery", settings.bool(Keys.STRIP_BATTERY, true)) {
                settings.putBool(Keys.STRIP_BATTERY, it)
            }
            MonoNote("The gear stays visible so you can always get back here.")
        }

        Section("Apps and dock") {
            ChoiceRow(
                label = "Apps per row",
                options = listOf("3", "4", "5", "6"),
                selected = settings.columns - 3,
            ) { settings.putInt(Keys.COLUMNS, it + 3) }
            ChoiceRow(
                label = "Icon size",
                options = listOf("Small", "Medium", "Large"),
                selected = settings.int(Keys.ICON_SIZE, 1).coerceIn(0, 2),
            ) { settings.putInt(Keys.ICON_SIZE, it) }
            ToggleRow("Show app names", settings.showLabels) {
                settings.putBool(Keys.LABELS, it)
            }
            ToggleRow("Show dock", settings.showDock) { settings.putBool(Keys.DOCK_SHOW, it) }
            ToggleRow("Show tab bar", settings.showTabBar) { settings.putBool(Keys.TAB_BAR, it) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Kicker("Hidden apps · ${hiddenApps.size}", color = Cyber.muted)
                if (hiddenApps.isEmpty()) {
                    MonoNote("None. Long-press an app and choose Hide app.")
                } else {
                    hiddenApps.forEach { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { settings.toggleHiddenApp(app.packageName) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = app.label, modifier = Modifier.weight(1f))
                            Text(
                                text = "UNHIDE",
                                color = accent,
                                fontFamily = PlexMono,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp,
                            )
                        }
                    }
                }
            }
        }

        Section("Look") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Kicker("Neon scheme", color = Cyber.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Accents.forEachIndexed { index, scheme ->
                        val selected = index == settings.accentIndex
                        Column(
                            Modifier.clickable { settings.putInt(Keys.ACCENT, index) },
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
            ToggleRow("Grid background", settings.showGrid) { settings.putBool(Keys.GRID, it) }
            ToggleRow("Scanlines", settings.scanlines) { settings.putBool(Keys.SCANLINES, it) }
            ToggleRow("Clock glow", settings.glow) { settings.putBool(Keys.GLOW, it) }
        }

        Section("Behavior") {
            ChoiceRow(
                label = "Open on",
                options = listOf("Brief", "Apps"),
                selected = settings.int(Keys.START_PAGE, 0).coerceIn(0, 1),
            ) { settings.putInt(Keys.START_PAGE, it) }
            ToggleRow(
                "Remind me to set RIFT as home",
                settings.bool(Keys.DEFAULT_REMINDER, true),
            ) { settings.putBool(Keys.DEFAULT_REMINDER, it) }
        }

        Section("Updates") {
            val status = updates.status
            MonoNote("Installed: ${BuildConfig.VERSION_NAME} · ${BuildConfig.GIT_SHA.take(7)}")
            val line = when (status) {
                UpdateStatus.Idle -> "Not checked yet."
                UpdateStatus.Checking -> "Checking GitHub…"
                UpdateStatus.UpToDate -> "You are on the latest build."
                is UpdateStatus.Available ->
                    "New build ${status.info.sha.take(7)} is available " +
                        "(%.1f MB).".format(status.info.sizeBytes / 1048576.0)
                is UpdateStatus.Downloading ->
                    "Downloading… ${(status.progress * 100).toInt()}%"
                is UpdateStatus.Ready ->
                    "Downloaded. If the installer did not open, allow RIFT to install apps, then tap Install again."
                is UpdateStatus.Failed -> "Could not update: ${status.message}"
            }
            Text(text = line)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RiftButton("Check now") { updates.check() }
                when (status) {
                    is UpdateStatus.Available ->
                        RiftButton("Install") { updates.downloadAndInstall(status.info) }
                    is UpdateStatus.Ready ->
                        RiftButton("Install again") { updates.installAgain(status.info, status.file) }
                    else -> Unit
                }
            }
            ToggleRow("Check automatically", settings.autoUpdate) {
                settings.putBool(Keys.AUTO_UPDATE, it)
            }
        }

        Section("About") {
            MonoNote("RIFT launcher ${BuildConfig.VERSION_NAME}")
            RiftButton("Choose default home app", Modifier.fillMaxWidth()) {
                safeStart(context, Intent(Settings.ACTION_HOME_SETTINGS))
            }
            RiftButton(
                text = if (confirmReset) "Tap again to confirm reset" else "Reset all settings",
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (confirmReset) {
                    settings.reset()
                    confirmReset = false
                } else {
                    confirmReset = true
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
