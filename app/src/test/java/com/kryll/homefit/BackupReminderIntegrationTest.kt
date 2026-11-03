package com.kryll.homefit

import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.kryll.homefit.data.AppSettings
import com.kryll.homefit.data.BackupApplier
import com.kryll.homefit.data.BackupCodec
import com.kryll.homefit.data.BackupRepository
import com.kryll.homefit.data.UserProfile
import com.kryll.homefit.reminders.ReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Importar una copia con recordatorio activo reprograma el Worker real (WorkManager de pruebas). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HomeFitApp::class)
class BackupReminderIntegrationTest {
    private val app: HomeFitApp = ApplicationProvider.getApplicationContext()

    @Before fun setUp() { WorkManagerTestInitHelper.initializeTestWorkManager(app) }

    private fun enqueued() = WorkManager.getInstance(app).getWorkInfosForUniqueWork("recordatorio_diario").get()
        .filter { it.state == WorkInfo.State.ENQUEUED }

    @Test fun importResyncsReminder() = runBlocking {
        val db = app.container.database
        db.profileDao().saveProfile(
            UserProfile(name = "T", sex = "male", birthEpochDay = 9000, heightCm = 180f, weightKg = 80f, goal = "tonificar", level = "principiante", daysMask = 0b1111111, minutesPerSession = 30),
        )
        val repo = BackupRepository(db)
        val backup = BackupCodec.fromJson(
            BackupCodec.toJson(repo.export(AppSettings(reminderEnabled = true, reminderHour = 7, reminderMinute = 30), 1)),
        )
        app.container.settings.update { it.copy(reminderEnabled = false) }
        ReminderScheduler.sync(app)
        assertEquals(0, enqueued().size)

        BackupApplier(
            repo,
            currentSettings = { app.container.settings.settings.first() },
            writeSettings = { n -> app.container.settings.update { n } },
            afterApply = { ReminderScheduler.sync(app) },
        ).apply(backup)

        val s = app.container.settings.settings.first()
        assertEquals(true, s.reminderEnabled); assertEquals(7, s.reminderHour); assertEquals(30, s.reminderMinute)
        assertEquals(1, enqueued().size)
    }
}
