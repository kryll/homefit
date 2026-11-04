package com.kryll.homefit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kryll.homefit.data.AppSettings
import com.kryll.homefit.data.BackupCodec
import com.kryll.homefit.data.BackupException
import com.kryll.homefit.data.BackupRepository
import com.kryll.homefit.data.BodyMeasurement
import com.kryll.homefit.data.HomeFitDatabase
import com.kryll.homefit.data.Routine
import com.kryll.homefit.data.RoutineItem
import com.kryll.homefit.data.ScheduledSession
import com.kryll.homefit.data.SetLog
import com.kryll.homefit.data.UserEquipment
import com.kryll.homefit.data.UserProfile
import com.kryll.homefit.data.WorkoutSession
import com.kryll.homefit.domain.ExerciseCatalog
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupRepositoryTest {
    private lateinit var db: HomeFitDatabase
    private lateinit var repo: BackupRepository
    private var predefinedId = 0L

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HomeFitDatabase::class.java).allowMainThreadQueries().build()
        repo = BackupRepository(db)
        db.exerciseDao().insertAll(ExerciseCatalog.parse(File("src/main/assets/exercises.json").readText()).exercises)
        predefinedId = db.routineDao().insertRoutine(Routine(name = "Fuerza base", goal = "tonificar", level = "principiante", type = "fuerza", predefined = true))
        db.routineDao().insertItems(listOf(RoutineItem(routineId = predefinedId, position = 0, exerciseId = "plancha", sets = 3, seconds = 30, restSeconds = 30)))
    }

    @After fun tearDown() = db.close()

    private fun fill() = runBlocking {
        db.profileDao().saveProfile(UserProfile(name = "Ana", sex = "female", birthEpochDay = 9000, heightCm = 165f, weightKg = 60f, goal = "tonificar", level = "principiante", daysMask = 21, minutesPerSession = 30))
        db.profileDao().addEquipment(listOf(UserEquipment("esterilla")))
        val mine = db.routineDao().insertRoutine(Routine(name = "Mi rutina", goal = "tonificar", level = "principiante", type = "personalizada", predefined = false))
        db.routineDao().insertItems(listOf(RoutineItem(routineId = mine, position = 0, exerciseId = "sentadilla_goblet", sets = 3, reps = 10, restSeconds = 45)))
        val w1 = db.sessionDao().insertWorkout(WorkoutSession(routineId = mine, startMillis = 1000, endMillis = 2000, durationSeconds = 1))
        db.sessionDao().insertSets(listOf(SetLog(sessionId = w1, exerciseId = "sentadilla_goblet", setIndex = 0, reps = 10, rpe = 7)))
        db.sessionDao().insertWorkout(WorkoutSession(routineId = predefinedId, startMillis = 3000, endMillis = 4000, durationSeconds = 1))
        db.sessionDao().insertScheduled(ScheduledSession(routineId = mine, startMillis = 5000, status = "SCHEDULED"))
        db.backupDao().insertMeasurements(listOf(BodyMeasurement(epochDay = 20000, weightKg = 60f)))
        mine
    }

    @Test fun exportThenRestoreAfterWipeRecoversEverythingAndRemapsRoutines() = runBlocking {
        fill()
        val json = BackupCodec.toJson(repo.export(AppSettings(), 99))
        // Estado distinto antes de importar: otra rutina propia y otro entreno.
        db.routineDao().insertRoutine(Routine(name = "Basura", goal = "x", level = "y", type = "personalizada", predefined = false))
        db.sessionDao().insertWorkout(WorkoutSession(routineId = null, startMillis = 9, endMillis = 10, durationSeconds = 1))

        repo.restore(BackupCodec.fromJson(json))

        val dao = db.backupDao()
        assertEquals(listOf("Mi rutina"), dao.userRoutines().map { it.name })
        val mine = dao.userRoutines().single()
        assertEquals(1, dao.allItems().count { it.routineId == mine.id })
        assertEquals(2, dao.workouts().size)
        val byStart = dao.workouts().associateBy { it.startMillis }
        assertEquals(mine.id, byStart.getValue(1000).routineId)
        assertEquals(predefinedId, byStart.getValue(3000).routineId)
        val sets = dao.sets()
        assertEquals(1, sets.size)
        assertEquals(byStart.getValue(1000).id, sets[0].sessionId)
        assertEquals(7, sets[0].rpe)
        assertEquals(mine.id, dao.scheduled().single().routineId)
        assertEquals(1, dao.measurements().size)
        assertEquals("Ana", dao.profile()?.name)
        assertEquals(listOf("esterilla"), dao.equipment())
        // El catálogo y las predefinidas siguen ahí.
        assertTrue(dao.exerciseIds().size > 50)
        assertEquals(1, dao.predefinedRoutines().size)
    }

    @Test fun restoreIsIdempotent() = runBlocking {
        fill()
        val b = BackupCodec.fromJson(BackupCodec.toJson(repo.export(AppSettings(), 1)))
        repo.restore(b); repo.restore(b)
        assertEquals(1, db.backupDao().userRoutines().size)
        assertEquals(2, db.backupDao().workouts().size)
    }

    @Test fun unknownExerciseAbortsWithoutTouchingData() = runBlocking {
        fill()
        val b = repo.export(AppSettings(), 1)
        val bad = b.copy(routines = b.routines.map { r -> r.copy(items = r.items.map { it.copy(exerciseId = "no_existe") }) })
        try { repo.restore(bad); fail("debía fallar") } catch (e: BackupException) {
            assertEquals(BackupException.Kind.UNKNOWN_EXERCISE, e.kind)
        }
        assertEquals(1, db.backupDao().userRoutines().size)
        assertEquals(2, db.backupDao().workouts().size)
        assertNotNull(db.backupDao().profile())
    }

    @Test fun backupWithoutProfileKeepsCurrentProfile() = runBlocking {
        fill()
        val b = repo.export(AppSettings(), 1).copy(profile = null)
        repo.restore(b)
        assertEquals("Ana", db.backupDao().profile()?.name)
    }

    @Test fun invalidRpeIsRejected() = runBlocking {
        fill()
        val b = repo.export(AppSettings(), 1)
        val bad = b.copy(workouts = b.workouts.map { w -> w.copy(sets = w.sets.map { it.copy(rpe = 11) }) })
        try { repo.validate(bad); fail("debía fallar") } catch (e: BackupException) {
            assertEquals(BackupException.Kind.INVALID, e.kind)
        }
    }
}
