package app.rift.launcher

import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.BatteryManager
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One launchable app. */
data class AppInfo(
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val icon: ImageBitmap,
)

/** Every app with a launcher icon, A-Z, excluding this launcher itself. */
fun loadApps(pm: PackageManager, selfPackage: String): List<AppInfo> {
    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(query, 0)
        .filter { it.activityInfo.packageName != selfPackage }
        .map { ri ->
            AppInfo(
                label = ri.loadLabel(pm).toString(),
                packageName = ri.activityInfo.packageName,
                component = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name),
                icon = ri.loadIcon(pm).toBitmap(144, 144).asImageBitmap(),
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

/** Names of the stored settings. */
object Keys {
    const val ACCENT = "accent"
    const val COLUMNS = "columns"
    const val LABELS = "labels"
    const val SCANLINES = "scanlines"
    const val GLOW = "glow"
    const val GRID = "grid"
    const val DOCK = "dock"
    const val DOCK_SHOW = "dock_show"
    const val TAB_BAR = "tab_bar"
    const val STRIP_TIME = "strip_time"
    const val STRIP_TITLE = "strip_title"
    const val STRIP_BATTERY = "strip_battery"
    const val TITLE_TEXT = "title_text"
    const val CLOCK_MODE = "clock_mode"
    const val CLOCK_SECONDS = "clock_seconds"
    const val CLOCK_SIZE = "clock_size"
    const val ST_GREETING = "st_greeting"
    const val ST_BATTERY = "st_battery"
    const val ST_ALARM = "st_alarm"
    const val DEFAULT_REMINDER = "default_reminder"
    const val ICON_SIZE = "icon_size"
    const val START_PAGE = "start_page"
    const val ORDER = "module_order"
    const val HIDDEN_MODULES = "module_hidden"
    const val QUICK = "quick"
    const val QUICK_COLUMNS = "quick_columns"
    const val HIDDEN_APPS = "hidden_apps"
    const val AUTO_UPDATE = "auto_update"
    const val LAST_CHECK = "last_check"
    const val WEATHER_CITY = "weather_city"
    const val WEATHER_UNIT = "weather_unit"
    const val FEED_URL = "feed_url"
    const val GEO_QUERY = "geo_query"
    const val GEO_LAT = "geo_lat"
    const val GEO_LON = "geo_lon"
    const val GEO_LABEL = "geo_label"
    const val TASKS = "tasks"
    const val NOTES = "notes"
    const val STREAM = "stream"
    const val STREAM_TRAIL = "stream_trail"
    const val STREAM_SPEED = "stream_speed"
    const val STREAM_DENSITY = "stream_density"
    const val STREAM_EDGE = "stream_edge"
    const val SHADE_ALPHA = "shade_alpha"
    const val SHADE_TOGGLES = "shade_toggles"
    const val SHADE_MEDIA = "shade_media"
    const val SHADE_NOTES = "shade_notes"
    const val FOLDERS = "folders"
    const val WIDGETS = "widgets"
    const val G_DOWN = "g_down"
    const val G_UP = "g_up"
    const val G_LONG = "g_long"
    const val G_DOUBLE = "g_double"
}

/** The blocks that can appear on the Brief page: id to display name. */
val BriefModules = listOf(
    "clock" to "Clock",
    "date" to "Date",
    "weather" to "Weather",
    "agenda" to "Calendar agenda",
    "comms" to "Notifications",
    "media" to "Now playing",
    "headlines" to "Headlines",
    "vitals" to "Device vitals",
    "widgets" to "Widgets",
    "status" to "Status plate",
    "quick" to "Quick apps",
)

/**
 * Every launcher setting, stored in SharedPreferences and observable by Compose.
 * Reads return the default until a value has been saved.
 */
class SettingsState(private val prefs: SharedPreferences) {
    private val values = mutableStateMapOf<String, Any>().also { map ->
        prefs.all.forEach { (key, value) ->
            if (value != null) map[key] = value
        }
    }

    fun bool(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
    fun int(key: String, default: Int): Int = values[key] as? Int ?: default
    fun long(key: String, default: Long): Long = values[key] as? Long ?: default
    fun str(key: String, default: String): String = values[key] as? String ?: default

    fun list(key: String): List<String> =
        (values[key] as? String)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

    fun putBool(key: String, value: Boolean) {
        values[key] = value
        prefs.edit().putBoolean(key, value).apply()
    }

    fun putInt(key: String, value: Int) {
        values[key] = value
        prefs.edit().putInt(key, value).apply()
    }

    fun putLong(key: String, value: Long) {
        values[key] = value
        prefs.edit().putLong(key, value).apply()
    }

    fun putStr(key: String, value: String) {
        values[key] = value
        prefs.edit().putString(key, value).apply()
    }

    fun putList(key: String, value: List<String>) = putStr(key, value.joinToString(","))

    /** Forget everything and go back to the defaults. */
    fun reset() {
        values.clear()
        prefs.edit().clear().apply()
    }

    // --- Convenience reads -------------------------------------------------

    val accentIndex: Int get() = int(Keys.ACCENT, 0).coerceIn(Accents.indices)
    val columns: Int get() = int(Keys.COLUMNS, 4).coerceIn(3, 6)
    val showLabels: Boolean get() = bool(Keys.LABELS, true)
    val scanlines: Boolean get() = bool(Keys.SCANLINES, true)
    val glow: Boolean get() = bool(Keys.GLOW, true)
    val showGrid: Boolean get() = bool(Keys.GRID, true)
    val showDock: Boolean get() = bool(Keys.DOCK_SHOW, true)
    val showTabBar: Boolean get() = bool(Keys.TAB_BAR, true)
    val dock: List<String> get() = list(Keys.DOCK)
    val quick: List<String> get() = list(Keys.QUICK)
    val hiddenApps: List<String> get() = list(Keys.HIDDEN_APPS)
    val hiddenModules: List<String> get() = list(Keys.HIDDEN_MODULES)
    val autoUpdate: Boolean get() = bool(Keys.AUTO_UPDATE, true)

    /** Icon size in dp: small, medium or large. */
    val iconDp: Int get() = listOf(40, 48, 56)[int(Keys.ICON_SIZE, 1).coerceIn(0, 2)]

    /** Brief-page modules in the user's order, including any added in later versions. */
    val moduleOrder: List<String>
        get() {
            val known = BriefModules.map { it.first }
            val saved = list(Keys.ORDER).filter { it in known }
            return saved + known.filter { it !in saved }
        }

    // --- Folders -----------------------------------------------------------

    val folders: List<Folder> get() = parseFolders(str(Keys.FOLDERS, ""))

    private fun saveFolders(list: List<Folder>) = putStr(Keys.FOLDERS, encodeFolders(list))

    fun folderOf(pkg: String): Folder? = folders.firstOrNull { pkg in it.pkgs }

    fun folderById(id: String): Folder? = folders.firstOrNull { it.id == id }

    /** Makes a folder, optionally with a first app in it, and returns its id. */
    fun createFolder(name: String, firstPkg: String?): String {
        val id = System.currentTimeMillis().toString(36)
        val cleaned = cleanFolderName(name)
        val without = folders.map { f -> if (firstPkg != null) f.copy(pkgs = f.pkgs - firstPkg) else f }
        saveFolders(without + Folder(id, cleaned, listOfNotNull(firstPkg)))
        return id
    }

    /** An app lives in at most one folder, so adding it here takes it out of any other. */
    fun moveToFolder(pkg: String, folderId: String) {
        saveFolders(
            folders.map { f ->
                when {
                    f.id == folderId -> f.copy(pkgs = (f.pkgs - pkg) + pkg)
                    else -> f.copy(pkgs = f.pkgs - pkg)
                }
            }
        )
    }

    fun removeFromFolder(pkg: String) {
        saveFolders(folders.map { it.copy(pkgs = it.pkgs - pkg) })
    }

    fun renameFolder(id: String, name: String) {
        saveFolders(folders.map { if (it.id == id) it.copy(name = cleanFolderName(name, allowEmpty = true)) else it })
    }

    fun deleteFolder(id: String) {
        saveFolders(folders.filter { it.id != id })
        putList(Keys.DOCK, dock - "folder:$id")
        putList(Keys.QUICK, quick - "folder:$id")
    }

    // --- Changes -----------------------------------------------------------

    fun toggleDock(packageName: String) {
        val current = dock
        putList(
            Keys.DOCK,
            if (packageName in current) current - packageName
            else (current + packageName).takeLast(DOCK_MAX),
        )
    }

    fun toggleQuick(packageName: String) {
        val current = quick
        putList(Keys.QUICK, if (packageName in current) current - packageName else current + packageName)
    }

    fun toggleHiddenApp(packageName: String) {
        val current = hiddenApps
        putList(
            Keys.HIDDEN_APPS,
            if (packageName in current) current - packageName else current + packageName,
        )
    }

    fun toggleModuleHidden(id: String) {
        val current = hiddenModules
        putList(Keys.HIDDEN_MODULES, if (id in current) current - id else current + id)
    }

    fun moveModule(id: String, delta: Int) {
        val order = moduleOrder.toMutableList()
        val from = order.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in order.indices) return
        order.removeAt(from)
        order.add(to, id)
        putList(Keys.ORDER, order)
    }

    companion object {
        const val DOCK_MAX = 5
    }
}

/** Battery percent and whether it is charging. */
fun readBattery(context: Context): Pair<Int, Boolean> {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?: return 0 to false
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val percent = if (level >= 0 && scale > 0) level * 100 / scale else 0
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
    return percent to charging
}

/** Next alarm as text like "Thu 6:30 AM", or null when none is set. */
fun readNextAlarm(context: Context): String? {
    val manager = context.getSystemService(AlarmManager::class.java) ?: return null
    val trigger = manager.nextAlarmClock?.triggerTime ?: return null
    return Instant.ofEpochMilli(trigger)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE h:mm a"))
}

/** True when this app is the phone's current default home app. */
fun isDefaultHome(context: Context): Boolean {
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
    return resolved?.activityInfo?.packageName == context.packageName
}
