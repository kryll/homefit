package com.kryll.homefit.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Lo que queda registrado de una serie hecha (las saltadas no se registran). */
data class LoggedSet(val rpe: Int?, val repsDone: Int? = null, val secondsDone: Int? = null)

/** Ejercicio tal como lo prescribe la rutina. */
data class Prescribed(val exerciseId: String, val sets: Int, val reps: Int?, val seconds: Int?)

sealed class Suggestion {
    /** Aún no hay historial de este ejercicio en esta rutina. */
    object NoData : Suggestion()
    /** Mantener: el esfuerzo es el adecuado o falta información. */
    data class Hold(val reason: Reason) : Suggestion()
    data class AddReps(val from: Int, val to: Int) : Suggestion()
    data class AddSeconds(val from: Int, val to: Int) : Suggestion()
    data class HarderVariant(val variantId: String) : Suggestion()
    data class AddSet(val from: Int, val to: Int) : Suggestion()
    data class Reduce(val from: Int, val to: Int, val timed: Boolean) : Suggestion()

    enum class Reason { INCOMPLETE, NO_RPE, RIGHT_EFFORT, AT_CEILING, NO_ACHIEVED, DELOAD }
}

object ProgressionConfig {
    const val REPS_CEILING = 15
    const val REPS_FLOOR = 5
    const val SECONDS_CEILING = 60
    const val SECONDS_FLOOR = 15
    const val MAX_SETS = 5
    const val EASY_RPE = 7.0
    const val HARD_RPE = 9.5
    const val DELOAD_TRAIN_WEEKS = 3
    const val DELOAD_MIN_SESSIONS_PER_WEEK = 2
    const val DELOAD_RPE = 9.0
    const val DELOAD_VOLUME = 0.6
}

/**
 * Doble progresión sin carga: primero se sube la repetición (o el tiempo) hasta el techo con esfuerzo bajo,
 * y al llegar al techo se pasa a la variante más difícil, o se añade una serie. Se decide con las series
 * hechas y el RPE de la última sesión de esa rutina. Es una sugerencia: no cambia la rutina sola.
 */
object Progression {
    fun suggest(item: Prescribed, last: List<LoggedSet>, harderVariantId: String?): Suggestion {
        if (last.isEmpty()) return Suggestion.NoData
        if (last.size < item.sets) return Suggestion.Hold(Suggestion.Reason.INCOMPLETE)
        val rpes = last.mapNotNull { it.rpe }
        if (rpes.isEmpty()) return Suggestion.Hold(Suggestion.Reason.NO_RPE)
        val avg = rpes.average()
        val timed = item.seconds != null
        val value = item.seconds ?: item.reps ?: return Suggestion.NoData
        if (avg >= ProgressionConfig.HARD_RPE) {
            val floor = if (timed) ProgressionConfig.SECONDS_FLOOR else ProgressionConfig.REPS_FLOOR
            val step = if (timed) 5 else 2
            val to = maxOf(floor, value - step)
            return if (to < value) Suggestion.Reduce(value, to, timed) else Suggestion.Hold(Suggestion.Reason.RIGHT_EFFORT)
        }
        if (avg > ProgressionConfig.EASY_RPE) return Suggestion.Hold(Suggestion.Reason.RIGHT_EFFORT)
        // Subir exige resultados reales: lo prescrito no prueba que se lograra.
        val done = last.map { if (timed) it.secondsDone else it.repsDone }
        if (done.any { it == null || it < value }) return Suggestion.Hold(Suggestion.Reason.NO_ACHIEVED)
        val ceiling = if (timed) ProgressionConfig.SECONDS_CEILING else ProgressionConfig.REPS_CEILING
        if (value < ceiling) {
            val to = minOf(ceiling, value + if (timed) 5 else 2)
            return if (timed) Suggestion.AddSeconds(value, to) else Suggestion.AddReps(value, to)
        }
        if (harderVariantId != null) return Suggestion.HarderVariant(harderVariantId)
        if (item.sets < ProgressionConfig.MAX_SETS) return Suggestion.AddSet(item.sets, item.sets + 1)
        return Suggestion.Hold(Suggestion.Reason.AT_CEILING)
    }

    /** Sugerencias de una rutina: para cada ejercicio, con las series de su última sesión que lo contiene. */
    fun forRoutine(
        routineId: Long,
        items: List<Prescribed>,
        sessionsNewestFirst: List<SessionSets>,
        deload: Boolean = false,
        harderOf: (String) -> String?,
    ): Map<String, Suggestion> = items.associate { item ->
        val last = sessionsNewestFirst.firstOrNull { it.routineId == routineId && it.byExercise.containsKey(item.exerciseId) }
            ?.byExercise?.get(item.exerciseId).orEmpty()
        val sg = suggest(item, last, harderOf(item.exerciseId))
        val raises = sg is Suggestion.AddReps || sg is Suggestion.AddSeconds || sg is Suggestion.HarderVariant || sg is Suggestion.AddSet
        item.exerciseId to if (deload && raises) Suggestion.Hold(Suggestion.Reason.DELOAD) else sg
    }

    fun deloadSets(sets: Int): Int = maxOf(1, Math.round(sets * ProgressionConfig.DELOAD_VOLUME).toInt())
}

/** Una sesión del historial con sus series agrupadas por ejercicio. */
data class SessionSets(val routineId: Long?, val startMillis: Long, val byExercise: Map<String, List<LoggedSet>>)

enum class DeloadReason { LONG_BLOCK, HIGH_EFFORT }

object Deload {
    /**
     * Sugiere una semana de descarga si las últimas [ProgressionConfig.DELOAD_TRAIN_WEEKS] semanas completas
     * tuvieron cada una al menos 2 entrenos (bloque largo), o si la última semana completa fue muy dura
     * (RPE medio >= 9). La semana en curso no cuenta como completa. Devuelve null si no hace falta.
     */
    fun advice(sessions: List<SessionSets>, nowMillis: Long, zone: ZoneId): DeloadReason? {
        val thisWeek = weekStart(Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate())
        val byWeek = sessions.groupBy { weekStart(Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalDate()) }
        val last = thisWeek.minusWeeks(1)
        val lastSessions = byWeek[last].orEmpty()
        val rpes = lastSessions.flatMap { s -> s.byExercise.values.flatten().mapNotNull { it.rpe } }
        if (lastSessions.size >= ProgressionConfig.DELOAD_MIN_SESSIONS_PER_WEEK && rpes.isNotEmpty() && rpes.average() >= ProgressionConfig.DELOAD_RPE) {
            return DeloadReason.HIGH_EFFORT
        }
        val block = (1..ProgressionConfig.DELOAD_TRAIN_WEEKS).all {
            byWeek[thisWeek.minusWeeks(it.toLong())].orEmpty().size >= ProgressionConfig.DELOAD_MIN_SESSIONS_PER_WEEK
        }
        // Si ya hay entrenos esta semana con menos volumen no se insiste; se calcula solo con semanas completas.
        return if (block) DeloadReason.LONG_BLOCK else null
    }

    private fun weekStart(d: LocalDate): LocalDate = d.with(DayOfWeek.MONDAY)
}
