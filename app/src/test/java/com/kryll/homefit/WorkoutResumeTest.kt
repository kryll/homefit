package com.kryll.homefit

import com.kryll.homefit.domain.InterruptedSet
import com.kryll.homefit.domain.WorkItem
import com.kryll.homefit.domain.WorkoutResume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutResumeTest {
    private val plan = listOf(
        WorkItem("sentadilla", "Sentadilla", 2, 10, null, 30),
        WorkItem("plancha", "Plancha", 1, null, 20, 0),
    )

    @Test fun mapsLogsToPlanPositionsInOrder() {
        val logs = listOf(
            InterruptedSet("sentadilla", 1, 10, null, 7, null),
            InterruptedSet("sentadilla", 2, 12, null, 8, null),
            InterruptedSet("plancha", 1, null, 20, 9, null),
        )
        val r = WorkoutResume.fromLogs(plan, logs)!!
        assertEquals(3, r.size)
        assertEquals(0, r[0].itemIndex); assertEquals(1, r[0].set); assertEquals(10, r[0].reps)
        assertEquals(0, r[1].itemIndex); assertEquals(2, r[1].set)
        assertEquals(1, r[2].itemIndex); assertEquals(20, r[2].workSeconds); assertEquals(9, r[2].rpe)
    }

    @Test fun emptyOrUnknownLogsGiveNoResume() {
        assertNull(WorkoutResume.fromLogs(plan, emptyList()))
        assertNull(WorkoutResume.fromLogs(plan, listOf(InterruptedSet("borrado", 1, 5, null, null, null))))
    }

    @Test fun repeatedExerciseInPlanGivesNoResume() {
        val circuit = listOf(
            WorkItem("sentadilla", "Sentadilla", 1, 10, null, 30),
            WorkItem("plancha", "Plancha", 1, null, 20, 0),
            WorkItem("sentadilla", "Sentadilla", 1, 10, null, 30),
        )
        val logs = listOf(InterruptedSet("sentadilla", 1, 10, null, 7, null))
        assertNull("con el ejercicio repetido no se sabe a qué ocurrencia pertenece", WorkoutResume.fromLogs(circuit, logs))
    }

    @Test fun gapsInSetNumbersGiveNoResume() {
        val logs = listOf(InterruptedSet("sentadilla", 2, 10, null, 7, null))
        assertNull(WorkoutResume.fromLogs(plan, logs))
    }

    @Test fun positionalLogsMapOccurrences() {
        val circuit = listOf(
            WorkItem("sentadilla", "Sentadilla", 1, 10, null, 30),
            WorkItem("plancha", "Plancha", 1, null, 20, 0),
            WorkItem("sentadilla", "Sentadilla", 1, 10, null, 30),
        )
        val logs = listOf(
            InterruptedSet("sentadilla", 1, 10, null, 7, null, itemIndex = 0),
            InterruptedSet("sentadilla", 1, 12, null, 9, null, itemIndex = 2),
        )
        val r = WorkoutResume.fromLogsPositional(circuit, logs)!!
        assertEquals(2, r.size)
        assertEquals(0, r[0].itemIndex); assertEquals(2, r[1].itemIndex)
    }

    @Test fun positionalRejectsHolesMismatchAndOutOfRange() {
        assertNull(WorkoutResume.fromLogsPositional(plan, listOf(InterruptedSet("sentadilla", 2, 10, null, 7, null, itemIndex = 0))))
        assertNull(WorkoutResume.fromLogsPositional(plan, listOf(InterruptedSet("plancha", 1, null, 20, 7, null, itemIndex = 0))))
        assertNull(WorkoutResume.fromLogsPositional(plan, listOf(InterruptedSet("sentadilla", 1, 10, null, 7, null, itemIndex = 5))))
    }
}
