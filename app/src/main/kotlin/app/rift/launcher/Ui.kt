package app.rift.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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

private val GlassFill = Color.White.copy(alpha = 0.10f)
private val GlassStroke = Color.White.copy(alpha = 0.14f)
private val MutedText = Color.White.copy(alpha = 0.70f)

private fun safeStart(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        // The target screen does not exist on this phone. Nothing sensible to do.
    }
}

// ---------------------------------------------------------------------------
// Root: two pages (Brief, Apps), a dock, and a labelled tab bar.
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
            .background(
                Brush.verticalGradient(listOf(Color(0x66000000), Color(0xB3000000)))
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                if (page == PAGE_BRIEF) {
                    BriefPage(
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
    }

    if (showSettings) {
        SettingsSheet(settings = settings, onDismiss = { showSettings = false })
    }
}

// ---------------------------------------------------------------------------
// Brief page: clock, date, a few glanceable cards.
// ---------------------------------------------------------------------------

@Composable
fun BriefPage(onOpenApps: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
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
    val dateText = now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
    val greeting = when {
        now.hour < 5 -> "Up late"
        now.hour < 12 -> "Good morning"
        now.hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(timeText, fontSize = 88.sp, fontWeight = FontWeight.Light)
        Text(dateText, fontSize = 18.sp, color = MutedText)
        Spacer(Modifier.height(8.dp))

        if (!isDefault) {
            GlassCard(
                modifier = Modifier.clickable {
                    safeStart(context, Intent(Settings.ACTION_HOME_SETTINGS))
                },
                accentBorder = true,
            ) {
                Text("Make RIFT your home screen", fontWeight = FontWeight.SemiBold)
                Text(
                    "Tap here, then pick RIFT under Home app.",
                    fontSize = 14.sp,
                    color = MutedText,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        GlassCard {
            Text(greeting, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            val batteryText = "Battery ${battery.first}%" + if (battery.second) " (charging)" else ""
            Text(batteryText, color = MutedText, modifier = Modifier.padding(top = 4.dp))
            Text(
                text = alarm?.let { "Next alarm: $it" } ?: "No alarm set",
                color = MutedText,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        GlassCard(modifier = Modifier.clickable { onOpenApps() }) {
            Text("All apps", fontWeight = FontWeight.SemiBold)
            Text(
                "Tap here, or swipe left, to see everything installed.",
                fontSize = 14.sp,
                color = MutedText,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        GlassCard(modifier = Modifier.clickable { onOpenSettings() }) {
            Text("Settings", fontWeight = FontWeight.SemiBold)
            Text(
                "Accent color, grid size, labels, dock.",
                fontSize = 14.sp,
                color = MutedText,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
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
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search apps", color = MutedText) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = GlassStroke,
                    focusedContainerColor = GlassFill,
                    unfocusedContainerColor = GlassFill,
                    cursorColor = accent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { filtered.firstOrNull()?.let { onLaunch(it) } }
                ),
            )
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(GlassFill)
                    .clickable { onOpenSettings() },
                contentAlignment = Alignment.Center,
            ) {
                Text("⚙", fontSize = 22.sp)
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (apps.isEmpty()) "Loading apps…" else "No apps match \"${query.trim()}\"",
                    color = MutedText,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.columns),
                contentPadding = PaddingValues(vertical = 16.dp),
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
// Pieces
// ---------------------------------------------------------------------------

/** One app icon. Tap launches it; long-press opens the actions menu. */
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
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = { onLaunch(app) },
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier.size(56.dp),
            )
            if (showLabel) {
                Text(
                    text = app.label,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, GlassStroke),
    ) {
        if (dockApps.isEmpty()) {
            Text(
                text = "Long-press any app to pin it here",
                fontSize = 13.sp,
                color = MutedText,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 26.dp),
            )
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 4.dp),
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
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        listOf("Brief", "Apps").forEachIndexed { index, label ->
            val selected = index == current
            Box(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (selected) accent.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 32.dp, vertical = 14.dp),
            ) {
                Text(
                    text = label,
                    color = if (selected) accent else MutedText,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accentBorder: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val stroke = if (accentBorder) MaterialTheme.colorScheme.primary else GlassStroke
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = GlassFill,
        border = BorderStroke(1.dp, stroke),
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(settings: SettingsState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SheetColor) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Accent color", color = MutedText)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Accents.forEachIndexed { index, color ->
                        val selected = index == settings.accentIndex
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (selected) Modifier.border(3.dp, Color.White, CircleShape)
                                    else Modifier
                                )
                                .clickable { settings.setAccent(index) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Apps per row", color = MutedText)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(3, 4, 5).forEach { count ->
                        val selected = count == settings.columns
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selected) accent else GlassFill)
                                .clickable { settings.setColumns(count) }
                                .padding(horizontal = 22.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = "$count",
                                color = if (selected) Color.Black else Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show app names", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.showLabels,
                    onCheckedChange = { settings.setShowLabels(it) },
                )
            }

            Button(
                onClick = { safeStart(context, Intent(Settings.ACTION_HOME_SETTINGS)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Choose default home app")
            }
        }
    }
}
