package com.kryll.homefit

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithText as node
import org.junit.Assert.assertEquals
import com.kryll.homefit.ui.OnboardingScreen
import com.kryll.homefit.ui.theme.HomeFitTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renderiza el onboarding sin emulador y guarda un PNG en app/build/outputs/compose-shots/ (artefacto de CI). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h900dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String) {
        val view = rule.activity.window.decorView
        assertTrue(view.width > 0 && view.height > 0)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/outputs/compose-shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun showsFormAndRejectsEmptySubmit() {
        var done = false
        rule.setContent { HomeFitTheme { OnboardingScreen { _, _ -> done = true } } }
        rule.onNodeWithText("Tu perfil").assertIsDisplayed()
        rule.onNodeWithText("Fecha de nacimiento").assertIsDisplayed()
        capture("onboarding_inicio")
        rule.onNodeWithText("Empezar").performScrollTo().performClick()
        rule.waitForIdle()
        capture("onboarding_errores")
        assertFalse(done)
    }

    @Test fun fullFormSavesSexGoalBirthAndEquipment() {
        var profile: com.kryll.homefit.data.UserProfile? = null
        var equipment: Set<String> = emptySet()
        rule.setContent { HomeFitTheme { OnboardingScreen { p, e -> profile = p; equipment = e } } }
        rule.onNodeWithText("Nombre").performTextInput("Ana")
        rule.onNodeWithText("mujer").performScrollTo().performClick()
        rule.onNodeWithText("Día").performScrollTo().performTextInput("23")
        rule.onNodeWithText("Mes").performScrollTo().performTextInput("12")
        rule.onNodeWithText("Año").performScrollTo().performTextInput("1994")
        rule.onNodeWithText("Altura (cm)").performScrollTo().performTextInput("175")
        rule.onNodeWithText("Peso (kg)").performScrollTo().performTextInput("70")
        rule.onNodeWithText("tonificar").performScrollTo().performClick()
        rule.onNodeWithText("mancuernas").performScrollTo().performClick()
        rule.onNodeWithText("silla / banco").performScrollTo().performClick()
        rule.onNodeWithText("Empezar").performScrollTo()
        rule.waitForIdle()
        capture("onboarding_completo_inferior")
        rule.onNodeWithText("Empezar").performClick()
        rule.waitForIdle()
        val p = profile!!
        assertEquals("mujer", p.sex)
        assertEquals("tonificar", p.goal)
        assertEquals(java.time.LocalDate.of(1994, 12, 23).toEpochDay(), p.birthEpochDay)
        assertEquals(setOf("mancuernas", "silla", "banco"), equipment)
    }
}
