package com.kryll.homefit.data

import androidx.room.withTransaction
import com.kryll.homefit.domain.SetResult
import com.kryll.homefit.domain.WorkItem

/** Guarda el entreno hecho en las tablas workout_session y set_log. Las series saltadas no se registran. */
class WorkoutRepository(private val db: HomeFitDatabase) {

    /** Devuelve el id de la sesión, o null si no hubo ninguna serie hecha. */
    suspend fun save(
        routineId: Long?,
        plan: List<WorkItem>,
        startMillis: Long,
        endMillis: Long,
        finished: Boolean,
        results: List<SetResult>,
    ): Long? {
        val done = results.filter { !it.skipped && it.itemIndex in plan.indices }
        if (done.isEmpty()) return null
        val seconds = ((endMillis - startMillis) / 1000).coerceAtLeast(0).toInt()
        return db.withTransaction {
            val dao = db.sessionDao()
            val id = dao.insertWorkout(
                WorkoutSession(
                    routineId = routineId, startMillis = startMillis, endMillis = endMillis,
                    durationSeconds = seconds, notes = if (finished) "" else NOTE_INCOMPLETE,
                ),
            )
            dao.insertSets(
                done.map { r ->
                    val item = plan[r.itemIndex]
                    SetLog(
                        sessionId = id, exerciseId = item.exerciseId, setIndex = r.set,
                        reps = item.reps, seconds = if (item.timed) r.workSeconds else null, rpe = r.rpe,
                    )
                },
            )
            id
        }
    }

    companion object { const val NOTE_INCOMPLETE = "incompleto" }
}
