package app.rift.launcher

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs

// ---------------------------------------------------------------------------
// Flashlight, Do Not Disturb, vibration
// ---------------------------------------------------------------------------

/** The flashlight. State is kept in sync through a torch callback registered in LauncherRoot. */
object Torch {
    var on: Boolean by mutableStateOf(false)

    fun toggle(context: Context) {
        val manager = context.getSystemService(CameraManager::class.java) ?: return
        try {
            val id = manager.cameraIdList.firstOrNull { cameraId ->
                manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            manager.setTorchMode(id, !on)
        } catch (_: Exception) {
            // No flash, or the camera is busy.
        }
    }
}

fun dndEnabled(context: Context): Boolean {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return false
    val filter = manager.currentInterruptionFilter
    return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
        filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
}

/** Flip Do Not Disturb, or open the access screen the first time. */
fun toggleDnd(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (!manager.isNotificationPolicyAccessGranted) {
        safeStart(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        return
    }
    manager.setInterruptionFilter(
        if (dndEnabled(context)) NotificationManager.INTERRUPTION_FILTER_ALL
        else NotificationManager.INTERRUPTION_FILTER_PRIORITY,
    )
}

fun vibrate(context: Context) {
    try {
        val vibrator = context.getSystemService(Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (_: Exception) {
        // No vibrator.
    }
}

private class Tile(
    val label: String,
    val state: String,
    val active: Boolean,
    val onClick: () -> Unit,
)

/** Six shortcut tiles. Flashlight and Do Not Disturb really toggle; the rest open the right screen. */
@Composable
fun QuickToggles() {
    val context = LocalContext.current
    var dndOn by remember { mutableStateOf(dndEnabled(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            dndOn = dndEnabled(context)
            delay(2_000)
        }
    }
    val torchOn = Torch.on
    val wifiIntent = if (Build.VERSION.SDK_INT >= 29) {
        Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
    } else {
        Intent(Settings.ACTION_WIFI_SETTINGS)
    }
    val tiles = listOf(
        Tile("Flashlight", if (torchOn) "On" else "Off", torchOn) { Torch.toggle(context) },
        Tile("Do not disturb", if (dndOn) "On" else "Off", dndOn) {
            toggleDnd(context)
            dndOn = dndEnabled(context)
        },
        Tile("Wi-Fi", "Open", false) { safeStart(context, wifiIntent) },
        Tile("Bluetooth", "Open", false) { safeStart(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
        Tile("Airplane", "Open", false) { safeStart(context, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) },
        Tile("Location", "Open", false) { safeStart(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
    )
    val accent = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tiles.chunked(3).forEach { rowTiles ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowTiles.forEach { tile ->
                    Column(
                        Modifier
                            .weight(1f)
                            .background(if (tile.active) accent.copy(alpha = 0.18f) else Cyber.surface2)
                            .border(1.dp, if (tile.active) accent else Cyber.border)
                            .clickable { tile.onClick() }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = tile.label,
                            fontSize = 13.sp,
                            maxLines = 1,
                            color = if (tile.active) accent else Cyber.fg,
                        )
                        Text(
                            text = tile.state.uppercase(),
                            color = Cyber.muted,
                            fontFamily = PlexMono,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Timer and stopwatch state (lives in LauncherRoot so it keeps running between pages)
// ---------------------------------------------------------------------------

class ToolsState {
    var timerTotalMs: Long by mutableLongStateOf(5 * 60_000L)
    var timerLeftMs: Long by mutableLongStateOf(5 * 60_000L)
    var timerEndAt: Long by mutableLongStateOf(0L)
    var timerRunning: Boolean by mutableStateOf(false)
    var timerDone: Boolean by mutableStateOf(false)

    var swRunning: Boolean by mutableStateOf(false)
    var swStartAt: Long by mutableLongStateOf(0L)
    var swBaseMs: Long by mutableLongStateOf(0L)

    var now: Long by mutableLongStateOf(System.currentTimeMillis())

    val timerRemaining: Long
        get() = if (timerRunning) maxOf(0L, timerEndAt - now) else timerLeftMs

    val swElapsed: Long
        get() = swBaseMs + if (swRunning) maxOf(0L, now - swStartAt) else 0L

    fun setTimer(ms: Long) {
        if (timerRunning) return
        timerTotalMs = ms
        timerLeftMs = ms
        timerDone = false
    }

    fun startTimer() {
        if (timerRunning || timerLeftMs <= 0L) return
        now = System.currentTimeMillis()
        timerEndAt = now + timerLeftMs
        timerRunning = true
        timerDone = false
    }

    fun pauseTimer() {
        if (!timerRunning) return
        timerLeftMs = maxOf(0L, timerEndAt - System.currentTimeMillis())
        timerRunning = false
    }

    fun resetTimer() {
        timerRunning = false
        timerLeftMs = timerTotalMs
        timerDone = false
    }

    fun startStopwatch() {
        if (swRunning) return
        now = System.currentTimeMillis()
        swStartAt = now
        swRunning = true
    }

    fun stopStopwatch() {
        if (!swRunning) return
        swBaseMs += System.currentTimeMillis() - swStartAt
        swRunning = false
    }

    fun resetStopwatch() {
        swRunning = false
        swBaseMs = 0L
    }

    /** Called about ten times a second while something runs. True once when the timer hits zero. */
    fun tick(): Boolean {
        now = System.currentTimeMillis()
        if (timerRunning && now >= timerEndAt) {
            timerRunning = false
            timerLeftMs = 0L
            timerDone = true
            return true
        }
        return false
    }
}

fun formatTimer(ms: Long): String {
    val total = (ms + 999L) / 1000L
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

fun formatStopwatch(ms: Long): String {
    val tenths = (ms / 100L) % 10L
    val totalSeconds = ms / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d.%d".format(minutes, seconds, tenths)
}

// ---------------------------------------------------------------------------
// Calculator
// ---------------------------------------------------------------------------

class CalcState {
    var display: String by mutableStateOf("0")

    private var acc: Double? = null
    private var op: Char? = null
    private var fresh = true

    private fun fmt(value: Double): String {
        if (!value.isFinite()) return "Error"
        if (value == Math.rint(value) && abs(value) < 1e15) return value.toLong().toString()
        return BigDecimal(value).round(MathContext(12)).stripTrailingZeros().toPlainString()
    }

    private fun combine(a: Double, o: Char, b: Double): Double = when (o) {
        '+' -> a + b
        '−' -> a - b
        '×' -> a * b
        '÷' -> if (b == 0.0) Double.NaN else a / b
        else -> b
    }

    fun digit(d: String) {
        if (display == "Error") display = "0"
        if (fresh) {
            display = if (d == ".") "0." else d
            fresh = false
            return
        }
        if (d == "." && display.contains('.')) return
        if (display.length >= 14) return
        display = if (display == "0" && d != ".") d else display + d
    }

    fun operator(next: Char) {
        val current = display.toDoubleOrNull() ?: 0.0
        val a = acc
        val o = op
        val result = when {
            a != null && o != null && !fresh -> combine(a, o, current)
            a != null && o != null -> a
            else -> current
        }
        if (!result.isFinite()) {
            clear()
            display = "Error"
            return
        }
        acc = result
        op = next
        fresh = true
        display = fmt(result)
    }

    fun evaluate() {
        val a = acc
        val o = op
        if (a == null || o == null) {
            fresh = true
            return
        }
        val result = combine(a, o, display.toDoubleOrNull() ?: 0.0)
        acc = null
        op = null
        fresh = true
        display = fmt(result)
    }

    fun clear() {
        display = "0"
        acc = null
        op = null
        fresh = true
    }

    fun backspace() {
        if (fresh || display == "Error") return
        display = if (display.length <= 1 || (display.length == 2 && display.startsWith("-"))) {
            "0"
        } else {
            display.dropLast(1)
        }
    }

    fun negate() {
        display = when {
            display == "Error" || display == "0" -> display
            display.startsWith("-") -> display.drop(1)
            else -> "-$display"
        }
    }

    fun press(key: String) {
        when (key) {
            "C" -> clear()
            "⌫" -> backspace()
            "±" -> negate()
            "=" -> evaluate()
            "+", "−", "×", "÷" -> operator(key[0])
            else -> digit(key)
        }
    }
}

// ---------------------------------------------------------------------------
// Tasks
// ---------------------------------------------------------------------------

private data class TaskItem(val done: Boolean, val text: String)

private fun parseTasks(raw: String): List<TaskItem> =
    raw.split("\n")
        .filter { it.length > 2 }
        .map { TaskItem(it.startsWith("1|"), it.substringAfter("|")) }

private fun encodeTasks(tasks: List<TaskItem>): String =
    tasks.joinToString("\n") { (if (it.done) "1|" else "0|") + it.text }

// ---------------------------------------------------------------------------
// Deck page
// ---------------------------------------------------------------------------

/** Tasks, timer, stopwatch, calculator and notes. Shown inside the shade. */
@Composable
fun DeckContent(settings: SettingsState, tools: ToolsState, calc: CalcState) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TasksPlate(settings)
        TimerPlate(tools)
        StopwatchPlate(tools)
        CalculatorPlate(calc)
        NotesPlate(settings)
    }
}

@Composable
private fun TasksPlate(settings: SettingsState) {
    val tasks = parseTasks(settings.str(Keys.TASKS, ""))
    var draft by remember { mutableStateOf("") }
    val accent = MaterialTheme.colorScheme.primary

    fun add() {
        val text = draft.trim().replace("\n", " ")
        if (text.isNotEmpty()) {
            settings.putStr(Keys.TASKS, encodeTasks(tasks + TaskItem(false, text)))
            draft = ""
        }
    }

    Plate {
        Kicker("Tasks · ${tasks.count { !it.done }} open")
        Spacer(Modifier.height(8.dp))
        tasks.forEachIndexed { index, task ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(if (task.done) accent else Cyber.surface2)
                        .border(1.dp, if (task.done) accent else Cyber.border)
                        .clickable {
                            val updated = tasks.toMutableList()
                            updated[index] = task.copy(done = !task.done)
                            settings.putStr(Keys.TASKS, encodeTasks(updated))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (task.done) {
                        Text(text = "✓", color = Cyber.stage, fontSize = 16.sp)
                    }
                }
                Text(
                    text = task.text,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    color = if (task.done) Cyber.muted else Cyber.fg,
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                )
                Box(
                    Modifier
                        .size(40.dp)
                        .clickable {
                            val updated = tasks.toMutableList()
                            updated.removeAt(index)
                            settings.putStr(Keys.TASKS, encodeTasks(updated))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "✕", color = Cyber.muted)
                }
            }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            textStyle = TextStyle(fontFamily = PlexMono, fontSize = 14.sp),
            placeholder = {
                Text(
                    text = "ADD TASK_",
                    color = Cyber.muted,
                    fontFamily = PlexMono,
                    fontSize = 14.sp,
                    letterSpacing = 2.sp,
                )
            },
            singleLine = true,
            shape = RectangleShape,
            colors = riftFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
        )
    }
}

@Composable
private fun TimerPlate(tools: ToolsState) {
    val hot = MaterialTheme.colorScheme.secondary
    Plate {
        Kicker("Timer")
        Text(
            text = formatTimer(tools.timerRemaining),
            fontFamily = PlexMono,
            fontSize = 40.sp,
            color = if (tools.timerDone) hot else Cyber.fg,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (tools.timerDone) {
            Text(
                text = "TIME IS UP",
                color = hot,
                fontFamily = PlexMono,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
            )
        }
        Row(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(1, 5, 10, 15).forEach { minutes ->
                val ms = minutes * 60_000L
                Chip(text = "${minutes}m", selected = tools.timerTotalMs == ms) { tools.setTimer(ms) }
            }
        }
        Row(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (tools.timerRunning) {
                RiftButton("Pause") { tools.pauseTimer() }
            } else {
                RiftButton("Start") { tools.startTimer() }
            }
            RiftButton("Reset") { tools.resetTimer() }
        }
    }
}

@Composable
private fun StopwatchPlate(tools: ToolsState) {
    Plate {
        Kicker("Stopwatch")
        Text(
            text = formatStopwatch(tools.swElapsed),
            fontFamily = PlexMono,
            fontSize = 40.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (tools.swRunning) {
                RiftButton("Stop") { tools.stopStopwatch() }
            } else {
                RiftButton("Start") { tools.startStopwatch() }
            }
            RiftButton("Reset") { tools.resetStopwatch() }
        }
    }
}

@Composable
private fun CalcKey(key: String, modifier: Modifier, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val hot = MaterialTheme.colorScheme.secondary
    val isOperator = key == "+" || key == "−" || key == "×" || key == "÷"
    val isEquals = key == "="
    Box(
        modifier
            .height(52.dp)
            .background(if (isEquals) accent else Cyber.surface2)
            .border(1.dp, if (isEquals) accent else Cyber.border)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key,
            fontFamily = PlexMono,
            fontSize = 18.sp,
            color = when {
                isEquals -> Cyber.stage
                isOperator -> accent
                key == "C" -> hot
                else -> Cyber.fg
            },
        )
    }
}

@Composable
private fun CalculatorPlate(calc: CalcState) {
    val rows = listOf(
        listOf("C", "⌫", "±", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("0", ".", "="),
    )
    Plate {
        Kicker("Calculator")
        Text(
            text = calc.display,
            fontFamily = PlexMono,
            fontSize = 32.sp,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        )
        rows.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { key ->
                    CalcKey(
                        key = key,
                        modifier = Modifier.weight(if (key == "0") 2f else 1f),
                    ) { calc.press(key) }
                }
            }
        }
    }
}

@Composable
private fun NotesPlate(settings: SettingsState) {
    Plate {
        Kicker("Notes")
        OutlinedTextField(
            value = settings.str(Keys.NOTES, ""),
            onValueChange = { settings.putStr(Keys.NOTES, it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            textStyle = TextStyle(fontFamily = PlexMono, fontSize = 14.sp),
            placeholder = {
                Text(
                    text = "SCRATCHPAD_",
                    color = Cyber.muted,
                    fontFamily = PlexMono,
                    fontSize = 14.sp,
                    letterSpacing = 2.sp,
                )
            },
            minLines = 5,
            shape = RectangleShape,
            colors = riftFieldColors(),
        )
    }
}
