package com.kryll.homefit.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** Fecha civil, no instante UTC: no desplaza el día al cambiar de zona. */
object MeasureDate {
    private val format = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
    fun text(epochDay: Long): String = format.format(LocalDate.ofEpochDay(epochDay))
    fun parse(text: String, today: Long): Long? = runCatching {
        LocalDate.parse(text.trim(), format).toEpochDay().takeIf { it <= today }
    }.getOrNull()
}
