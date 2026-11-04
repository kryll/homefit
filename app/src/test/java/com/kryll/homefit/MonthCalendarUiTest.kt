package com.kryll.homefit

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
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
class MonthCalendarUiTest {
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

    @Test fun novemberGridShowsCompleteMonthAndSelectsRealDate() {
        var selected: java.time.LocalDate? = null
        rule.setContent { HomeFitTheme {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
                androidx.compose.material3.Text("Noviembre 2026")
                com.kryll.homefit.ui.MonthCalendar(java.time.YearMonth.of(2026, 11), java.time.LocalDate.of(2026, 11, 3),
                    setOf(java.time.LocalDate.of(2026, 11, 3))) { selected = it }
            }
        } }
        rule.onNodeWithText("30").assertIsDisplayed().performClick()
        assertEquals(java.time.LocalDate.of(2026, 11, 30), selected)
        rule.waitForIdle(); rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
        rule.runOnIdle { capture("calendario_mensual") }
    }
}
