package com.kryll.homefit

import com.kryll.homefit.domain.Planning
import com.kryll.homefit.domain.PlannedDay
import com.kryll.homefit.domain.ReminderDecision
import com.kryll.homefit.domain.ReminderPrefs
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanningTest {
    private val zone = ZoneId.of("Europe/Madrid")
    // 2026-11-04 es miércoles (lunes = 2 nov).
    private val wed = LocalDate.of(2026, 11, 4)
    private fun at(d: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 11, d, h, m, 0, 0, zone)
    private val monWedFri = 0b0010101
    private val prefs = ReminderPrefs(true, 18, 30)

    @Test fun trainingDayUsesProfileMask() {
        assertTrue(Planning.isTrainingDay(monWedFri, wed))
        assertTrue(!Planning.isTrainingDay(monWedFri, wed.plusDays(1)))
        assertTrue(Planning.isTrainingDay(0b1000000, LocalDate.of(2026, 11, 8))) // domingo
    }

    @Test fun nextReminderTodayIfStillAhead() = assertEquals(at(4, 18, 30), Planning.nextReminder(at(4, 9), monWedFri, prefs))

    @Test fun nextReminderSkipsToNextTrainingDayIfPassed() = assertEquals(at(6, 18, 30), Planning.nextReminder(at(4, 19), monWedFri, prefs))

    @Test fun nextReminderIsStrictlyAfterNow() = assertEquals(at(6, 18, 30), Planning.nextReminder(at(4, 18, 30), monWedFri, prefs))

    @Test fun nextReminderWrapsToNextWeek() = assertEquals(at(9, 18, 30), Planning.nextReminder(at(7, 12), monWedFri, prefs))

    @Test fun noDaysMeansNoReminder() = assertNull(Planning.nextReminder(at(4, 9), 0, prefs))

    @Test fun reminderHoldsLocalTimeAcrossDstChange() {
        // El cambio a horario de invierno fue el 25 oct 2026; la hora local se mantiene.
        val from = ZonedDateTime.of(2026, 10, 23, 20, 0, 0, 0, zone) // viernes
        val next = Planning.nextReminder(from, 0b0000001, ReminderPrefs(true, 8, 0)) // lunes
        assertEquals(ZonedDateTime.of(2026, 10, 26, 8, 0, 0, 0, zone), next)
    }

    @Test fun decisions() {
        assertEquals(ReminderDecision.SKIP_DISABLED, Planning.decide(ReminderPrefs(false), monWedFri, wed, false))
        assertEquals(ReminderDecision.SKIP_REST_DAY, Planning.decide(prefs, monWedFri, wed.plusDays(1), false))
        assertEquals(ReminderDecision.SKIP_ALREADY_TRAINED, Planning.decide(prefs, monWedFri, wed, true))
        assertEquals(ReminderDecision.SHOW, Planning.decide(prefs, monWedFri, wed, false))
    }

    @Test fun decideSkipsWhenNoSessionPendingToday() {
        val prefs = ReminderPrefs(true, 18, 0)
        assertEquals(ReminderDecision.SKIP_DAY_SETTLED, Planning.decide(prefs, monWedFri, wed, false, listOf("SKIPPED", "MOVED")))
        assertEquals(ReminderDecision.SKIP_DAY_SETTLED, Planning.decide(prefs, monWedFri, wed, false, listOf("COMPLETED")))
        assertEquals(ReminderDecision.SHOW, Planning.decide(prefs, monWedFri, wed, false, listOf("SKIPPED", "SCHEDULED")))
        assertEquals(ReminderDecision.SHOW, Planning.decide(prefs, monWedFri, wed, false, emptyList()))
        assertEquals(ReminderDecision.SKIP_ALREADY_TRAINED, Planning.decide(prefs, monWedFri, wed, true, listOf("SCHEDULED")))
    }

    @Test fun sessionStatusParsingAndChanges() {
        assertEquals(com.kryll.homefit.domain.SessionStatus.SCHEDULED, com.kryll.homefit.domain.sessionStatusOf("desconocido"))
        assertEquals(com.kryll.homefit.domain.SessionStatus.SKIPPED, com.kryll.homefit.domain.sessionStatusOf("SKIPPED"))
        assertTrue(com.kryll.homefit.domain.canChangeSessionStatus("SCHEDULED"))
        assertTrue(!com.kryll.homefit.domain.canChangeSessionStatus("SKIPPED"))
        assertTrue(!com.kryll.homefit.domain.canChangeSessionStatus("MOVED"))
    }

    @Test fun moveKeepsLocalTime() {
        val from = ZonedDateTime.of(2026, 11, 4, 18, 30, 0, 0, zone)
        val moved = com.kryll.homefit.domain.movedStartMillis(from.toInstant().toEpochMilli(), zone, LocalDate.of(2026, 11, 9))
        assertEquals(ZonedDateTime.of(2026, 11, 9, 18, 30, 0, 0, zone).toInstant().toEpochMilli(), moved)
    }

    @Test fun autoPlanRotatesRoutinesOverTrainingDaysOnly() {
        val plan = Planning.autoPlan(monWedFri, listOf(10, 20), wed)
        assertEquals(
            listOf(PlannedDay(wed, 10), PlannedDay(LocalDate.of(2026, 11, 6), 20), PlannedDay(LocalDate.of(2026, 11, 9), 10)),
            plan,
        )
    }

    @Test fun autoPlanWithoutRoutinesIsEmpty() = assertEquals(emptyList<PlannedDay>(), Planning.autoPlan(monWedFri, emptyList(), wed))

    @Test fun steppersWrapAround() {
        assertEquals(0, Planning.stepHour(ReminderPrefs(true, 23, 0), 1).hour)
        assertEquals(23, Planning.stepHour(ReminderPrefs(true, 0, 0), -1).hour)
        assertEquals(0, Planning.stepMinute(ReminderPrefs(true, 8, 55), 5).minute)
        assertEquals(55, Planning.stepMinute(ReminderPrefs(true, 8, 0), -5).minute)
    }

    @Test fun leadMovesTheNotificationEarlier() {
        val p = ReminderPrefs(true, 18, 30, leadMinutes = 15)
        assertEquals(at(4, 18, 15), Planning.nextReminder(at(4, 9), monWedFri, p))
        // si ya pasó el aviso de hoy (aunque no la hora de entrenar), toca el siguiente día
        assertEquals(at(6, 18, 15), Planning.nextReminder(at(4, 18, 20), monWedFri, p))
    }

    @Test fun leadNeverCrossesIntoThePreviousDay() {
        val p = ReminderPrefs(true, 0, 20, leadMinutes = 60)
        assertEquals(java.time.LocalTime.MIDNIGHT, Planning.notifyTime(p))
        assertEquals(at(4, 0, 0), Planning.nextReminder(at(3, 23), monWedFri, p))
    }

    @Test fun leadStepsThroughAllowedOptionsAndStopsAtTheEnds() {
        var p = ReminderPrefs(true)
        assertEquals(0, p.leadMinutes)
        p = Planning.stepLead(p, -1); assertEquals(0, p.leadMinutes)
        p = Planning.stepLead(p, 1); assertEquals(5, p.leadMinutes)
        p = Planning.stepLead(p, 1); p = Planning.stepLead(p, 1); assertEquals(15, p.leadMinutes)
        repeat(10) { p = Planning.stepLead(p, 1) }
        assertEquals(120, p.leadMinutes)
        assertEquals(java.time.LocalTime.of(16, 30), Planning.notifyTime(ReminderPrefs(true, 18, 30, 120)))
    }

    @Test fun invalidLeadIsRejected() {
        assertTrue(runCatching { ReminderPrefs(true, 18, 0, 121) }.isFailure)
        assertTrue(runCatching { ReminderPrefs(true, 18, 0, -1) }.isFailure)
    }

    @Test fun fillsOnlyFutureFreeDaysAcrossRealMonths() {
        for (month in listOf(java.time.YearMonth.of(2028, 2), java.time.YearMonth.of(2027, 2), java.time.YearMonth.of(2026, 12))) {
            val from = month.atDay(1)
            val today = month.atDay(5)
            val occupied = setOf(month.atDay(8), month.atDay(15))
            val plan = Planning.fillPeriod(127, listOf(1L, 2L), from, month.plusMonths(1).atDay(1), today, occupied)
            assertEquals(month.lengthOfMonth() - 4 - 2, plan.size)
            assertEquals(today, plan.first().date)
            assertEquals(month.atEndOfMonth(), plan.last().date)
            assertTrue(plan.none { it.date in occupied || it.date < today })
            assertEquals(listOf(1L, 2L, 1L, 2L), plan.take(4).map { it.routineId })
        }
    }

    @Test fun movedReminderUsesDestinationEvenOnRestDayAndSuppressesOrigin() {
        val zone = ZoneId.of("Europe/Madrid")
        val from = LocalDate.of(2026, 12, 28) // lunes
        val to = LocalDate.of(2027, 1, 3) // domingo, fuera de plantilla
        val p = ReminderPrefs(enabled = true, hour = 18, minute = 30, leadMinutes = 15)
        val sessions = listOf(
            com.kryll.homefit.domain.ReminderSession(from.atTime(18, 30).atZone(zone).toInstant().toEpochMilli(), "MOVED"),
            com.kryll.homefit.domain.ReminderSession(to.atTime(18, 30).atZone(zone).toInstant().toEpochMilli(), "SCHEDULED"))
        assertEquals(to.atTime(18, 15).atZone(zone), Planning.nextWithSessions(from.atStartOfDay(zone), 1, p, sessions))
        assertEquals(com.kryll.homefit.domain.ReminderDecision.SHOW, Planning.decide(p, 1, to, false, listOf("SCHEDULED")))
        assertEquals(com.kryll.homefit.domain.ReminderDecision.SKIP_DAY_SETTLED, Planning.decide(p, 1, from, false, listOf("MOVED")))
    }
}
