package com.kryll.homefit.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Un punto por entreno: la mejor serie de ese ejercicio ese día. */
data class ExercisePoint(val epochDay: Long, val best: Int)

data class ExerciseSeries(val name: String, val seconds: Boolean, val points: List<ExercisePoint>)

/** Datos de las gráficas de progreso, sin Android. Solo salen de series realmente registradas. */
object ProgressCharts {
    /** Entrenos por semana (lunes a domingo) de las últimas [weeks] semanas, la más antigua primero. */
    fun weeklySessions(sessions: List<SessionRow>, today: LocalDate, zone: ZoneId, weeks: Int = 8): List<Pair<LocalDate, Int>> {
        require(weeks > 0)
        val thisWeek = today.with(DayOfWeek.MONDAY)
        val counts = sessions.groupingBy { Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalDate().with(DayOfWeek.MONDAY) }.eachCount()
        return (weeks - 1 downTo 0).map { thisWeek.minusWeeks(it.toLong()) }.map { it to (counts[it] ?: 0) }
    }

    /** Ejercicios con alguna serie con repeticiones o segundos, los más entrenados primero. */
    fun exercisesWithData(sessions: List<SessionRow>): List<String> =
        sessions.flatMap { s -> s.sets.filter { it.reps != null || it.seconds != null }.map { it.exerciseName }.distinct() }
            .groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key }

    /**
     * Mejor serie por día para [name]: repeticiones, o segundos si el ejercicio se mide en tiempo.
     * Null si no hay datos.
     */
    fun exerciseSeries(sessions: List<SessionRow>, name: String, zone: ZoneId): ExerciseSeries? {
        val sets = sessions.flatMap { s -> s.sets.filter { it.exerciseName == name }.map { s.startMillis to it } }
        if (sets.isEmpty()) return null
        val seconds = sets.none { it.second.reps != null } && sets.any { it.second.seconds != null }
        val points = sets.mapNotNull { (ms, r) ->
            val v = if (seconds) r.seconds else r.reps
            v?.let { Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay() to it }
        }.groupBy({ it.first }, { it.second }).map { (d, v) -> ExercisePoint(d, v.max()) }.sortedBy { it.epochDay }
        return if (points.isEmpty()) null else ExerciseSeries(name, seconds, points)
    }
}
