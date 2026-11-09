package com.kryll.homefit

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.performClick
import androidx.compose.ui.Modifier
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import com.kryll.homefit.data.Routine
import com.kryll.homefit.data.ScheduledSession
import com.kryll.homefit.data.WorkoutSession
import com.kryll.homefit.ui.PlanScreen
import com.kryll.homefit.ui.theme.HomeFitTheme
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/outputs/compose-shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Franja compacta de 7 días + detalle del día seleccionado (diseño aprobado por José el 09-11). */
    @Test fun weekStripAndSelectedDayCard() {
        rule.setContent { HomeFitTheme { PlanScreen(today, zone, mask, routines, scheduled, emptyList()) } }
        rule.onNodeWithText("Tu semana").assertIsDisplayed()
        // Franja compacta: las 7 celdas con inicial de día de un vistazo.
        listOf("L", "M", "X", "J", "V", "S", "D").forEach {
            assertTrue(rule.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty())
        }
        // Detalle del día seleccionado (hoy por defecto): rutina, acción de entrenar y tipo de día.
        rule.onNodeWithText("Mié 4 nov · hoy").assertIsDisplayed()
        rule.onNodeWithText("Fuerza en casa").assertIsDisplayed()
        rule.onNodeWithText("Empezar entreno").assertIsDisplayed()
        rule.onNodeWithText("Día de entreno").assertIsDisplayed()
        // Seleccionar un día de descanso cambia el detalle.
        rule.onNodeWithContentDescription("Sáb 7 nov").performClick()
        rule.onNodeWithText("Sáb 7 nov").assertIsDisplayed()
        rule.onNodeWithText("Descanso").assertIsDisplayed()
        rule.onNodeWithText("Sin rutina asignada").assertIsDisplayed()
        capture("plan_semana")
    }

    @Test fun autoDisabledWithoutTrainingDaysShowsHint() {
        rule.setContent { HomeFitTheme { PlanScreen(today, zone, 0, routines, emptyList(), emptyList()) } }
        rule.onNodeWithText("Marca tus días de entrenamiento en Ajustes para poder generar el plan.").assertIsDisplayed()
        rule.onNodeWithText("Generar esta semana").assertIsEnabled()
    }

    @Test fun actionMessageIsShown() {
        rule.setContent { HomeFitTheme { PlanScreen(today, zone, mask, routines, scheduled, emptyList(), message = "Plan generado para: Vie 6 nov.") } }
        rule.onNodeWithText("Plan generado para: Vie 6 nov.").assertIsDisplayed()
    }

    @Test fun doneWorkoutHidesTrainButton() {
        val w = WorkoutSession(1, 1, ms(today, 8), ms(today, 9), 3600)
        rule.setContent { HomeFitTheme { PlanScreen(today, zone, mask, routines, scheduled, listOf(w)) } }
        rule.onNodeWithText("Fuerza en casa · Hecho").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Empezar entreno").fetchSemanticsNodes().size)
    }

    @Test fun assignDialogCallsBackWithRoutine() {
        var got: Pair<LocalDate, Long?>? = null
        rule.setContent { HomeFitTheme { PlanScreen(today, zone, mask, routines, emptyList(), emptyList(), onAssign = { d, r -> got = d to r }) } }
        // Hoy es el día seleccionado por defecto: su tarjeta ofrece Asignar.
        rule.onNodeWithText("Asignar").performClick()
        rule.onNodeWithText("Cardio suave").performClick()
        assertEquals(today to 2L, got)
    }

    @Test fun skippedAndMovedSessionsShowStatusWithoutActions() {
        val s = listOf(
            ScheduledSession(1, 1, ms(today), "SKIPPED"),
            ScheduledSession(2, 2, ms(today.plusDays(2)), "MOVED"),
        )
        rule.setContent { HomeFitTheme { PlanScreen(today, zone, mask, routines, s, emptyList()) } }
        rule.onNodeWithText("Fuerza en casa · Saltada").assertIsDisplayed()
        rule.onNodeWithContentDescription("Vie 6 nov").performClick()
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
        rule.setContent { HomeFitTheme { PlanScreen(today, zone, mask, routines, s, emptyList()) } }
        rule.onNodeWithText("Fuerza en casa · Hecho").assertIsDisplayed()
        rule.onNodeWithContentDescription("Jue 5 nov").performClick()
        rule.onNodeWithText("Cardio suave · Saltada").assertIsDisplayed()
        rule.onNodeWithContentDescription("Vie 6 nov").performClick()
        rule.onNodeWithText("Fuerza en casa · Movida").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Empezar entreno").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("Saltar").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("Mover").fetchSemanticsNodes().size)
        capture("plan_estados_resultantes")
    }

    @Test fun skipAndMoveEmitCallbacks() {
        var skipped: ScheduledSession? = null
        var moved: Pair<Long, LocalDate>? = null
        rule.setContent {
            HomeFitTheme {
                PlanScreen(today, zone, mask, routines, scheduled, emptyList(),
                    onSkip = { skipped = it }, onMove = { e, d -> moved = e.id to d })
            }
        }
        capture("plan_estados")
        rule.onAllNodesWithText("Saltar")[0].performClick()
        assertEquals(1L, skipped?.id)
        rule.onAllNodesWithText("Mover")[0].performClick()
        rule.onNodeWithText("Mover a Jue 5 nov").performClick()
        assertEquals(1L to today.plusDays(1), moved)
    }

    @Test fun weekStripDarkThemeCapture() {
        rule.setContent { HomeFitTheme(darkTheme = true) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { PlanScreen(today, zone, mask, routines, scheduled, emptyList()) }
        } }
        rule.onNodeWithText("Mié 4 nov · hoy").assertIsDisplayed()
        capture("plan_semana_dark")
    }
    @Test fun monthGeneratesVisibleMonthNotWeek() {
        var generated: java.time.YearMonth? = null
        val dark = androidx.compose.runtime.mutableStateOf(false)
        rule.setContent { HomeFitTheme(darkTheme = dark.value) { Surface(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.4f)) {
                PlanScreen(today, zone, mask, routines, scheduled, emptyList(), onAutoMonth = { generated = it })
            }
        } } }
        rule.onNodeWithText("Mes").performClick()
        capture("plan_mes_calendario_light")
        rule.onNodeWithText("Generar este mes").performScrollTo().performClick()
        assertEquals(java.time.YearMonth.from(today), generated)
        capture("plan_mes_generar_light")
        rule.runOnIdle { dark.value = true }
        capture("plan_mes_generar_dark")
        rule.onNodeWithText("Mes").performScrollTo()
        capture("plan_mes_calendario_dark")
    }
}
