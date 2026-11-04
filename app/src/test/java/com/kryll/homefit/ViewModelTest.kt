package com.kryll.homefit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kryll.homefit.data.HomeFitDatabase
import com.kryll.homefit.data.BackupRepository
import com.kryll.homefit.data.RoutineRepository
import com.kryll.homefit.data.SettingsStore
import com.kryll.homefit.data.UserProfile
import com.kryll.homefit.data.WorkoutRepository
import com.kryll.homefit.domain.RoutineDraft
import com.kryll.homefit.domain.SetResult
import com.kryll.homefit.domain.WorkItem
import com.kryll.homefit.ui.vm.RoutinesViewModel
import com.kryll.homefit.data.BodyMeasurement
import com.kryll.homefit.data.WorkoutSession
import com.kryll.homefit.ui.vm.ProgressViewModel
import com.kryll.homefit.ui.vm.SettingsViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Regla estándar: Main = dispatcher de prueba para que viewModelScope sea determinista. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(UnconfinedTestDispatcher())
    override fun finished(description: Description) = Dispatchers.resetMain()
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private lateinit var db: HomeFitDatabase
    private lateinit var vm: RoutinesViewModel

    // Ejecutor Room de un solo hilo: trabajo serial y orden determinista entre consultas.
    // (Un ejecutor "directo" NO vale: compartiría el hilo del test con las transacciones
    // suspending de Room y rompería su control por ThreadLocal.)
    private val roomExecutor = Executors.newSingleThreadExecutor()
    private val trackedVms = mutableListOf<ViewModel>()

    private fun <T : ViewModel> track(instance: T): T = instance.also(trackedVms::add)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HomeFitDatabase::class.java)
            .setQueryExecutor(roomExecutor)
            .setTransactionExecutor(roomExecutor)
            .allowMainThreadQueries().build()
        vm = track(RoutinesViewModel(db, RoutineRepository(db), WorkoutRepository(db), SettingsStore(ApplicationProvider.getApplicationContext())))
    }

    // Cancela los collectors de cada ViewModel y espera su fin ANTES de cerrar Room:
    // sin tareas en vuelo, ninguna excepción cruzaba al siguiente test (UncaughtExceptionsBeforeTest).
    @After fun tearDown() = runBlocking {
        trackedVms.forEach { it.viewModelScope.cancel() }
        trackedVms.forEach { it.viewModelScope.coroutineContext[Job]?.join() }
        trackedVms.clear()
        db.close()
        roomExecutor.shutdown()
    }

    private suspend fun seedRoutine(): Long {
        val id = db.routineDao().insertRoutine(
            com.kryll.homefit.data.Routine(name = "R", goal = "FUERZA", level = "BASE", type = "FULL", predefined = false)
        )
        db.routineDao().insertItems(listOf(
            com.kryll.homefit.data.RoutineItem(routineId = id, position = 0, exerciseId = "sentadilla", sets = 2, reps = 10, seconds = null, restSeconds = 30, alternateSides = false),
            com.kryll.homefit.data.RoutineItem(routineId = id, position = 1, exerciseId = "plancha", sets = 1, reps = null, seconds = 30, restSeconds = 0, alternateSides = false),
        ))
        return id
    }

    @Test fun routinesFlowEmiteDatosDeRoom() = runTest {
        val id = seedRoutine()
        val list = vm.routines.first { it.isNotEmpty() }
        assertEquals(id, list[0].id)
        val items = vm.itemsByRoutine.first { it.isNotEmpty() }
        assertEquals(2, items[id]?.size)
    }

    @Test fun deleteRoutineLaQuitaDelFlujo() = runTest {
        val id = seedRoutine()
        vm.routines.first { it.isNotEmpty() }
        vm.deleteRoutine(id)
        vm.routines.first { it.isEmpty() }
    }

    @Test fun sesionSinInterrumpidoArrancaLimpia() = runTest {
        val id = seedRoutine()
        vm.startWorkout(id, 5000)
        vm.checkResume(plan(), 5000)
        val s = vm.session.first { it.resumeChecked }
        assertTrue(s.resumeChecked)
        assertNull(s.resumable)
        assertNull(s.resumedSets)
    }

    @Test fun sesionInterrumpidaSeOfreceYReanuda() = runTest {
        val id = seedRoutine()
        val plan = plan()
        // Entreno interrumpido real vía repositorio (checkpoint guarda la fila).
        val repo = WorkoutRepository(db)
        repo.checkpoint(id, plan, 1000, 2000, listOf(SetResult(0, 1, null, false, 0, reps = 8)), isClosed = { false })
        vm.startWorkout(id, 3000)
        vm.checkResume(plan, 3000)
        val offered = vm.session.first { it.resumable != null }
        assertEquals(1000, offered.resumable!!.startMillis)
        vm.continueResume()
        val cont = vm.session.value
        assertNull(cont.resumable)
        assertNotNull(cont.resumedSets)
        assertEquals(1000, cont.startMillis)
    }

    @Test fun descartarReanudacionNoVuelveAOfrecerla() = runTest {
        val id = seedRoutine()
        val plan = plan()
        val repo = WorkoutRepository(db)
        repo.checkpoint(id, plan, 1000, 2000, listOf(SetResult(0, 1, null, false, 0, reps = 8)), isClosed = { false })
        vm.startWorkout(id, 3000)
        vm.checkResume(plan, 3000)
        vm.session.first { it.resumable != null }
        vm.discardResume()
        assertNull(vm.session.value.resumable)
        // Tras descartar, una nueva comprobación no la adopta de nuevo (fila marcada 'descartado').
        vm.cancelWorkout()
        vm.startWorkout(id, 4000)
        vm.checkResume(plan, 4000)
        // La adopción corre en el ejecutor Room (un solo hilo serial): cualquier consulta
        // encolada después garantiza que aquella ya terminó; assert determinista, sin esperas fijas.
        db.routineDao().observeAll().first()
        assertNull(vm.session.value.resumable)
    }

    @Test fun finishWorkoutGuardaYLimpiaSesion() = runTest {
        val id = seedRoutine()
        vm.startWorkout(id, 1000)
        vm.finishWorkout(plan(), 2000, finished = true, results = listOf(SetResult(0, 1, null, false, 0, reps = 10)))
        assertNull(vm.session.value.routineId)
        val workouts = db.sessionDao().observeWorkouts().first { it.isNotEmpty() }
        assertEquals(id, workouts[0].routineId)
    }

    private fun plan() = listOf(
        WorkItem("sentadilla", "Sentadilla", 2, 10, null, 30),
        WorkItem("plancha", "Plancha", 1, null, 30, 0),
    )

    @Test fun ajustesCambianYSeReflejanEnElFlujo() = runTest {
        val store = SettingsStore(ApplicationProvider.getApplicationContext())
        val svm = track(SettingsViewModel(db, store, BackupRepository(db)))
        svm.changeSettings { it.copy(defaultRestSeconds = 60) }
        val s = svm.appSettings.first { it.defaultRestSeconds == 60 }
        assertEquals(60, s.defaultRestSeconds)
    }

    @Test fun guardarPerfilLoExponeEnLosFlujos() = runTest {
        val svm = track(SettingsViewModel(db, SettingsStore(ApplicationProvider.getApplicationContext()), BackupRepository(db)))
        svm.saveProfile(
            UserProfile(name = "Ana", sex = "mujer", birthEpochDay = 0, heightCm = 165f, weightKg = 60f, goal = "TONIFICAR", level = "BASE", daysMask = 31, minutesPerSession = 30),
            setOf("bandas_elasticas"),
        )
        val p = svm.profile.first { it != null }
        assertEquals("Ana", p!!.name)
        assertEquals(setOf("bandas_elasticas"), svm.equipment.first { it.isNotEmpty() })
    }

    @Test fun progresoResumeElHistorial() = runTest {
        val pvm = track(ProgressViewModel(db, SettingsStore(ApplicationProvider.getApplicationContext())))
        db.sessionDao().insertWorkout(WorkoutSession(routineId = null, startMillis = System.currentTimeMillis(), endMillis = System.currentTimeMillis(), durationSeconds = 1800))
        val s = pvm.summary.first { it.totalSessions > 0 }
        assertEquals(1, s.totalSessions)
        assertEquals(30, s.totalMinutes)
    }

    @Test fun importarMedidasCsvOmiteDiasDuplicados() = runTest {
        val pvm = track(ProgressViewModel(db, SettingsStore(ApplicationProvider.getApplicationContext())))
        pvm.saveMeasurement(BodyMeasurement(epochDay = 20000, weightKg = 80f))
        pvm.measurements.first { it.isNotEmpty() }
        val csv = pvm.measuresCsv()
        val again = pvm.importMeasuresCsv(csv)
        assertEquals(0, again.imported)
        assertEquals(1, again.skipped)
        assertTrue(pvm.measurements.first { it.isNotEmpty() }.size == 1)
    }
}
