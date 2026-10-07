package app.rift.launcher

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** A named group of apps. Apps are stored by package name. */
data class Folder(val id: String, val name: String, val pkgs: List<String>)

private val FolderBad = Regex("[|:;,]")

fun cleanFolderName(raw: String, allowEmpty: Boolean = false): String {
    val cleaned = raw.replace(FolderBad, " ").take(18)
    return if (allowEmpty) cleaned else cleaned.trim().ifEmpty { "Folder" }
}

/** Stored as "id:name:pkg;pkg|id:name:pkg". Names never contain the separators. */
fun parseFolders(raw: String): List<Folder> =
    raw.split("|").mapNotNull { part ->
        val bits = part.split(":")
        if (bits.size < 3 || bits[0].isBlank()) null
        else Folder(bits[0], bits[1], bits[2].split(";").filter { it.isNotBlank() })
    }

fun encodeFolders(list: List<Folder>): String =
    list.joinToString("|") { "${it.id}:${it.name}:${it.pkgs.joinToString(";")}" }

/** One thing in a grid, dock or quick row: an app or a folder. */
sealed class Entry {
    abstract val key: String
    data class AppEntry(val app: AppInfo) : Entry() {
        override val key get() = "app:" + app.packageName
    }
    data class FolderEntry(val folder: Folder) : Entry() {
        override val key get() = "folder:" + folder.id
    }
}

/** Turns stored keys ("com.pkg" or "folder:id") into entries, skipping anything that is gone. */
fun resolveEntries(keys: List<String>, apps: List<AppInfo>, folders: List<Folder>): List<Entry> =
    keys.mapNotNull { k ->
        if (k.startsWith("folder:")) {
            folders.firstOrNull { it.id == k.removePrefix("folder:") }?.let { Entry.FolderEntry(it) }
        } else {
            apps.firstOrNull { it.packageName == k }?.let { Entry.AppEntry(it) }
        }
    }

/** How apps and folders ask the root to open a folder or the "add to folder" picker. */
class FolderActions(val open: (String) -> Unit, val addTo: (AppInfo) -> Unit)

val LocalFolderActions = compositionLocalOf { FolderActions({}, {}) }

/** An app or a folder, drawn the same size. */
@Composable
fun EntryIcon(
    entry: Entry,
    apps: List<AppInfo>,
    settings: SettingsState,
    onLaunch: (AppInfo) -> Unit,
    showLabel: Boolean,
    iconDp: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    when (entry) {
        is Entry.AppEntry -> AppIcon(entry.app, settings, onLaunch, showLabel, iconDp, modifier, compact)
        is Entry.FolderEntry -> FolderIcon(entry.folder, apps, settings, showLabel, iconDp, modifier, compact)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderIcon(
    folder: Folder,
    apps: List<AppInfo>,
    settings: SettingsState,
    showLabel: Boolean,
    iconDp: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val actions = LocalFolderActions.current
    val accent = MaterialTheme.colorScheme.primary
    val inside = folder.pkgs.mapNotNull { p -> apps.firstOrNull { it.packageName == p } }
    val mini = (iconDp / 2.2f).dp
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .combinedClickable(onClick = { actions.open(folder.id) }, onLongClick = { actions.open(folder.id) })
                .padding(horizontal = if (compact) 2.dp else 4.dp, vertical = if (compact) 2.dp else 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size((iconDp + if (compact) 8 else 16).dp)
                    .background(Cyber.surface2)
                    .border(1.dp, accent.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (r in 0..1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (c in 0..1) {
                                val app = inside.getOrNull(r * 2 + c)
                                if (app != null) {
                                    Image(app.icon, app.label, Modifier.size(mini))
                                } else {
                                    Spacer(Modifier.size(mini))
                                }
                            }
                        }
                    }
                }
            }
            if (showLabel) {
                Text(
                    text = folder.name.ifBlank { "Folder" },
                    color = accent,
                    fontFamily = PlexMono,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** The inside of a folder: rename it, launch its apps, pin it, or delete it. */
@Composable
fun FolderDialog(
    folderId: String,
    apps: List<AppInfo>,
    settings: SettingsState,
    onLaunch: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val folder = settings.folderById(folderId)
    if (folder == null) {
        onDismiss()
        return
    }
    val inside = folder.pkgs.mapNotNull { p -> apps.firstOrNull { it.packageName == p } }
        .filter { it.packageName !in settings.hiddenApps }
    val columns = settings.columns.coerceAtMost(4)
    val key = "folder:$folderId"
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .plate()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Kicker("Folder")
            OutlinedTextField(
                value = folder.name,
                onValueChange = { settings.renameFolder(folderId, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                textStyle = TextStyle(fontFamily = PlexMono, fontSize = 14.sp),
                singleLine = true,
                shape = RectangleShape,
                colors = riftFieldColors(),
            )
            Column(
                Modifier
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (inside.isEmpty()) {
                    MonoNote("Empty. Long-press any app and choose Add to folder.")
                }
                inside.chunked(columns).forEach { rowApps ->
                    Row(Modifier.fillMaxWidth()) {
                        rowApps.forEach { app ->
                            AppIcon(
                                app = app,
                                settings = settings,
                                onLaunch = {
                                    onDismiss()
                                    onLaunch(it)
                                },
                                showLabel = settings.showLabels,
                                iconDp = settings.iconDp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columns - rowApps.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(if (key in settings.dock) "On dock" else "Pin to dock", key in settings.dock) {
                    settings.toggleDock(key)
                }
                Chip(if (key in settings.quick) "On home" else "Pin to home", key in settings.quick) {
                    settings.toggleQuick(key)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RiftButton("Delete folder") {
                    settings.deleteFolder(folderId)
                    onDismiss()
                }
                RiftButton("Close") { onDismiss() }
            }
        }
    }
}

/** Pick a folder for an app, or make a new one. */
@Composable
fun AddToFolderDialog(app: AppInfo, settings: SettingsState, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val folders = settings.folders
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .plate()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Kicker("Add ${app.label} to folder")
            if (folders.isNotEmpty()) {
                Column(
                    Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    folders.forEach { f ->
                        Chip(f.name.ifBlank { "Folder" } + " · ${f.pkgs.size}", app.packageName in f.pkgs) {
                            settings.moveToFolder(app.packageName, f.id)
                            onDismiss()
                        }
                    }
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(18) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("New folder name") },
                textStyle = TextStyle(fontFamily = PlexMono, fontSize = 14.sp),
                singleLine = true,
                shape = RectangleShape,
                colors = riftFieldColors(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RiftButton("Create") {
                    settings.createFolder(name, app.packageName)
                    onDismiss()
                }
                RiftButton("Cancel") { onDismiss() }
            }
        }
    }
}
