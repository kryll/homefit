package com.kryll.homefit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kryll.homefit.data.HomeFitDatabase
import com.kryll.homefit.data.WorkoutRepository
import com.kryll.homefit.domain.SetResult
import com.kryll.homefit.domain.WorkItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkoutRepositoryTest {
    private lateinit var db: HomeFitDatabase
    private lateinit var repo: WorkoutRepository
    private val plan = listOf(
        WorkItem("sentadilla", "Sentadilla", 2, 10, null, 30),
        WorkItem("plancha", "Plancha", 1, null, 30, 0),
    )

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HomeFitDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = WorkoutRepository(db)
    }

    @After fun tearDown() = db.close()

    @Test fun savesSessionAndDoneSetsWithRpe() = runBlocking {
        val results = listOf(
            SetResult(0, 1, 7, false, 25),
            SetResult(0, 2, null, true, 3),
            SetResult(1, 1, 9, false, 30),
        )
        val id = repo.save(5L, plan, 1_000_000L, 1_300_000L, true, results)!!
        val s = db.sessionDao().observeWorkouts().first().single()
        assertEquals(id, s.id); assertEquals(5L, s.routineId); assertEquals(300, s.durationSeconds); assertEquals("", s.notes)
        val sets = db.sessionDao().setsOf(id)
        assertEquals(listOf("sentadilla", "plancha"), sets.map { it.exerciseId })
        assertEquals(listOf(1, 1), sets.map { it.setIndex })
        assertEquals(10, sets[0].reps); assertNull(sets[0].seconds); assertEquals(7, sets[0].rpe)
        assertNull(sets[1].reps); assertEquals(30, sets[1].seconds); assertEquals(9, sets[1].rpe)
    }

    @Test fun incompleteWorkoutIsMarked() = runBlocking {
        val id = repo.save(null, plan, 0L, 60_000L, false, listOf(SetResult(0, 1, null, false, 20)))!!
        assertEquals(WorkoutRepository.NOTE_INCOMPLETE, db.sessionDao().observeWorkouts().first().single().notes)
        assertEquals(1, db.sessionDao().setsOf(id).size)
    }

    @Test fun nothingDoneSavesNothing() = runBlocking {
        assertNull(repo.save(1L, plan, 0L, 1000L, false, emptyList()))
        assertNull(repo.save(1L, plan, 0L, 1000L, false, listOf(SetResult(0, 1, null, true, 0))))
        assertEquals(0, db.sessionDao().observeWorkouts().first().size)
    }

    @Test fun checkpointKeepsOneIncompleteSessionAndSaveClosesIt() = runBlocking {
        val r1 = listOf(SetResult(0, 1, null, false, 20))
        repo.checkpoint(1L, plan, 1000L, 21_000L, r1) { false }
        val r2 = listOf(SetResult(0, 1, 6, false, 20), SetResult(0, 2, null, false, 22))
        repo.checkpoint(1L, plan, 1000L, 60_000L, r2) { false }
        val mid = db.sessionDao().observeWorkouts().first().single()
        assertEquals(WorkoutRepository.NOTE_INCOMPLETE, mid.notes)
        assertEquals(59, mid.durationSeconds)
        assertEquals(2, db.sessionDao().setsOf(mid.id).size)
        val id = repo.save(1L, plan, 1000L, 100_000L, true, r2 + SetResult(1, 1, 8, false, 30))!!
        assertEquals(mid.id, id)
        val s = db.sessionDao().observeWorkouts().first().single()
        assertEquals("", s.notes); assertEquals(99, s.durationSeconds)
        assertEquals(3, db.sessionDao().setsOf(id).size)
    }

    @Test fun checkpointAfterCloseOrWithoutDoneSetsWritesNothing() = runBlocking {
        repo.checkpoint(1L, plan, 0L, 5000L, listOf(SetResult(0, 1, null, true, 0))) { false }
        repo.checkpoint(1L, plan, 0L, 5000L, listOf(SetResult(0, 1, null, false, 9))) { true }
        assertEquals(0, db.sessionDao().observeWorkouts().first().size)
    }

    @Test fun nextSessionAfterSaveIsANewRow() = runBlocking {
        val r = listOf(SetResult(0, 1, null, false, 9))
        repo.checkpoint(1L, plan, 0L, 5000L, r) { false }
        repo.save(1L, plan, 0L, 6000L, false, r)
        repo.checkpoint(1L, plan, 10_000L, 15_000L, r) { false }
        assertEquals(2, db.sessionDao().observeWorkouts().first().size)
    }
}
