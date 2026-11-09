package com.kryll.homefit.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** [hour]:[minute] es la hora de entrenar; el aviso sale [leadMinutes] antes (0 = a esa hora). */
data class ReminderPrefs(val enabled: Boolean = false, val hour: Int = 18, val minute: Int = 0, val leadMinutes: Int = 0, val exact: Boolean = false) {
    init {
        require(hour in 0..23 && minute in 0..59) { "Hora no válida" }
        require(leadMinutes in 0..120) { "Antelación no válida" }
    }
}

data class ReminderSession(val startMillis: Long, val status: String)

data class PlannedDay(val date: LocalDate, val routineId: Long)

/** Estado de una sesión planificada. Solo SCHEDULED admite cambios; el resto son finales. */
enum class SessionStatus { SCHEDULED, COMPLETED, SKIPPED, MOVED }

/** Valores desconocidos se tratan como SCHEDULED para no bloquear sesiones antiguas. */
fun sessionStatusOf(raw: String): SessionStatus =
    runCatching { SessionStatus.valueOf(raw) }.getOrDefault(SessionStatus.SCHEDULED)

fun canChangeSessionStatus(raw: String): Boolean = sessionStatusOf(raw) == SessionStatus.SCHEDULED

/** Mismo horario, otro día: conserva la hora local de [startMillis] al moverlo a [newDate]. */
fun movedStartMillis(startMillis: Long, zone: ZoneId, newDate: LocalDate): Long {
    val time = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalTime()
    return newDate.atTime(time).atZone(zone).toInstant().toEpochMilli()
}

enum class ReminderDecision { SHOW, SKIP_DISABLED, SKIP_REST_DAY, SKIP_ALREADY_TRAINED, SKIP_DAY_SETTLED }

/** Lógica pura de planificación y recordatorios; la parte de Android solo la ejecuta. */
object Planning {
    /** Rellena solo huecos futuros del periodo semiabierto, con una rotación continua de rutinas. */
    fun fillPeriod(daysMask: Int, routineIds: List<Long>, from: LocalDate, until: LocalDate, today: LocalDate, occupied: Set<LocalDate>): List<PlannedDay> {
        if (routineIds.isEmpty()) return emptyList()
        val start = maxOf(from, today)
        var k = 0
        return generateSequence(start) { it.plusDays(1) }.takeWhile { it < until }
            .filter { isTrainingDay(daysMask, it) && it !in occupied }
            .map { PlannedDay(it, routineIds[k++ % routineIds.size]) }.toList()
    }

    /** Bit 0 = lunes ... bit 6 = domingo (igual que el perfil). */
    fun isTrainingDay(daysMask: Int, d: LocalDate): Boolean = daysMask and (1 shl (d.dayOfWeek.value - 1)) != 0

    /** Las fechas planificadas mandan sobre la plantilla semanal; un origen movido no avisa. */
    fun nextWithSessions(from: ZonedDateTime, daysMask: Int, prefs: ReminderPrefs, sessions: List<ReminderSession>): ZonedDateTime? {
        val grouped = sessions.groupBy { Instant.ofEpochMilli(it.startMillis).atZone(from.zone).toLocalDate() }
        val dates = ((0..7).map { from.toLocalDate().plusDays(it.toLong()) } + grouped.keys.flatMap { day -> (0..7).map { day.plusDays(it.toLong()) } }).filter { it >= from.toLocalDate() }.sorted()
        return dates.mapNotNull { date ->
            val entries = grouped[date].orEmpty()
            if (entries.isNotEmpty()) {
                entries.filter { sessionStatusOf(it.status) == SessionStatus.SCHEDULED }.map {
                    val time = Instant.ofEpochMilli(it.startMillis).atZone(from.zone).toLocalTime()
                    val minutes = (time.hour * 60 + time.minute - prefs.leadMinutes).coerceAtLeast(0)
                    date.atTime(minutes / 60, minutes % 60).atZone(from.zone)
                }.filter { it.isAfter(from) }.minOrNull()
            } else if (isTrainingDay(daysMask, date)) date.atTime(notifyTime(prefs)).atZone(from.zone).takeIf { it.isAfter(from) }
            else null
        }.minOrNull()
    }

    /** Primera hora de aviso estrictamente posterior a [from], en un día de entreno. Null si no hay días elegidos. */
    fun nextReminder(from: ZonedDateTime, daysMask: Int, prefs: ReminderPrefs): ZonedDateTime? {
        if (daysMask and 0x7F == 0) return null
        for (i in 0..7) {
            val day = from.toLocalDate().plusDays(i.toLong())
            if (!isTrainingDay(daysMask, day)) continue
            // El aviso nunca pasa al día anterior: con antelación mayor que la hora, sale a las 00:00 de ese día.
            val at = day.atTime(notifyTime(prefs)).atZone(from.zone)
            if (at.isAfter(from)) return at
        }
        return null
    }

    /** [todayStatuses]: estados de las sesiones planificadas hoy; si hay y ninguna sigue pendiente, no se avisa. */
    fun decide(prefs: ReminderPrefs, daysMask: Int, today: LocalDate, trainedToday: Boolean, todayStatuses: List<String>? = null): ReminderDecision = when {
        !prefs.enabled -> ReminderDecision.SKIP_DISABLED
        !isTrainingDay(daysMask, today) && todayStatuses.orEmpty().none { sessionStatusOf(it) == SessionStatus.SCHEDULED } -> ReminderDecision.SKIP_REST_DAY
        trainedToday -> ReminderDecision.SKIP_ALREADY_TRAINED
        todayStatuses != null && todayStatuses.isNotEmpty() && todayStatuses.none { sessionStatusOf(it) == SessionStatus.SCHEDULED } -> ReminderDecision.SKIP_DAY_SETTLED
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

    /** Hora a la que sale el aviso: la de entrenar menos la antelación, sin pasar de las 00:00. */
    fun notifyTime(p: ReminderPrefs): java.time.LocalTime =
        java.time.LocalTime.ofSecondOfDay(((p.hour * 60 + p.minute - p.leadMinutes).coerceAtLeast(0) * 60).toLong())

    val LEAD_OPTIONS = listOf(0, 5, 10, 15, 30, 60, 120)

    /** Sube o baja entre las antelaciones permitidas; no pasa de los extremos. */
    fun stepLead(p: ReminderPrefs, delta: Int): ReminderPrefs {
        val i = LEAD_OPTIONS.indexOf(p.leadMinutes).let { if (it < 0) LEAD_OPTIONS.indexOfLast { o -> o <= p.leadMinutes }.coerceAtLeast(0) else it }
        return p.copy(leadMinutes = LEAD_OPTIONS[(i + delta).coerceIn(0, LEAD_OPTIONS.lastIndex)])
    }

    fun stepHour(p: ReminderPrefs, delta: Int) = p.copy(hour = Math.floorMod(p.hour + delta, 24))

    /** Pasos de 5 minutos; al pasar de 55 a 00 no cambia la hora (se ajusta con los botones de hora). */
    fun stepMinute(p: ReminderPrefs, delta: Int) = p.copy(minute = Math.floorMod(p.minute + delta, 60))
}
