package com.kryll.homefit

import com.kryll.homefit.data.SetLog
import com.kryll.homefit.data.WorkoutSession
import com.kryll.homefit.domain.Prescribed
import com.kryll.homefit.domain.Progression
import com.kryll.homefit.domain.ProgressionData
import com.kryll.homefit.domain.Suggestion
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressionDataTest {
    private val session = WorkoutSession(id = 1, routineId = 1, startMillis = 1000, endMillis = 2000, durationSeconds = 1)
    private val reps = Prescribed("sentadilla", 3, 10, null)
    private val timed = Prescribed("plancha", 3, null, 30)

    private fun suggestFor(item: Prescribed, sets: List<SetLog>): Suggestion {
        val data = ProgressionData.sessionSets(listOf(session), sets)
        return Progression.forRoutine(1, listOf(item), data) { null }.getValue(item.exerciseId)
    }

    private fun log(ex: String, i: Int, reps: Int? = null, secs: Int? = null, rpe: Int? = 6) =
        SetLog(sessionId = 1, exerciseId = ex, setIndex = i, reps = reps, seconds = secs, rpe = rpe)

    @Test fun realRepsAtTargetAllowRaise() =
        assertEquals(Suggestion.AddReps(10, 12), suggestFor(reps, (1..3).map { log("sentadilla", it, reps = 10) }))

    @Test fun oneSetBelowTargetHolds() =
        assertEquals(
            Suggestion.Hold(Suggestion.Reason.NO_ACHIEVED),
            suggestFor(reps, listOf(log("sentadilla", 1, reps = 10), log("sentadilla", 2, reps = 8), log("sentadilla", 3, reps = 10))),
        )

    @Test fun unrecordedRepsHold() =
        assertEquals(Suggestion.Hold(Suggestion.Reason.NO_ACHIEVED), suggestFor(reps, (1..3).map { log("sentadilla", it) }))

    @Test fun timedUsesRealSecondsHeld() {
        assertEquals(Suggestion.AddSeconds(30, 35), suggestFor(timed, (1..3).map { log("plancha", it, secs = 30) }))
        assertEquals(Suggestion.Hold(Suggestion.Reason.NO_ACHIEVED), suggestFor(timed, (1..3).map { log("plancha", it, secs = 22) }))
    }
}
