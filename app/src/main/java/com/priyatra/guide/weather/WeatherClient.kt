package com.priyatra.guide.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class WeatherNow(
    val tempC: Double,
    val windKmh: Double,
    val summary: String,
)

class WeatherClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun fetch(lat: Double, lng: Double): WeatherNow = withContext(Dispatchers.IO) {
        val url =
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&current_weather=true"
        val body = client.newCall(Request.Builder().url(url).build()).execute().use { rsp ->
            if (!rsp.isSuccessful) error("weather http ${rsp.code}")
            rsp.body?.string().orEmpty()
        }
        val root = JSONObject(body)
        val cur = root.getJSONObject("current_weather")
        val code = cur.optInt("weathercode")
        WeatherNow(
            tempC = cur.getDouble("temperature"),
            windKmh = cur.getDouble("windspeed"),
            summary = wmoLabel(code),
        )
    }

    private fun wmoLabel(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2, 3 -> "Mainly clear to overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        61, 63, 65 -> "Rain"
        71, 73, 75 -> "Snow"
        80, 81, 82 -> "Rain showers"
        95 -> "Thunderstorm"
        else -> "Open-Meteo code $code"
    }
}
