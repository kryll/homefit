package com.kryll.homefit.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SetRow(val exerciseName: String, val setIndex: Int, val reps: Int?, val seconds: Int?, val rpe: Int?)

data class SessionRow(
    val id: Long,
    val startMillis: Long,
    val durationSeconds: Int,
    /** null = entreno sin rutina. */
    val routineName: String?,
    val incomplete: Boolean,
    val sets: List<SetRow>,
)

data class ProgressSummary(
    val totalSessions: Int,
    val weekSessions: Int,
    /** Días seguidos con entreno, contando hoy o, si hoy aún no, desde ayer. */
    val streakDays: Int,
    val totalMinutes: Int,
    val totalSets: Int,
    /** Media del esfuerzo percibido de las series con RPE; null si no hay ninguna. */
    val avgRpe: Double?,
)

/** Cálculos del historial sin Android. Las sesiones incompletas cuentan: son entreno hecho. */
object ProgressStats {
    fun summary(sessions: List<SessionRow>, nowMillis: Long, zone: ZoneId): ProgressSummary {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val days = sessions.map { Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalDate() }.toSet()
        val weekStart = today.with(DayOfWeek.MONDAY)
        val week = sessions.count {
            val d = Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalDate()
            !d.isBefore(weekStart) && !d.isAfter(today)
        }
        val rpes = sessions.flatMap { s -> s.sets.mapNotNull { it.rpe } }
        return ProgressSummary(
            totalSessions = sessions.size,
            weekSessions = week,
            streakDays = streak(days, today),
            totalMinutes = sessions.sumOf { it.durationSeconds } / 60,
            totalSets = sessions.sumOf { it.sets.size },
            avgRpe = if (rpes.isEmpty()) null else rpes.average(),
        )
    }

    private fun streak(days: Set<LocalDate>, today: LocalDate): Int {
        var d = when {
            today in days -> today
            today.minusDays(1) in days -> today.minusDays(1)
            else -> return 0
        }
        var n = 0
        while (d in days) { n++; d = d.minusDays(1) }
        return n
    }

    /** Une sesiones, series y nombres. Más reciente primero; las series conservan su orden de registro. */
    fun rows(
        sessions: List<com.kryll.homefit.data.WorkoutSession>,
        sets: List<com.kryll.homefit.data.SetLog>,
        routineNames: Map<Long, String>,
        exerciseNames: Map<String, String>,
    ): List<SessionRow> {
        val bySession = sets.groupBy { it.sessionId }
        return sessions.sortedByDescending { it.startMillis }.map { s ->
            SessionRow(
                id = s.id, startMillis = s.startMillis, durationSeconds = s.durationSeconds,
                routineName = s.routineId?.let { routineNames[it] },
                incomplete = s.notes == com.kryll.homefit.data.WorkoutRepository.NOTE_INCOMPLETE,
                sets = (bySession[s.id] ?: emptyList()).sortedBy { it.id }.map {
                    SetRow(exerciseNames[it.exerciseId] ?: it.exerciseId, it.setIndex, it.reps, it.seconds, it.rpe)
                },
            )
        }
    }
}
