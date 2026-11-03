package com.igorbarros.glanceclima

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

/** Cliente mínimo da Open-Meteo (gratuita e sem chave): clima atual e busca de cidades. */
object WeatherApi {
    data class City(val name: String, val detail: String, val lat: Double, val lon: Double)

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "GlanceClima/1.0 (Android)")
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun fetchCurrent(lat: Double, lon: Double): Prefs.Weather {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day&timezone=auto",
            lat, lon,
        )
        val cur = JSONObject(get(url)).getJSONObject("current")
        return Prefs.Weather(
            tempC = cur.getDouble("temperature_2m").roundToInt(),
            code = cur.getInt("weather_code"),
            isDay = cur.getInt("is_day") == 1,
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun searchCities(query: String): List<City> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
            URLEncoder.encode(query.trim(), "UTF-8") + "&count=6&language=pt&format=json"
        val results = JSONObject(get(url)).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { i ->
            val o = results.getJSONObject(i)
            val detail = listOf(o.optString("admin1"), o.optString("country")).filter { it.isNotBlank() }.joinToString(", ")
            City(o.getString("name"), detail, o.getDouble("latitude"), o.getDouble("longitude"))
        }
    }
}
