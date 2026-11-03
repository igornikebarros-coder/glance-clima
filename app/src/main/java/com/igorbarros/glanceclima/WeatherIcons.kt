package com.igorbarros.glanceclima

import androidx.annotation.DrawableRes

/** Converte o código WMO da Open-Meteo (e is_day) em um dos ícones vetoriais do app. */
object WeatherIcons {
    @DrawableRes
    fun forCode(code: Int, isDay: Boolean): Int = when (code) {
        0, 1 -> if (isDay) R.drawable.ic_w_clear_day else R.drawable.ic_w_clear_night
        2 -> if (isDay) R.drawable.ic_w_partly_day else R.drawable.ic_w_partly_night
        3 -> R.drawable.ic_w_cloudy
        45, 48 -> R.drawable.ic_w_fog
        in 51..67, in 80..82 -> R.drawable.ic_w_rain
        in 71..77, 85, 86 -> R.drawable.ic_w_snow
        95, 96, 99 -> R.drawable.ic_w_storm
        else -> R.drawable.ic_w_cloudy
    }
}
