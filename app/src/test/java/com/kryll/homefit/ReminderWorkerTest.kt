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
import kotlinx.coroutines.flow.first
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
        runBlocking { app.startup?.join() }
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    private fun profile(mask: Int) = UserProfile(
        name = "Test", sex = "male", birthEpochDay = 9000, heightCm = 180f, weightKg = 80f,
        goal = "tonificar", level = "principiante", daysMask = mask, minutesPerSession = 30,
    )

    private fun setup(mask: Int, prefs: ReminderPrefs) = runBlocking {
        app.container.database.profileDao().saveProfile(profile(mask))
        app.container.settings.update { it.copy(reminderEnabled = prefs.enabled, reminderHour = prefs.hour, reminderMinute = prefs.minute, reminderLeadMinutes = prefs.leadMinutes) }
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
        // REPLACE descarta el trabajo anterior: no queda ninguno vivo con el id viejo.
        assertEquals(null, infos().firstOrNull { it.id == first.id && !it.state.isFinished })
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

    @Test fun dailyReminderMarksTheDaySoTheBackupDoesNotRepeatIt() {
        setup(allDays, ReminderPrefs(true, 8, 0))
        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertEquals(1, shadowOf(nm).allNotifications.size)
        assertEquals(LocalDate.now().toEpochDay(), runBlocking { app.container.settings.settings.first() }.reminderFiredEpochDay)
        // Si el usuario quita el aviso y el respaldo de WorkManager dispara el mismo día, no repite…
        shadowOf(nm).cancel(com.kryll.homefit.reminders.ReminderNotifier.NOTIFICATION_ID)
        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertEquals(0, shadowOf(nm).allNotifications.size)
        assertEquals(1, enqueued().size) // …pero el ciclo diario se reprograma igual
    }

    @Test fun snoozeStillNotifiesWhenTheDailyReminderAlreadyFired() {
        setup(allDays, ReminderPrefs(true, 8, 0))
        runWorker()
        shadowOf(nm).cancel(com.kryll.homefit.reminders.ReminderNotifier.NOTIFICATION_ID)
        assertEquals(ListenableWorker.Result.success(), runSnoozedWorker())
        assertEquals(1, shadowOf(nm).allNotifications.size)
    }

    @Test fun workerStaysQuietWhenNotificationsAreBlockedByTheUser() {
        setup(allDays, ReminderPrefs(true, 8, 0))
        shadowOf(nm).setNotificationsEnabled(false)
        runWorker()
        assertEquals(0, shadowOf(nm).allNotifications.size)
    }

    private fun runSnoozedWorker(): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<ReminderWorker>(app as Context)
            .setInputData(androidx.work.workDataOf(ReminderScheduler.KEY_SNOOZE to true)).build().doWork()
    }

    private fun snoozeInfos(): List<WorkInfo> =
        WorkManager.getInstance(app).getWorkInfosForUniqueWork("recordatorio_posponer").get()

    @Test fun snoozeActionRemovesTheNotificationAndQueuesOneLaterWork() {
        setup(allDays, ReminderPrefs(true, 8, 0))
        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertEquals(1, shadowOf(nm).allNotifications.size)
        com.kryll.homefit.reminders.SnoozeReceiver().onReceive(app, android.content.Intent(com.kryll.homefit.reminders.SnoozeReceiver.ACTION))
        assertEquals(0, shadowOf(nm).allNotifications.size)
        val queued = snoozeInfos().filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(1, queued.size)
        assertEquals(15 * 60_000L, queued.single().initialDelayMillis)
    }

    @Test fun snoozedWorkShowsAgainButDoesNotTouchTheDailyChain() {
        setup(allDays, ReminderPrefs(true, 8, 0))
        runBlocking { ReminderScheduler.sync(app) }
        val daily = enqueued().single()
        assertEquals(ListenableWorker.Result.success(), runSnoozedWorker())
        assertEquals(1, shadowOf(nm).allNotifications.size)
        assertEquals(daily.id, enqueued().single().id)
    }

    @Test fun disablingTheReminderAlsoCancelsASnooze() {
        setup(allDays, ReminderPrefs(true, 8, 0))
        ReminderScheduler.snooze(app)
        assertEquals(1, snoozeInfos().count { it.state == WorkInfo.State.ENQUEUED })
        setup(allDays, ReminderPrefs(false, 8, 0))
        runBlocking { ReminderScheduler.sync(app) }
        assertEquals(0, snoozeInfos().count { it.state == WorkInfo.State.ENQUEUED })
    }
}
