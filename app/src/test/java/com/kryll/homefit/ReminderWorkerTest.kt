package com.kryll.homefit

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.kryll.homefit.data.UserProfile
import com.kryll.homefit.domain.ReminderPrefs
import com.kryll.homefit.reminders.ReminderScheduler
import com.kryll.homefit.reminders.ReminderWorker
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Ejecuta el Worker y el planificador de verdad con el WorkManager de pruebas (no solo la lógica). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HomeFitApp::class)
class ReminderWorkerTest {
    private val app: HomeFitApp = ApplicationProvider.getApplicationContext()
    private val nm get() = app.getSystemService(NotificationManager::class.java)
    private val allDays = 0b1111111

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    private fun profile(mask: Int) = UserProfile(
        name = "Test", sex = "male", birthEpochDay = 9000, heightCm = 180f, weightKg = 80f,
        goal = "tonificar", level = "principiante", daysMask = mask, minutesPerSession = 30,
    )

    private fun setup(mask: Int, prefs: ReminderPrefs) = runBlocking {
        app.container.database.profileDao().saveProfile(profile(mask))
        app.container.settings.update { it.copy(reminderEnabled = prefs.enabled, reminderHour = prefs.hour, reminderMinute = prefs.minute) }
    }

    private fun infos(): List<WorkInfo> =
        WorkManager.getInstance(app).getWorkInfosForUniqueWork("recordatorio_diario").get()

    private fun runWorker(): ListenableWorker.Result =
        runBlocking { TestListenableWorkerBuilder<ReminderWorker>(app as Context).build().doWork() }

    private fun enqueued() = infos().filter { it.state == WorkInfo.State.ENQUEUED }

    @Test fun enablingSchedulesOneWorkWithDelay() {
        setup(allDays, ReminderPrefs(true, 8, 0))
        runBlocking { ReminderScheduler.sync(app) }
        assertEquals(1, enqueued().size)
    }

    @Test fun changingTimeReplacesTheWork() {
        setup(allDays, ReminderPrefs(true, 8, 0))
        runBlocking { ReminderScheduler.sync(app) }
        val first = enqueued().single()
        setup(allDays, ReminderPrefs(true, 21, 15))
        runBlocking { ReminderScheduler.sync(app) }
        val now = enqueued()
        assertEquals(1, now.size)
        assertNotEquals(first.id, now.single().id)
        assertNotEquals(first.initialDelayMillis, now.single().initialDelayMillis)
        assertEquals(WorkInfo.State.CANCELLED, infos().first { it.id == first.id }.state)
    }

    @Test fun changingDaysReplacesTheWork() {
        setup(0b0000001, ReminderPrefs(true, 8, 0)) // solo lunes
        runBlocking { ReminderScheduler.sync(app) }
        val first = enqueued().single()
        setup(allDays, ReminderPrefs(true, 8, 0))
        runBlocking { ReminderScheduler.sync(app) }
        assertEquals(1, enqueued().size)
        assertNotEquals(first.id, enqueued().single().id)
    }

    @Test fun disablingCancelsAndLeavesNothingEnqueued() {
        setup(allDays, ReminderPrefs(true, 8, 0))
        runBlocking { ReminderScheduler.sync(app) }
        setup(allDays, ReminderPrefs(false, 8, 0))
        runBlocking { ReminderScheduler.sync(app) }
        assertEquals(0, enqueued().size)
    }

    @Test fun noDaysChosenLeavesNothingEnqueued() {
        setup(0, ReminderPrefs(true, 8, 0))
        runBlocking { ReminderScheduler.sync(app) }
        assertEquals(0, enqueued().size)
    }

    @Test fun workerPostsNotificationAndSchedulesTheNextOne() {
        setup(allDays, ReminderPrefs(true, 8, 0))
        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertEquals(1, shadowOf(nm).allNotifications.size)
        assertEquals(1, enqueued().size)
    }

    @Test fun workerStaysQuietWhenDisabledAndDoesNotReschedule() {
        setup(allDays, ReminderPrefs(false, 8, 0))
        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertEquals(0, shadowOf(nm).allNotifications.size)
        assertEquals(0, enqueued().size)
    }

    @Test fun workerStaysQuietOnRestDay() {
        val todayBit = 1 shl (LocalDate.now().dayOfWeek.value - 1)
        setup(allDays and todayBit.inv(), ReminderPrefs(true, 8, 0))
        runWorker()
        assertEquals(0, shadowOf(nm).allNotifications.size)
        assertEquals(1, enqueued().size) // sigue programado para el próximo día de entreno
    }

    @Test fun workerStaysQuietIfAlreadyTrainedToday() {
        setup(allDays, ReminderPrefs(true, 8, 0))
        runBlocking {
            val now = System.currentTimeMillis()
            app.container.database.sessionDao().insertWorkout(
                com.kryll.homefit.data.WorkoutSession(routineId = null, startMillis = now - 60_000, endMillis = now, durationSeconds = 60),
            )
        }
        runWorker()
        assertEquals(0, shadowOf(nm).allNotifications.size)
    }

    @Test fun workerStaysQuietWhenNotificationsAreBlockedByTheUser() {
        setup(allDays, ReminderPrefs(true, 8, 0))
        shadowOf(nm).setNotificationsEnabled(false)
        runWorker()
        assertEquals(0, shadowOf(nm).allNotifications.size)
    }
}
