package com.kryll.homefit

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import com.kryll.homefit.data.Routine
import com.kryll.homefit.data.ScheduledSession
import com.kryll.homefit.data.WorkoutSession
import com.kryll.homefit.domain.ReminderPrefs
import com.kryll.homefit.ui.PlanScreen
import com.kryll.homefit.ui.theme.HomeFitTheme
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId
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
class PlanUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("Europe/Madrid")
    private val today = LocalDate.of(2026, 11, 4) // miércoles
    private val routines = listOf(
        Routine(1, "Fuerza en casa", "tonificar", "principiante", "fuerza", false),
        Routine(2, "Cardio suave", "resistencia", "principiante", "cardio", false),
    )
    private fun ms(d: LocalDate, h: Int = 18) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    private val scheduled = listOf(
        ScheduledSession(1, 1, ms(today), "SCHEDULED"),
        ScheduledSession(2, 2, ms(today.plusDays(2)), "SCHEDULED"),
    )
    private val mask = 0b0010101 // lun, mié, vie

    private fun capture(name: String) {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/outputs/compose-shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun showsWeekWithRoutinesAndReminder() {
        rule.setContent {
            HomeFitTheme {
                PlanScreen(today, zone, mask, routines, scheduled, emptyList(), ReminderPrefs(false), notificationsAllowed = true)
            }
        }
        rule.onNodeWithText("Tu semana").assertIsDisplayed()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Mié 4 nov · hoy"))
        rule.onNodeWithText("Mié 4 nov · hoy").assertIsDisplayed()
        rule.onNodeWithText("Fuerza en casa").assertIsDisplayed()
        rule.onNodeWithText("Entrenar").assertIsDisplayed()
        capture("plan_semana")
    }

    @Test fun enabledReminderShowsTimeAndSteppers() {
        rule.setContent { HomeFitTheme { PlanScreen(today, zone, mask, routines, scheduled, emptyList(), ReminderPrefs(true, 18, 30), true) } }
        rule.onNodeWithText("Hora de entrenar: 18:30").assertIsDisplayed()
        rule.onNodeWithText("+ hora").assertIsDisplayed()
        capture("plan_recordatorio")
    }

    @Test fun doneWorkoutHidesTrainButton() {
        val w = WorkoutSession(1, 1, ms(today, 8), ms(today, 9), 3600)
        rule.setContent { HomeFitTheme { PlanScreen(today, zone, mask, routines, scheduled, listOf(w), ReminderPrefs(), true) } }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Fuerza en casa · Hecho"))
        rule.onNodeWithText("Fuerza en casa · Hecho").assertIsDisplayed()
        assertEquals(0, rule.onAllNodes(androidx.compose.ui.test.hasText("Entrenar")).fetchSemanticsNodes().size)
    }

    @Test fun warnsWhenNotificationsAreOff() {
        rule.setContent { HomeFitTheme { PlanScreen(today, zone, mask, routines, emptyList(), emptyList(), ReminderPrefs(true), notificationsAllowed = false) } }
        rule.onNodeWithText("Las notificaciones de HomeFit están desactivadas en el sistema: actívalas para recibir el aviso.").assertIsDisplayed()
    }

    @Test fun timeButtonsEmitNewPrefs() {
        var last: ReminderPrefs? = null
        rule.setContent { HomeFitTheme { PlanScreen(today, zone, mask, routines, emptyList(), emptyList(), ReminderPrefs(true, 18, 30), true, onPrefs = { last = it }) } }
        rule.onNodeWithText("+ hora").performClick()
        assertEquals(ReminderPrefs(true, 19, 30), last)
        rule.onNodeWithText("− 5 min").performClick()
        assertEquals(ReminderPrefs(true, 18, 25), last)
    }

    @Test fun assignDialogCallsBackWithRoutine() {
        var got: Pair<LocalDate, Long?>? = null
        rule.setContent { HomeFitTheme { PlanScreen(today, zone, mask, routines, emptyList(), emptyList(), ReminderPrefs(), true, onAssign = { d, r -> got = d to r }) } }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Asignar"))
        rule.onAllNodesWithText("Asignar")[0].performClick()
        rule.onNodeWithText("Cardio suave").performClick()
        assertEquals(com.kryll.homefit.domain.CalendarPeriod.weekStart(today) to 2L, got)
    }

    @Test fun skippedAndMovedSessionsShowStatusWithoutActions() {
        val s = listOf(
            ScheduledSession(1, 1, ms(today), "SKIPPED"),
            ScheduledSession(2, 2, ms(today.plusDays(2)), "MOVED"),
        )
        rule.setContent { HomeFitTheme { PlanScreen(today, zone, mask, routines, s, emptyList(), ReminderPrefs(), true) } }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Fuerza en casa · Saltada"))
        rule.onNodeWithText("Fuerza en casa · Saltada").assertIsDisplayed()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Cardio suave · Movida"))
        rule.onNodeWithText("Cardio suave · Movida").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Saltar").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("Mover").fetchSemanticsNodes().size)
    }

    @Test fun resultingStatesAreShownWithoutActions() {
        val s = listOf(
            ScheduledSession(1, 1, ms(today), "COMPLETED"),
            ScheduledSession(2, 2, ms(today.plusDays(1)), "SKIPPED"),
            ScheduledSession(3, 1, ms(today.plusDays(2)), "MOVED"),
        )
        rule.setContent { HomeFitTheme { PlanScreen(today, zone, mask, routines, s, emptyList(), ReminderPrefs(), true) } }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Fuerza en casa · Hecho"))
        rule.onNodeWithText("Fuerza en casa · Hecho").assertIsDisplayed()
        rule.onNodeWithText("Cardio suave · Saltada").assertIsDisplayed()
        rule.onNodeWithText("Fuerza en casa · Movida").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Entrenar").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("Saltar").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("Mover").fetchSemanticsNodes().size)
        capture("plan_estados_resultantes")
    }

    @Test fun skipAndMoveEmitCallbacks() {
        var skipped: ScheduledSession? = null
        var moved: Pair<Long, LocalDate>? = null
        rule.setContent {
            HomeFitTheme {
                PlanScreen(today, zone, mask, routines, scheduled, emptyList(), ReminderPrefs(), true,
                    onSkip = { skipped = it }, onMove = { e, d -> moved = e.id to d })
            }
        }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Mié 4 nov · hoy"))
        capture("plan_estados")
        rule.onAllNodesWithText("Saltar")[0].performClick()
        assertEquals(1L, skipped?.id)
        rule.onAllNodesWithText("Mover")[0].performClick()
        rule.onNodeWithText("Mover a Jue 5 nov").performClick()
        assertEquals(1L to today.plusDays(1), moved)
    }

    @Test fun leadShowsNotificationTimeAndEmitsSteps() {
        var prefs = ReminderPrefs(true, 18, 30, leadMinutes = 15)
        rule.setContent { HomeFitTheme { PlanScreen(today, zone, mask, routines, scheduled, emptyList(), prefs, true, onPrefs = { prefs = it }) } }
        rule.onNodeWithText("Aviso 15 min antes (a las 18:15)").assertIsDisplayed()
        rule.onNodeWithText("Más antelación").performClick()
        org.junit.Assert.assertEquals(30, prefs.leadMinutes)
        capture("plan_antelacion")
    }
}
