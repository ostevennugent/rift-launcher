package app.rift.launcher

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.util.Xml
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

const val DEFAULT_FEED = "https://feeds.bbci.co.uk/news/rss.xml"

data class WeatherInfo(
    val place: String,
    val temp: Int,
    val feelsLike: Int,
    val high: Int,
    val low: Int,
    val wind: Int,
    val summary: String,
    val unit: String,
    val windUnit: String,
)

data class AgendaEvent(val title: String, val whenText: String, val place: String)

data class Vitals(
    val storageUsedPct: Int,
    val storageFreeGb: Float,
    val memUsedPct: Int,
    val network: String,
    val tempC: Float,
)

object InfoFetcher {

    private fun httpGet(url: String, accept: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("Accept", accept)
            conn.setRequestProperty("User-Agent", "rift-launcher")
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** City name to coordinates via Open-Meteo, remembered so it is looked up once. */
    private fun geocode(settings: SettingsState, city: String): Triple<Double, Double, String> {
        val cachedLat = settings.str(Keys.GEO_LAT, "").toDoubleOrNull()
        val cachedLon = settings.str(Keys.GEO_LON, "").toDoubleOrNull()
        if (settings.str(Keys.GEO_QUERY, "") == city && cachedLat != null && cachedLon != null) {
            return Triple(cachedLat, cachedLon, settings.str(Keys.GEO_LABEL, city))
        }
        val encoded = URLEncoder.encode(city, "UTF-8")
        val body = httpGet(
            "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json",
            "application/json",
        )
        val results = JSONObject(body).optJSONArray("results")
        if (results == null || results.length() == 0) throw IOException("City not found: $city")
        val first = results.getJSONObject(0)
        val lat = first.getDouble("latitude")
        val lon = first.getDouble("longitude")
        val region = first.optString("admin1", "")
        val label = first.optString("name", city) + if (region.isNotBlank()) ", $region" else ""
        settings.putStr(Keys.GEO_QUERY, city)
        settings.putStr(Keys.GEO_LAT, lat.toString())
        settings.putStr(Keys.GEO_LON, lon.toString())
        settings.putStr(Keys.GEO_LABEL, label)
        return Triple(lat, lon, label)
    }

    fun fetchWeather(settings: SettingsState, city: String, fahrenheit: Boolean): WeatherInfo {
        val (lat, lon, label) = geocode(settings, city)
        val tempUnit = if (fahrenheit) "fahrenheit" else "celsius"
        val windUnit = if (fahrenheit) "mph" else "kmh"
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m" +
            "&daily=temperature_2m_max,temperature_2m_min" +
            "&temperature_unit=$tempUnit&wind_speed_unit=$windUnit&forecast_days=1&timezone=auto"
        val json = JSONObject(httpGet(url, "application/json"))
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        return WeatherInfo(
            place = label,
            temp = current.getDouble("temperature_2m").roundToInt(),
            feelsLike = current.getDouble("apparent_temperature").roundToInt(),
            high = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            low = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            wind = current.getDouble("wind_speed_10m").roundToInt(),
            summary = weatherText(current.getInt("weather_code")),
            unit = if (fahrenheit) "F" else "C",
            windUnit = if (fahrenheit) "mph" else "km/h",
        )
    }

    private fun weatherText(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75 -> "Snow"
        77 -> "Snow grains"
        80, 81, 82 -> "Showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm, hail"
        else -> "Unknown"
    }

    /** Titles from an RSS or Atom feed. */
    fun fetchHeadlines(feedUrl: String): List<String> {
        val body = httpGet(feedUrl, "application/rss+xml, application/atom+xml, application/xml, text/xml")
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(body))
        val titles = mutableListOf<String>()
        var inEntry = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && titles.size < 15) {
            if (event == XmlPullParser.START_TAG) {
                val name = parser.name
                if (name == "item" || name == "entry") {
                    inEntry = true
                } else if (name == "title" && inEntry) {
                    val text = parser.nextText().trim()
                    if (text.isNotEmpty()) titles.add(text)
                }
            } else if (event == XmlPullParser.END_TAG) {
                if (parser.name == "item" || parser.name == "entry") inEntry = false
            }
            event = parser.next()
        }
        if (titles.isEmpty()) throw IOException("No headlines found in that feed")
        return titles
    }

    /** Upcoming calendar events over the next 36 hours. Needs the Calendar permission. */
    fun agenda(context: Context): List<AgendaEvent> {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return emptyList()
        val begin = System.currentTimeMillis()
        val end = begin + 36L * 60L * 60L * 1000L
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, begin)
        ContentUris.appendId(builder, end)
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val use24h = DateFormat.is24HourFormat(context)
        val timeFormat = DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a")
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val out = mutableListOf<AgendaEvent>()
        try {
            context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext() && out.size < 5) {
                    val title = cursor.getString(0)?.ifBlank { null } ?: "(No title)"
                    val startMs = cursor.getLong(1)
                    val allDay = cursor.getInt(2) == 1
                    val place = cursor.getString(3) ?: ""
                    val start = Instant.ofEpochMilli(startMs).atZone(zone)
                    val dayLabel = when (start.toLocalDate()) {
                        today -> ""
                        today.plusDays(1) -> "Tomorrow "
                        else -> start.format(DateTimeFormatter.ofPattern("EEE "))
                    }
                    val whenText = if (allDay) {
                        dayLabel + "All day"
                    } else {
                        dayLabel + start.format(timeFormat)
                    }
                    out.add(AgendaEvent(title, whenText.trim(), place))
                }
            }
        } catch (_: Exception) {
            // Calendar provider unavailable; show whatever we have.
        }
        return out
    }

    fun vitals(context: Context): Vitals {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        val storagePct = if (total > 0) (((total - free) * 100) / total).toInt() else 0

        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memory)
        val memPct = if (memory.totalMem > 0) {
            (((memory.totalMem - memory.availMem) * 100) / memory.totalMem).toInt()
        } else {
            0
        }

        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
        val network = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Online"
        }

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tempC = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

        return Vitals(
            storageUsedPct = storagePct,
            storageFreeGb = free / 1_073_741_824f,
            memUsedPct = memPct,
            network = network,
            tempC = tempC,
        )
    }
}

