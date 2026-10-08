package app.rift.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.graphics.toArgb
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Starts or stops the floating HUD to match the settings and the overlay permission. */
object HudControl {
    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun apply(context: Context, settings: SettingsState) {
        val app = context.applicationContext
        val on = settings.bool(Keys.HUD_ON, false) && canDraw(app)
        val intent = Intent(app, HudService::class.java)
        try {
            if (on) ContextCompat.startForegroundService(app, intent) else app.stopService(intent)
        } catch (_: Exception) {
            // Not allowed to start from here right now; the next time RIFT opens will retry.
        }
    }
}

/**
 * A small draggable panel that floats over every app: time, battery, weather and a notification
 * count. Tap it to open a short list of notifications and a few tools. Built from plain views
 * so it can live in a service.
 */
class HudService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var settings: SettingsState
    private val handler = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var expanded = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 20_000)
        }
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            Torch.on = enabled
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        settings = SettingsState(getSharedPreferences("rift", MODE_PRIVATE))
        startAsForeground()
        try {
            getSystemService(CameraManager::class.java)?.registerTorchCallback(torchCallback, handler)
        } catch (_: Exception) {
            // No camera service.
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        settings = SettingsState(getSharedPreferences("rift", MODE_PRIVATE))
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        removeView()
        ensureView()
        handler.removeCallbacks(tickRunnable)
        tickRunnable.run()
        updateVisibility()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            getSystemService(CameraManager::class.java)?.unregisterTorchCallback(torchCallback)
        } catch (_: Exception) {
            // Already gone.
        }
        removeView()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "RIFT floating HUD", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("RIFT HUD is on")
            .setContentText("Turn it off in RIFT > Config > Overlays.")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTE_ID, notification)
        }
    }

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    private fun statusBarPx(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(28f)
    }

    private fun ensureView() {
        if (root != null) return
        val bar = settings.int(Keys.HUD_MODE, 0) == 1
        val anchor = settings.int(Keys.HUD_ANCHOR, 0).coerceIn(0, 5)
        val container = LinearLayout(this).apply {
            orientation = if (bar) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        val lp = if (bar) {
            // A full-width strip drawn over the system status bar. Touches pass through it.
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                statusBarPx(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }
        } else {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                val top = statusBarPx() + dp(4f)
                when (anchor) {
                    1 -> { gravity = Gravity.TOP or Gravity.START; x = dp(8f); y = top }
                    2 -> { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; x = 0; y = top }
                    3 -> { gravity = Gravity.TOP or Gravity.END; x = dp(8f); y = top }
                    4 -> { gravity = Gravity.BOTTOM or Gravity.START; x = dp(8f); y = dp(24f) }
                    5 -> { gravity = Gravity.BOTTOM or Gravity.END; x = dp(8f); y = dp(24f) }
                    else -> {
                        gravity = Gravity.TOP or Gravity.START
                        val savedX = settings.int(Keys.HUD_X, -1)
                        val savedY = settings.int(Keys.HUD_Y, -1)
                        x = if (savedX >= 0) savedX else dp(12f)
                        y = if (savedY >= 0) savedY else dp(120f)
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (!bar && anchor == 0) {
            container.setOnTouchListener(dragListener(lp))
        } else if (!bar) {
            // Pinned in place: a tap still opens the panel, but it cannot be dragged.
            container.setOnClickListener {
                expanded = !expanded
                render()
            }
        }
        try {
            windowManager.addView(container, lp)
            root = container
            params = lp
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun renderBar(container: LinearLayout) {
        val accent = Accents[settings.accentIndex].primary.toArgb()
        val fg = Cyber.fg.toArgb()
        container.setBackgroundColor(Color.BLACK)
        container.gravity = Gravity.CENTER_VERTICAL
        container.setPadding(dp(18f), 0, dp(18f), 0)
        container.removeAllViews()

        val left = mutableListOf<String>()
        if (settings.bool(Keys.HUD_TIME, true)) {
            val use24 = android.text.format.DateFormat.is24HourFormat(this)
            left += LocalTime.now().format(DateTimeFormatter.ofPattern(if (use24) "HH:mm" else "h:mm"))
        }
        if (settings.bool(Keys.HUD_WEATHER, true)) {
            settings.str(Keys.WEATHER_TEXT, "").takeIf { it.isNotBlank() }?.let { left += it }
        }
        if (settings.bool(Keys.HUD_NOTES, true)) {
            val count = NotificationHub.items.size
            if (count > 0) left += "✉$count"
        }
        val right = mutableListOf<String>()
        right += networkLabel(this)
        if (settings.bool(Keys.HUD_BATTERY, true)) {
            val (pct, charging) = readBattery(this)
            right += (if (charging) "+" else "") + "$pct%"
        }

        fun cell(value: String, gravity: Int, color: Int): TextView =
            text(value, 12f, color, bold = false).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setGravity(gravity or Gravity.CENTER_VERTICAL)
                maxLines = 1
            }
        container.addView(cell(left.joinToString("  ·  "), Gravity.START, fg))
        // The middle is left empty because it is usually where the camera sits.
        container.addView(cell("", Gravity.CENTER, fg))
        container.addView(cell(right.filter { it.isNotBlank() }.joinToString("  ·  "), Gravity.END, accent))
    }

    private fun removeView() {
        root?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
                // Already removed.
            }
        }
        root = null
    }

    private fun dragListener(lp: WindowManager.LayoutParams): View.OnTouchListener {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f
        var moved = false
        return View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    downX = event.rawX
                    downY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!moved && (Math.abs(dx) > slop || Math.abs(dy) > slop)) moved = true
                    if (moved) {
                        lp.x = (startX + dx).toInt().coerceAtLeast(0)
                        lp.y = (startY + dy).toInt().coerceAtLeast(0)
                        root?.let { windowManager.updateViewLayout(it, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        settings.putInt(Keys.HUD_X, lp.x)
                        settings.putInt(Keys.HUD_Y, lp.y)
                    } else {
                        expanded = !expanded
                        render()
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun updateVisibility() {
        val hide = launcherVisible && settings.bool(Keys.HUD_HIDE_HOME, true)
        root?.visibility = if (hide) View.GONE else View.VISIBLE
    }

    private fun mono(): Typeface =
        ResourcesCompat.getFont(this, R.font.plexmono_regular) ?: Typeface.MONOSPACE

    private fun text(value: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            setTextColor(color)
            textSize = sizeSp
            typeface = if (bold) Typeface.create(mono(), Typeface.BOLD) else mono()
            includeFontPadding = false
        }

    private fun render() {
        val container = root ?: return
        if (settings.int(Keys.HUD_MODE, 0) == 1) {
            renderBar(container)
            return
        }
        val accent = Accents[settings.accentIndex].primary.toArgb()
        val fg = Cyber.fg.toArgb()
        val muted = Cyber.muted.toArgb()
        val size = floatArrayOf(11f, 13f, 16f)[settings.int(Keys.HUD_SIZE, 1).coerceIn(0, 2)]
        val alpha = intArrayOf(0x99, 0xDD, 0xFF)[settings.int(Keys.HUD_ALPHA, 1).coerceIn(0, 2)]
        val background = GradientDrawable().apply {
            setColor((alpha shl 24) or 0x0A0E14)
            setStroke(dp(1f), accent)
        }
        container.background = background
        container.setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
        container.removeAllViews()

        val parts = mutableListOf<String>()
        if (settings.bool(Keys.HUD_TIME, true)) {
            val use24 = android.text.format.DateFormat.is24HourFormat(this)
            parts += LocalTime.now().format(DateTimeFormatter.ofPattern(if (use24) "HH:mm" else "h:mm"))
        }
        if (settings.bool(Keys.HUD_BATTERY, true)) {
            val (pct, charging) = readBattery(this)
            parts += (if (charging) "+" else "") + "$pct%"
        }
        if (settings.bool(Keys.HUD_WEATHER, true)) {
            settings.str(Keys.WEATHER_TEXT, "").takeIf { it.isNotBlank() }?.let { parts += it }
        }
        if (settings.bool(Keys.HUD_NOTES, true)) {
            val count = NotificationHub.items.size
            if (count > 0) parts += "✉$count"
        }
        if (parts.isEmpty()) parts += "RIFT"
        container.addView(text(parts.joinToString("  ·  "), size, accent, bold = true))

        if (expanded) {
            val items = NotificationHub.items.take(5)
            if (items.isEmpty()) {
                container.addView(text("No notifications", size - 2, muted).apply { setPadding(0, dp(8f), 0, 0) })
            }
            items.forEach { n ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(8f), 0, 0)
                    setOnClickListener {
                        openNote(this@HudService, n)
                        expanded = false
                        render()
                    }
                }
                row.addView(text(n.app.uppercase(), size - 4, muted))
                row.addView(text(n.title.ifBlank { n.text }, size - 1, fg).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    maxWidth = dp(240f)
                })
                container.addView(row)
            }
            val buttons = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10f), 0, 0)
            }
            fun button(label: String, onClick: () -> Unit) {
                buttons.addView(text(label, size - 1, accent, bold = true).apply {
                    setPadding(0, 0, dp(16f), 0)
                    setOnClickListener { onClick() }
                })
            }
            button(if (Torch.on) "TORCH OFF" else "TORCH") {
                Torch.toggle(this)
                handler.postDelayed({ render() }, 300)
            }
            button("RIFT") {
                safeStart(
                    this,
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setClass(this, MainActivity::class.java),
                )
                expanded = false
                render()
            }
            button("CLOSE") {
                expanded = false
                render()
            }
            container.addView(buttons)
        }
        params?.let {
            try {
                windowManager.updateViewLayout(container, it)
            } catch (_: Exception) {
                // View detached.
            }
        }
    }

    companion object {
        private const val CHANNEL = "rift_hud"
        private const val NOTE_ID = 4201
        private var instance: HudService? = null

        /** True while RIFT itself is on screen. The HUD hides then if the setting says so. */
        var launcherVisible: Boolean = false
            set(value) {
                field = value
                instance?.updateVisibility()
            }
    }
}
