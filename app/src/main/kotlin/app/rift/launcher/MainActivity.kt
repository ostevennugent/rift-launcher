package app.rift.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    // Bumped every time Home is pressed while we are already open.
    private val homeSignal = mutableIntStateOf(0)

    // Bumped when the "Config" app shortcut is used.
    private val configSignal = mutableIntStateOf(0)

    // Bumped whenever the launcher comes back to the front, to refresh information.
    private val resumeSignal = mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeSignal.intValue++
    }

    private var widgets: WidgetHostController? = null

    override fun onStart() {
        super.onStart()
        widgets?.start()
    }

    override fun onStop() {
        widgets?.stop()
        super.onStop()
    }

    @Deprecated("Widget setup results arrive here.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        widgets?.onResult(requestCode, resultCode, data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        val crash = CrashLog.read(this)
        // Always light status/navigation icons: the stage is always dark.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val settings = SettingsState(getSharedPreferences("rift", MODE_PRIVATE))
        // A fresh start never counts as a Home press, so the chosen start page is respected.
        if (intent?.action == ACTION_OPEN_CONFIG) configSignal.intValue++
        val widgetController = WidgetHostController(this, settings)
        widgets = widgetController
        widgetController.start()
        setContent {
            var report by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(crash)
            }
            RiftTheme(accent = Accents[settings.accentIndex]) {
                val shown = report
                if (shown != null) {
                    CrashScreen(shown) {
                        CrashLog.clear(this@MainActivity)
                        report = null
                    }
                } else androidx.compose.runtime.CompositionLocalProvider(LocalWidgets provides widgetController) {
                    LauncherRoot(
                        settings = settings,
                        homeSignal = homeSignal.intValue,
                        configSignal = configSignal.intValue,
                        resumeSignal = resumeSignal.intValue,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        if (intent.action == ACTION_OPEN_CONFIG) {
            configSignal.intValue++
        } else if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeSignal.intValue++
        }
    }

    companion object {
        const val ACTION_OPEN_CONFIG = "app.rift.launcher.OPEN_CONFIG"
    }
}
