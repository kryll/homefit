package com.kryll.homefit

import com.kryll.homefit.domain.ProgressCharts
import com.kryll.homefit.domain.SessionRow
import com.kryll.homefit.domain.SetRow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressChartsTest {
    private val zone = ZoneId.of("UTC")
    private fun ms(m: Int, d: Int) = LocalDateTime.of(2026, m, d, 9, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun s(id: Long, m: Int, d: Int, vararg sets: SetRow) = SessionRow(id, ms(m, d), 600, null, false, sets.toList())
    private val today = LocalDate.of(2026, 11, 4) // miércoles

    @Test fun weeklyCountsAreOrderedOldestFirstWithZeroWeeks() {
        val w = ProgressCharts.weeklySessions(listOf(s(1, 11, 2), s(2, 11, 3), s(3, 10, 20)), today, zone, weeks = 3)
        assertEquals(listOf(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 2)), w.map { it.first })
        assertEquals(listOf(1, 0, 2), w.map { it.second })
    }

    @Test fun sessionsOutsideTheWindowAreIgnored() {
        val w = ProgressCharts.weeklySessions(listOf(s(1, 1, 5)), today, zone, weeks = 4)
        assertEquals(0, w.sumOf { it.second })
    }

    @Test fun exercisesAreSortedByTimesTrained() {
        val rows = listOf(
            s(1, 11, 1, SetRow("B", 1, 10, null, null), SetRow("A", 1, 5, null, null)),
            s(2, 11, 2, SetRow("B", 1, 11, null, null), SetRow("B", 2, 11, null, null)),
            s(3, 11, 3, SetRow("C", 1, null, null, 8)),
        )
        assertEquals(listOf("B", "A"), ProgressCharts.exercisesWithData(rows))
    }

    @Test fun seriesTakesBestSetPerDayAndSortsByDate() {
        val rows = listOf(
            s(2, 11, 3, SetRow("Sentadilla", 1, 12, null, null), SetRow("Sentadilla", 2, 14, null, null)),
            s(1, 11, 1, SetRow("Sentadilla", 1, 10, null, null)),
        )
        val ser = ProgressCharts.exerciseSeries(rows, "Sentadilla", zone)!!
        assertEquals(false, ser.seconds)
        assertEquals(listOf(10, 14), ser.points.map { it.best })
        assertTrue(ser.points[0].epochDay < ser.points[1].epochDay)
    }

    @Test fun timedExerciseUsesSeconds() {
        val ser = ProgressCharts.exerciseSeries(listOf(s(1, 11, 1, SetRow("Plancha", 1, null, 30, null))), "Plancha", zone)!!
        assertEquals(true, ser.seconds)
        assertEquals(30, ser.points.single().best)
    }

    @Test fun weightOfTheBestSetIsKept() {
        val rows = listOf(s(1, 11, 1, SetRow("Press", 1, 10, null, null, 20f), SetRow("Press", 2, 12, null, null, 17.5f)))
        val p = ProgressCharts.exerciseSeries(rows, "Press", zone)!!.points.single()
        assertEquals(12, p.best); assertEquals(17.5f, p.weightKg)
    }

    @Test fun noDataGivesNull() {
        assertNull(ProgressCharts.exerciseSeries(emptyList(), "X", zone))
        assertNull(ProgressCharts.exerciseSeries(listOf(s(1, 11, 1, SetRow("X", 1, null, null, 7))), "X", zone))
    }
    @Test fun loadSeriesUsesDailyMaximumNotMostRepsAndIgnoresMissingWeights() {
        val rows = listOf(
            s(2, 11, 3, SetRow("Press", 1, 12, null, null, 15f), SetRow("Press", 2, 5, null, null, 20f)),
            s(1, 11, 1, SetRow("Press", 1, 10, null, null, 17.5f)),
            s(3, 11, 4, SetRow("Press", 1, 20, null, null)),
        )
        assertEquals(listOf(17.5f, 20f), ProgressCharts.weightSeries(rows, "Press", zone).map { it.second })
        assertTrue(ProgressCharts.weightSeries(rows, "Other", zone).isEmpty())
    }
}
