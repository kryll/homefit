package com.kryll.homefit

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.kryll.homefit.data.Routine
import com.kryll.homefit.domain.ExerciseCatalog
import com.kryll.homefit.ui.ExerciseFilter
import com.kryll.homefit.ui.RoutinesScreen
import com.kryll.homefit.ui.filterExercises
import com.kryll.homefit.ui.theme.HomeFitTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h1300dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoutinesUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val catalog = ExerciseCatalog.parse(File("src/main/assets/exercises.json").readText())
    private val equipment = catalog.equipment.groupBy({ it.exerciseId }, { it.equipment }).mapValues { it.value.toSet() }
    private val routines = listOf(
        Routine(1, "Cuerpo completo sin material", "tonificar", "principiante", "fuerza", true, "Tres días por semana."),
        Routine(2, "HIIT 20 minutos", "perder_grasa", "intermedio", "cardio", true, "Intervalos de 40 s."),
    )

    private fun capture(name: String) {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/outputs/compose-shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun filtersCombine() {
        val all = catalog.exercises
        val chest = filterExercises(all, equipment, ExerciseFilter(muscle = "pecho"))
        assertTrue(chest.isNotEmpty() && chest.all { it.primaryMuscle == "pecho" })
        val noGearBeginner = filterExercises(all, equipment, ExerciseFilter(difficulty = "principiante", equipment = "ninguno"))
        assertTrue(noGearBeginner.all { it.difficulty == "principiante" && "ninguno" in equipment[it.id]!! })
        assertEquals(all.size, filterExercises(all, equipment, ExerciseFilter()).size)
    }

    @Test fun showsRoutinesAndCatalog() {
        rule.setContent { HomeFitTheme { RoutinesScreen(routines, catalog.exercises, equipment) } }
        rule.onNodeWithText("HIIT 20 minutos").assertIsDisplayed()
        capture("rutinas_lista")
        rule.onNodeWithText("Ejercicios").performClick()
        rule.onNodeWithText("${catalog.exercises.size} ejercicios").assertIsDisplayed()
        capture("ejercicios_filtros_plegados")
        rule.onNodeWithText("Filtros (0)").performClick()
        rule.waitForIdle()
        capture("ejercicios_filtros_abiertos")
        rule.onNodeWithText("pecho").performClick()
        rule.onNodeWithText("Ocultar filtros (1)").performClick()
        rule.waitForIdle()
        capture("ejercicios_filtrado_pecho")
        rule.onNodeWithText("Flexiones").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Pasos").assertIsDisplayed()
        rule.onNodeWithContentDescription("Flexiones: posición inicial").assertIsDisplayed()
        capture("ejercicio_detalle")
    }

    @Test fun editorCreatesRoutineAndShowsValidation() {
        var saved: com.kryll.homefit.domain.RoutineDraft? = null
        rule.setContent {
            HomeFitTheme {
                RoutinesScreen(routines, catalog.exercises, equipment, onSaveRoutine = { _, d -> saved = d })
            }
        }
        rule.onNodeWithText("Nueva rutina").performClick()
        rule.onNodeWithText("Guardar rutina").performClick()
        rule.onNodeWithText("Escribe un nombre.").assertIsDisplayed()
        rule.onNodeWithText("Añade al menos un ejercicio.").assertIsDisplayed()
        capture("editor_errores")
        rule.onNodeWithText("Nombre de la rutina").performTextInput("Mi rutina de prueba")
        val first = catalog.exercises[0]
        val second = catalog.exercises[1]
        rule.onNodeWithText("Añadir ejercicio").performClick()
        rule.waitForIdle()
        capture("editor_selector")
        rule.onNodeWithText(first.name).performClick()
        rule.onNodeWithText("Añadir ejercicio").performClick()
        rule.onNodeWithText(second.name).performClick()
        rule.waitForIdle()
        capture("editor_con_ejercicios")
        rule.onNodeWithText("Guardar rutina").performScrollTo().performClick()
        rule.waitForIdle()
        val d = saved!!
        d.items.forEach { assertEquals(it.exerciseId in com.kryll.homefit.domain.DraftItem.TIMED_DEFAULTS, it.timed) }
        assertEquals("Mi rutina de prueba", d.name)
        assertEquals(listOf(first.id, second.id), d.items.map { it.exerciseId })
        assertTrue(d.errors().isEmpty())
    }
}
