package app.rift.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The newest build published on GitHub. */
data class UpdateInfo(val sha: String, val downloadUrl: String, val sizeBytes: Long)

sealed interface UpdateStatus {
    object Idle : UpdateStatus
    object Checking : UpdateStatus
    object UpToDate : UpdateStatus
    data class Available(val info: UpdateInfo) : UpdateStatus
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateStatus
    data class Ready(val info: UpdateInfo, val file: File) : UpdateStatus
    data class Failed(val message: String) : UpdateStatus
}

object Updater {
    private const val RELEASE_URL =
        "https://api.github.com/repos/ostevennugent/rift-launcher/releases/tags/latest-apk"

    /** Ask GitHub which build is newest. Throws on network or parse problems. */
    suspend fun fetchLatest(): UpdateInfo = withContext(Dispatchers.IO) {
        val conn = URL(RELEASE_URL).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "rift-launcher")
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            if (conn.responseCode != 200) throw IOException("GitHub answered ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = json.getJSONArray("assets")
            if (assets.length() == 0) throw IOException("No APK attached to the release")
            val asset = assets.getJSONObject(0)
            UpdateInfo(
                sha = json.optString("target_commitish"),
                downloadUrl = asset.getString("browser_download_url"),
                sizeBytes = asset.optLong("size"),
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Download the APK into the app cache, reporting progress from 0 to 1. */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "rift-update.apk")
            val conn = URL(info.downloadUrl).openConnection() as HttpURLConnection
            try {
                conn.setRequestProperty("User-Agent", "rift-launcher")
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                if (conn.responseCode !in 200..299) throw IOException("Download failed (${conn.responseCode})")
                val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else info.sizeBytes
                conn.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var done = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            target
        }

    /**
     * Hand the downloaded APK to Android's installer. Returns false when RIFT is not
     * yet allowed to install apps; in that case the permission screen is opened.
     */
    fun install(context: Context, file: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            safeStart(
                context,
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        safeStart(context, intent)
        return true
    }

    /** Remove leftovers from a previous update. */
    fun cleanup(context: Context) {
        File(context.cacheDir, "updates").deleteRecursively()
    }
}

/** Holds the update state for the UI and runs the check / download / install steps. */
class UpdateController(
    private val appContext: Context,
    private val settings: SettingsState,
    private val scope: CoroutineScope,
) {
    var status: UpdateStatus by mutableStateOf(UpdateStatus.Idle)
        private set

    private val busy: Boolean
        get() = status is UpdateStatus.Checking || status is UpdateStatus.Downloading

    fun check() {
        if (busy) return
        status = UpdateStatus.Checking
        scope.launch {
            status = try {
                val info = Updater.fetchLatest()
                settings.putLong(Keys.LAST_CHECK, System.currentTimeMillis())
                if (info.sha.isNotBlank() && info.sha != BuildConfig.GIT_SHA) {
                    UpdateStatus.Available(info)
                } else {
                    UpdateStatus.UpToDate
                }
            } catch (e: Exception) {
                UpdateStatus.Failed(e.message ?: "Could not reach GitHub")
            }
        }
    }

    /** Download the given build, then open the installer. */
    fun downloadAndInstall(info: UpdateInfo) {
        if (busy) return
        status = UpdateStatus.Downloading(info, 0f)
        scope.launch {
            status = try {
                val file = Updater.download(appContext, info) { progress ->
                    status = UpdateStatus.Downloading(info, progress)
                }
                Updater.install(appContext, file)
                UpdateStatus.Ready(info, file)
            } catch (e: Exception) {
                UpdateStatus.Failed(e.message ?: "Download failed")
            }
        }
    }

    /** Open the installer again for an APK that is already downloaded. */
    fun installAgain(info: UpdateInfo, file: File) {
        Updater.install(appContext, file)
        status = UpdateStatus.Ready(info, file)
    }
}
