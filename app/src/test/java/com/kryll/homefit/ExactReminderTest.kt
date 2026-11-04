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
import org.robolectric.shadows.ShadowAlarmManager

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

    private fun enqueuedWork(name: String = "recordatorio_diario") = WorkManager.getInstance(app).getWorkInfosForUniqueWork(name).get()
        .filter { it.state == WorkInfo.State.ENQUEUED }

    private fun prefs(exact: Boolean) = ReminderPrefs(enabled = true, hour = 18, minute = 0, leadMinutes = 0, exact = exact)

    @Test fun exactEnabledAndGrantedSchedulesAlarmPlusWorkManagerBackup() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val p = prefs(exact = true)
        val from = ZonedDateTime.now()
        ReminderScheduler.schedule(app, p, 0b1111111, from)
        val alarm = shadowOf(alarmManager).nextScheduledAlarm
        assertNotNull("con el permiso concedido debe armar la alarma exacta", alarm)
        assertEquals(Planning.nextReminder(from, 0b1111111, p)!!.toInstant().toEpochMilli(), alarm.triggerAtMs)
        // Respaldo de WorkManager unos minutos después: si el sistema borra la alarma (revocación),
        // ese día avisa el respaldo con retraso y el ciclo se reconstruye solo.
        val backup = enqueuedWork().singleOrNull()
        assertNotNull("el respaldo de WorkManager debe estar en cola junto a la alarma", backup)
        val expectedDelay = java.time.Duration.between(ZonedDateTime.now(), Planning.nextReminder(from, 0b1111111, p)!!.plusMinutes(ReminderScheduler.BACKUP_OFFSET_MINUTES)).toMillis()
        kotlin.math.abs(backup!!.initialDelayMillis - expectedDelay).let { assertTrue("respaldo ~15 min después: $it ms de desvío", it < 60_000) }
    }

    @Test fun snoozeWithoutPermissionCancelsExactSnooze() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        ReminderScheduler.snooze(app)
        assertNotNull("con permiso el pospuesto usa la alarma exacta", shadowOf(alarmManager).nextScheduledAlarm)
        // El permiso se pierde antes de volver a posponer: no debe quedar el pospuesto exacto armado.
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        ReminderScheduler.snooze(app)
        assertNull("sin permiso no debe quedar ninguna alarma exacta armada", shadowOf(alarmManager).nextScheduledAlarm)
        assertEquals(1, enqueuedWork("recordatorio_posponer").size)
    }

    @Test fun exactEnabledButDeniedFallsBackToWorkManager() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        ReminderScheduler.schedule(app, prefs(exact = true), 0b1111111)
        assertNull(shadowOf(alarmManager).nextScheduledAlarm)
        assertEquals(1, enqueuedWork().size)
    }

    @Test fun exactDisabledUsesWorkManagerEvenIfGranted() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
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
