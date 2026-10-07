package app.rift.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf

class MainActivity : ComponentActivity() {

    // Bumped every time the Home button is pressed while we are already open.
    private val homeSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always light status/navigation icons: the stage is always dark.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val settings = SettingsState(getSharedPreferences("rift", MODE_PRIVATE))
        setContent {
            RiftTheme(accent = Accents[settings.accentIndex.coerceIn(Accents.indices)]) {
                LauncherRoot(settings = settings, homeSignal = homeSignal.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeSignal.intValue++
        }
    }
}
