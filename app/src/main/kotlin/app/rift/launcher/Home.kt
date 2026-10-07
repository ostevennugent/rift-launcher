package app.rift.launcher

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The home grid: widgets on top, then the apps and folders you pinned to home. */
@Composable
fun HomePage(
    settings: SettingsState,
    apps: List<AppInfo>,
    edge: NestedScrollConnection,
    onLongPress: () -> Unit,
    onDoubleTap: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
) {
    val columns = settings.int(Keys.QUICK_COLUMNS, 4).coerceIn(3, 6)
    val entries = resolveEntries(settings.quick, apps, settings.folders)
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() }, onDoubleTap = { onDoubleTap() })
            }
            .nestedScroll(edge)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        WidgetsModule()
        if (entries.isEmpty()) {
            Plate {
                Kicker("Home grid")
                MonoNote("Empty. Swipe up for all apps, long-press one and choose Pin to home.")
            }
        } else {
            entries.chunked(columns).forEach { rowEntries ->
                Row(Modifier.fillMaxWidth()) {
                    rowEntries.forEach { entry ->
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
                    repeat(columns - rowEntries.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.padding(bottom = 8.dp))
    }
}

/**
 * All apps, sliding up over the home screen. Search sits at the bottom where a thumb reaches it.
 * Swipe down at the top of the list, tap Close, or press Back to dismiss.
 */
@Composable
fun AppDrawer(
    open: Boolean,
    apps: List<AppInfo>,
    settings: SettingsState,
    autoFocus: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(enabled = open) { onClose() }
    AnimatedVisibility(
        visible = open,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        DrawerContent(apps, settings, autoFocus, onLaunch, onClose)
    }
}

@Composable
private fun DrawerContent(
    apps: List<AppInfo>,
    settings: SettingsState,
    autoFocus: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (autoFocus) {
            try {
                searchFocus.requestFocus()
            } catch (_: Exception) {
                // Field not ready yet.
            }
        }
    }
    val closeNow by rememberUpdatedState(onClose)
    val threshold = with(LocalDensity.current) { 70.dp.toPx() }
    val edge = remember(threshold) { EdgeSwipe(threshold, onDown = { closeNow() }, onUp = null) }
    val launch: (AppInfo) -> Unit = {
        onClose()
        onLaunch(it)
    }
    val filtered = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }
    val folders = settings.folders
    val entries = remember(filtered, query, folders) {
        if (query.isBlank()) {
            val packed = folders.flatMap { it.pkgs }.toSet()
            folders.map { Entry.FolderEntry(it) as Entry } +
                filtered.filter { it.packageName !in packed }.map { Entry.AppEntry(it) }
        } else {
            filtered.map { Entry.AppEntry(it) }
        }
    }
    val accent = androidx.compose.material3.MaterialTheme.colorScheme.primary

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05070A).copy(alpha = 0.97f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp),
    ) {
        // Grab bar and title: drag down anywhere on this strip to close the drawer.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .swipeDismiss(up = false, thresholdPx = threshold * 0.6f, onClose = { closeNow() }),
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .width(44.dp)
                    .height(4.dp)
                    .background(Cyber.muted)
            )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Kicker("Apps · ${filtered.size}", modifier = Modifier.weight(1f), color = Cyber.muted)
            Text(
                text = "CLOSE ▼",
                fontFamily = PlexMono,
                fontSize = 12.sp,
                color = accent,
                modifier = Modifier
                    .clickable { onClose() }
                    .padding(8.dp),
            )
        }
        }
        if (entries.isEmpty()) {
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
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .nestedScroll(edge),
            ) {
                items(entries, key = { it.key }) { entry ->
                    EntryIcon(
                        entry = entry,
                        apps = apps,
                        settings = settings,
                        onLaunch = launch,
                        showLabel = settings.showLabels,
                        iconDp = settings.iconDp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .focusRequester(searchFocus),
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
            keyboardActions = KeyboardActions(onSearch = { filtered.firstOrNull()?.let { launch(it) } }),
        )
    }
}
