package app.rift.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always light status/navigation icons: the stage is always dark.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val settings = SettingsState(getSharedPreferences("rift", MODE_PRIVATE))
        // A fresh start never counts as a Home press, so the chosen start page is respected.
        if (intent?.action == ACTION_OPEN_CONFIG) configSignal.intValue++
        setContent {
            RiftTheme(accent = Accents[settings.accentIndex]) {
                LauncherRoot(
                    settings = settings,
                    homeSignal = homeSignal.intValue,
                    configSignal = configSignal.intValue,
                    resumeSignal = resumeSignal.intValue,
                )
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
