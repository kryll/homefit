package com.kryll.homefit

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNode
import com.kryll.homefit.data.BodyMeasurement
import com.kryll.homefit.ui.MeasuresContent
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
class MeasuresUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String) {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/outputs/compose-shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun scrollTo(text: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(androidx.compose.ui.test.hasText(text))
    }

    @Test fun savesWhatWasTypedAndRejectsEmpty() {
        val saved = mutableListOf<BodyMeasurement>()
        rule.setContent { HomeFitTheme { MeasuresContent(emptyList(), 100, { saved += it }) } }
        scrollTo("Guardar medidas")
        rule.onNodeWithText("Guardar medidas").performClick()
        rule.onNodeWithText("Escribe al menos una medida.").assertIsDisplayed()
        assertEquals(0, saved.size)
        rule.onNodeWithText("Peso (kg)").performTextInput("71,4")
        scrollTo("Cintura (cm)")
        rule.onNodeWithText("Cintura (cm)").performTextInput("82")
        scrollTo("Guardar medidas")
        rule.onNodeWithText("Guardar medidas").performClick()
        assertEquals(1, saved.size)
        assertEquals(71.4f, saved[0].weightKg!!, 0.001f)
        assertEquals(82f, saved[0].waistCm!!, 0.001f)
        assertEquals(null, saved[0].chestCm)
        assertEquals(100L, saved[0].epochDay)
    }

    @Test fun invalidValueShowsFieldName() {
        rule.setContent { HomeFitTheme { MeasuresContent(emptyList(), 100, {}) } }
        rule.onNodeWithText("Peso (kg)").performTextInput("5")
        scrollTo("Guardar medidas")
        rule.onNodeWithText("Guardar medidas").performClick()
        rule.onNodeWithText("Revisa el valor de Peso (kg).").assertIsDisplayed()
    }

    @Test fun chartAndHistory() {
        val items = listOf(
            BodyMeasurement(id = 3, epochDay = 20400, weightKg = 70.5f, waistCm = 80f),
            BodyMeasurement(id = 2, epochDay = 20393, weightKg = 71.2f),
            BodyMeasurement(id = 1, epochDay = 20386, weightKg = 72f, bodyFatPct = 19f),
        )
        rule.setContent { HomeFitTheme { MeasuresContent(items, 20400, {}) } }
        scrollTo("Peso a lo largo del tiempo")
        rule.onNodeWithText("70,5 – 72 kg").assertIsDisplayed()
        capture("medidas_grafica")
        scrollTo("Peso 70,5 kg · Cintura 80 cm")
        rule.onNodeWithText("Peso 70,5 kg · Cintura 80 cm").assertIsDisplayed()
        capture("medidas_historial")
    }

    @Test fun chartNeedsTwoWeights() {
        rule.setContent { HomeFitTheme { MeasuresContent(listOf(BodyMeasurement(id = 1, epochDay = 1, weightKg = 70f)), 2, {}) } }
        scrollTo("Anota el peso en al menos dos días para ver la gráfica.")
        rule.onNodeWithText("Anota el peso en al menos dos días para ver la gráfica.").assertIsDisplayed()
    }
}
