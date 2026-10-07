package app.rift.launcher

import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.BatteryManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/** Settings that survive restarts. Backed by SharedPreferences. */
class SettingsState(private val prefs: SharedPreferences) {
    var accentIndex by mutableIntStateOf(prefs.getInt(KEY_ACCENT, 0))
        private set
    var columns by mutableIntStateOf(prefs.getInt(KEY_COLUMNS, 4))
        private set
    var showLabels by mutableStateOf(prefs.getBoolean(KEY_LABELS, true))
        private set
    var dock by mutableStateOf(
        (prefs.getString(KEY_DOCK, "") ?: "").split(",").filter { it.isNotBlank() }
    )
        private set

    fun setAccent(index: Int) {
        accentIndex = index
        prefs.edit().putInt(KEY_ACCENT, index).apply()
    }

    fun updateColumns(count: Int) {
        columns = count
        prefs.edit().putInt(KEY_COLUMNS, count).apply()
    }

    fun updateShowLabels(show: Boolean) {
        showLabels = show
        prefs.edit().putBoolean(KEY_LABELS, show).apply()
    }

    /** Pin or unpin an app. The dock holds at most [DOCK_MAX] apps. */
    fun toggleDock(packageName: String) {
        val next = if (packageName in dock) {
            dock - packageName
        } else {
            (dock + packageName).takeLast(DOCK_MAX)
        }
        dock = next
        prefs.edit().putString(KEY_DOCK, next.joinToString(",")).apply()
    }

    companion object {
        const val DOCK_MAX = 5
        private const val KEY_ACCENT = "accent"
        private const val KEY_COLUMNS = "columns"
        private const val KEY_LABELS = "labels"
        private const val KEY_DOCK = "dock"
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
