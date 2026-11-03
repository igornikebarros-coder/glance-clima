package com.igorbarros.glanceclima

import java.util.Calendar

/** Data curta em pt-BR, ex.: "Seg., 2 de nov." (tabelas próprias, iguais em qualquer versão do Android). */
object DateFormatter {
    private val DAYS = arrayOf("Dom.", "Seg.", "Ter.", "Qua.", "Qui.", "Sex.", "Sáb.")
    private val MONTHS = arrayOf("jan.", "fev.", "mar.", "abr.", "mai.", "jun.", "jul.", "ago.", "set.", "out.", "nov.", "dez.")

    fun short(cal: Calendar = Calendar.getInstance()): String {
        val day = DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val month = MONTHS[cal.get(Calendar.MONTH)]
        return "$day, ${cal.get(Calendar.DAY_OF_MONTH)} de $month"
    }
}
