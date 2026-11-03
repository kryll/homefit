package com.kryll.homefit

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import com.kryll.homefit.data.AppSettings
import com.kryll.homefit.data.ThemeMode
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.kryll.homefit.ui.ImportConfirmContent
import com.kryll.homefit.ui.ImportSummary
import com.kryll.homefit.ui.SettingsScreen
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
class SettingsUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String) {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/outputs/compose-shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun showsSectionsAndCurrentValues() {
        rule.setContent { HomeFitTheme { SettingsScreen(AppSettings(beepVolume = 0.8f, ttsRate = 1.25f), "1.2.3", null, null) } }
        rule.onNodeWithText("Ajustes").assertIsDisplayed()
        capture("ajustes_arriba")
        rule.onNodeWithText("Volumen de los pitidos: 80 %").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Velocidad de la voz: 1,25×").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Exportar copia").performScrollTo().assertIsDisplayed()
    }

    @Test fun settingChangesAreEmittedAndApplied() {
        var s = AppSettings()
        rule.setContent { HomeFitTheme { SettingsScreen(s, "1", null, null, onChange = { t -> s = t(s) }) } }
        rule.onNodeWithText("Oscuro").performClick()
        assertEquals(ThemeMode.DARK, s.theme)
    }

    @Test fun zeroVolumeSaysBeepsAreOff() {
        rule.setContent { HomeFitTheme { SettingsScreen(AppSettings(beepVolume = 0f), "1", null, null) } }
        rule.onNodeWithText("Pitidos desactivados").performScrollTo().assertIsDisplayed()
    }

    @Test fun importAsksForConfirmationWithCounts() {
        var confirmed = false
        rule.setContent {
            HomeFitTheme {
                SettingsScreen(AppSettings(), "1", null, ImportSummary(2, 5, 3, 1), onConfirmImport = { confirmed = true })
            }
        }
        rule.onNodeWithText("Contiene: rutinas propias 2, entrenos 5, días planificados 3, medidas 1. Se borrarán tus rutinas propias, entrenos, plan y medidas actuales y se sustituirán por estos. No se puede deshacer. Se intenta volver a tus datos si falla, pero no está garantizado: haz antes una copia.").assertIsDisplayed()
        rule.onNodeWithText("Importar").performClick()
        assertEquals(true, confirmed)
    }

    /** Renderiza el contenido del diálogo directamente (la captura no incluye ventanas de diálogo). */
    @Test fun importConfirmContentCapture() {
        rule.setContent {
            HomeFitTheme {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().padding(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    ImportConfirmContent(ImportSummary(2, 5, 3, 1), {}, {})
                }
            }
        }
        rule.onNodeWithText("¿Importar esta copia?").assertIsDisplayed()
        rule.onNodeWithText("Cancelar").assertIsDisplayed()
        capture("ajustes_importar")
    }

    @Test fun showsMessage() {
        rule.setContent { HomeFitTheme { SettingsScreen(AppSettings(), "1", "Copia guardada.", null) } }
        rule.onNodeWithText("Copia guardada.").performScrollTo().assertIsDisplayed()
    }

    @Test fun restAndCountdownSettingsAreShownAndEmitted() {
        var s = AppSettings(defaultRestSeconds = 45, defaultCountdownSeconds = 5)
        rule.setContent { HomeFitTheme { SettingsScreen(s, "1", null, null, onChange = { t -> s = t(s) }) } }
        rule.onNodeWithText("Descanso por defecto: 45 s").assertIsDisplayed()
        rule.onNodeWithText("Cuenta atrás: 5 s").assertIsDisplayed()
        rule.onAllNodesWithText("+")[0].performScrollTo().performClick()
        assertEquals(50, s.defaultRestSeconds)
        rule.onAllNodesWithText("+")[1].performScrollTo().performClick()
        assertEquals(6, s.defaultCountdownSeconds)
        capture("ajustes_entreno")
    }

    @Test fun unitChipsChangeWeightAndLengthUnits() {
        var s = AppSettings()
        rule.setContent { HomeFitTheme { SettingsScreen(s, "1", null, null, onChange = { t -> s = t(s) }) } }
        rule.onNodeWithText("Peso: lb").performScrollTo().performClick()
        assertEquals(com.kryll.homefit.data.WeightUnit.LB, s.weightUnit)
        rule.onNodeWithText("Medidas: in").performScrollTo().performClick()
        assertEquals(com.kryll.homefit.data.LengthUnit.IN, s.lengthUnit)
    }
}
