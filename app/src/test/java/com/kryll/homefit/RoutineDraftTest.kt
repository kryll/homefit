package com.kryll.homefit

import com.kryll.homefit.domain.DraftError
import com.kryll.homefit.domain.DraftItem
import com.kryll.homefit.domain.RoutineDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineDraftTest {
    private val ok = RoutineDraft("Mi rutina", items = listOf(DraftItem("sentadilla"), DraftItem("plancha").asTimed()))

    @Test fun validDraftHasNoErrors() = assertTrue(ok.errors().isEmpty())

    @Test fun emptyDraftReportsNameAndItems() {
        assertEquals(setOf(DraftError.NAME_EMPTY, DraftError.NO_ITEMS), RoutineDraft().errors())
    }

    @Test fun blankNameAndLongNameFail() {
        assertTrue(DraftError.NAME_EMPTY in ok.copy(name = "   ").errors())
        assertTrue(DraftError.NAME_TOO_LONG in ok.copy(name = "x".repeat(61)).errors())
    }

    @Test fun volumeIsRepsXorSeconds() {
        assertTrue(DraftError.BAD_VOLUME in ok.update(0, DraftItem("sentadilla", reps = null, seconds = null)).errors())
        assertTrue(DraftError.BAD_VOLUME in ok.update(0, DraftItem("sentadilla", reps = 10, seconds = 30)).errors())
        assertTrue(DraftError.BAD_VOLUME in ok.update(0, DraftItem("sentadilla", reps = 0)).errors())
        assertTrue(DraftError.BAD_VOLUME in ok.update(1, DraftItem("plancha", reps = null, seconds = 4)).errors())
    }

    @Test fun setsAndRestRanges() {
        assertTrue(DraftError.BAD_SETS in ok.update(0, DraftItem("sentadilla", sets = 0)).errors())
        assertTrue(DraftError.BAD_SETS in ok.update(0, DraftItem("sentadilla", sets = null)).errors())
        assertTrue(DraftError.BAD_REST in ok.update(0, DraftItem("sentadilla", restSeconds = 601)).errors())
        assertTrue(ok.update(0, DraftItem("sentadilla", restSeconds = 0)).errors().isEmpty())
    }

    @Test fun switchingModeKeepsExactlyOneVolume() {
        val t = DraftItem("a").asTimed()
        assertEquals(null, t.reps); assertEquals(30, t.seconds)
        val r = t.asReps()
        assertEquals(null, r.seconds); assertEquals(10, r.reps)
    }

    @Test fun moveAndRemove() {
        val d = RoutineDraft("x", items = listOf(DraftItem("a"), DraftItem("b"), DraftItem("c")))
        assertEquals(listOf("b", "a", "c"), d.move(0, 1).items.map { it.exerciseId })
        assertEquals(d, d.move(0, -1))
        assertEquals(d, d.move(2, 1))
        assertEquals(listOf("a", "c"), d.remove(1).items.map { it.exerciseId })
    }

    @Test fun newExerciseUsesTheDefaultRestSetting() {
        val d = RoutineDraft().add("sentadilla", defaultRest = 75).add("plancha", defaultRest = 20)
        assertEquals(listOf(75, 20), d.items.map { it.restSeconds })
        assertEquals(45, RoutineDraft().add("sentadilla").items.single().restSeconds)
    }
}
