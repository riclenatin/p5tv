package com.chroniac.tvhome

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/** Current conditions; [code] is a WMO weather code as returned by Open-Meteo. */
data class Weather(val temperature: Int, val code: Int, val isDay: Boolean)

/**
 * Open-Meteo current weather (no API key) for the coordinates set at build time (weather.latitude
 * and weather.longitude in local.properties); without them nothing is fetched and no chip shows.
 * The last result is cached so a cold start shows it immediately.
 */
class WeatherRepository(context: Context) {
    private val latitude = context.getString(R.string.weather_latitude)
    private val longitude = context.getString(R.string.weather_longitude)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _weather = MutableStateFlow(cached())
    val weather: StateFlow<Weather?> = _weather.asStateFlow()

    suspend fun refreshIfStale() {
        if (latitude.isBlank() || longitude.isBlank()) return
        if (System.currentTimeMillis() - prefs.getLong(KEY_AT, 0L) < REFRESH_MS) return
        val fresh = withContext(Dispatchers.IO) { runCatching { fetch() }.getOrNull() } ?: return
        prefs.edit()
            .putInt(KEY_TEMPERATURE, fresh.temperature)
            .putInt(KEY_CODE, fresh.code)
            .putBoolean(KEY_DAY, fresh.isDay)
            .putLong(KEY_AT, System.currentTimeMillis())
            .apply()
        _weather.value = fresh
    }

    private fun cached(): Weather? {
        val at = prefs.getLong(KEY_AT, 0L)
        if (System.currentTimeMillis() - at > MAX_AGE_MS) return null
        return Weather(prefs.getInt(KEY_TEMPERATURE, 0), prefs.getInt(KEY_CODE, 0), prefs.getBoolean(KEY_DAY, true))
    }

    private fun fetch(): Weather {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,weather_code,is_day&timezone=auto"
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            val current = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                .getJSONObject("current")
            return Weather(
                temperature = current.getDouble("temperature_2m").roundToInt(),
                code = current.getInt("weather_code"),
                isDay = current.getInt("is_day") == 1,
            )
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val PREFS = "weather"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_CODE = "code"
        const val KEY_DAY = "day"
        const val KEY_AT = "at"
        const val REFRESH_MS = 30 * 60 * 1000L
        const val MAX_AGE_MS = 6 * 60 * 60 * 1000L
        const val TIMEOUT_MS = 8000
    }
}
