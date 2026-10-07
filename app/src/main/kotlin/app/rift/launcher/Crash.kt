package app.rift.launcher

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** Saves the stack trace of a crash so the next start can show it instead of failing silently. */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                file(app).writeText(
                    "RIFT ${BuildConfig.VERSION_NAME} ${BuildConfig.GIT_SHA.take(7)}\n" +
                        "thread ${thread.name}\n" + error.stackTraceToString().take(6000)
                )
            } catch (_: Exception) {
                // Nothing more we can do.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        try {
            file(context).takeIf { it.exists() }?.readText()
        } catch (_: Exception) {
            null
        }

    fun clear(context: Context) {
        try {
            file(context).delete()
        } catch (_: Exception) {
            // Ignore.
        }
    }
}

@Composable
fun CrashScreen(report: String, onContinue: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Cyber.stage)
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Kicker("RIFT crashed last time")
        Text("Tap Copy and send this to Claude so it can be fixed.", color = Cyber.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RiftButton("Copy") { clipboard.setText(AnnotatedString(report)) }
            RiftButton("Continue") { onContinue() }
        }
        SelectionContainer(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(report, fontFamily = PlexMono, fontSize = 10.sp, color = Cyber.fg)
        }
    }
}
