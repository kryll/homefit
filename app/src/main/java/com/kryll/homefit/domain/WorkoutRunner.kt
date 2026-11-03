package com.kryll.homefit.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class WorkoutSnapshot(
    val state: WorkoutState,
    val item: WorkItem,
    val completedSets: Int,
    val totalSets: Int,
    val closed: Boolean = false,
)

/**
 * Dueño del motor y del reloj, sin Android. Cuenta segundos con un reloj monotónico ([now], en ms)
 * y no con recomposiciones: [sync] aplica los segundos enteros que han pasado desde la última vez,
 * así que si el proceso se duerme o un aviso llega tarde no se pierden ticks.
 * Los avisos se entregan una sola vez a [cueListener] (los atrasados de una recuperación se descartan).
 */
class WorkoutRunner(
    val items: List<WorkItem>,
    getReadySeconds: Int = 5,
    private val now: () -> Long,
) {
    private val engine = WorkoutEngine(items, getReadySeconds)
    private var last = now()
    private var closed = false
    private val flow = MutableStateFlow(snap())

    @Volatile var cueListener: ((Cue) -> Unit)? = null
    val snapshot: StateFlow<WorkoutSnapshot> get() = flow
    val results: List<SetResult> @Synchronized get() = engine.setResults
    val isClosed: Boolean @Synchronized get() = closed
    val isFinished: Boolean @Synchronized get() = engine.state().phase == Phase.FINISHED

    private fun snap() = WorkoutSnapshot(engine.state(), engine.current, engine.completedSets, engine.totalSets, closed)

    private fun publish() {
        val cues = engine.drainCues()
        flow.value = snap()
        val l = cueListener
        if (l != null) cues.forEach(l)
    }

    @Synchronized
    private fun advance() {
        val t = now()
        val secs = ((t - last) / 1000).coerceAtLeast(0)
        if (secs == 0L) return
        last += secs * 1000
        if (engine.state().paused) return
        val n = minOf(secs, MAX_CATCH_UP).toInt()
        repeat(n) { i ->
            engine.tick()
            if (i < n - 1) engine.drainCues() // aviso atrasado: no se habla
        }
    }

    /** Llamar a menudo (el servicio lo hace cada 250 ms). */
    @Synchronized fun sync() { if (closed) return; advance(); publish() }

    @Synchronized fun done() { if (closed) return; advance(); engine.done(); publish() }
    @Synchronized fun skip() { if (closed) return; advance(); engine.skip(); publish() }
    @Synchronized fun rate(rpe: Int?) { if (closed) return; advance(); engine.rate(rpe); publish() }
    @Synchronized fun record(reps: Int?, weightKg: Float?) { if (closed) return; advance(); engine.record(reps, weightKg); publish() }
    @Synchronized fun pause() { if (closed) return; advance(); engine.pause(); publish() }
    @Synchronized fun resume() { if (closed) return; advance(); engine.resume(); publish() }
    @Synchronized fun togglePause() { if (engine.state().paused) resume() else pause() }

    /** Cierra la sesión. Devuelve resultados y si terminó, solo la primera vez; después null (evita guardar dos veces). */
    @Synchronized
    fun exit(): Pair<List<SetResult>, Boolean>? {
        if (closed) return null
        advance()
        closed = true
        cueListener = null
        flow.value = snap()
        return engine.setResults to (engine.state().phase == Phase.FINISHED)
    }

    companion object { const val MAX_CATCH_UP = 3600L }
}
