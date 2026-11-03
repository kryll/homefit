package com.kryll.homefit

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.kryll.homefit.domain.Phase
import com.kryll.homefit.domain.WorkItem
import com.kryll.homefit.domain.WorkoutState
import com.kryll.homefit.ui.WorkoutContent
import com.kryll.homefit.ui.theme.HomeFitTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h780dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkoutUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val items = listOf(
        WorkItem("sentadilla", "Sentadilla", 3, 12, null, 45),
        WorkItem("plancha", "Plancha", 2, null, 30, 30),
    )

    private fun capture(name: String) {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/outputs/compose-shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun show(state: WorkoutState, item: WorkItem, done: Int = 0, onDone: () -> Unit = {}, onRate: (Int?) -> Unit = {}) {
        rule.setContent {
            HomeFitTheme {
                WorkoutContent(state, item, items, done, 5, onDone, {}, {}, onRate, {})
            }
        }
    }

    @Test fun repsSetShowsTargetAndDoneButton() {
        var done = 0
        show(WorkoutState(Phase.WORK, 0, 1, 0, false), items[0], onDone = { done++ })
        rule.onNodeWithText("repeticiones").assertIsDisplayed()
        rule.onNodeWithText("12").assertIsDisplayed()
        capture("entreno_reps")
        rule.onNodeWithText("Hecho").performClick()
        assertEquals(1, done)
    }

    @Test fun timedSetShowsSecondsAndNoDoneButton() {
        show(WorkoutState(Phase.WORK, 1, 1, 24, false), items[1])
        rule.onNodeWithText("24").assertIsDisplayed()
        rule.onNodeWithText("segundos").assertIsDisplayed()
        rule.onNodeWithText("Plancha").assertIsDisplayed()
        capture("entreno_tiempo")
        assertEquals(0, rule.onAllNodesWithTextCount("Hecho"))
    }

    @Test fun rateScreenOffersRpeOneToTenAndSkip() {
        var rated: Int? = -1
        show(WorkoutState(Phase.RATE, 0, 1, 0, false), items[0], done = 1, onRate = { rated = it })
        rule.onNodeWithText("Esfuerzo percibido (RPE)").assertIsDisplayed()
        rule.onNodeWithText("10").assertIsDisplayed()
        capture("entreno_rpe")
        rule.onNodeWithText("7").performClick()
        assertEquals(7, rated)
        rule.onNodeWithText("Sin RPE").performClick()
        assertEquals(null, rated)
    }

    @Test fun rateScreenRecordsConfirmedRepsAndWeightWithTheRpe() {
        var rec: Pair<Int?, Float?>? = null
        var rated: Int? = -1
        rule.setContent {
            HomeFitTheme {
                WorkoutContent(WorkoutState(Phase.RATE, 0, 1, 0, false), items[0], items, 1, 5, {}, {}, {}, { rated = it }, {}, onRecord = { r, w -> rec = r to w })
            }
        }
        rule.onNodeWithText("Repeticiones hechas: ${items[0].reps}").assertIsDisplayed()
        rule.onNodeWithText("− rep").performClick()
        rule.onNodeWithText("Repeticiones hechas: ${items[0].reps!! - 1}").assertIsDisplayed()
        rule.onNodeWithText("+ 0,5 kg").performClick()
        rule.onNodeWithText("Peso: 0,5 kg").assertIsDisplayed()
        capture("entreno_reps_reales")
        rule.onNodeWithText("8").performClick()
        assertEquals(items[0].reps!! - 1, rec?.first)
        assertEquals(0.5f, rec?.second)
        assertEquals(8, rated)
    }

    @Test fun timedSetRateScreenHasNoRepsInput() {
        show(WorkoutState(Phase.RATE, 1, 1, 0, false), items[1], done = 1)
        assertEquals(0, rule.onAllNodesWithTextCount("− rep"))
    }

    @Test fun restShowsNextExercise() {
        show(WorkoutState(Phase.REST, 0, 3, 18, false), items[0], done = 3)
        rule.onNodeWithText("Siguiente: Plancha").assertIsDisplayed()
        capture("entreno_descanso")
    }

    @Test fun pausedShowsResume() {
        show(WorkoutState(Phase.WORK, 1, 1, 10, true), items[1])
        rule.onNodeWithText("Reanudar").assertIsDisplayed()
    }

    @Test fun finishedShowsSummary() {
        show(WorkoutState(Phase.FINISHED, 1, 2, 0, false), items[1], done = 5)
        rule.onNodeWithText("¡Entreno terminado!").assertIsDisplayed()
        rule.onNodeWithText("Series completadas: 5").assertIsDisplayed()
        capture("entreno_fin")
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextCount(t: String) =
        onAllNodesWithText(t).fetchSemanticsNodes().size
    @Test fun workoutRequestsScreenOnAndRestoresPreviousValueOnDispose() {
        val visible = androidx.compose.runtime.mutableStateOf(true)
        var view: android.view.View? = null
        rule.setContent {
            view = androidx.compose.ui.platform.LocalView.current
            if (visible.value) HomeFitTheme {
                WorkoutContent(WorkoutState(Phase.WORK, 0, 1, 0, false), items[0], items, 0, 5, {}, {}, {}, {}, {})
            }
        }
        rule.runOnIdle { assertEquals(true, view!!.keepScreenOn); visible.value = false }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(false, view!!.keepScreenOn) }
    }
}
