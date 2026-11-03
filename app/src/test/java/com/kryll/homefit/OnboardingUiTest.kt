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
}
