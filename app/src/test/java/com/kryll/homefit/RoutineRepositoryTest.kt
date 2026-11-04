package com.kryll.homefit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kryll.homefit.data.HomeFitDatabase
import com.kryll.homefit.data.Routine
import com.kryll.homefit.data.RoutineRepository
import com.kryll.homefit.domain.DraftItem
import com.kryll.homefit.domain.ExerciseCatalog
import com.kryll.homefit.domain.RoutineDraft
import java.io.File
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
class RoutineRepositoryTest {
    private lateinit var db: HomeFitDatabase
    private lateinit var repo: RoutineRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HomeFitDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = RoutineRepository(db)
        runBlocking {
            val ex = ExerciseCatalog.parse(File("src/main/assets/exercises.json").readText())
            db.exerciseDao().insertAll(ex.exercises)
        }
    }

    @After fun tearDown() = db.close()

    private val draft = RoutineDraft(
        "  Mi rutina  ", "ganar_fuerza", "intermedio",
        listOf(DraftItem("sentadilla", 4, 8, null, 60), DraftItem("plancha", 3, null, 40, 30)),
    )

    @Test fun createsRoutineWithOrderedItems() = runBlocking {
        val id = repo.save(null, draft)!!
        val r = db.routineDao().byId(id)!!
        assertEquals("Mi rutina", r.name); assertEquals(false, r.predefined); assertEquals("ganar_fuerza", r.goal)
        val items = db.routineDao().observeItems(id).first()
        assertEquals(listOf("sentadilla", "plancha"), items.map { it.exerciseId })
        assertEquals(listOf(0, 1), items.map { it.position })
        assertEquals(40, items[1].seconds); assertNull(items[1].reps)
    }

    @Test fun editReplacesItems() = runBlocking {
        val id = repo.save(null, draft)!!
        repo.save(id, draft.copy(name = "Otra", items = listOf(DraftItem("flexiones"))))
        assertEquals("Otra", db.routineDao().byId(id)!!.name)
        assertEquals(listOf("flexiones"), db.routineDao().observeItems(id).first().map { it.exerciseId })
    }

    @Test fun invalidDraftIsNotSaved() = runBlocking {
        assertNull(repo.save(null, RoutineDraft()))
        assertEquals(0, db.routineDao().observeAll().first().size)
    }

    @Test fun predefinedRoutinesAreNeverEditedOrDeleted() = runBlocking {
        val pid = db.routineDao().insertRoutine(Routine(name = "HIIT", goal = "perder_grasa", level = "intermedio", type = "cardio", predefined = true))
        assertNull(repo.save(pid, draft))
        repo.delete(pid)
        assertEquals("HIIT", db.routineDao().byId(pid)!!.name)
    }

    @Test fun deleteRemovesUserRoutineAndItems() = runBlocking {
        val id = repo.save(null, draft)!!
        repo.delete(id)
        assertNull(db.routineDao().byId(id))
        assertEquals(0, db.routineDao().observeItems(id).first().size)
    }
}
