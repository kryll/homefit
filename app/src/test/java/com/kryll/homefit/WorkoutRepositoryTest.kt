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
            SetResult(0, 1, 7, false, 25, reps = 8, weightKg = 12.5f),
            SetResult(0, 2, null, true, 3),
            SetResult(1, 1, 9, false, 30),
        )
        val id = repo.save(5L, plan, 1_000_000L, 1_300_000L, true, results)!!
        val s = db.sessionDao().observeWorkouts().first().single()
        assertEquals(id, s.id); assertEquals(5L, s.routineId); assertEquals(300, s.durationSeconds); assertEquals("", s.notes)
        val sets = db.sessionDao().setsOf(id)
        assertEquals(listOf("sentadilla", "plancha"), sets.map { it.exerciseId })
        assertEquals(listOf(1, 1), sets.map { it.setIndex })
        assertEquals(8, sets[0].reps); assertEquals(12.5f, sets[0].weightKg); assertNull(sets[0].seconds); assertEquals(7, sets[0].rpe)
        assertNull(sets[1].reps); assertNull(sets[1].weightKg); assertEquals(30, sets[1].seconds); assertEquals(9, sets[1].rpe)
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

    @Test fun finishingWorkoutMarksPlannedSessionCompleted() = runBlocking {
        val zone = java.time.ZoneId.systemDefault()
        val day = java.time.LocalDate.now(zone)
        fun ms(d: java.time.LocalDate, h: Int) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
        val dao = db.sessionDao()
        dao.insertScheduled(com.kryll.homefit.data.ScheduledSession(routineId = 1, startMillis = ms(day, 18), status = "SCHEDULED"))
        dao.insertScheduled(com.kryll.homefit.data.ScheduledSession(routineId = 2, startMillis = ms(day, 19), status = "SCHEDULED"))
        dao.insertScheduled(com.kryll.homefit.data.ScheduledSession(routineId = 1, startMillis = ms(day.plusDays(1), 18), status = "SCHEDULED"))
        repo.save(1L, plan, ms(day, 18), ms(day, 18) + 600_000L, true, listOf(SetResult(0, 1, 8, false, 25)))
        val all = dao.observeAllScheduled().first().sortedBy { it.id }
        assertEquals(listOf("COMPLETED", "SCHEDULED", "SCHEDULED"), all.map { it.status })
    }

    @Test fun unfinishedWorkoutKeepsPlannedSessionScheduled() = runBlocking {
        val zone = java.time.ZoneId.systemDefault()
        val day = java.time.LocalDate.now(zone)
        fun ms(d: java.time.LocalDate, h: Int) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
        val dao = db.sessionDao()
        dao.insertScheduled(com.kryll.homefit.data.ScheduledSession(routineId = 1, startMillis = ms(day, 18), status = "SCHEDULED"))
        repo.save(1L, plan, ms(day, 18), ms(day, 18) + 60_000L, false, listOf(SetResult(0, 1, 8, false, 25)))
        assertEquals("SCHEDULED", dao.observeAllScheduled().first().single().status)
    }

    @Test fun nextSessionAfterSaveIsANewRow() = runBlocking {
        val r = listOf(SetResult(0, 1, null, false, 9))
        repo.checkpoint(1L, plan, 0L, 5000L, r) { false }
        repo.save(1L, plan, 0L, 6000L, false, r)
        repo.checkpoint(1L, plan, 10_000L, 15_000L, r) { false }
        assertEquals(2, db.sessionDao().observeWorkouts().first().size)
    }
    @Test fun interruptedTodayAdoptsSessionAndCheckpointUpdatesSameRow() = runBlocking {
        val today = java.time.LocalDate.of(2026, 11, 4).atTime(10, 0).atZone(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()
        val results = listOf(SetResult(0, 1, 7, false, 25, reps = 8))
        repo.checkpoint(5L, plan, today, today + 60_000L, results) { false }
        val before = db.sessionDao().observeWorkouts().first().single()
        assertEquals("incompleto", before.notes)

        val r = repo.interruptedToday(5L, plan, today + 120_000L)!!
        assertEquals(before.id, r.sessionId)
        assertEquals(before.startMillis, r.startMillis)
        assertEquals(1, r.results.size)
        assertEquals(8, r.results[0].reps)

        // Al reanudar, los checkpoints siguen en la misma fila: no hay duplicados.
        repo.checkpoint(5L, plan, r.startMillis, today + 300_000L, r.results + SetResult(0, 2, 8, false, 30, reps = 10)) { false }
        val all = db.sessionDao().observeWorkouts().first()
        assertEquals(1, all.size)
        assertEquals(2, db.sessionDao().setsOf(before.id).size)
    }

    @Test fun interruptedTodayIsNullWithoutMatchOrAfterRelease() = runBlocking {
        val today = java.time.LocalDate.of(2026, 11, 4).atTime(10, 0).atZone(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertNull(repo.interruptedToday(5L, plan, today))
        repo.save(5L, plan, today, today + 60_000L, true, listOf(SetResult(0, 1, 7, false, 25, reps = 8)))
        assertNull("una sesión terminada no se reanuda", repo.interruptedToday(5L, plan, today + 120_000L))

        repo.checkpoint(5L, plan, today, today + 60_000L, listOf(SetResult(0, 1, 7, false, 25, reps = 8))) { false }
        repo.interruptedToday(5L, plan, today + 120_000L)!!
        repo.releaseInterrupted()
        // Tras descartarla no se vuelve a ofrecer, y un checkpoint abre fila nueva (la interrumpida queda como historial).
        assertNull("una sesión descartada no se reanuda", repo.interruptedToday(5L, plan, today + 180_000L))
        repo.checkpoint(5L, plan, today + 600_000L, today + 660_000L, listOf(SetResult(0, 1, 6, false, 20, reps = 8))) { false }
        assertEquals(3, db.sessionDao().observeWorkouts().first().size)
    }

    @Test fun interruptedFromPreviousDayWithin48hIsOffered() = runBlocking {
        val zone = java.time.ZoneId.of("UTC")
        val day1 = java.time.LocalDate.of(2026, 11, 4)
        val start = day1.atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
        val nowNextDay = day1.plusDays(1).atTime(1, 0).atZone(zone).toInstant().toEpochMilli()
        repo.checkpoint(5L, plan, start, start + 60_000L, listOf(SetResult(0, 1, 7, false, 25, reps = 8))) { false }
        // Cruza medianoche: la sesión de ayer 23:00 sigue ofreciéndose a la 01:00.
        val r = repo.interruptedToday(5L, plan, nowNextDay)
        assertEquals(1, r?.results?.size)
        // Fuera de la ventana de 48 h ya no se ofrece.
        repo.releaseInterrupted()
    }

    @Test fun interruptedOlderThan48hIsNotOffered() = runBlocking {
        val zone = java.time.ZoneId.of("UTC")
        val day1 = java.time.LocalDate.of(2026, 11, 1)
        val start = day1.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val now = day1.plusDays(3).atTime(11, 0).atZone(zone).toInstant().toEpochMilli()
        repo.checkpoint(5L, plan, start, start + 60_000L, listOf(SetResult(0, 1, 7, false, 25, reps = 8))) { false }
        assertNull(repo.interruptedToday(5L, plan, now))
    }

    @Test fun snapshotPlanSurvivesRoutineEdit() = runBlocking {
        val start = 1_000L
        repo.checkpoint(5L, plan, start, start + 60_000L, listOf(SetResult(0, 1, 7, false, 25, reps = 8))) { false }
        // La rutina se edita entretanto (otras series y descansos)
        val edited = listOf(
            WorkItem("sentadilla", "Sentadilla", 3, 10, null, 45),
            WorkItem("plancha", "Plancha", 2, null, 30, 15),
        )
        val r = repo.interruptedToday(5L, edited, start + 120_000L)!!
        // La reanudación es contra el plan guardado con la sesión, no contra la rutina editada
        assertEquals(2, r.plan.size)
        assertEquals(2, r.plan[0].sets)
        assertEquals(1, r.results.size)
        assertEquals(0, r.results[0].itemIndex)
    }

    @Test fun repeatedExerciseResumesPositionally() = runBlocking {
        val circuit = listOf(
            WorkItem("sentadilla", "Sentadilla", 1, 10, null, 30),
            WorkItem("plancha", "Plancha", 1, null, 30, 0),
            WorkItem("sentadilla", "Sentadilla", 1, 10, null, 30),
        )
        val start = 1_000L
        val results = listOf(SetResult(0, 1, 7, false, 25, reps = 8), SetResult(2, 1, 9, false, 25, reps = 10))
        repo.checkpoint(5L, circuit, start, start + 60_000L, results) { false }
        val r = repo.interruptedToday(5L, circuit, start + 120_000L)!!
        assertEquals(2, r.results.size)
        assertEquals(0, r.results[0].itemIndex); assertEquals(8, r.results[0].reps)
        assertEquals(2, r.results[1].itemIndex); assertEquals(10, r.results[1].reps)
    }

    @Test fun legacyRowWithoutSnapshotMapsByExerciseId() = runBlocking {
        val dao = db.sessionDao()
        val sid = dao.insertWorkout(com.kryll.homefit.data.WorkoutSession(routineId = 5L, startMillis = 1_000L, endMillis = 2_000L, durationSeconds = 1, notes = "incompleto"))
        dao.insertSets(listOf(com.kryll.homefit.data.SetLog(sessionId = sid, exerciseId = "sentadilla", setIndex = 1, reps = 8)))
        val r = repo.interruptedToday(5L, plan, 120_000L)!!
        assertEquals(1, r.results.size)
        assertEquals(0, r.results[0].itemIndex)
    }
}
