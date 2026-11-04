package com.kryll.homefit

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kryll.homefit.data.HomeFitDatabase
import com.kryll.homefit.ui.MeasuresContent
import com.kryll.homefit.ui.theme.HomeFitTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Cableado real: pantalla -> MeasurementDao (Room en memoria) -> historial. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h780dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MeasuresWiringTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: HomeFitDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HomeFitDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
    }
    @After fun tearDown() = db.close()

    private fun scrollTo(text: String) {
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction()).performScrollToNode(hasText(text))
    }

    @Test fun saveShowsInHistoryAndDeleteNeedsConfirmation() {
        rule.setContent {
            HomeFitTheme {
                val items by db.measurementDao().observeAll().collectAsState(initial = emptyList())
                val scope = rememberCoroutineScope()
                MeasuresContent(
                    items, 100,
                    onSave = { m -> scope.launch { db.measurementDao().insert(m) } },
                    onDelete = { id -> scope.launch { db.measurementDao().deleteById(id) } },
                )
            }
        }
        rule.onNodeWithText("Peso (kg)").performTextInput("71,4")
        scrollTo("Guardar medidas")
        rule.onNodeWithText("Guardar medidas").performClick()
        rule.waitUntil(5000) { runBlocking { db.measurementDao().observeAll().first().size } == 1 }
        val saved = runBlocking { db.measurementDao().observeAll().first() }
        assertEquals(71.4f, saved[0].weightKg!!, 0.001f)
        assertEquals(100L, saved[0].epochDay)
        scrollTo("Peso 71,4 kg")
        rule.onNodeWithText("Peso 71,4 kg").assertExists()
        // borrar pide confirmación; cancelar no borra
        rule.onNodeWithText("Borrar").performClick()
        rule.onNodeWithText("¿Borrar este registro? No se puede deshacer.").assertExists()
        rule.onNodeWithText("Cancelar").performClick()
        assertEquals(1, runBlocking { db.measurementDao().observeAll().first().size })
        rule.onNodeWithText("Borrar").performClick()
        rule.onNodeWithText("Sí, borrar").performClick()
        rule.waitUntil(5000) { runBlocking { db.measurementDao().observeAll().first().isEmpty() } }
        rule.waitUntil(5000) { rule.onAllNodesWithText("Peso 71,4 kg").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun editUpdatesTheSameRecord() {
        runBlocking { db.measurementDao().insert(com.kryll.homefit.data.BodyMeasurement(epochDay = 50, weightKg = 70f, waistCm = 80f)) }
        rule.setContent {
            HomeFitTheme {
                val items by db.measurementDao().observeAll().collectAsState(initial = emptyList())
                val scope = rememberCoroutineScope()
                MeasuresContent(
                    items, 100,
                    onSave = { m -> scope.launch { if (m.id != 0L) db.measurementDao().update(m) else db.measurementDao().insert(m) } },
                    onDelete = { id -> scope.launch { db.measurementDao().deleteById(id) } },
                )
            }
        }
        val id = runBlocking { db.measurementDao().observeAll().first() }.single().id
        scrollTo("Editar")
        rule.onNodeWithText("Editar").performClick()
        rule.onNodeWithText("Guardar cambios").assertExists()
        rule.onNodeWithText("Peso (kg)").performTextReplacement("72,5")
        scrollTo("Guardar cambios")
        rule.onNodeWithText("Guardar cambios").performClick()
        rule.waitUntil(5000) { runBlocking { db.measurementDao().observeAll().first().single().weightKg } == 72.5f }
        val after = runBlocking { db.measurementDao().observeAll().first() }.single()
        assertEquals(id, after.id)
        assertEquals(50L, after.epochDay)
        assertEquals(80f, after.waistCm!!, 0f)
    }
}
