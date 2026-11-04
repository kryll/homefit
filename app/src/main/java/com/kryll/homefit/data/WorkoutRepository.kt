package com.kryll.homefit.data

import androidx.room.withTransaction
import com.kryll.homefit.domain.SetResult
import java.time.Instant
import java.time.ZoneId
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

    /** Sesión interrumpida reciente (48 h) lista para reanudar contra su plan guardado (o el actual en filas legadas); null si no hay o no encaja. */
    data class ResumableSession(val sessionId: Long, val startMillis: Long, val plan: List<com.kryll.homefit.domain.WorkItem>, val results: List<com.kryll.homefit.domain.SetResult>)

    /**
     * Busca el entreno incompleto de hoy para esa rutina y, si sus series encajan en el plan,
     * adopta su fila para que los próximos [checkpoint] la actualicen en vez de duplicarla.
     */
    suspend fun interruptedToday(routineId: Long, plan: List<WorkItem>, nowMillis: Long): ResumableSession? = lock.withLock {
        val from = nowMillis - RESUME_WINDOW_MILLIS
        val s = db.sessionDao().latestIncomplete(routineId, from) ?: return@withLock null
        val logs = db.sessionDao().setsOf(s.id)
        // Reanudar contra el plan guardado con la sesión; las filas legadas (sin snapshot) usan el plan actual.
        val usePlan = s.planJson?.let { com.kryll.homefit.domain.WorkoutPlanSnapshot.fromJson(it) } ?: plan
        val mapped = logs.map { com.kryll.homefit.domain.InterruptedSet(it.exerciseId, it.setIndex, it.reps, it.seconds, it.rpe, it.weightKg, it.itemIndex) }
        val results = if (mapped.all { it.itemIndex >= 0 }) {
            com.kryll.homefit.domain.WorkoutResume.fromLogsPositional(usePlan, mapped)
        } else {
            com.kryll.homefit.domain.WorkoutResume.fromLogs(usePlan, mapped)
        } ?: return@withLock null
        openSessionId = s.id
        ResumableSession(s.id, s.startMillis, usePlan, results)
    }

    /** Descarta la fila adoptada por [interruptedToday] si el usuario prefiere empezar de nuevo: no se vuelve a ofrecer. */
    suspend fun releaseInterrupted() = lock.withLock {
        openSessionId?.let { db.sessionDao().discardSession(it) }
        openSessionId = null
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
            val id = openSessionId
            val session = WorkoutSession(
                id = id ?: 0, routineId = routineId, startMillis = startMillis, endMillis = endMillis,
                durationSeconds = seconds, notes = if (finished) "" else NOTE_INCOMPLETE,
                // El snapshot se escribe al abrir la sesión y no se sobrescribe en checkpoints posteriores.
                planJson = id?.let { dao.workoutById(it)?.planJson } ?: com.kryll.homefit.domain.WorkoutPlanSnapshot.toJson(plan),
            )
            val sid = if (id == null) dao.insertWorkout(session) else { dao.updateWorkout(session); dao.deleteSets(id); id }
            dao.insertSets(
                done.map { r ->
                    val item = plan[r.itemIndex]
                    SetLog(
                        sessionId = sid, exerciseId = item.exerciseId, setIndex = r.set,
                        reps = r.reps, weightKg = r.weightKg, seconds = if (item.timed) r.workSeconds else null, rpe = r.rpe,
                        itemIndex = r.itemIndex,
                    )
                },
            )
            openSessionId = sid
            sid
        }
    }

    companion object {
        const val NOTE_INCOMPLETE = "incompleto"

        /** Sesión descartada por el usuario al elegir "empezar de nuevo": se conserva como parcial, no se re-ofrece. */
        const val NOTE_DISCARDED = "descartado"

        /** Una sesión interrumpida se puede reanudar durante 48 h (cubre entrenos que cruzan medianoche). */
        private const val RESUME_WINDOW_MILLIS = 48L * 3_600_000L
    }
}
