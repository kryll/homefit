package com.kryll.homefit

import com.kryll.homefit.data.SetLog
import com.kryll.homefit.data.WorkoutSession
import com.kryll.homefit.domain.WorkoutCsv
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutCsvTest {
    private val zone: ZoneId = ZoneId.of("Europe/Madrid")
    // 2026-11-04 10:00 Europe/Madrid = 2026-11-04T09:00:00Z
    private val t0 = 1793782800000L

    private fun session(id: Long, start: Long, notes: String = "") =
        WorkoutSession(id = id, routineId = 7L, startMillis = start, endMillis = start + 1800_000, durationSeconds = 1800, notes = notes)

    @Test fun headerAndOneRowPerSet() {
        val csv = WorkoutCsv.build(
            listOf(session(1, t0) to listOf(
                SetLog(id = 1, sessionId = 1, exerciseId = "sentadilla", setIndex = 0, reps = 12, weightKg = 20f, rpe = 7),
                SetLog(id = 2, sessionId = 1, exerciseId = "sentadilla", setIndex = 1, reps = 10, weightKg = 20f, rpe = 8),
            )),
            mapOf(7L to "Fuerza en casa"), zone)
        val lines = csv.lines().filter { it.isNotBlank() }
        assertEquals("fecha,hora,rutina,duracion_min,ejercicio,serie,repeticiones,peso_kg,segundos,rpe,notas", lines[0])
        assertEquals(3, lines.size)
        assertEquals("2026-11-04,10:00,Fuerza en casa,30.0,sentadilla,1,12,20.0,,7,", lines[1])
        assertEquals("2026-11-04,10:00,Fuerza en casa,30.0,sentadilla,2,10,20.0,,8,", lines[2])
    }

    @Test fun oldestFirstAndSecondsSets() {
        val csv = WorkoutCsv.build(listOf(
            session(2, t0 + 86_400_000) to listOf(SetLog(id = 3, sessionId = 2, exerciseId = "plancha", setIndex = 0, seconds = 30)),
            session(1, t0) to listOf(SetLog(id = 1, sessionId = 1, exerciseId = "plancha", setIndex = 0, seconds = 40)),
        ), emptyMap(), zone)
        val lines = csv.lines().filter { it.isNotBlank() }
        assertEquals("2026-11-04,10:00,,30.0,plancha,1,,,40,,", lines[1])
        assertEquals("2026-11-05,10:00,,30.0,plancha,1,,,30,,", lines[2])
    }

    @Test fun quotesCommasAndQuotes() {
        val csv = WorkoutCsv.build(
            listOf(session(1, t0, notes = "bien, con agujetas") to listOf(
                SetLog(id = 1, sessionId = 1, exerciseId = "crunch", setIndex = 0, reps = 15))),
            mapOf(7L to "Core \"fuerte\""), zone)
        val line = csv.lines().filter { it.isNotBlank() }[1]
        assertTrue(line.contains("\"Core \"\"fuerte\"\"\""))
        assertTrue(line.endsWith("\"bien, con agujetas\""))
    }

    @Test fun emptyHistoryIsHeaderOnly() {
        assertEquals("fecha,hora,rutina,duracion_min,ejercicio,serie,repeticiones,peso_kg,segundos,rpe,notas\n", WorkoutCsv.build(emptyList(), emptyMap(), zone))
    }
}
