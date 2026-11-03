package com.kryll.homefit.data

import androidx.room.withTransaction
import com.kryll.homefit.domain.SetResult
import com.kryll.homefit.domain.WorkItem
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Guarda el entreno en las tablas workout_session y set_log. Las series saltadas no se registran.
 * Mientras se entrena, [checkpoint] mantiene una sola fila de sesión marcada "incompleto" con las series hechas;
 * [save] la cierra al salir. Así un entreno no se pierde si el proceso muere. Usar una instancia por proceso.
 */
class WorkoutRepository(private val db: HomeFitDatabase) {
    private val lock = Mutex()
    private var openSessionId: Long? = null

    /** Punto de control idempotente. No hace nada si la sesión ya está cerrada o aún no hay series hechas. */
    suspend fun checkpoint(
        routineId: Long?, plan: List<WorkItem>, startMillis: Long, nowMillis: Long,
        results: List<SetResult>, isClosed: () -> Boolean,
    ) {
        lock.withLock {
            if (isClosed()) return
            write(routineId, plan, startMillis, nowMillis, false, results)
        }
    }

    /**
     * Cierra la sesión. Devuelve su id, o null si no hubo ninguna serie hecha.
     * Al terminar (finished) marca COMPLETED las sesiones planificadas ese día con la misma rutina.
     */
    suspend fun save(
        routineId: Long?,
        plan: List<WorkItem>,
        startMillis: Long,
        endMillis: Long,
        finished: Boolean,
        results: List<SetResult>,
    ): Long? = lock.withLock {
        val id = write(routineId, plan, startMillis, endMillis, finished, results)
        if (finished && routineId != null && id != null) {
            val zone = ZoneId.systemDefault()
            val day = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
            val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            db.sessionDao().scheduledBetween(from, to)
                .filter { it.routineId == routineId && it.status == "SCHEDULED" }
                .forEach { db.sessionDao().setScheduledStatus(it.id, "COMPLETED") }
        }
        openSessionId = null
        id
    }

    private suspend fun write(
        routineId: Long?, plan: List<WorkItem>, startMillis: Long, endMillis: Long,
        finished: Boolean, results: List<SetResult>,
    ): Long? {
        val done = results.filter { !it.skipped && it.itemIndex in plan.indices }
        if (done.isEmpty()) return openSessionId
        val seconds = ((endMillis - startMillis) / 1000).coerceAtLeast(0).toInt()
        return db.withTransaction {
            val dao = db.sessionDao()
            val session = WorkoutSession(
                id = openSessionId ?: 0, routineId = routineId, startMillis = startMillis, endMillis = endMillis,
                durationSeconds = seconds, notes = if (finished) "" else NOTE_INCOMPLETE,
            )
            val id = openSessionId
            val sid = if (id == null) dao.insertWorkout(session) else { dao.updateWorkout(session); dao.deleteSets(id); id }
            dao.insertSets(
                done.map { r ->
                    val item = plan[r.itemIndex]
                    SetLog(
                        sessionId = sid, exerciseId = item.exerciseId, setIndex = r.set,
                        reps = r.reps, weightKg = r.weightKg, seconds = if (item.timed) r.workSeconds else null, rpe = r.rpe,
                    )
                },
            )
            openSessionId = sid
            sid
        }
    }

    companion object { const val NOTE_INCOMPLETE = "incompleto" }
}
