package com.kryll.homefit

import com.kryll.homefit.domain.ExerciseCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogTest {
    private val catalog = ExerciseCatalog.parse(File("src/main/assets/exercises.json").readText())

    @Test fun hasAtLeast80Exercises() = assertTrue(catalog.exercises.size >= 80)

    @Test fun idsAreUnique() = assertEquals(catalog.exercises.size, catalog.exercises.map { it.id }.toSet().size)

    @Test fun variantsExist() {
        val ids = catalog.exercises.map { it.id }.toSet()
        catalog.exercises.forEach {
            it.easierVariantId?.let { v -> assertTrue("${it.id} -> $v", v in ids) }
            it.harderVariantId?.let { v -> assertTrue("${it.id} -> $v", v in ids) }
        }
    }

    @Test fun everyExerciseHasStepsAndMuscle() {
        catalog.exercises.forEach {
            assertTrue(it.id, it.steps.lines().size >= 2)
            assertTrue(it.id, it.primaryMuscle.isNotBlank())
        }
    }

    @Test fun difficultyValuesAreKnown() {
        val ok = setOf("principiante", "intermedio", "avanzado")
        catalog.exercises.forEach { assertTrue(it.id, it.difficulty in ok) }
    }

    @Test fun mediaFilesExistAndAreCredited() {
        val licenses = File("../LICENSES.md").readText()
        val withMedia = catalog.exercises.filter { it.images.isNotBlank() }
        assertTrue(withMedia.size >= 40)
        withMedia.forEach { e ->
            val paths = e.images.lines()
            assertEquals(e.id, 2, paths.size)
            paths.forEach { assertTrue("${e.id}: $it", File("src/main/assets/$it").length() > 1000) }
            assertTrue("sin crédito: ${e.id}", licenses.contains("| ${e.id} |"))
        }
    }

    @Test fun searchIgnoresCaseAndAccents() {
        val a = com.kryll.homefit.ui.searchExercises(catalog.exercises, "BICEPS", limit = 100)
        assertTrue(a.isNotEmpty() && a.any { it.id == "curl_biceps_mancuernas" })
        assertEquals(a, com.kryll.homefit.ui.searchExercises(catalog.exercises, "bíceps", limit = 100))
        assertEquals(3, com.kryll.homefit.ui.searchExercises(catalog.exercises, "", limit = 3).size)
    }
}
