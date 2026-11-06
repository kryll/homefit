package com.kryll.homefit

import android.app.Application
import java.io.FileOutputStream
import java.io.File
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.Modifier
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import android.graphics.Canvas
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kryll.homefit.data.HomeFitDatabase
import com.kryll.homefit.data.Routine
import com.kryll.homefit.data.ScheduledSession
import com.kryll.homefit.data.SettingsStore
import com.kryll.homefit.data.UserProfile
import com.kryll.homefit.ui.PlanScreen
import com.kryll.homefit.ui.dayLabel
import com.kryll.homefit.ui.theme.HomeFitTheme
import com.kryll.homefit.ui.vm.PlanMessage
import com.kryll.homefit.ui.vm.PlanViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Recorrido completo: clic real en la UI de Plan -> PlanViewModel -> Room real (en memoria)
 * -> flujo reemitido -> estado renderizado. Fija que Generar/Vaciar funcionan de punta a punta,
 * no solo la lógica aislada del ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h780dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlanIntegrationTest {
    @get:Rule(order = 0) val mainDispatcher = MainDispatcherRule()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: HomeFitDatabase
    private val roomExecutor = Executors.newSingleThreadExecutor()
    private val trackedVms = mutableListOf<ViewModel>()
    private fun <T : ViewModel> track(instance: T): T = instance.also(trackedVms::add)

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now(zone)
    private fun startOf(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
    private val todayMask = 1 shl (today.dayOfWeek.value - 1)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HomeFitDatabase::class.java)
            .setQueryExecutor(roomExecutor)
            .setTransactionExecutor(roomExecutor)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = runBlocking {
        trackedVms.forEach { it.viewModelScope.cancel() }
        trackedVms.forEach { it.viewModelScope.coroutineContext[Job]?.join() }
        trackedVms.clear()
        db.close()
        roomExecutor.shutdown()
    }

    private fun seedProfile(mask: Int) = runBlocking {
        db.profileDao().saveProfile(UserProfile(name = "A", sex = "male", birthEpochDay = 9000, heightCm = 180f, weightKg = 80f, goal = "g", level = "l", daysMask = mask, minutesPerSession = 30))
    }

    private fun seedRoutine(): Long = runBlocking {
        db.routineDao().insertRoutine(Routine(name = "Fuerza en casa", goal = "g", level = "l", type = "FULL", predefined = false))
    }

    private fun newVm(): PlanViewModel = track(PlanViewModel(db, SettingsStore(ApplicationProvider.getApplicationContext())))

    /** PlanScreen conectada al ViewModel real, con el mismo mapeo de mensajes que PlanTab. */
    private fun renderPlan(vm: PlanViewModel) {
        rule.setContent {
            val ui by vm.ui.collectAsState()
            val message by vm.message.collectAsState()
            val u = ui
            HomeFitTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (u != null) PlanScreen(
                    today = u.today, zone = u.zone, daysMask = u.daysMask, routines = u.routines,
                    scheduled = u.scheduled, workouts = u.workouts,
                    message = when (val m = message) {
                        is PlanMessage.Generated -> "Plan generado para: " + m.days.joinToString(", ") { dayLabel(it) } + "."
                        is PlanMessage.Cleared -> if (m.count == 0) "Semana vaciada: no había sesiones planificadas." else if (m.count == 1) "Semana vaciada: 1 sesión eliminada." else "Semana vaciada: ${m.count} sesiones eliminadas."
                        PlanMessage.NoRoutines -> "Aún no hay rutinas que repartir."
                        PlanMessage.NoTrainingDays -> "Marca tus días de entrenamiento en Ajustes para poder generar el plan."
                        PlanMessage.AllBusy -> "No hay días libres en esta semana."
                        null -> null
                    },
                    onAuto = { ws -> vm.autoPlan(ws) },
                    onClear = { ws -> vm.clearWeek(ws) },
                )
                }
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

    @Test fun generarDesdeLaUiPersisteYRenderiza() {
        seedProfile(todayMask) // hoy es el único día de entreno
        seedRoutine()
        val vm = newVm()
        renderPlan(vm)

        rule.waitUntil(5_000) { rule.onAllNodesWithText("Generar esta semana").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Generar esta semana").performClick()

        // Mensaje de resultado visible (persistente) y estado persistido con la rutina asignada hoy.
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Plan generado para:", substring = true).fetchSemanticsNodes().isNotEmpty() }
        capture("plan_generado")
        runBlocking {
            val sessions = db.sessionDao().observeAllScheduled().first()
            assertEquals(1, sessions.size)
            val d = java.time.Instant.ofEpochMilli(sessions[0].startMillis).atZone(zone).toLocalDate()
            assertEquals(today, d)
        }
        // La tarjeta del día muestra la rutina tras la reemisión del flujo.
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Fuerza en casa"))
        rule.onNodeWithText("Fuerza en casa").assertIsDisplayed()
    }

    @Test fun vaciarDesdeLaUiEliminaYRenderiza() {
        seedProfile(todayMask)
        val routineId = seedRoutine()
        runBlocking {
            db.sessionDao().insertScheduled(ScheduledSession(routineId = routineId, startMillis = startOf(today), status = "SCHEDULED"))
            // Caso José: sesión pendiente en un día PASADO de la semana visible (Mié de esta semana si hoy es posterior).
            val pastDay = com.kryll.homefit.domain.CalendarPeriod.weekStart(today)
            if (pastDay.isBefore(today)) {
                db.sessionDao().insertScheduled(ScheduledSession(routineId = routineId, startMillis = startOf(pastDay), status = "SCHEDULED"))
            }
            // Historial: una sesión saltada NO se borra al vaciar.
            db.sessionDao().insertScheduled(ScheduledSession(routineId = routineId, startMillis = startOf(today.plusDays(1)), status = "SKIPPED"))
        }
        val vm = newVm()
        renderPlan(vm)

        rule.waitUntil(5_000) { rule.onAllNodesWithText("Vaciar esta semana").fetchSemanticsNodes().isNotEmpty() }
        // La sesión sembrada se ve antes de vaciar (hoy puede quedar bajo el pliegue: hay que hacer scroll).
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Fuerza en casa"))
        rule.onNodeWithText("Fuerza en casa").assertIsDisplayed()

        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Vaciar esta semana"))
        rule.onNodeWithText("Vaciar esta semana").performClick()

        val expectedDeleted = db.sessionDao().let { dao -> runBlocking { dao.observeAllScheduled().first().count { it.status == "SCHEDULED" } } }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Semana vaciada: ", substring = true).fetchSemanticsNodes().isNotEmpty() }
        capture("plan_vaciado")
        runBlocking {
            val remaining = db.sessionDao().observeAllScheduled().first()
            // Se borran las pendientes de la semana visible (incluida la pasada) y se conserva la saltada.
            assertEquals(1, remaining.size)
            assertEquals("SKIPPED", remaining[0].status)
            assertTrue(expectedDeleted >= 1)
        }
        // Tras el borrado la tarjeta de hoy queda sin rutina ni botón de entrenar.
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("· hoy", substring = true))
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Entrenar").fetchSemanticsNodes().isEmpty() }
        // Varias tarjetas vacías muestran el texto: basta con que exista alguna.
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Sin rutina asignada").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun generarSinDiasDeEntrenoExplicaSinBorrar() {
        seedProfile(0) // sin días de entreno: el botón sigue pulsable y explica la causa
        val routineId = seedRoutine()
        runBlocking { db.sessionDao().insertScheduled(ScheduledSession(routineId = routineId, startMillis = startOf(today.plusDays(1)), status = "SCHEDULED")) }
        val vm = newVm()
        renderPlan(vm)

        rule.waitUntil(5_000) { rule.onAllNodesWithText("Generar esta semana").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Generar esta semana").performClick()

        rule.waitUntil(5_000) { vm.message.value == PlanMessage.NoTrainingDays }
        rule.onAllNodesWithText("Marca tus días de entrenamiento en Ajustes", substring = true).fetchSemanticsNodes().also {
            assertTrue(it.isNotEmpty())
        }
        runBlocking { assertEquals(1, db.sessionDao().observeAllScheduled().first().size) }
        capture("plan_sin_dias")
    }

    /** Generar respeta lo ya planificado/hecho: solo rellena días de entreno libres desde hoy. */
    @Test fun generarSoloRellenaDiasLibres() {
        seedProfile(todayMask) // hoy es el único día de entreno
        val routineId = seedRoutine()
        runBlocking { db.sessionDao().insertScheduled(ScheduledSession(routineId = routineId, startMillis = startOf(today), status = "SCHEDULED")) }
        val vm = newVm()
        renderPlan(vm)

        rule.waitUntil(5_000) { rule.onAllNodesWithText("Generar esta semana").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Generar esta semana").performClick()

        // Hoy ya está ocupado: no hay días libres, no toca nada.
        rule.waitUntil(5_000) { rule.onAllNodesWithText("No hay días libres", substring = true).fetchSemanticsNodes().isNotEmpty() }
        runBlocking {
            val sessions = db.sessionDao().observeAllScheduled().first()
            assertEquals(1, sessions.size)
            assertEquals("SCHEDULED", sessions[0].status)
        }
    }
}
