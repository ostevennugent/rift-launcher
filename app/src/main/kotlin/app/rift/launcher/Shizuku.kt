package app.rift.launcher

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** Runs shell commands through Shizuku and drives the system status bar with them. */
object ShizukuBridge {
    private const val REQ = 4417

    /** Flags `cmd statusbar send-disable-flag` may accept, per mode. Unknown ones are skipped. */
    private val CLEAN = listOf("clock", "system-icons", "notification-icons")
    private val LOCK = listOf("statusbar-expansion", "quick-settings")

    fun running(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    fun granted(): Boolean = try {
        running() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    fun request() {
        try {
            if (running() && !Shizuku.isPreV11()) Shizuku.requestPermission(REQ)
        } catch (_: Throwable) {
        }
    }

    /** Runs a shell command as the shell user. Returns combined output, or an error line. */
    suspend fun exec(cmd: String): String = withContext(Dispatchers.IO) {
        try {
            if (!granted()) return@withContext "ERR: Shizuku not ready"
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java, Array<String>::class.java, String::class.java,
            )
            m.isAccessible = true
            val p = m.invoke(null, arrayOf("sh", "-c", "$cmd 2>&1"), null, null) as Process
            val out = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            out
        } catch (t: Throwable) {
            "ERR: ${t.cause?.message ?: t.message ?: t.javaClass.simpleName}"
        }
    }

    private fun bad(out: String) =
        out.startsWith("ERR") || out.contains("nrecogni", true) || out.contains("nknown", true) ||
            out.contains("nvalid", true) || out.contains("xception", true) || out.contains("usage", true)

    /** mode 0 = stock bar, 1 = blank bar, 2 = blank bar + stock shade locked. Returns a report. */
    suspend fun applyBar(mode: Int): String {
        if (mode <= 0) return exec("cmd statusbar send-disable-flag none").ifBlank { "stock bar restored" }
        val want = if (mode >= 2) CLEAN + LOCK else CLEAN
        val ok = ArrayList<String>()
        val skipped = ArrayList<String>()
        for (f in want) {
            val r = exec("cmd statusbar send-disable-flag $f")
            if (bad(r)) skipped.add(f) else ok.add(f)
        }
        if (ok.isEmpty()) return "No flag accepted: " + skipped.joinToString()
        val r = exec("cmd statusbar send-disable-flag " + ok.joinToString(" "))
        return if (bad(r)) r else "applied: ${ok.joinToString()}" +
            if (skipped.isNotEmpty()) " (not supported: ${skipped.joinToString()})" else ""
    }

    suspend fun expandShade() = exec("cmd statusbar expand-notifications")
    suspend fun expandSettings() = exec("cmd statusbar expand-settings")
}
