package com.kryll.homefit

import com.kryll.homefit.domain.Cue
import com.kryll.homefit.domain.Phase
import com.kryll.homefit.domain.WorkItem
import com.kryll.homefit.domain.WorkoutEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutEngineTest {
    private val squat = WorkItem("sentadilla", "Sentadilla", sets = 2, reps = 10, seconds = null, restSeconds = 30)
    private val plank = WorkItem("plancha", "Plancha", sets = 1, reps = null, seconds = 20, restSeconds = 0)

    private fun WorkoutEngine.ticks(n: Int) = repeat(n) { tick() }

    @Test fun startsWithGetReadyThenWork() {
        val e = WorkoutEngine(listOf(squat), getReadySeconds = 3)
        assertEquals(Phase.GET_READY, e.state().phase)
        assertTrue(e.drainCues().first() is Cue.GetReady)
        e.ticks(2); assertEquals(Phase.GET_READY, e.state().phase)
        e.tick()
        assertEquals(Phase.WORK, e.state().phase)
        assertTrue(e.drainCues().any { it is Cue.Go })
    }

    @Test fun countdownBeepsLastThreeSeconds() {
        val e = WorkoutEngine(listOf(squat), getReadySeconds = 5)
        e.drainCues(); e.ticks(5)
        val left = e.drainCues().filterIsInstance<Cue.Countdown>().map { it.secondsLeft }
        assertEquals(listOf(3, 2, 1), left)
    }

    @Test fun repsSetEndsWithDoneThenRateThenRest() {
        val e = WorkoutEngine(listOf(squat), getReadySeconds = 0)
        assertEquals(Phase.WORK, e.state().phase)
        e.ticks(7); assertEquals(7, e.state().remaining)
        e.done()
        assertEquals(Phase.RATE, e.state().phase)
        e.rate(8)
        assertEquals(Phase.REST, e.state().phase)
        assertEquals(30, e.state().remaining)
        assertEquals(8, e.setResults[0].rpe); assertEquals(7, e.setResults[0].workSeconds)
        e.ticks(30)
        assertEquals(Phase.WORK, e.state().phase); assertEquals(2, e.state().set)
    }

    @Test fun timedSetEndsByItself() {
        val e = WorkoutEngine(listOf(plank), getReadySeconds = 0)
        e.ticks(19); assertEquals(Phase.WORK, e.state().phase); assertEquals(1, e.state().remaining)
        e.tick()
        assertEquals(Phase.RATE, e.state().phase)
        assertEquals(20, e.setResults[0].workSeconds)
    }

    @Test fun halfwayCueOnlyForLongSets() {
        val e = WorkoutEngine(listOf(plank), getReadySeconds = 0)
        e.drainCues(); e.ticks(10)
        assertTrue(e.drainCues().any { it is Cue.Halfway })
    }

    @Test fun pauseStopsTheClock() {
        val e = WorkoutEngine(listOf(plank), getReadySeconds = 0)
        e.ticks(5); e.pause(); e.ticks(100)
        assertEquals(15, e.state().remaining)
        assertTrue(e.state().paused)
        e.done()
        assertEquals(Phase.WORK, e.state().phase)
        e.resume(); e.tick()
        assertEquals(14, e.state().remaining)
    }

    @Test fun skipWorkMarksSetSkippedAndGoesOn() {
        val e = WorkoutEngine(listOf(squat), getReadySeconds = 0)
        e.skip()
        assertTrue(e.setResults[0].skipped); assertNull(e.setResults[0].rpe)
        assertEquals(Phase.REST, e.state().phase)
        e.skip()
        assertEquals(Phase.WORK, e.state().phase); assertEquals(2, e.state().set)
    }

    @Test fun skipRateKeepsNoRpe() {
        val e = WorkoutEngine(listOf(squat), getReadySeconds = 0)
        e.done(); e.skip()
        assertNull(e.setResults[0].rpe); assertFalse(e.setResults[0].skipped)
    }

    @Test fun rpeOutOfRangeIsRejected() {
        val e = WorkoutEngine(listOf(squat), getReadySeconds = 0)
        e.done()
        try { e.rate(11); throw AssertionError("debía fallar") } catch (_: IllegalArgumentException) {}
        try { e.rate(0); throw AssertionError("debía fallar") } catch (_: IllegalArgumentException) {}
        assertEquals(Phase.RATE, e.state().phase)
    }

    @Test fun movesToNextExerciseWithGetReadyAndRestAnnouncesIt() {
        val one = WorkItem("a", "A", 1, 5, null, 10)
        val two = WorkItem("b", "B", 1, 5, null, 10)
        val e = WorkoutEngine(listOf(one, two), getReadySeconds = 2)
        e.drainCues(); e.ticks(2); e.done(); e.rate(null)
        val rest = e.drainCues().filterIsInstance<Cue.Rest>().single()
        assertEquals("B", rest.nextName)
        e.ticks(10)
        assertEquals(Phase.GET_READY, e.state().phase); assertEquals(1, e.state().itemIndex)
    }

    @Test fun finishesAfterLastSetAndCountsSets() {
        val e = WorkoutEngine(listOf(squat, plank), getReadySeconds = 0)
        assertEquals(3, e.totalSets)
        repeat(2) { e.done(); e.rate(6); if (e.state().phase == Phase.REST) e.skip() }
        assertEquals(1, e.state().itemIndex)
        e.ticks(20)
        e.rate(9)
        assertEquals(Phase.FINISHED, e.state().phase)
        assertEquals(3, e.completedSets)
        assertTrue(e.drainCues().last() is Cue.Finished)
        e.tick(); e.done(); e.skip()
        assertEquals(Phase.FINISHED, e.state().phase)
    }

    @Test(expected = IllegalArgumentException::class)
    fun itemNeedsExactlyOneVolume() { WorkItem("x", "X", 1, 5, 30, 10) }
}
