package com.kryll.homefit.domain

import java.time.LocalDate
import java.time.ZonedDateTime

data class ReminderPrefs(val enabled: Boolean = false, val hour: Int = 18, val minute: Int = 0) {
    init {
        require(hour in 0..23 && minute in 0..59) { "Hora no válida" }
    }
}

data class PlannedDay(val date: LocalDate, val routineId: Long)

enum class ReminderDecision { SHOW, SKIP_DISABLED, SKIP_REST_DAY, SKIP_ALREADY_TRAINED }

/** Lógica pura de planificación y recordatorios; la parte de Android solo la ejecuta. */
object Planning {
    /** Bit 0 = lunes ... bit 6 = domingo (igual que el perfil). */
    fun isTrainingDay(daysMask: Int, d: LocalDate): Boolean = daysMask and (1 shl (d.dayOfWeek.value - 1)) != 0

    /** Primera hora de aviso estrictamente posterior a [from], en un día de entreno. Null si no hay días elegidos. */
    fun nextReminder(from: ZonedDateTime, daysMask: Int, prefs: ReminderPrefs): ZonedDateTime? {
        if (daysMask and 0x7F == 0) return null
        for (i in 0..7) {
            val day = from.toLocalDate().plusDays(i.toLong())
            if (!isTrainingDay(daysMask, day)) continue
            val at = day.atTime(prefs.hour, prefs.minute).atZone(from.zone)
            if (at.isAfter(from)) return at
        }
        return null
    }

    fun decide(prefs: ReminderPrefs, daysMask: Int, today: LocalDate, trainedToday: Boolean): ReminderDecision = when {
        !prefs.enabled -> ReminderDecision.SKIP_DISABLED
        !isTrainingDay(daysMask, today) -> ReminderDecision.SKIP_REST_DAY
        trainedToday -> ReminderDecision.SKIP_ALREADY_TRAINED
        else -> ReminderDecision.SHOW
    }

    /** Reparte las rutinas, por turnos, entre los días de entreno de los próximos [days] días desde [from] (incluido). */
    fun autoPlan(daysMask: Int, routineIds: List<Long>, from: LocalDate, days: Int = 7): List<PlannedDay> {
        if (routineIds.isEmpty()) return emptyList()
        var k = 0
        return (0 until days).mapNotNull { i ->
            val d = from.plusDays(i.toLong())
            if (isTrainingDay(daysMask, d)) PlannedDay(d, routineIds[k++ % routineIds.size]) else null
        }
    }

    fun stepHour(p: ReminderPrefs, delta: Int) = p.copy(hour = Math.floorMod(p.hour + delta, 24))

    /** Pasos de 5 minutos; al pasar de 55 a 00 no cambia la hora (se ajusta con los botones de hora). */
    fun stepMinute(p: ReminderPrefs, delta: Int) = p.copy(minute = Math.floorMod(p.minute + delta, 60))
}
