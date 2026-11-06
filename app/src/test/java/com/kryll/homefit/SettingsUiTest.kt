package com.kryll.homefit

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo

import androidx.compose.ui.test.hasText

import androidx.compose.ui.test.performClick
import com.kryll.homefit.data.AppSettings
import com.kryll.homefit.domain.ReminderPrefs
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

    private fun reminderContent(prefs: ReminderPrefs, notificationsAllowed: Boolean = true, exactAllowed: Boolean = true, onPrefs: (ReminderPrefs) -> Unit = {}) {
        rule.setContent {
            HomeFitTheme {
                SettingsScreen(AppSettings(), "1.0", null, null,
                    reminderPrefs = prefs, reminderNotificationsAllowed = notificationsAllowed, reminderExactAllowed = exactAllowed, onReminderPrefs = onPrefs)
            }
        }
    }

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

    @Test fun updateStatesAreShown() {
        var checked = 0
        var opened = ""
        val info = com.kryll.homefit.domain.ReleaseInfo("v1.2.0", "1.2.0", "https://github.com/kryll/homefit/releases/download/v1.2.0/homefit.apk", "https://example.test/rel")
        rule.setContent {
            HomeFitTheme {
                SettingsScreen(
                    AppSettings(), "1.0.0", null, null,
                    updateState = com.kryll.homefit.domain.UpdateState.Available(info),
                    onCheckUpdates = { checked++ },
                    onOpenUpdate = { opened = it },
                )
            }
        }
        rule.onNodeWithText("Nueva versión 1.2.0 disponible").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Descargar APK").performScrollTo().performClick()
        assertEquals(info.apkUrl, opened)
        rule.onNodeWithText("Buscar actualizaciones").performScrollTo().performClick()
        assertEquals(1, checked)
        capture("ajustes_actualizacion")
    }

    @Test fun updateErrorIsShown() {
        rule.setContent { HomeFitTheme { SettingsScreen(AppSettings(), "1", null, null, updateState = com.kryll.homefit.domain.UpdateState.Failed) } }
        rule.onNodeWithText("No se pudo comprobar. Inténtalo más tarde.").performScrollTo().assertIsDisplayed()
    }
    @Test fun verifiedApkOffersAndroidInstallerButDoesNotInstallItself() {
        var installs = 0
        val info = com.kryll.homefit.domain.ReleaseInfo("v1.2.0", "1.2.0", "https://github.com/kryll/homefit/releases/download/v1.2.0/homefit.apk", "")
        rule.setContent { HomeFitTheme {
            com.kryll.homefit.ui.UpdateInstallContent("1.0.0",
                updateState = com.kryll.homefit.domain.UpdateState.Available(info),
                downloadState = com.kryll.homefit.update.DownloadUi(file = File("fixture.apk")),
                onInstallUpdate = { installs++ })
        } }
        assertEquals(0, installs)
        rule.onNodeWithText("Instalar actualización").assertIsDisplayed()
        rule.waitForIdle(); rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
        rule.runOnIdle { capture("ajustes_instalar_apk") }
        rule.onNodeWithText("Instalar actualización").performClick()
        assertEquals(1, installs)
    }

    @Test fun voiceSelectorListsAndEmits() {
        val s = androidx.compose.runtime.mutableStateOf(AppSettings(ttsVoice = "es-es-x-eee-local"))
        val voices = listOf("es-es-x-eee-local", "es-es-x-eef-network")
        rule.setContent { HomeFitTheme { SettingsScreen(s.value, "1", null, null, availableVoices = voices, onChange = { t -> s.value = t(s.value) }) } }
        rule.onNodeWithText("Voz de los avisos").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("es-es-x-eee-local")[0].performClick()
        rule.onNodeWithText("Elige la voz de los avisos").assertIsDisplayed()
        capture("ajustes_voz_dialogo")
        rule.onNodeWithText("es-es-x-eef-network").performClick()
        assertEquals("es-es-x-eef-network", s.value.ttsVoice)
        rule.onNodeWithText("es-es-x-eef-network").performClick()
        rule.onNodeWithText("Predeterminada del sistema").performClick()
        assertEquals(null, s.value.ttsVoice)
        capture("ajustes_voz")
    }

    @Test fun showsProfileAndBmi() {
        val prof = com.kryll.homefit.data.UserProfile(
            name = "Ana", sex = "mujer", birthEpochDay = java.time.LocalDate.of(1990, 1, 1).toEpochDay(),
            heightCm = 170f, weightKg = 60f, goal = "tonificar", level = "principiante",
            daysMask = 21, minutesPerSession = 30,
        )
        rule.setContent { HomeFitTheme { SettingsScreen(AppSettings(), "1", null, null, profile = prof, latestWeightKg = 62f) } }
        rule.onNodeWithText("Ana", substring = true).assertExists()
        rule.onNodeWithText("IMC orientativo", substring = true).assertExists()
        rule.onNodeWithText("Editar perfil").assertExists()
    }

    @Test fun reminderSectionShowsTimeAndSteppers() {
        reminderContent(ReminderPrefs(true, 18, 30))
        rule.onNodeWithText("Recordatorio diario").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Hora de entrenar: 18:30").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("+ hora").performScrollTo().assertIsDisplayed()
        capture("ajustes_recordatorio")
    }

    @Test fun reminderExactGrantedShowsNoteWithoutGenericDelay() {
        reminderContent(ReminderPrefs(true, 18, 30, exact = true), exactAllowed = true)
        rule.onNodeWithText("Aviso exacto (permiso «Alarmas y recordatorios»)").performScrollTo().assertIsDisplayed()
        // Modo exacto concedido: nota específica sin promesa absoluta y SIN la frase de retraso del modo normal.
        rule.onNodeWithText("Android intenta avisar a la hora elegida incluso con ahorro de batería.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Android puede retrasar el aviso por el ahorro de batería.").assertDoesNotExist()
        capture("ajustes_recordatorio_exacto")
    }

    @Test fun reminderExactDeniedShowsPermissionNote() {
        reminderContent(ReminderPrefs(true, 18, 30, exact = true), exactAllowed = false)
        // La nota de denegación ya avisa del retraso: la frase genérica no debe duplicarla.
        rule.onNodeWithText("Sin el permiso de alarma, el aviso usa el mecanismo normal y el ahorro de batería puede retrasarlo. Toca el interruptor de nuevo para abrir los ajustes de Android.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Android puede retrasar el aviso por el ahorro de batería.").assertDoesNotExist()
    }

    @Test fun reminderNotificationsOffWarns() {
        reminderContent(ReminderPrefs(true), notificationsAllowed = false)
        rule.onNodeWithText("Las notificaciones de HomeFit están desactivadas en el sistema: actívalas para recibir el aviso.").performScrollTo().assertIsDisplayed()
    }

    @Test fun reminderTimeButtonsEmitNewPrefs() {
        var last: ReminderPrefs? = null
        reminderContent(ReminderPrefs(true, 18, 30)) { last = it }
        rule.onNodeWithText("+ hora").performScrollTo().performClick()
        assertEquals(ReminderPrefs(true, 19, 30), last)
        rule.onNodeWithText("− 5 min").performScrollTo().performClick()
        assertEquals(ReminderPrefs(true, 18, 25), last)
    }

    @Test fun reminderLeadShowsNotifyTimeAndEmitsSteps() {
        var prefs = ReminderPrefs(true, 18, 30, leadMinutes = 15)
        reminderContent(prefs) { prefs = it }
        rule.onNodeWithText("Aviso 15 min antes (a las 18:15)").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Más antelación").performScrollTo().performClick()
        assertEquals(30, prefs.leadMinutes)
    }

    @Test fun reminderSectionDarkThemeCapture() {
        rule.setContent {
            HomeFitTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(AppSettings(), "1.0", null, null, reminderPrefs = ReminderPrefs(true, 18, 30))
                }
            }
        }
        rule.onNodeWithText("Hora de entrenar: 18:30").performScrollTo().assertIsDisplayed()
        capture("ajustes_recordatorio_dark")
    }
}
