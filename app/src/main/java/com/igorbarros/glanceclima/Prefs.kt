package com.igorbarros.glanceclima

import android.content.Context

/** Local escolhido pelo usuário e último clima salvo (cache usado para desenhar o widget). */
object Prefs {
    private const val FILE = "glance_clima"

    const val DEFAULT_NAME = "São Paulo"
    const val DEFAULT_LAT = -23.55
    const val DEFAULT_LON = -46.63

    data class Place(val name: String, val lat: Double, val lon: Double, val isDefault: Boolean, val fromGps: Boolean)

    data class Weather(val tempC: Int, val code: Int, val isDay: Boolean, val updatedAt: Long)

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun place(c: Context): Place {
        val p = sp(c)
        if (!p.contains("lat")) return Place(DEFAULT_NAME, DEFAULT_LAT, DEFAULT_LON, isDefault = true, fromGps = false)
        return Place(
            p.getString("name", DEFAULT_NAME) ?: DEFAULT_NAME,
            java.lang.Double.longBitsToDouble(p.getLong("lat", 0)),
            java.lang.Double.longBitsToDouble(p.getLong("lon", 0)),
            isDefault = false,
            fromGps = p.getBoolean("gps", false),
        )
    }

    fun savePlace(c: Context, name: String, lat: Double, lon: Double, fromGps: Boolean) {
        sp(c).edit()
            .putString("name", name)
            .putLong("lat", java.lang.Double.doubleToRawLongBits(lat))
            .putLong("lon", java.lang.Double.doubleToRawLongBits(lon))
            .putBoolean("gps", fromGps)
            .remove("temp") // força novo clima para o novo local
            .apply()
    }

    fun resetToDefault(c: Context) {
        sp(c).edit().remove("name").remove("lat").remove("lon").remove("gps").remove("temp").apply()
    }

    fun weather(c: Context): Weather? {
        val p = sp(c)
        if (!p.contains("temp")) return null
        return Weather(p.getInt("temp", 0), p.getInt("code", 3), p.getBoolean("day", true), p.getLong("at", 0))
    }

    fun saveWeather(c: Context, w: Weather) {
        sp(c).edit().putInt("temp", w.tempC).putInt("code", w.code).putBoolean("day", w.isDay).putLong("at", w.updatedAt).apply()
    }
}
