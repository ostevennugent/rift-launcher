package app.rift.launcher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
private const val PAGE_HOME = 1
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
fun LauncherRoot(settings: SettingsState, homeSignal: Int, configSignal: Int, resumeSignal: Int) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = settings.int(Keys.START_PAGE, 0).coerceIn(0, 1),
        pageCount = { 3 },
    )
    val updates = remember { UpdateController(context.applicationContext, settings, scope) }
    val info = remember { InfoController(context.applicationContext, settings, scope) }
    val tools = remember { ToolsState() }
    val calc = remember { CalcState() }
    var shadeOpen by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    var drawerFocus by remember { mutableStateOf(false) }
    var configScreen by remember { mutableStateOf("") }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var addTarget by remember { mutableStateOf<AppInfo?>(null) }
    val folderActions = remember { FolderActions({ openFolder = it }, { addTarget = it }) }

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    val reload = remember { mutableIntStateOf(0) }

    val calendarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { info.refreshAgenda() }
    val requestCalendar: () -> Unit = { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) }

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

    // Keep the flashlight tile in sync with the real torch.
    DisposableEffect(context) {
        val manager = context.getSystemService(CameraManager::class.java)
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                Torch.on = enabled
            }
        }
        try {
            manager?.registerTorchCallback(callback, null)
        } catch (_: Exception) {
            // No camera service.
        }
        onDispose {
            try {
                manager?.unregisterTorchCallback(callback)
            } catch (_: Exception) {
                // Already gone.
            }
        }
    }

    // Timer and stopwatch keep running while you are on any page.
    LaunchedEffect(Unit) {
        while (true) {
            if (tools.timerRunning || tools.swRunning) {
                if (tools.tick()) vibrate(context)
                delay(100)
            } else {
                delay(500)
            }
        }
    }

    // On start: clear any old downloaded update, then check GitHub at most every 6 hours.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { Updater.cleanup(context) }
        val last = settings.long(Keys.LAST_CHECK, 0L)
        if (settings.autoUpdate && System.currentTimeMillis() - last > SIX_HOURS_MS) {
            updates.check()
        }
    }

    // Refresh information whenever the launcher comes to the front, and every 30 minutes.
    LaunchedEffect(resumeSignal) {
        info.refreshAll()
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30 * 60_000L)
            info.refreshWeather()
            info.refreshHeadlines()
            info.refreshAgenda()
        }
    }
    val cityKey = settings.str(Keys.WEATHER_CITY, "") + settings.int(Keys.WEATHER_UNIT, 0)
    LaunchedEffect(cityKey) {
        delay(1_200)
        info.refreshWeather()
    }
    val feedKey = settings.str(Keys.FEED_URL, "")
    LaunchedEffect(feedKey) {
        delay(1_200)
        info.refreshHeadlines()
    }

    // Pressing Home while already on the launcher returns to the Brief page.
    LaunchedEffect(homeSignal) {
        if (homeSignal > 0) {
            focusManager.clearFocus()
            shadeOpen = false
            drawerOpen = false
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
    BackHandler(enabled = !shadeOpen && !drawerOpen) {
        if (pagerState.currentPage == PAGE_CONFIG && configScreen.isNotEmpty()) {
            configScreen = configScreen.substringBeforeLast('/', "")
        } else if (pagerState.currentPage != PAGE_BRIEF) {
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
    val runAction: (String) -> Unit = { action ->
        when (action) {
            "shade" -> shadeOpen = true
            "search" -> {
                drawerFocus = true
                drawerOpen = true
            }
            "apps" -> {
                drawerFocus = false
                drawerOpen = true
            }
            "deck" -> shadeOpen = true
            "home" -> scope.launch { pagerState.animateScrollToPage(PAGE_HOME) }
            "brief" -> scope.launch { pagerState.animateScrollToPage(PAGE_BRIEF) }
            "config" -> openConfig()
            else -> Unit
        }
    }
    fun gesture(slot: Triple<String, String, String>): String = settings.str(slot.first, slot.third)
    val gDown = gesture(GestureSlots[0])
    val gUp = gesture(GestureSlots[1])
    val gLong = gesture(GestureSlots[2])
    val gDouble = gesture(GestureSlots[3])
    val thresholdPx = with(LocalDensity.current) { 70.dp.toPx() }
    val currentRun by rememberUpdatedState(runAction)
    val edge = remember(thresholdPx, gDown, gUp) {
        EdgeSwipe(
            thresholdPx,
            onDown = if (gDown == "none") null else ({ currentRun(gDown) }),
            onUp = if (gUp == "none") null else ({ currentRun(gUp) }),
        )
    }

    val hidden = settings.hiddenApps
    val visibleApps = remember(apps, hidden) { apps.filter { it.packageName !in hidden } }

    CompositionLocalProvider(LocalFolderActions provides folderActions) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Cyber.stage)
    ) {
        if (settings.showGrid) {
            GridBackdrop()
        }
        if (settings.bool(Keys.STREAM, true)) {
            DataStream(settings)
        }
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
        ) {
            if (settings.bool(Keys.STRIP_SHOW, false)) {
                StatusStrip(settings = settings, onOpenConfig = openConfig)
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    PAGE_BRIEF -> BriefPage(
                        settings = settings,
                        apps = apps,
                        updates = updates,
                        info = info,
                        edge = edge,
                        onLongPress = { currentRun(gLong) },
                        onDoubleTap = { currentRun(gDouble) },
                        onRequestCalendar = requestCalendar,
                        onLaunch = launchApp,
                        onOpenConfig = openConfig,
                    )
                    PAGE_HOME -> HomePage(
                        settings = settings,
                        apps = apps,
                        edge = edge,
                        onLongPress = { currentRun(gLong) },
                        onDoubleTap = { currentRun(gDouble) },
                        onLaunch = launchApp,
                    )
                    else -> ConfigPage(
                        settings = settings,
                        apps = apps,
                        updates = updates,
                        info = info,
                        onRequestCalendar = requestCalendar,
                        screen = configScreen,
                        onScreen = { configScreen = it },
                    )
                }
            }
            if (settings.showDock) {
                Dock(apps = apps, settings = settings, onLaunch = launchApp)
            }
            if (settings.showTabBar) {
                PageBar(
                    current = pagerState.currentPage,
                    style = settings.int(Keys.TAB_STYLE, 0),
                    onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                    onConfig = openConfig,
                )
            }
        }
        AppDrawer(
            open = drawerOpen,
            apps = visibleApps,
            settings = settings,
            autoFocus = drawerFocus,
            onLaunch = launchApp,
            onClose = { drawerOpen = false },
        )
        Shade(
            open = shadeOpen,
            info = info,
            settings = settings,
            tools = tools,
            calc = calc,
            onClose = { shadeOpen = false },
        )
        if (settings.scanlines) {
            Scanlines()
        }
    }
    openFolder?.let { id ->
        FolderDialog(id, apps, settings, launchApp) { openFolder = null }
    }
    addTarget?.let { app ->
        AddToFolderDialog(app, settings) { addTarget = null }
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
            .padding(12.dp),
        content = content,
    )
}

