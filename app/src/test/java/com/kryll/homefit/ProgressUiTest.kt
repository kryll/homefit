package com.kryll.homefit

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kryll.homefit.domain.ProgressStats
import com.kryll.homefit.domain.SessionRow
import com.kryll.homefit.domain.SetRow
import com.kryll.homefit.ui.ProgressContent
import com.kryll.homefit.ui.theme.HomeFitTheme
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h780dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProgressUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.of("UTC")
    private fun ms(d: Int, h: Int) = LocalDateTime.of(2026, 11, d, h, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val now = ms(4, 12)

    private val sessions = listOf(
        SessionRow(3, ms(4, 8), 1500, "Fuerza en casa", false, listOf(
            SetRow("Sentadilla", 1, 12, null, 7), SetRow("Sentadilla", 2, 12, null, 8), SetRow("Plancha", 1, null, 30, 9),
        )),
        SessionRow(2, ms(3, 19), 600, null, true, listOf(SetRow("Flexiones", 1, 10, null, null))),
        SessionRow(1, ms(2, 7), 1800, "Cardio suave", false, emptyList()),
    )

    private fun capture(name: String) {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/outputs/compose-shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun showsSummaryAndHistory() {
        rule.setContent { HomeFitTheme { ProgressContent(ProgressStats.summary(sessions, now, zone), sessions, zone) } }
        rule.onNodeWithText("Tu progreso").assertIsDisplayed()
        rule.onNodeWithText("Racha (días)").assertIsDisplayed()
        rule.onNodeWithText("Fuerza en casa").assertIsDisplayed()
        rule.onNodeWithText("Entreno libre").assertIsDisplayed()
        rule.onNodeWithText("Incompleto").assertIsDisplayed()
        capture("progreso_historial")
    }

    @Test fun expandingASessionListsItsSets() {
        rule.setContent { HomeFitTheme { ProgressContent(ProgressStats.summary(sessions, now, zone), sessions, zone) } }
        rule.onNodeWithText("Fuerza en casa").performClick()
        rule.onNodeWithText("Sentadilla · serie 1 · 12 rep · RPE 7").assertIsDisplayed()
        rule.onNodeWithText("Plancha · serie 1 · 30 s · RPE 9").assertIsDisplayed()
        capture("progreso_detalle")
    }

    @Test fun emptyStateExplainsWhatHappensNext() {
        rule.setContent { HomeFitTheme { ProgressContent(ProgressStats.summary(emptyList(), now, zone), emptyList(), zone) } }
        rule.onNodeWithText("Sin datos").assertIsDisplayed()
        rule.onNodeWithText("Aún no has entrenado. Cuando termines tu primer entreno aparecerá aquí.").assertIsDisplayed()
        capture("progreso_vacio")
    }
}
