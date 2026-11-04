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
        val sets = listOf(
            SetLog(sessionId = 1, exerciseId = "x", setIndex = 1, reps = 10, rpe = 7),
            SetLog(sessionId = 2, exerciseId = "gone", setIndex = 1, seconds = 20),
        )
        val rows = ProgressStats.rows(sessions, sets, mapOf(5L to "Fuerza"), mapOf("x" to "Sentadilla"))
        assertEquals(listOf(2L, 1L), rows.map { it.id })
        assertEquals(true, rows[0].incomplete); assertNull(rows[0].routineName)
        assertEquals("gone", rows[0].sets.single().exerciseName)
        assertEquals("Fuerza", rows[1].routineName)
        assertEquals("Sentadilla", rows[1].sets.single().exerciseName); assertEquals(7, rows[1].sets.single().rpe)
    }
    @Test fun volumeGroupsByPrimaryMuscleSortedBySets() {
        val sets = listOf(
            SetRow("Sentadilla", 1, 12, null, 7, exerciseId = "squat"),
            SetRow("Sentadilla", 2, 12, null, 8, exerciseId = "squat"),
            SetRow("Plancha", 1, null, 30, 9, exerciseId = "plank"),
            SetRow("Flexiones", 1, 10, null, 7, exerciseId = "pushup"),
            SetRow("Raro", 1, 5, null, null, exerciseId = "unknown"),
        )
        val muscles = mapOf("squat" to "piernas", "plank" to "core", "pushup" to "pecho")
        val v = ProgressStats.volumeByMuscle(listOf(row(1, ms(2026, 11, 3), sets = sets))) { muscles[it] }
        assertEquals(listOf("piernas", "core", "pecho"), v.map { it.muscle })
        assertEquals(com.kryll.homefit.domain.MuscleVolume("piernas", 2, 24, 0), v[0])
        assertEquals(com.kryll.homefit.domain.MuscleVolume("core", 1, 0, 30), v[1])
        assertEquals(com.kryll.homefit.domain.MuscleVolume("pecho", 1, 10, 0), v[2])
    }

    @Test fun volumeIsEmptyWithoutSetsOrKnownMuscles() {
        assertEquals(emptyList<com.kryll.homefit.domain.MuscleVolume>(), ProgressStats.volumeByMuscle(emptyList()) { "pecho" })
        val sets = listOf(SetRow("Raro", 1, 5, null, null, exerciseId = "unknown"))
        assertEquals(emptyList<com.kryll.homefit.domain.MuscleVolume>(), ProgressStats.volumeByMuscle(listOf(row(1, ms(2026, 11, 3), sets = sets))) { null })
    }

    @Test fun personalRecordsTakesMaxPerMetric() {
        val sets = listOf(
            SetRow("Flexiones", 1, reps = 10, seconds = null, rpe = null, weightKg = null, exerciseId = "flexiones"),
            SetRow("Flexiones", 2, reps = 14, seconds = null, rpe = null, weightKg = 5f, exerciseId = "flexiones"),
            SetRow("Plancha", 1, reps = null, seconds = 40, rpe = null, exerciseId = "plancha"),
            SetRow("Plancha", 2, reps = null, seconds = 65, rpe = null, exerciseId = "plancha"),
        )
        val recs = com.kryll.homefit.domain.personalRecords(listOf(row(1, ms(2026, 11, 3), sets = sets)))
        assertEquals(2, recs.size)
        assertEquals("Flexiones", recs[0].exerciseName)
        assertEquals(14, recs[0].maxReps)
        assertEquals(5f, recs[0].maxWeightKg!!, 0.001f)
        assertNull(recs[0].maxSeconds)
        assertEquals("Plancha", recs[1].exerciseName)
        assertEquals(65, recs[1].maxSeconds)
        assertNull(recs[1].maxReps)
    }

    @Test fun personalRecordsSkipsEmptyAndSortsByName() {
        assertEquals(emptyList<com.kryll.homefit.domain.ExerciseRecord>(), com.kryll.homefit.domain.personalRecords(emptyList()))
        val sets = listOf(
            SetRow("Zancadas", 1, reps = 8, seconds = null, rpe = null, exerciseId = "zancadas"),
            SetRow("Abdominales", 1, reps = 12, seconds = null, rpe = null, exerciseId = "abdominales"),
            SetRow("Sin datos", 1, reps = null, seconds = null, rpe = 5, exerciseId = "sindatos"),
        )
        val recs = com.kryll.homefit.domain.personalRecords(listOf(row(1, ms(2026, 11, 3), sets = sets)))
        assertEquals(listOf("Abdominales", "Zancadas"), recs.map { it.exerciseName })
    }

    @Test fun partialSessionsAreKeptButNotCountedAsComplete() {
        val done = row(1, ms(2026, 11, 4), 600, listOf(SetRow("Sentadilla", 1, 10, null, 7, null, "sentadilla")))
        val partial = row(2, ms(2026, 11, 4), 600).copy(incomplete = true)
        val s = ProgressStats.summary(listOf(done, partial), now, zone)
        assertEquals(1, s.totalSessions)
        assertEquals(1, s.weekSessions)
        assertEquals(1, s.streakDays)
        assertEquals(10, s.totalMinutes)
        assertEquals(1, s.totalSets)
        assertEquals(7.0, s.avgRpe!!, 0.001)
        assertEquals(1, s.partialSessions)
    }
}