/** A tappable highlighted plate used for notices (updates, set-as-home). */
@Composable
internal fun NoticePlate(kicker: String, title: String, body: String, onClick: () -> Unit) {
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
    info: InfoController,
    edge: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    onLongPress: () -> Unit,
    onDoubleTap: () -> Unit,
    onRequestCalendar: () -> Unit,
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
    val baseSize = listOf(48, 60, 76)[settings.int(Keys.CLOCK_SIZE, 1).coerceIn(0, 2)]
    val clockSize = baseSize * (if (showSeconds) 0.72f else 1f)
    val dateText = now.format(DateTimeFormatter.ofPattern("EEEE, MMM d")).uppercase()
    val greeting = when {
        now.hour < 5 -> "Up late"
        now.hour < 12 -> "Good morning"
        now.hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    val modules = settings.moduleOrder.filter { it !in settings.hiddenModules }

    @Composable
    fun Mod(id: String) {
            when (id) {
                "clock" -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(
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
                        if ("date" in modules) {
                            Text(
                                text = dateText,
                                color = Cyber.muted,
                                fontFamily = PlexMono,
                                fontSize = 11.sp,
                                letterSpacing = 2.sp,
                            )
                        }
                    }
                    val side = info.weather
                    if (side != null && "weather" in modules) {
                        WeatherSide(side, Modifier.padding(start = 8.dp))
                    }
                }
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
                "weather" -> if (info.weather == null || "clock" !in modules) WeatherModule(info, onOpenConfig)
                "agenda" -> AgendaModule(info, onRequestCalendar)
                "comms" -> CommsModule(info)
                "media" -> MediaModule(info)
                "headlines" -> HeadlinesModule(info)
                "vitals" -> VitalsModule()
                else -> Unit
            }
    }
    val shown = modules.filter { !(it == "date" && "clock" in modules) }
    val half = setOf("status", "vitals")

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() }, onDoubleTap = { onDoubleTap() })
            }
            .nestedScroll(edge)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {

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

        var idx = 0
        while (idx < shown.size) {
            val id = shown[idx]
            val next = shown.getOrNull(idx + 1)
            if (id in half && next != null && next in half) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { Mod(id) }
                    Column(Modifier.weight(1f)) { Mod(next) }
                }
                idx += 2
            } else {
                Mod(id)
                idx += 1
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
    val quickApps = resolveEntries(settings.quick, apps, settings.folders)
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
                    rowApps.forEach { entry ->
                        EntryIcon(
                            entry = entry,
                            apps = apps,
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
    compact: Boolean = false,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val onDock = app.packageName in settings.dock
    val onHome = app.packageName in settings.quick
    val folderActions = LocalFolderActions.current

    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .combinedClickable(
                    onClick = { onLaunch(app) },
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = if (compact) 2.dp else 4.dp, vertical = if (compact) 2.dp else 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size((iconDp + if (compact) 8 else 16).dp)
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
            if (onDock) {
                DropdownMenuItem(
                    text = { Text("Move left in dock") },
                    onClick = { settings.moveDock(app.packageName, -1) },
                )
                DropdownMenuItem(
                    text = { Text("Move right in dock") },
                    onClick = { settings.moveDock(app.packageName, 1) },
                )
            }
            if (onHome) {
                DropdownMenuItem(
                    text = { Text("Move earlier on home") },
                    onClick = { settings.moveQuick(app.packageName, -1) },
                )
                DropdownMenuItem(
                    text = { Text("Move later on home") },
                    onClick = { settings.moveQuick(app.packageName, 1) },
                )
            }
            DropdownMenuItem(
                text = { Text("Add to folder…") },
                onClick = {
                    menuOpen = false
                    folderActions.addTo(app)
                },
            )
            if (settings.folderOf(app.packageName) != null) {
                DropdownMenuItem(
                    text = { Text("Remove from folder") },
                    onClick = {
                        menuOpen = false
                        settings.removeFromFolder(app.packageName)
                    },
                )
            }
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

/** Pinned favourites, visible on every page, as a slim bar. */
@Composable
fun Dock(apps: List<AppInfo>, settings: SettingsState, onLaunch: (AppInfo) -> Unit) {
    val dockApps = resolveEntries(settings.dock, apps, settings.folders)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .plate()
    ) {
        if (dockApps.isEmpty()) {
            Text(
                text = "LONG-PRESS ANY APP TO PIN IT HERE",
                color = Cyber.muted,
                fontFamily = PlexMono,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
            )
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                dockApps.forEach { entry ->
                    EntryIcon(
                        entry = entry,
                        apps = apps,
                        settings = settings,
                        onLaunch = onLaunch,
                        showLabel = false,
                        iconDp = minOf(settings.iconDp, 40),
                        modifier = Modifier.weight(1f),
                        compact = true,
                    )
                }
            }
        }
    }
}

/** Small page dots with a gear, or (in Config > Look) labelled tabs. */
@Composable
fun PageBar(current: Int, style: Int, onSelect: (Int) -> Unit, onConfig: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val labels = listOf("Brief", "Home", "Config")
    if (style == 1) {
        Column {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Cyber.border))
            Row(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { index, label ->
                    val selected = index == current
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable { onSelect(index) }
                            .drawBehind {
                                if (selected) {
                                    drawLine(accent, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 3.dp.toPx())
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label.uppercase(),
                            color = if (selected) accent else Cyber.muted,
                            fontFamily = PlexMono,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            fontSize = 12.sp,
                            letterSpacing = 3.sp,
                        )
                    }
                }
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(48.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
            labels.forEachIndexed { index, _ ->
                val selected = index == current
                Box(
                    Modifier
                        .width(32.dp)
                        .height(30.dp)
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(if (selected) 9.dp else 6.dp)
                            .background(if (selected) accent else Cyber.muted)
                    )
                }
            }
        }
        Box(
            Modifier
                .width(48.dp)
                .height(30.dp)
                .clickable { onConfig() },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "⚙", fontSize = 16.sp, color = accent)
        }
    }
}

