package com.kryll.homefit

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.kryll.homefit.data.UserProfile
import com.kryll.homefit.data.WeightUnit
import com.kryll.homefit.ui.HomeFitRoot
import com.kryll.homefit.ui.theme.HomeFitTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Prueba de la app entera: HomeFitRoot con HomeFitApp real (Room y DataStore reales, semillas reales),
 * navegando entre pestañas como lo haría el usuario. No sustituye a una prueba en dispositivo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HomeFitApp::class, qualifiers = "w360dp-h780dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppRootUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app: HomeFitApp = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        runBlocking {
            app.startup?.join()
            app.container.database.profileDao().saveProfile(
                UserProfile(
                    name = "Test", sex = "male", birthEpochDay = 9000, heightCm = 180f, weightKg = 80f,
                    goal = "tonificar", level = "principiante", daysMask = 0b0010101, minutesPerSession = 30,
                ),
            )
            app.container.settings.update { it.copy(onboardingDone = true) }
        }
    }

    private fun scrollTo(text: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    private fun tab(label: String) = rule.onAllNodesWithText(label)[0].performClick()

    @Test fun tabsShowRealDataAndSettingsReachMeasures() {
        rule.setContent { HomeFitTheme { HomeFitRoot() } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Hoy").fetchSemanticsNodes().isNotEmpty() }

        // Rutinas: salen las predefinidas sembradas en Room
        tab("Rutinas")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Cuerpo completo sin material").fetchSemanticsNodes().isNotEmpty() }

        // Progreso > Medidas: guardar un peso y verlo en el historial
        tab("Progreso")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Medidas").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Medidas").performClick()
        rule.onNodeWithText("Peso (kg)").performTextInput("71,4")
        scrollTo("Guardar medidas")
        rule.onNodeWithText("Guardar medidas").performClick()
        rule.waitUntil(10_000) { runCatching { scrollTo("Peso 71,4 kg"); true }.getOrDefault(false) }
        rule.onNodeWithText("Peso 71,4 kg").assertIsDisplayed()

        // Ajustes: pasar a libras y comprobar que se guarda
        tab("Ajustes")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Peso: lb").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Peso: lb").performScrollTo().performClick()
        rule.waitUntil(10_000) { runBlocking { app.container.settings.settings.first().weightUnit } == WeightUnit.LB }

        // Vuelta a Progreso > Medidas: el mismo registro se ve en lb (71,4 kg = 157,4 lb)
        tab("Progreso")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Medidas").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Medidas").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Peso (lb)").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(10_000) { runCatching { scrollTo("Peso 157,4 lb"); true }.getOrDefault(false) }
        rule.onNodeWithText("Peso 157,4 lb").assertIsDisplayed()
        assertEquals(WeightUnit.LB, runBlocking { app.container.settings.settings.first().weightUnit })
    }
}
