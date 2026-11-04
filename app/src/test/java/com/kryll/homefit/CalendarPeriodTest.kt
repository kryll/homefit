package com.kryll.homefit

import com.kryll.homefit.domain.CalendarPeriod
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class CalendarPeriodTest {
    @Test fun leapFebruaryHas29DatesAndFullWeeksMondayFirst() {
        val cells = CalendarPeriod.monthCells(YearMonth.of(2028, 2))
        assertEquals(35, cells.size)
        assertNull(cells.first())
        assertEquals(LocalDate.of(2028, 2, 1), cells[1])
        assertEquals(29, cells.filterNotNull().size)
        assertEquals(LocalDate.of(2028, 2, 29), cells.filterNotNull().last())
    }
    @Test fun sixWeekMonthAndYearBoundary() {
        val cells = CalendarPeriod.monthCells(YearMonth.of(2026, 11))
        assertEquals(42, cells.size)
        assertEquals(LocalDate.of(2026, 11, 1), cells[6])
        assertEquals(LocalDate.of(2025, 12, 29), CalendarPeriod.weekStart(LocalDate.of(2026, 1, 1)))
    }
}
