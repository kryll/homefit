package com.kryll.homefit

import android.app.AlarmManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.kryll.homefit.data.AppSettings
import com.kryll.homefit.data.BackupCodec
import com.kryll.homefit.data.BackupRepository
import com.kryll.homefit.domain.Planning
import com.kryll.homefit.domain.ReminderPrefs
import com.kryll.homefit.reminders.ExactReminders
import com.kryll.homefit.reminders.ReminderScheduler
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Selección de mecanismo del recordatorio: alarma exacta solo con el ajuste activo Y el permiso concedido;
 * en cualquier otro caso cae al trabajo inexacto de WorkManager. Y el ajuste sobrevive al backup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HomeFitApp::class)
class ExactReminderTest {
    private val app: HomeFitApp = ApplicationProvider.getApplicationContext()
    private val alarmManager: AlarmManager = app.getSystemService(AlarmManager::class.java)

    @Before fun setUp() { WorkManagerTestInitHelper.initializeTestWorkManager(app) }

    private fun enqueuedWork() = WorkManager.getInstance(app).getWorkInfosForUniqueWork("recordatorio_diario").get()
        .filter { it.state == WorkInfo.State.ENQUEUED }

    private fun prefs(exact: Boolean) = ReminderPrefs(enabled = true, hour = 18, minute = 0, leadMinutes = 0, exact = exact)

    @Test fun exactEnabledAndGrantedSchedulesAlarmNotWork() {
        shadowOf(alarmManager).setCanScheduleExactAlarms(true)
        val p = prefs(exact = true)
        val from = ZonedDateTime.now()
        ReminderScheduler.schedule(app, p, 0b1111111, from)
        val alarm = shadowOf(alarmManager).nextScheduledAlarm
        assertNotNull(alarm)
        assertEquals(Planning.nextReminder(from, 0b1111111, p)!!.toInstant().toEpochMilli(), alarm.triggerAtMillis)
        assertTrue("WorkManager no debe tener el aviso diario en modo exacto", enqueuedWork().isEmpty())
    }

    @Test fun exactEnabledButDeniedFallsBackToWorkManager() {
        shadowOf(alarmManager).setCanScheduleExactAlarms(false)
        ReminderScheduler.schedule(app, prefs(exact = true), 0b1111111)
        assertNull(shadowOf(alarmManager).nextScheduledAlarm)
        assertEquals(1, enqueuedWork().size)
    }

    @Test fun exactDisabledUsesWorkManagerEvenIfGranted() {
        shadowOf(alarmManager).setCanScheduleExactAlarms(true)
        ReminderScheduler.schedule(app, prefs(exact = false), 0b1111111)
        assertNull(shadowOf(alarmManager).nextScheduledAlarm)
        assertEquals(1, enqueuedWork().size)
    }

    @Test fun reminderExactSurvivesBackupRoundTrip() = runBlocking {
        val repo = BackupRepository(app.container.database)
        val json = BackupCodec.toJson(repo.export(AppSettings(reminderEnabled = true, reminderHour = 7, reminderMinute = 30, reminderExact = true), 1))
        val restored = BackupCodec.fromJson(json)
        assertEquals(true, restored.settings.reminderEnabled)
        assertEquals(true, restored.settings.reminderExact)
    }
}