// ---------------------------------------------------------------------------
// Config page
// ---------------------------------------------------------------------------

@Composable
internal fun riftSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Cyber.stage,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = Cyber.muted,
    uncheckedTrackColor = Cyber.surface2,
    uncheckedBorderColor = Cyber.border,
)

@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
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
internal fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, colors = riftSwitchColors())
    }
}

@Composable
internal fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
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
internal fun ChoiceRow(
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
internal fun PickerRow(
    label: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, modifier = Modifier.weight(1f))
        Box {
            Box(
                Modifier
                    .background(Cyber.surface)
                    .border(1.dp, Cyber.border)
                    .clickable { open = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = (options.firstOrNull { it.first == selectedId }?.second ?: selectedId) + "  ▾",
                    fontFamily = PlexMono,
                    fontSize = 12.sp,
                )
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                modifier = Modifier.background(Cyber.surface2).border(1.dp, Cyber.border),
            ) {
                options.forEach { (id, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            open = false
                            onSelect(id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun RiftButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
internal fun MonoNote(text: String) {
    Text(text = text, color = Cyber.muted, fontFamily = PlexMono, fontSize = 12.sp)
}

@Composable
fun ConfigPage(
    settings: SettingsState,
    apps: List<AppInfo>,
    updates: UpdateController,
    info: InfoController,
    onRequestCalendar: () -> Unit,
    screen: String,
    onScreen: (String) -> Unit,
) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    var confirmReset by remember { mutableStateOf(false) }
    val order = settings.moduleOrder
    val hiddenModules = settings.hiddenModules
    val hiddenApps = apps.filter { it.packageName in settings.hiddenApps }
    LaunchedEffect(Unit) { info.refreshAccess() }
    val parts = screen.split("/").filter { it.isNotEmpty() }
    val top = ConfigTree.firstOrNull { it.id == parts.getOrNull(0) }
    val sub = top?.children?.firstOrNull { it.id == parts.getOrNull(1) }
    val showMenu: List<ConfigNode>? = when {
        top == null -> ConfigTree
        top.children.isNotEmpty() && sub == null -> top.children
        else -> null
    }
    val contentId = if (showMenu != null) "" else (sub?.id ?: top?.id ?: "")
    val heading = (sub ?: top)?.title ?: ""
    val crumb = if (sub != null) top?.title ?: "" else "Settings"

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        if (screen.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onScreen(screen.substringBeforeLast('/', "")) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("‹", color = accent, fontSize = 22.sp, modifier = Modifier.padding(end = 10.dp))
                Column {
                    Kicker(crumb, color = Cyber.muted)
                    Text(heading, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                }
            }
        }
        if (showMenu != null) {
            showMenu.forEach { node ->
                MenuRow(node.title, node.desc) {
                    onScreen(if (screen.isEmpty()) node.id else "$screen/${node.id}")
                }
            }
        }
        when (contentId) {
            "modules" -> {
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
                    label = "Home grid icons per row",
                    options = listOf("3", "4", "5", "6"),
                    selected = settings.int(Keys.QUICK_COLUMNS, 4).coerceIn(3, 6) - 3,
                ) { settings.putInt(Keys.QUICK_COLUMNS, it + 3) }
            }
            }
            "clock" -> {
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
            }
            "strip" -> {
            Section("Top strip") {
            ToggleRow("Show top strip", settings.bool(Keys.STRIP_SHOW, false)) {
                settings.putBool(Keys.STRIP_SHOW, it)
            }
            MonoNote("Off by default: the phone already shows time and battery up top.")
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
            }
            "grid" -> {
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
                ToggleRow("Show page dots and gear", settings.showTabBar) { settings.putBool(Keys.TAB_BAR, it) }
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
            }
            "pins" -> {
            Section("Dock") {
                PinList(settings, apps, settings.dock, { k, d -> settings.moveDock(k, d) }) { settings.toggleDock(it) }
            }
            Section("Quick apps on home") {
                PinList(settings, apps, settings.quick, { k, d -> settings.moveQuick(k, d) }) { settings.toggleQuick(it) }
            }
            }
            "folders" -> {
            Section("Folders") {
                val all = settings.folders
                if (all.isEmpty()) {
                    MonoNote("None yet. Long-press any app and choose Add to folder.")
                }
                all.forEach { f ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name.ifBlank { "Folder" })
                            MonoNote("${f.pkgs.size} apps")
                        }
                        RiftButton("Delete") { settings.deleteFolder(f.id) }
                    }
                }
                MonoNote("Folders can sit in the Apps grid, the dock and the Quick apps block. Open one and use Pin.")
            }
            }
            "weather" -> {
            Section("Information") {
                OutlinedTextField(
                    value = settings.str(Keys.WEATHER_CITY, ""),
                    onValueChange = { settings.putStr(Keys.WEATHER_CITY, it.take(60)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Weather city") },
                    placeholder = { Text("e.g. Chicago") },
                    textStyle = TextStyle(fontFamily = PlexMono, fontSize = 14.sp),
                    singleLine = true,
                    shape = RectangleShape,
                    colors = riftFieldColors(),
                )
                info.weather?.let { MonoNote("Showing ${it.place}") }
                info.weatherError?.let { MonoNote("Weather: $it") }
                ChoiceRow(
                    label = "Temperature",
                    options = listOf("Auto", "°F", "°C"),
                    selected = settings.int(Keys.WEATHER_UNIT, 0).coerceIn(0, 2),
                ) { settings.putInt(Keys.WEATHER_UNIT, it) }
                OutlinedTextField(
                    value = settings.str(Keys.FEED_URL, ""),
                    onValueChange = { settings.putStr(Keys.FEED_URL, it.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Headlines feed (RSS or Atom URL)") },
                    placeholder = { Text(DEFAULT_FEED) },
                    textStyle = TextStyle(fontFamily = PlexMono, fontSize = 12.sp),
                    singleLine = true,
                    shape = RectangleShape,
                    colors = riftFieldColors(),
                )
                MonoNote("Leave the feed empty to use BBC News. Tap the headlines on Brief to expand them.")
            }
            }
            "access" -> {
            Section("Access") {
                MonoNote("RIFT only reads these on your phone. Nothing is uploaded.")
                ToggleStatus("Calendar", info.calendarGranted, "Allow", onRequestCalendar)
                ToggleStatus("Notifications", info.notificationAccess, "Open settings") {
                    safeStart(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                ToggleStatus("Do not disturb control", info.dndAccess, "Open settings") {
                    safeStart(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
                MonoNote(
                    "If the notification switch is greyed out, open App info, tap the three dots " +
                        "and choose Allow restricted settings, then try again."
                )
                RiftButton("Open RIFT app info", Modifier.fillMaxWidth()) {
                    safeStart(
                        context,
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }
            }
            }
            "gestures" -> {
            Section("Gestures") {
                MonoNote("Swipes work on the Brief page once it is scrolled to its end.")
                GestureSlots.forEach { slot ->
                    PickerRow(
                        label = slot.second,
                        options = GestureActions,
                        selectedId = settings.str(slot.first, slot.third),
                    ) { settings.putStr(slot.first, it) }
                }
            }
            }
            "shade" -> {
            Section("Shade") {
                MonoNote("The shade slides over the home screen. Choose how it looks and what it shows.")
                ChoiceRow(
                    label = "Shade background",
                    options = listOf("Light", "Medium", "Solid"),
                    selected = settings.int(Keys.SHADE_ALPHA, 1).coerceIn(0, 2),
                ) { settings.putInt(Keys.SHADE_ALPHA, it) }
                ToggleRow("Quick toggles in shade", settings.bool(Keys.SHADE_TOGGLES, true)) {
                    settings.putBool(Keys.SHADE_TOGGLES, it)
                }
                ToggleRow("Now playing in shade", settings.bool(Keys.SHADE_MEDIA, true)) {
                    settings.putBool(Keys.SHADE_MEDIA, it)
                }
                ToggleRow("Notifications in shade", settings.bool(Keys.SHADE_NOTES, true)) {
                    settings.putBool(Keys.SHADE_NOTES, it)
                }
                ToggleRow("Tools in shade (tasks, timer, calculator)", settings.bool(Keys.SHADE_TOOLS, true)) {
                    settings.putBool(Keys.SHADE_TOOLS, it)
                }
            }
            }
            "stream" -> {
            Section("Edge data stream") {
                ToggleRow("Show data stream", settings.bool(Keys.STREAM, true)) {
                    settings.putBool(Keys.STREAM, it)
                }
                ChoiceRow(
                    label = "Speed",
                    options = listOf("Slow", "Normal", "Fast"),
                    selected = settings.int(Keys.STREAM_SPEED, 1).coerceIn(0, 2),
                ) { settings.putInt(Keys.STREAM_SPEED, it) }
                ChoiceRow(
                    label = "Trail length",
                    options = listOf("Short", "Long", "Max"),
                    selected = settings.int(Keys.STREAM_TRAIL, 1).coerceIn(0, 2),
                ) { settings.putInt(Keys.STREAM_TRAIL, it) }
                ChoiceRow(
                    label = "Density",
                    options = listOf("Low", "Medium", "High"),
                    selected = settings.int(Keys.STREAM_DENSITY, 1).coerceIn(0, 2),
                ) { settings.putInt(Keys.STREAM_DENSITY, it) }
                ChoiceRow(
                    label = "Edges",
                    options = listOf("Both", "Left", "Right"),
                    selected = settings.int(Keys.STREAM_EDGE, 0).coerceIn(0, 2),
                ) { settings.putInt(Keys.STREAM_EDGE, it) }
            }
            }
            "hud" -> {
            Section("Floating HUD over other apps") {
                var canDraw by remember { mutableStateOf(HudControl.canDraw(context)) }
                LaunchedEffect(Unit) {
                    while (true) {
                        canDraw = HudControl.canDraw(context)
                        delay(1_500)
                    }
                }
                ToggleRow("Show floating HUD", settings.bool(Keys.HUD_ON, false)) { on ->
                    settings.putBool(Keys.HUD_ON, on)
                    if (on && !canDraw) {
                        safeStart(
                            context,
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }
                }
                MonoNote(
                    if (canDraw) "Permission to draw over other apps: granted."
                    else "Needs permission to draw over other apps. Switch it on, then allow RIFT in the screen that opens."
                )
                ToggleRow("Time", settings.bool(Keys.HUD_TIME, true)) { settings.putBool(Keys.HUD_TIME, it) }
                ToggleRow("Battery", settings.bool(Keys.HUD_BATTERY, true)) { settings.putBool(Keys.HUD_BATTERY, it) }
                ToggleRow("Weather", settings.bool(Keys.HUD_WEATHER, true)) { settings.putBool(Keys.HUD_WEATHER, it) }
                ToggleRow("Notification count", settings.bool(Keys.HUD_NOTES, true)) {
                    settings.putBool(Keys.HUD_NOTES, it)
                }
                ToggleRow("Hide while RIFT is open", settings.bool(Keys.HUD_HIDE_HOME, true)) {
                    settings.putBool(Keys.HUD_HIDE_HOME, it)
                }
                ChoiceRow(
                    label = "HUD text size",
                    options = listOf("Small", "Medium", "Large"),
                    selected = settings.int(Keys.HUD_SIZE, 1).coerceIn(0, 2),
                ) { settings.putInt(Keys.HUD_SIZE, it) }
                ChoiceRow(
                    label = "HUD background",
                    options = listOf("Light", "Medium", "Solid"),
                    selected = settings.int(Keys.HUD_ALPHA, 1).coerceIn(0, 2),
                ) { settings.putInt(Keys.HUD_ALPHA, it) }
                RiftButton("Reset HUD position", Modifier.fillMaxWidth()) {
                    settings.putInt(Keys.HUD_X, -1)
                    settings.putInt(Keys.HUD_Y, -1)
                    HudControl.apply(context, settings)
                }
                MonoNote("Drag the HUD to move it. Tap it to see notifications, the torch and a way back to RIFT.")
                val hudKey = listOf(
                    Keys.HUD_ON, Keys.HUD_TIME, Keys.HUD_BATTERY, Keys.HUD_WEATHER, Keys.HUD_NOTES,
                    Keys.HUD_HIDE_HOME, Keys.HUD_SIZE, Keys.HUD_ALPHA, Keys.HUD_X, Keys.HUD_Y,
                ).joinToString { settings.str(it, "") + settings.int(it, -2) + settings.bool(it, false) }
                LaunchedEffect(hudKey, canDraw) { HudControl.apply(context, settings) }
            }
            }
            "look" -> {
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
                ChoiceRow(
                label = "Page indicator",
                options = listOf("Dots", "Labelled tabs"),
                selected = settings.int(Keys.TAB_STYLE, 0).coerceIn(0, 1),
            ) { settings.putInt(Keys.TAB_STYLE, it) }
            ToggleRow("Grid background", settings.showGrid) { settings.putBool(Keys.GRID, it) }
                ToggleRow("Scanlines", settings.scanlines) { settings.putBool(Keys.SCANLINES, it) }
                ToggleRow("Clock glow", settings.glow) { settings.putBool(Keys.GLOW, it) }
            }
            }
            "behavior" -> {
            Section("Behavior") {
                ChoiceRow(
                    label = "Open on",
                    options = listOf("Brief", "Home"),
                selected = settings.int(Keys.START_PAGE, 0).coerceIn(0, 1),
                ) { settings.putInt(Keys.START_PAGE, it) }
                ToggleRow(
                    "Remind me to set RIFT as home",
                    settings.bool(Keys.DEFAULT_REMINDER, true),
                ) { settings.putBool(Keys.DEFAULT_REMINDER, it) }
            }
            }
            "updates" -> {
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
            }
            "about" -> {
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
            }
            else -> Unit
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ToggleStatus(label: String, granted: Boolean, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text = label)
            Text(
                text = if (granted) "ON" else "OFF",
                color = if (granted) MaterialTheme.colorScheme.primary else Cyber.muted,
                fontFamily = PlexMono,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
            )
        }
        if (!granted) RiftButton(action, onClick = onClick)
    }
}

// ---------------------------------------------------------------------------
// Config menu structure
// ---------------------------------------------------------------------------

class ConfigNode(
    val id: String,
    val title: String,
    val desc: String,
    val children: List<ConfigNode> = emptyList(),
)

val ConfigTree = listOf(
    ConfigNode(
        "home", "Home screen", "Modules, clock and the top strip",
        listOf(
            ConfigNode("modules", "Brief modules", "What shows, and in which order"),
            ConfigNode("clock", "Clock and status", "Format, size, greeting"),
            ConfigNode("strip", "Top strip", "Time, title and battery"),
        ),
    ),
    ConfigNode(
        "apps", "Apps and dock", "Grid, folders and pinned apps",
        listOf(
            ConfigNode("grid", "Grid and icons", "Columns, sizes, hidden apps"),
            ConfigNode("pins", "Dock and quick apps", "Reorder and remove pinned items"),
            ConfigNode("folders", "Folders", "Manage your folders"),
        ),
    ),
    ConfigNode(
        "info", "Information", "Weather, headlines and permissions",
        listOf(
            ConfigNode("weather", "Weather and headlines", "City, units, news feed"),
            ConfigNode("access", "Permissions", "Calendar, notifications, do not disturb"),
        ),
    ),
    ConfigNode("gestures", "Gestures", "Swipes, long-press and double-tap"),
    ConfigNode(
        "overlays", "Overlays", "Shade, data stream and floating HUD",
        listOf(
            ConfigNode("shade", "Shade", "Swipe-down panel"),
            ConfigNode("stream", "Edge data stream", "The falling text on the edges"),
            ConfigNode("hud", "Floating HUD", "Panel that floats over other apps"),
        ),
    ),
    ConfigNode("look", "Look", "Neon scheme and effects"),
    ConfigNode(
        "system", "System", "Startup, updates and reset",
        listOf(
            ConfigNode("behavior", "Behavior", "Start page and reminders"),
            ConfigNode("updates", "Updates", "Check GitHub for new builds"),
            ConfigNode("about", "About", "Default home app and reset"),
        ),
    ),
)

@Composable
private fun MenuRow(title: String, desc: String, onClick: () -> Unit) {
    Plate(Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(
                    desc,
                    color = Cyber.muted,
                    fontFamily = PlexMono,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
        }
    }
}

/** A reorderable list of dock or home items (apps and folders). */
@Composable
private fun PinList(
    settings: SettingsState,
    apps: List<AppInfo>,
    keys: List<String>,
    onMove: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
) {
    if (keys.isEmpty()) {
        MonoNote("Nothing here yet. Long-press an app and choose Pin.")
        return
    }
    val folders = settings.folders
    keys.forEachIndexed { index, key ->
        val label = if (key.startsWith("folder:")) {
            (folders.firstOrNull { it.id == key.removePrefix("folder:") }?.name ?: "Folder") + "  (folder)"
        } else {
            apps.firstOrNull { it.packageName == key }?.label ?: key
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            MoveButton("▲", index > 0) { onMove(key, -1) }
            Spacer(Modifier.width(6.dp))
            MoveButton("▼", index < keys.lastIndex) { onMove(key, 1) }
            Spacer(Modifier.width(6.dp))
            MoveButton("✕", true) { onRemove(key) }
        }
    }
}

@Composable
internal fun riftFieldColors() = OutlinedTextFieldDefaults.colors(
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
