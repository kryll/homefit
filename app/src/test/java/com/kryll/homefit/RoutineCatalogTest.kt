package com.kryll.homefit

import com.kryll.homefit.domain.ExerciseCatalog
import com.kryll.homefit.domain.RoutineCatalog
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineCatalogTest {
    private val routines = RoutineCatalog.parse(File("src/main/assets/routines.json").readText())
    private val ids = ExerciseCatalog.parse(File("src/main/assets/exercises.json").readText()).exercises.map { it.id }.toSet()

    @Test fun hasPredefinedRoutines() = assertTrue(routines.size >= 5)

    @Test fun allExercisesExist() = routines.forEach { r -> r.items.forEach { assertTrue("${r.name}: ${it.exerciseId}", it.exerciseId in ids) } }

    @Test fun itemsHaveRepsOrSeconds() = routines.forEach { r ->
        assertTrue(r.name, r.items.isNotEmpty())
        r.items.forEach { assertTrue(r.name, (it.reps != null) != (it.seconds != null) && it.sets > 0) }
    }
}
