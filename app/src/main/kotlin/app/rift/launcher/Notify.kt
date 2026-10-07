package app.rift.launcher

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One notification, reduced to what the launcher shows. */
data class NoteItem(
    val key: String,
    val app: String,
    val pkg: String,
    val title: String,
    val text: String,
    val whenMs: Long,
    val intent: PendingIntent?,
    val clearable: Boolean,
)

/** Shared, observable list of current notifications. Filled by [RiftNotificationListener]. */
object NotificationHub {
    var items: List<NoteItem> by mutableStateOf(emptyList())

    internal var service: RiftNotificationListener? = null

    fun dismiss(key: String) {
        try {
            service?.cancelNotification(key)
        } catch (_: Exception) {
            // Listener disconnected meanwhile.
        }
    }

    fun clearAll() {
        try {
            service?.cancelAllNotifications()
        } catch (_: Exception) {
            // Listener disconnected meanwhile.
        }
    }
}

/**
 * Lets RIFT read the notifications that are already on the phone, so the Brief page and the
 * shade can summarise them. The list stays on the device; nothing is sent anywhere.
 */
class RiftNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        NotificationHub.service = this
        refresh()
    }

    override fun onListenerDisconnected() {
        NotificationHub.service = null
        NotificationHub.items = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refresh()
    }

    private fun refresh() {
        val fetched: Array<StatusBarNotification>? = try {
            activeNotifications
        } catch (_: Exception) {
            null
        }
        val active = fetched ?: return
        val pm = packageManager
        NotificationHub.items = active
            .filter { sbn ->
                val n = sbn.notification
                sbn.packageName != packageName &&
                    !sbn.isOngoing &&
                    (n.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
                    n.extras.getString(Notification.EXTRA_TEMPLATE) != "android.app.Notification\$MediaStyle"
            }
            .sortedByDescending { it.postTime }
            .map { sbn ->
                val extras = sbn.notification.extras
                val appLabel = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
                } catch (_: Exception) {
                    sbn.packageName
                }
                NoteItem(
                    key = sbn.key,
                    app = appLabel,
                    pkg = sbn.packageName,
                    title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "",
                    text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "",
                    whenMs = sbn.postTime,
                    intent = sbn.notification.contentIntent,
                    clearable = sbn.isClearable,
                )
            }
            .filter { it.title.isNotBlank() || it.text.isNotBlank() }
    }
}

/** What is playing right now, and the controller to drive it. */
data class NowPlaying(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val controller: MediaController,
)

/** Reads the active media session. Needs notification access. Returns null when nothing is active. */
fun readNowPlaying(context: Context): NowPlaying? {
    return try {
        val manager = context.getSystemService(MediaSessionManager::class.java) ?: return null
        val component = ComponentName(context, RiftNotificationListener::class.java)
        val sessions = manager.getActiveSessions(component)
        val controller = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull()
            ?: return null
        val metadata = controller.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown track"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        NowPlaying(
            title = title,
            artist = artist,
            playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING,
            controller = controller,
        )
    } catch (_: Exception) {
        null
    }
}
