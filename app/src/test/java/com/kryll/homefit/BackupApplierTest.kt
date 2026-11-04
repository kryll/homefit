package com.kryll.homefit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kryll.homefit.data.AppSettings
import com.kryll.homefit.data.BackupApplier
import com.kryll.homefit.data.BackupCodec
import com.kryll.homefit.data.BackupRepository
import com.kryll.homefit.data.HomeFitDatabase
import com.kryll.homefit.data.Routine
import com.kryll.homefit.data.ThemeMode
import com.kryll.homefit.data.UserProfile
import com.kryll.homefit.domain.ExerciseCatalog
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupApplierTest {
    private lateinit var db: HomeFitDatabase
    private lateinit var repo: BackupRepository
    private var stored = AppSettings(theme = ThemeMode.LIGHT, onboardingDone = true)
    private val writes = mutableListOf<AppSettings>()

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HomeFitDatabase::class.java).allowMainThreadQueries().build()
        repo = BackupRepository(db)
        db.exerciseDao().insertAll(ExerciseCatalog.parse(File("src/main/assets/exercises.json").readText()).exercises)
        db.routineDao().insertRoutine(Routine(name = "Actual", goal = "g", level = "l", type = "personalizada", predefined = false))
        db.profileDao().saveProfile(UserProfile(name = "Yo", sex = "male", birthEpochDay = 9000, heightCm = 180f, weightKg = 80f, goal = "g", level = "l", daysMask = 1, minutesPerSession = 30))
    }

    @After fun tearDown() = db.close()

    private fun incoming() = BackupCodec.fromJson(
        BackupCodec.toJson(
            runBlocking { repo.export(AppSettings(theme = ThemeMode.DARK), 1) }
                .copy(routines = emptyList(), profile = null),
        ),
    )

    private fun applier(afterApply: suspend () -> Unit = {}, failWriteOnce: Boolean = false): BackupApplier {
        var failed = false
        return BackupApplier(repo, { stored }, { n ->
            if (failWriteOnce && !failed) { failed = true; throw IllegalStateException("DataStore falló") }
            writes += n; stored = n
        }, afterApply)
    }

    @Test fun successReplacesDataAndSettingsAndRunsAfterApply() = runBlocking {
        var after = 0
        applier(afterApply = { after++ }).apply(incoming())
        assertEquals(emptyList<String>(), db.backupDao().userRoutines().map { it.name })
        assertEquals(ThemeMode.DARK, stored.theme)
        assertEquals(true, stored.onboardingDone)
        assertEquals(1, after)
    }

    @Test fun settingsFailureRollsBackRoomAndSettings() = runBlocking {
        try { applier(failWriteOnce = true).apply(incoming()); fail("debía fallar") } catch (e: IllegalStateException) { }
        assertEquals(listOf("Actual"), db.backupDao().userRoutines().map { it.name })
        assertEquals("Yo", db.backupDao().profile()?.name)
        assertEquals(ThemeMode.LIGHT, stored.theme)
    }

    @Test fun afterApplyFailureRollsBackEverything() = runBlocking {
        var calls = 0
        try {
            applier(afterApply = { if (calls++ == 0) throw IllegalStateException("reprogramar falló") }).apply(incoming())
            fail("debía fallar")
        } catch (e: IllegalStateException) { }
        assertEquals(listOf("Actual"), db.backupDao().userRoutines().map { it.name })
        assertEquals(ThemeMode.LIGHT, stored.theme)
        assertEquals(2, calls) // se reprograma otra vez con el estado restaurado
    }

    @Test fun invalidBackupTouchesNothing() = runBlocking {
        val b = incoming()
        val bad = b.copy(routines = listOf(com.kryll.homefit.data.BackupRoutine(1, Routine(name = "X", goal = "g", level = "l", type = "t", predefined = false),
            listOf(com.kryll.homefit.data.RoutineItem(routineId = 0, position = 0, exerciseId = "no_existe", sets = 1, restSeconds = 10)))))
        try { applier().apply(bad); fail("debía fallar") } catch (e: com.kryll.homefit.data.BackupException) { }
        assertEquals(listOf("Actual"), db.backupDao().userRoutines().map { it.name })
        assertEquals(0, writes.size)
    }
}
