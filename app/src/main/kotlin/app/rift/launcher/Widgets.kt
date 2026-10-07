package app.rift.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog

/**
 * Hosts real Android app widgets. Widgets are remembered by id in settings. Binding and
 * configuration go through Android's own permission and setup screens, so results arrive in the
 * activity and are forwarded to [onResult].
 */
class WidgetHostController(
    private val activity: Activity,
    private val settings: SettingsState,
) {
    private val appContext = activity.applicationContext
    val host = AppWidgetHost(appContext, HOST_ID)
    val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    private var pendingId = -1

    fun start() {
        try {
            host.startListening()
        } catch (_: Exception) {
            // Some phones refuse; widgets still draw their last state.
        }
    }

    fun stop() {
        try {
            host.stopListening()
        } catch (_: Exception) {
            // Already stopped.
        }
    }

    val ids: List<Int> get() = settings.list(Keys.WIDGETS).mapNotNull { it.toIntOrNull() }

    fun heightDp(id: Int): Int = settings.int("wh_$id", 160).coerceIn(60, 600)

    fun resize(id: Int, deltaDp: Int) = settings.putInt("wh_$id", (heightDp(id) + deltaDp).coerceIn(60, 600))

    fun providers(): List<AppWidgetProviderInfo> =
        try {
            manager.installedProviders.sortedBy { it.loadLabel(appContext.packageManager).lowercase() }
        } catch (_: Exception) {
            emptyList()
        }

    fun appLabel(info: AppWidgetProviderInfo): String =
        try {
            val pm = appContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
        } catch (_: Exception) {
            info.provider.packageName
        }

    /** Start adding a widget: bind it (asking the user if needed), configure it, then show it. */
    fun begin(info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pendingId = id
        val allowed = try {
            manager.bindAppWidgetIdIfAllowed(id, info.provider)
        } catch (_: Exception) {
            false
        }
        if (allowed) {
            afterBind(id)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            try {
                @Suppress("DEPRECATION")
                activity.startActivityForResult(intent, REQ_BIND)
            } catch (_: Exception) {
                cancelPending()
            }
        }
    }

    private fun afterBind(id: Int) {
        val info = manager.getAppWidgetInfo(id)
        if (info?.configure != null) {
            try {
                host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQ_CONFIGURE, null)
            } catch (_: Exception) {
                // The setup screen could not open; keep the widget with its defaults.
                commit(id)
            }
        } else {
            commit(id)
        }
    }

    private fun commit(id: Int) {
        settings.putList(Keys.WIDGETS, settings.list(Keys.WIDGETS) + id.toString())
        pendingId = -1
    }

    private fun cancelPending() {
        if (pendingId >= 0) {
            try {
                host.deleteAppWidgetId(pendingId)
            } catch (_: Exception) {
                // Nothing to clean up.
            }
        }
        pendingId = -1
    }

    fun onResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQ_BIND && requestCode != REQ_CONFIGURE) return
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId) ?: pendingId
        if (resultCode != Activity.RESULT_OK || id < 0) {
            cancelPending()
            return
        }
        pendingId = id
        if (requestCode == REQ_BIND) afterBind(id) else commit(id)
    }

    fun remove(id: Int) {
        try {
            host.deleteAppWidgetId(id)
        } catch (_: Exception) {
            // Ignore.
        }
        settings.putList(Keys.WIDGETS, settings.list(Keys.WIDGETS) - id.toString())
    }

    fun createView(context: Context, id: Int): AppWidgetHostView? {
        val info = manager.getAppWidgetInfo(id) ?: return null
        return try {
            host.createView(context, id, info).also { it.setAppWidget(id, info) }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val HOST_ID = 1024
        const val REQ_BIND = 7101
        const val REQ_CONFIGURE = 7102
    }
}

val LocalWidgets = staticCompositionLocalOf<WidgetHostController?> { null }

/** The Brief-page block that shows the hosted widgets and lets you add, resize and remove them. */
@Composable
fun WidgetsModule() {
    val controller = LocalWidgets.current
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    if (controller == null) return
    val ids = controller.ids

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Kicker("Widgets · ${ids.size}", modifier = Modifier.weight(1f), color = Cyber.muted)
            if (ids.isNotEmpty()) {
                Text(
                    text = if (editing) "DONE" else "EDIT",
                    fontFamily = PlexMono,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { editing = !editing }.padding(8.dp),
                )
            }
            Text(
                text = "+ ADD",
                fontFamily = PlexMono,
                fontSize = 12.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { picking = true }.padding(8.dp),
            )
        }
        if (ids.isEmpty()) {
            Plate {
                MonoNote("No widgets yet. Tap + ADD to put any Android widget on your home screen.")
            }
        }
        ids.forEach { id ->
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val width = maxWidth.value.toInt()
                val height = controller.heightDp(id)
                val view = remember(id) { controller.createView(context, id) }
                if (view == null) {
                    Plate {
                        MonoNote("This widget is no longer available.")
                        RiftButton("Remove") { controller.remove(id) }
                    }
                } else {
                    Column {
                        AndroidView(
                            factory = { view },
                            update = { it.updateAppWidgetSize(null, width, height, width, height) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(height.dp)
                                .background(Cyber.surface)
                                .border(1.dp, Cyber.border),
                        )
                        if (editing) {
                            Row(
                                Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Chip("Shorter", false) { controller.resize(id, -40) }
                                Chip("Taller", false) { controller.resize(id, 40) }
                                Chip("Remove", false) { controller.remove(id) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (picking) {
        WidgetPicker(controller) { picked ->
            picking = false
            if (picked != null) controller.begin(picked)
        }
    }
}

@Composable
private fun WidgetPicker(controller: WidgetHostController, onDone: (AppWidgetProviderInfo?) -> Unit) {
    val context = LocalContext.current
    val providers = remember { controller.providers() }
    val pm = context.packageManager
    Dialog(onDismissRequest = { onDone(null) }) {
        Column(
            Modifier
                .fillMaxWidth()
                .plate()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Kicker("Choose a widget")
            if (providers.isEmpty()) MonoNote("No widgets found on this phone.")
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                providers.forEach { info ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onDone(info) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(text = info.loadLabel(pm))
                        MonoNote(controller.appLabel(info))
                    }
                }
            }
            RiftButton("Cancel") { onDone(null) }
        }
    }
}
