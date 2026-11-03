package com.kryll.homefit

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kryll.homefit.data.Exercise
import com.kryll.homefit.data.Routine
import com.kryll.homefit.data.RoutineItem
import com.kryll.homefit.domain.DeloadReason
import com.kryll.homefit.domain.Suggestion
import com.kryll.homefit.ui.RoutinesScreen
import com.kryll.homefit.ui.theme.HomeFitTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h780dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProgressionUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val catalog = com.kryll.homefit.domain.ExerciseCatalog.parse(File("src/main/assets/exercises.json").readText())
    private val byId = catalog.exercises.associateBy { it.id }
    private val squat = byId.getValue("sentadilla_goblet")
    private val plank = byId.getValue("plancha")
    private val routines = listOf(Routine(1, "Fuerza en casa", "tonificar", "principiante", "fuerza", false))
    private val items = mapOf(
        1L to listOf(
            RoutineItem(1, 1, 0, squat.id, 3, reps = 10, restSeconds = 45),
            RoutineItem(2, 1, 1, plank.id, 3, seconds = 30, restSeconds = 30),
        ),
    )

    private fun capture(name: String) {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/outputs/compose-shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun showsSuggestionsPerExerciseAndDeloadBanner() {
        val suggestions = mapOf(
            (1L to squat.id) to Suggestion.AddReps(10, 12),
            (1L to plank.id) to Suggestion.Hold(Suggestion.Reason.RIGHT_EFFORT),
        )
        rule.setContent {
            HomeFitTheme { RoutinesScreen(routines, catalog.exercises, emptyMap(), items, suggestions = suggestions, deload = DeloadReason.LONG_BLOCK) }
        }
        rule.onNodeWithText("Semana de descarga").assertIsDisplayed()
        rule.onNodeWithText("Fuerza en casa").performClick()
        rule.onNodeWithText("Sugerencia: sube de 10 a 12 repeticiones.").assertIsDisplayed()
        rule.onNodeWithText("Mantén: el esfuerzo fue el adecuado.").assertIsDisplayed()
        capture("progresion_sugerencias")
    }

    @Test fun noSuggestionsMeansNoExtraText() {
        rule.setContent { HomeFitTheme { RoutinesScreen(routines, catalog.exercises, emptyMap(), items) } }
        rule.onNodeWithText("Fuerza en casa").performClick()
        val none = rule.onAllNodesCount("Sugerencia")
        org.junit.Assert.assertEquals(0, none)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesCount(prefix: String) =
        onAllNodes(androidx.compose.ui.test.hasText(prefix, substring = true)).fetchSemanticsNodes().size
}
