package com.kryll.homefit

import com.kryll.homefit.data.SetLog
import com.kryll.homefit.data.WorkoutSession
import com.kryll.homefit.domain.ProgressStats
import com.kryll.homefit.domain.SessionRow
import com.kryll.homefit.domain.SetRow
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressStatsTest {
    private val zone = ZoneId.of("UTC")
    private fun ms(y: Int, m: Int, d: Int, h: Int = 10) = LocalDateTime.of(y, m, d, h, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    // 2026-11-04 es miércoles.
    private val now = ms(2026, 11, 4, 12)
    private fun row(id: Long, start: Long, secs: Int = 600, sets: List<SetRow> = emptyList()) =
        SessionRow(id, start, secs, null, false, sets)

    @Test fun emptyHistoryIsAllZero() {
        val s = ProgressStats.summary(emptyList(), now, zone)
        assertEquals(0, s.totalSessions); assertEquals(0, s.streakDays); assertNull(s.avgRpe)
    }

    @Test fun streakCountsFromTodayOrYesterdayAndBreaksOnGap() {
        val today = listOf(row(1, ms(2026, 11, 4)), row(2, ms(2026, 11, 3)), row(3, ms(2026, 11, 2)), row(4, ms(2026, 10, 30)))
        assertEquals(3, ProgressStats.summary(today, now, zone).streakDays)
        val yesterdayOnly = listOf(row(1, ms(2026, 11, 3)), row(2, ms(2026, 11, 2)))
        assertEquals(2, ProgressStats.summary(yesterdayOnly, now, zone).streakDays)
        val old = listOf(row(1, ms(2026, 11, 1)))
        assertEquals(0, ProgressStats.summary(old, now, zone).streakDays)
    }

    @Test fun severalSessionsSameDayCountOnceInStreak() {
        val s = listOf(row(1, ms(2026, 11, 4, 8)), row(2, ms(2026, 11, 4, 19)))
        val sum = ProgressStats.summary(s, now, zone)
        assertEquals(1, sum.streakDays); assertEquals(2, sum.totalSessions)
    }

    @Test fun weekStartsOnMonday() {
        // Lunes 2 nov cuenta, domingo 1 nov no.
        val s = listOf(row(1, ms(2026, 11, 2)), row(2, ms(2026, 11, 1)), row(3, ms(2026, 11, 4)))
        assertEquals(2, ProgressStats.summary(s, now, zone).weekSessions)
    }

    @Test fun minutesSetsAndAverageRpe() {
        val sets = listOf(SetRow("A", 1, 10, null, 6), SetRow("A", 2, 10, null, 8), SetRow("B", 1, null, 30, null))
        val s = ProgressStats.summary(listOf(row(1, ms(2026, 11, 4), 1800, sets), row(2, ms(2026, 11, 3), 90)), now, zone)
        assertEquals(31, s.totalMinutes); assertEquals(3, s.totalSets); assertEquals(7.0, s.avgRpe!!, 0.001)
    }

    @Test fun rowsJoinNamesSortNewestFirstAndFlagIncomplete() {
        val sessions = listOf(
            WorkoutSession(1, 5L, 1000, 2000, 60, ""),
            WorkoutSession(2, null, 5000, 6000, 30, "incompleto"),
        )
        val sets = listOf(SetLog(1, 1, "x", 1, 10, null, null, 7), SetLog(2, 1, "gone", 1, null, null, 20, null))
        val rows = ProgressStats.rows(sessions, sets, mapOf(5L to "Fuerza"), mapOf("x" to "Sentadilla"))
        assertEquals(listOf(2L, 1L), rows.map { it.id })
        assertEquals(true, rows[0].incomplete); assertNull(rows[0].routineName)
        assertEquals("gone", rows[0].sets.single().exerciseName)
        assertEquals("Fuerza", rows[1].routineName)
        assertEquals("Sentadilla", rows[1].sets.single().exerciseName); assertEquals(7, rows[1].sets.single().rpe)
    }
}
