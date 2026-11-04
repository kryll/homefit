package com.kryll.homefit

import com.kryll.homefit.domain.DraftItem
import com.kryll.homefit.domain.ExerciseCatalog
import com.kryll.homefit.domain.RoutineDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineDefaultsTest {
    @Test fun isometricStartsTimedWithSeconds() {
        val d = RoutineDraft().add("plancha").add("sentadilla")
        assertEquals(30, d.items[0].seconds); assertNull(d.items[0].reps); assertTrue(d.items[0].timed)
        assertEquals(10, d.items[1].reps); assertNull(d.items[1].seconds)
    }

    @Test fun timedDefaultsAreValidAndExist() {
        val ids = ExerciseCatalog.parse(java.io.File("src/main/assets/exercises.json").readText()).exercises.map { it.id }.toSet()
        DraftItem.TIMED_DEFAULTS.forEach { (id, s) ->
            assertTrue("falta $id", id in ids)
            assertTrue(s in 5..RoutineDraft.MAX_SECONDS)
        }
    }
}
