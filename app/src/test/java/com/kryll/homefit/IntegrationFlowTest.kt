package com.kryll.homefit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kryll.homefit.data.HomeFitDatabase
import com.kryll.homefit.data.WorkoutRepository
import com.kryll.homefit.domain.Prescribed
import com.kryll.homefit.domain.Progression
import com.kryll.homefit.domain.ProgressionData
import com.kryll.homefit.domain.SetResult
import com.kryll.homefit.domain.Suggestion
import com.kryll.homefit.domain.WorkItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Ramas p2 (entreno guarda reps reales) + p4 (progresión) integradas: entreno -> Room -> sugerencia. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class IntegrationFlowTest {
    private lateinit var db: HomeFitDatabase
    private lateinit var repo: WorkoutRepository
    private val plan = listOf(WorkItem("sentadilla", "Sentadilla", 3, 10, null, 30))
    private val prescribed = listOf(Prescribed("sentadilla", 3, 10, null))

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HomeFitDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = WorkoutRepository(db)
    }
    @After fun tearDown() = db.close()

    private fun suggestion(): Suggestion = runBlocking {
        val sessions = db.sessionDao().observeWorkouts().first()
        val sets = db.sessionDao().observeAllSets().first()
        Progression.forRoutine(1, prescribed, ProgressionData.sessionSets(sessions, sets)) { null }.getValue("sentadilla")
    }

    @Test fun realRepsSavedByWorkoutRaiseTheSuggestion() = runBlocking {
        val results = (1..3).map { SetResult(0, it, 6, false, 30, reps = 10, weightKg = 20f) }
        repo.save(1L, plan, 1_000L, 200_000L, true, results)
        assertEquals(Suggestion.AddReps(10, 12), suggestion())
    }

    @Test fun fewerRealRepsHold() = runBlocking {
        val results = listOf(
            SetResult(0, 1, 6, false, 30, reps = 10), SetResult(0, 2, 6, false, 30, reps = 7), SetResult(0, 3, 6, false, 30, reps = 10),
        )
        repo.save(1L, plan, 1_000L, 200_000L, true, results)
        assertEquals(Suggestion.Hold(Suggestion.Reason.NO_ACHIEVED), suggestion())
    }

    @Test fun setsWithoutRecordedRepsDoNotRaise() = runBlocking {
        repo.save(1L, plan, 1_000L, 200_000L, true, (1..3).map { SetResult(0, it, 6, false, 30) })
        assertEquals(Suggestion.Hold(Suggestion.Reason.NO_ACHIEVED), suggestion())
    }
}
