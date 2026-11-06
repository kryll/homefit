package com.kryll.homefit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kryll.homefit.data.HomeFitDatabase
import com.kryll.homefit.data.Routine
import com.kryll.homefit.data.ScheduledSession
import com.kryll.homefit.data.SettingsStore
import com.kryll.homefit.data.UserProfile
import com.kryll.homefit.ui.vm.PlanMessage
import com.kryll.homefit.ui.vm.PlanViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlanViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private lateinit var db: HomeFitDatabase
    private lateinit var vm: PlanViewModel
    private val roomExecutor = Executors.newSingleThreadExecutor()
    private val trackedVms = mutableListOf<ViewModel>()
    private fun <T : ViewModel> track(instance: T): T = instance.also(trackedVms::add)

    private val zone = ZoneId.systemDefault()
    private fun startOf(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HomeFitDatabase::class.java)
            .setQueryExecutor(roomExecutor)
            .setTransactionExecutor(roomExecutor)
            .allowMainThreadQueries().build()
        vm = track(PlanViewModel(db, SettingsStore(ApplicationProvider.getApplicationContext())))
    }

    @After fun tearDown() = runBlocking {
        trackedVms.forEach { it.viewModelScope.cancel() }
        trackedVms.forEach { it.viewModelScope.coroutineContext[Job]?.join() }
        trackedVms.clear()
        db.close()
        roomExecutor.shutdown()
    }

    private suspend fun seedProfile(daysMask: Int) {
        db.profileDao().saveProfile(UserProfile(name = "A", sex = "male", birthEpochDay = 9000, heightCm = 180f, weightKg = 80f, goal = "g", level = "l", daysMask = daysMask, minutesPerSession = 30))
    }
    private suspend fun seedRoutine() {
        db.routineDao().insertRoutine(Routine(name = "R", goal = "g", level = "l", type = "FULL", predefined = false))
    }
    private suspend fun scheduledCount(): Int = db.sessionDao().observeAllScheduled().first().size

    @Test fun autoPlanSinDiasDeEntrenoNoBorraLaSemana() = runTest {
        seedProfile(0)
        seedRoutine()
        val today = LocalDate.now(zone)
        db.sessionDao().insertScheduled(ScheduledSession(routineId = 1, startMillis = startOf(today.plusDays(1)), status = "SCHEDULED"))
        vm.autoPlan()
        vm.message.first { it != null }
        assertEquals(PlanMessage.NoTrainingDays, vm.message.value)
        assertEquals(1, scheduledCount())
    }

    @Test fun autoPlanSinRutinasNoBorraLaSemana() = runTest {
        seedProfile(0b1111111)
        val today = LocalDate.now(zone)
        db.sessionDao().insertScheduled(ScheduledSession(routineId = null, startMillis = startOf(today.plusDays(1)), status = "SCHEDULED"))
        vm.autoPlan()
        vm.message.first { it != null }
        assertEquals(PlanMessage.NoRoutines, vm.message.value)
        assertEquals(1, scheduledCount())
    }

    @Test fun autoPlanReparteEnDiasDeEntrenoYAvisa() = runTest {
        seedProfile(0b1111111) // todos los días
        seedRoutine()
        val today = LocalDate.now(zone)
        vm.autoPlan()
        vm.message.first { it != null }
        val msg = vm.message.value
        assertTrue(msg is PlanMessage.Generated && msg.days.size == 7)
        assertEquals(7, scheduledCount())
    }

    @Test fun autoPlanSoloEnDiasMarcados() = runTest {
        seedProfile(0b0010101) // lun, mié, vie
        seedRoutine()
        val today = LocalDate.now(zone)
        vm.autoPlan()
        vm.message.first { it != null }
        val days = db.sessionDao().observeAllScheduled().first()
        assertTrue(days.isNotEmpty())
        days.forEach {
            val d = java.time.Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalDate()
            assertTrue(d.dayOfWeek.value == 1 || d.dayOfWeek.value == 3 || d.dayOfWeek.value == 5)
        }
    }

    @Test fun clearWeekBorraYReportaElConteo() = runTest {
        val today = LocalDate.now(zone)
        db.sessionDao().insertScheduled(ScheduledSession(routineId = null, startMillis = startOf(today.plusDays(1)), status = "SCHEDULED"))
        db.sessionDao().insertScheduled(ScheduledSession(routineId = null, startMillis = startOf(today.plusDays(2)), status = "SCHEDULED"))
        db.sessionDao().insertScheduled(ScheduledSession(routineId = null, startMillis = startOf(today.plusDays(9)), status = "SCHEDULED"))
        vm.clearWeek()
        vm.message.first { it != null }
        assertEquals(PlanMessage.Cleared(2), vm.message.value)
        assertEquals(1, scheduledCount())
    }

    @Test fun autoPlanNoAsignaFueraDeProximos7Dias() = runTest {
        seedProfile(0b1111111)
        seedRoutine()
        val today = LocalDate.now(zone)
        vm.autoPlan()
        vm.message.first { it != null }
        val days = db.sessionDao().observeAllScheduled().first()
        assertTrue(days.isNotEmpty())
        days.forEach {
            val d = java.time.Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalDate()
            assertTrue(!d.isBefore(today) && !d.isAfter(today.plusDays(6)))
        }
    }

    @Test fun clearWeekNoBorraAntesDeHoy() = runTest {
        val today = LocalDate.now(zone)
        db.sessionDao().insertScheduled(ScheduledSession(routineId = null, startMillis = startOf(today.minusDays(1)), status = "SCHEDULED"))
        db.sessionDao().insertScheduled(ScheduledSession(routineId = null, startMillis = startOf(today.plusDays(1)), status = "SCHEDULED"))
        vm.clearWeek()
        vm.message.first { it != null }
        assertEquals(PlanMessage.Cleared(1), vm.message.value)
        assertEquals(1, scheduledCount())
    }
}
