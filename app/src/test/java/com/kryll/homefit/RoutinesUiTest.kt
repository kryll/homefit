package com.kryll.homefit

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h900dp-port-xxhdpi")
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
        rule.onNodeWithText("pecho").performClick()
        rule.waitForIdle()
        capture("ejercicios_filtrado_pecho")
        rule.onNodeWithText("Flexiones").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Pasos").assertIsDisplayed()
        capture("ejercicio_detalle")
    }
}
