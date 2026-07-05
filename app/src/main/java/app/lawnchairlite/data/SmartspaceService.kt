package app.lawnchairlite.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.provider.CalendarContract
import android.util.Log
import androidx.annotation.StringRes
import app.lawnchairlite.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class SmartspaceService(private val context: Context) {
    suspend fun refresh(nowMillis: Long = System.currentTimeMillis()): SmartspaceState {
        val calendarNeeded = !hasCalendarPermission()
        val locationNeeded = !hasLocationPermission()
        val event = if (calendarNeeded) null else loadNextCalendarEvent(nowMillis)
        val weather = if (locationNeeded) null else loadWeather()
        return SmartspaceState(
            weather = weather,
            nextEvent = event,
            locationPermissionNeeded = locationNeeded,
            calendarPermissionNeeded = calendarNeeded,
            lastUpdatedMillis = System.currentTimeMillis(),
        )
    }

    private suspend fun loadNextCalendarEvent(nowMillis: Long): SmartspaceEvent? = withContext(Dispatchers.IO) {
        val end = nowMillis + TimeUnit.DAYS.toMillis(7)
        val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder, nowMillis)
        ContentUris.appendId(uriBuilder, end)
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        try {
            context.contentResolver.query(
                uriBuilder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val title = cursor.getString(0)?.trim().orEmpty()
                    val startsAt = cursor.getLong(1)
                    val location = cursor.getString(2)?.trim().orEmpty()
                    if (title.isNotBlank() && startsAt >= nowMillis) {
                        return@withContext SmartspaceEvent(title, startsAt, location)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Calendar smartspace query failed", e)
        }
        null
    }

    private suspend fun loadWeather(): SmartspaceWeather? = withContext(Dispatchers.IO) {
        val location = lastKnownLocation() ?: return@withContext null
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${location.latitude}&longitude=${location.longitude}" +
                "&current=temperature_2m,weather_code&temperature_unit=fahrenheit&timezone=auto",
        )
        var connection: HttpURLConnection? = null
        try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = WEATHER_TIMEOUT_MS
                readTimeout = WEATHER_TIMEOUT_MS
                requestMethod = "GET"
            }
            if (connection.responseCode !in 200..299) return@withContext null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(body).optJSONObject("current") ?: return@withContext null
            val temp = current.optDouble("temperature_2m", Double.NaN)
            if (temp.isNaN()) return@withContext null
            SmartspaceWeather(
                temperature = temp.roundToInt(),
                unit = "F",
                condition = context.getString(SmartspaceWeatherLabels.labelRes(current.optInt("weather_code", -1))),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Weather smartspace fetch failed", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    fun hasCalendarPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private companion object {
        private const val TAG = "SmartspaceService"
        private const val WEATHER_TIMEOUT_MS = 3500
    }
}

object SmartspaceWeatherLabels {
    @StringRes
    fun labelRes(code: Int): Int = when (code) {
        0 -> R.string.weather_clear
        1, 2 -> R.string.weather_partly_cloudy
        3 -> R.string.weather_cloudy
        45, 48 -> R.string.weather_fog
        51, 53, 55, 56, 57 -> R.string.weather_drizzle
        61, 63, 65, 66, 67, 80, 81, 82 -> R.string.weather_rain
        71, 73, 75, 77, 85, 86 -> R.string.weather_snow
        95, 96, 99 -> R.string.weather_storm
        else -> R.string.weather_generic
    }
}

object SmartspaceUnreadAggregator {
    fun summarize(counts: Map<String, Int>, selfPackage: String): SmartspaceUnread? {
        val readableCounts = counts
            .filterKeys { it != selfPackage }
            .values
            .filter { it > 0 }
        val total = readableCounts.sum()
        return if (total > 0) {
            SmartspaceUnread(totalCount = total, sourceCount = readableCounts.size)
        } else {
            null
        }
    }
}
