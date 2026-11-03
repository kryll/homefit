package com.kryll.homefit

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasScrollAction
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
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction()).performScrollToNode(androidx.compose.ui.test.hasText(text))
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
        scrollTo("Evolución de las medidas")
        scrollTo("Mín 70,5 kg · Máx 72 kg · 14 días entre la primera y la última")
        rule.onNodeWithText("Mín 70,5 kg · Máx 72 kg · 14 días entre la primera y la última").assertIsDisplayed()
        capture("medidas_grafica")
        scrollTo("Peso 70,5 kg · Cintura 80 cm")
        rule.onNodeWithText("Peso 70,5 kg · Cintura 80 cm").assertIsDisplayed()
        rule.onNodeWithText("25 oct 2025: 72 kg").assertExists()
        capture("medidas_historial")
    }

    @Test fun chartSwitchesToAnotherMeasure() {
        val items = listOf(
            BodyMeasurement(id = 3, epochDay = 20400, weightKg = 70.5f, waistCm = 80f),
            BodyMeasurement(id = 2, epochDay = 20393, waistCm = 81f),
            BodyMeasurement(id = 1, epochDay = 20386, weightKg = 72f, bodyFatPct = 19f),
        )
        rule.setContent { HomeFitTheme { MeasuresContent(items, 20400, {}) } }
        scrollTo("Evolución de las medidas")
        rule.onNodeWithText("Cintura").performClick()
        scrollTo("Mín 80 cm · Máx 81 cm · 7 días entre la primera y la última")
        rule.onNodeWithText("Mín 80 cm · Máx 81 cm · 7 días entre la primera y la última").assertIsDisplayed()
        rule.onNodeWithText("1 nov 2025: 81 cm").assertIsDisplayed()
        rule.onNodeWithText("8 nov 2025: 80 cm").assertIsDisplayed()
        capture("medidas_grafica_cintura")
        scrollTo("Grasa corporal")
        rule.onNodeWithText("Grasa corporal").performClick()
        scrollTo("Anota grasa corporal en al menos dos días para ver la gráfica.")
        rule.onNodeWithText("Anota grasa corporal en al menos dos días para ver la gráfica.").assertIsDisplayed()
    }

    @Test fun chartNeedsTwoWeights() {
        rule.setContent { HomeFitTheme { MeasuresContent(listOf(BodyMeasurement(id = 1, epochDay = 1, weightKg = 70f)), 2, {}) } }
        scrollTo("Anota peso en al menos dos días para ver la gráfica.")
        rule.onNodeWithText("Anota peso en al menos dos días para ver la gráfica.").assertIsDisplayed()
    }

    @Test fun poundsAndInchesChangeLabelsAndHistory() {
        val items = listOf(BodyMeasurement(id = 1, epochDay = 20400, weightKg = 80f, waistCm = 100f))
        rule.setContent {
            HomeFitTheme {
                MeasuresContent(items, 20400, {}, weightUnit = com.kryll.homefit.data.WeightUnit.LB, lengthUnit = com.kryll.homefit.data.LengthUnit.IN)
            }
        }
        rule.onNodeWithText("Peso (lb)").assertIsDisplayed()
        scrollTo("Cintura (in)")
        scrollTo("Peso 176,4 lb · Cintura 39,4 in")
        rule.onNodeWithText("Peso 176,4 lb · Cintura 39,4 in").assertIsDisplayed()
        scrollTo("Editar")
        rule.onNodeWithText("Editar").performClick()
        rule.onNodeWithText("Peso (lb)").assertTextContains("176,4")
        capture("medidas_editar_lb")
    }

    @Test fun exportButtonShowsOnlyWithDataAndCallsBack() {
        var exported = 0
        val items = listOf(BodyMeasurement(id = 1, epochDay = 20400, weightKg = 80f))
        rule.setContent { HomeFitTheme { MeasuresContent(items, 20400, {}, onExport = { exported++ }, exportMessage = "Medidas exportadas.") } }
        scrollTo("Exportar CSV")
        rule.onNodeWithText("Exportar CSV").performClick()
        assertEquals(1, exported)
        rule.onNodeWithText("Medidas exportadas.").assertIsDisplayed()
    }

    @Test fun exportButtonHiddenWithoutData() {
        rule.setContent { HomeFitTheme { MeasuresContent(emptyList(), 1, {}, onExport = {}) } }
        rule.onNodeWithText("Exportar CSV").assertDoesNotExist()
    }
}
