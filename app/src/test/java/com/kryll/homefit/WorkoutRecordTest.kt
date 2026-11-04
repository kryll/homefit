package com.kryll.homefit

import com.kryll.homefit.domain.Phase
import com.kryll.homefit.domain.WorkItem
import com.kryll.homefit.domain.WorkoutEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutRecordTest {
    private val items = listOf(WorkItem("sentadilla", "Sentadilla", 2, 10, null, 0))

    private fun toRate(): WorkoutEngine {
        val e = WorkoutEngine(items, getReadySeconds = 0)
        e.done()
        assertEquals(Phase.RATE, e.state().phase)
        return e
    }

    @Test fun recordStoresRepsAndWeightOnTheLastSet() {
        val e = toRate()
        e.record(8, 12.5f)
        e.rate(7)
        val r = e.setResults.single()
        assertEquals(8, r.reps); assertEquals(12.5f, r.weightKg); assertEquals(7, r.rpe)
    }

    @Test fun withoutRecordRepsStayUnknown() {
        val e = toRate()
        e.rate(7)
        assertNull(e.setResults.single().reps)
        assertNull(e.setResults.single().weightKg)
    }

    @Test fun recordOutsideRateIsIgnored() {
        val e = WorkoutEngine(items, getReadySeconds = 0)
        e.record(5, null)
        e.done(); e.rate(5)
        assertNull(e.setResults.single().reps)
    }

    @Test fun recordRejectsInvalidValues() {
        val e = toRate()
        try { e.record(-1, null); throw AssertionError("debía fallar") } catch (_: IllegalArgumentException) {}
        try { e.record(5, 0f); throw AssertionError("debía fallar") } catch (_: IllegalArgumentException) {}
        try { e.record(201, null); throw AssertionError("debía fallar") } catch (_: IllegalArgumentException) {}
    }

    @Test fun eachSetKeepsItsOwnRecord() {
        val e = WorkoutEngine(items, getReadySeconds = 0)
        e.done(); e.record(10, null); e.rate(6)
        e.done(); e.record(8, 5f); e.rate(8)
        assertEquals(listOf(10, 8), e.setResults.map { it.reps })
        assertEquals(listOf(null, 5f), e.setResults.map { it.weightKg })
    }

    @Test fun steppers() {
        assertEquals(0, WorkoutEngine.stepReps(0, -1))
        assertEquals(11, WorkoutEngine.stepReps(10, 1))
        assertEquals(200, WorkoutEngine.stepReps(200, 1))
        assertEquals(0.5f, WorkoutEngine.stepWeight(null, 1))
        assertNull(WorkoutEngine.stepWeight(null, -1))
        assertNull(WorkoutEngine.stepWeight(0.5f, -1))
        assertEquals(10.5f, WorkoutEngine.stepWeight(10f, 1))
        assertEquals(9.5f, WorkoutEngine.stepWeight(10f, -1))
    }
}