/** Holds the fetched information for the UI and refreshes it on demand. */
class InfoController(
    private val appContext: Context,
    private val settings: SettingsState,
    private val scope: CoroutineScope,
) {
    var weather: WeatherInfo? by mutableStateOf(null)
        private set
    var weatherError: String? by mutableStateOf(null)
        private set
    var headlines: List<String> by mutableStateOf(emptyList())
        private set
    var headlinesError: String? by mutableStateOf(null)
        private set
    var agenda: List<AgendaEvent> by mutableStateOf(emptyList())
        private set
    var calendarGranted: Boolean by mutableStateOf(false)
        private set
    var notificationAccess: Boolean by mutableStateOf(false)
        private set
    var dndAccess: Boolean by mutableStateOf(false)
        private set

    fun refreshAll() {
        refreshAccess()
        refreshAgenda()
        refreshWeather()
        refreshHeadlines()
    }

    fun refreshAccess() {
        notificationAccess = NotificationManagerCompat.getEnabledListenerPackages(appContext)
            .contains(appContext.packageName)
        dndAccess = appContext.getSystemService(NotificationManager::class.java)
            ?.isNotificationPolicyAccessGranted ?: false
    }

    fun refreshAgenda() {
        val granted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        calendarGranted = granted
        if (!granted) {
            agenda = emptyList()
            return
        }
        scope.launch {
            agenda = withContext(Dispatchers.IO) { InfoFetcher.agenda(appContext) }
        }
    }

    fun refreshWeather() {
        val city = settings.str(Keys.WEATHER_CITY, "").trim()
        if (city.isEmpty()) {
            weather = null
            weatherError = null
            return
        }
        val fahrenheit = when (settings.int(Keys.WEATHER_UNIT, 0)) {
            1 -> true
            2 -> false
            else -> Locale.getDefault().country == "US"
        }
        scope.launch {
            try {
                weather = withContext(Dispatchers.IO) {
                    InfoFetcher.fetchWeather(settings, city, fahrenheit)
                }
                weatherError = null
                weather?.let { settings.putStr(Keys.WEATHER_TEXT, "${it.temp}°${it.unit}") }
            } catch (e: Exception) {
                weatherError = e.message ?: "Weather unavailable"
            }
        }
    }

    fun refreshHeadlines() {
        val feed = settings.str(Keys.FEED_URL, DEFAULT_FEED).trim().ifEmpty { DEFAULT_FEED }
        scope.launch {
            try {
                headlines = withContext(Dispatchers.IO) { InfoFetcher.fetchHeadlines(feed) }
                headlinesError = null
            } catch (e: Exception) {
                headlinesError = e.message ?: "Headlines unavailable"
            }
        }
    }
}

/** Short connection label for the status strips: Wi-Fi, Mobile, Ethernet or No signal. */
fun networkLabel(context: Context): String {
    val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return ""
    val caps = try {
        connectivity.getNetworkCapabilities(connectivity.activeNetwork)
    } catch (_: Exception) {
        null
    }
    return when {
        caps == null -> "No signal"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "Online"
    }
}
