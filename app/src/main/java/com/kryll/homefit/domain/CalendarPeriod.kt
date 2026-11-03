package com.kryll.homefit.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

object CalendarPeriod {
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    /** Semanas completas de lunes a domingo; null significa celda fuera del mes. */
    fun monthCells(month: YearMonth): List<LocalDate?> {
        val offset = month.atDay(1).dayOfWeek.value - 1
        val count = ((offset + month.lengthOfMonth() + 6) / 7) * 7
        return (0 until count).map { i -> (i - offset + 1).takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay) }
    }
}
