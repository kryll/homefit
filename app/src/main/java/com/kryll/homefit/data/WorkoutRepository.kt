package com.kryll.homefit.data

import androidx.room.withTransaction
import com.kryll.homefit.domain.SetResult
import com.kryll.homefit.domain.WorkItem
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

    /** Cierra la sesión. Devuelve su id, o null si no hubo ninguna serie hecha. */
    suspend fun save(
        routineId: Long?,
        plan: List<WorkItem>,
        startMillis: Long,
        endMillis: Long,
        finished: Boolean,
        results: List<SetResult>,
    ): Long? = lock.withLock {
        val id = write(routineId, plan, startMillis, endMillis, finished, results)
        openSessionId = null
        id
    }

    /** Sesión interrumpida de hoy lista para reanudar con el plan actual; null si no hay o ya no encaja. */
    data class ResumableSession(val sessionId: Long, val startMillis: Long, val results: List<com.kryll.homefit.domain.SetResult>)

    /**
     * Busca el entreno incompleto de hoy para esa rutina y, si sus series encajan en el plan,
     * adopta su fila para que los próximos [checkpoint] la actualicen en vez de duplicarla.
     */
    suspend fun interruptedToday(routineId: Long, plan: List<WorkItem>, nowMillis: Long, zone: ZoneId): ResumableSession? = lock.withLock {
        val day = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val s = db.sessionDao().latestIncomplete(routineId, from, to) ?: return@withLock null
        val logs = db.sessionDao().setsOf(s.id)
        val results = com.kryll.homefit.domain.WorkoutResume.fromLogs(
            plan,
            logs.map { com.kryll.homefit.domain.InterruptedSet(it.exerciseId, it.setIndex, it.reps, it.seconds, it.rpe, it.weightKg) },
        ) ?: return@withLock null
        openSessionId = s.id
        ResumableSession(s.id, s.startMillis, results)
    }

    /** Suelta la fila adoptada por [interruptedToday] si el usuario prefiere empezar de nuevo. */
    suspend fun releaseInterrupted() = lock.withLock { openSessionId = null }

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
