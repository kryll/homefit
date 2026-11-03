package com.kryll.homefit

import com.kryll.homefit.domain.Deload
import com.kryll.homefit.domain.DeloadReason
import com.kryll.homefit.domain.LoggedSet
import com.kryll.homefit.domain.Prescribed
import com.kryll.homefit.domain.Progression
import com.kryll.homefit.domain.SessionSets
import com.kryll.homefit.domain.Suggestion
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionTest {
    private fun sets(n: Int, rpe: Int?) = List(n) { LoggedSet(rpe) }
    private val reps = Prescribed("sentadilla", 3, 10, null)
    private val timed = Prescribed("plancha", 3, null, 30)

    @Test fun noHistoryGivesNoData() = assertEquals(Suggestion.NoData, Progression.suggest(reps, emptyList(), null))

    @Test fun missingSetsHolds() =
        assertEquals(Suggestion.Hold(Suggestion.Reason.INCOMPLETE), Progression.suggest(reps, sets(2, 6), null))

    @Test fun noRpeHolds() =
        assertEquals(Suggestion.Hold(Suggestion.Reason.NO_RPE), Progression.suggest(reps, sets(3, null), null))

    @Test fun easyEffortAddsRepsUpToCeiling() {
        assertEquals(Suggestion.AddReps(10, 12), Progression.suggest(reps, sets(3, 6), null))
        assertEquals(Suggestion.AddReps(14, 15), Progression.suggest(reps.copy(reps = 14), sets(3, 7), null))
    }

    @Test fun easyEffortAddsSecondsForTimed() =
        assertEquals(Suggestion.AddSeconds(30, 35), Progression.suggest(timed, sets(3, 7), null))

    @Test fun atCeilingGoesToHarderVariantThenSetThenHolds() {
        val top = reps.copy(reps = 15)
        assertEquals(Suggestion.HarderVariant("flexiones_diamante"), Progression.suggest(top, sets(3, 6), "flexiones_diamante"))
        assertEquals(Suggestion.AddSet(3, 4), Progression.suggest(top, sets(3, 6), null))
        assertEquals(Suggestion.Hold(Suggestion.Reason.AT_CEILING), Progression.suggest(top.copy(sets = 5), sets(5, 6), null))
    }

    @Test fun mediumEffortHolds() =
        assertEquals(Suggestion.Hold(Suggestion.Reason.RIGHT_EFFORT), Progression.suggest(reps, sets(3, 8), null))

    @Test fun veryHardReducesWithFloor() {
        assertEquals(Suggestion.Reduce(10, 8, false), Progression.suggest(reps, sets(3, 10), null))
        assertEquals(Suggestion.Reduce(30, 25, true), Progression.suggest(timed, sets(3, 10), null))
        assertEquals(Suggestion.Hold(Suggestion.Reason.RIGHT_EFFORT), Progression.suggest(reps.copy(reps = 5), sets(3, 10), null))
    }

    @Test fun forRoutineUsesLatestSessionOfThatRoutineWithThatExercise() {
        val newest = SessionSets(1, 3000, mapOf("sentadilla" to sets(3, 6)))
        val otherRoutine = SessionSets(2, 2500, mapOf("sentadilla" to sets(3, 10)))
        val older = SessionSets(1, 1000, mapOf("sentadilla" to sets(3, 10), "plancha" to sets(3, 6)))
        val res = Progression.forRoutine(1, listOf(reps, timed), listOf(newest, otherRoutine, older)) { null }
        assertEquals(Suggestion.AddReps(10, 12), res["sentadilla"])
        assertEquals(Suggestion.AddSeconds(30, 35), res["plancha"]) // viene de la sesion mas antigua que la contiene
    }

    @Test fun deloadSetsKeepsAtLeastOne() {
        assertEquals(2, Progression.deloadSets(3))
        assertEquals(1, Progression.deloadSets(1))
        assertEquals(3, Progression.deloadSets(5))
    }

    // 2026-11-04 es miercoles; semana actual desde lunes 2 nov.
    private val zone = ZoneId.of("UTC")
    private fun ms(m: Int, d: Int) = LocalDateTime.of(2026, m, d, 10, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val now = LocalDateTime.of(2026, 11, 4, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun s(m: Int, d: Int, rpe: Int = 7) = SessionSets(1, ms(m, d), mapOf("x" to sets(3, rpe)))

    @Test fun noHistoryNoDeload() = assertNull(Deload.advice(emptyList(), now, zone))

    @Test fun threeFullWeeksWithTwoSessionsSuggestsDeload() {
        val sessions = listOf(
            s(10, 12), s(10, 14), // semana 12-18 oct... (lunes 12 oct)
            s(10, 19), s(10, 21),
            s(10, 26), s(10, 28),
        )
        assertEquals(DeloadReason.LONG_BLOCK, Deload.advice(sessions, now, zone))
    }

    @Test fun missingWeekBreaksTheBlock() {
        val sessions = listOf(s(10, 12), s(10, 14), s(10, 26), s(10, 28))
        assertNull(Deload.advice(sessions, now, zone))
    }

    @Test fun oneSessionWeeksDoNotCount() {
        val sessions = listOf(s(10, 12), s(10, 19), s(10, 26))
        assertNull(Deload.advice(sessions, now, zone))
    }

    @Test fun veryHardLastWeekSuggestsDeload() {
        val sessions = listOf(s(10, 26, 9), s(10, 28, 10))
        assertEquals(DeloadReason.HIGH_EFFORT, Deload.advice(sessions, now, zone))
    }

    @Test fun currentWeekDoesNotCountAsComplete() {
        val sessions = listOf(s(11, 2, 10), s(11, 3, 10))
        assertNull(Deload.advice(sessions, now, zone))
    }
}
