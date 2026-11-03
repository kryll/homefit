package com.kryll.homefit.domain

/** Un ejercicio de la sesión: por repeticiones (reps) o por tiempo (seconds), nunca los dos. */
data class WorkItem(
    val exerciseId: String,
    val name: String,
    val sets: Int,
    val reps: Int?,
    val seconds: Int?,
    val restSeconds: Int,
    /** Ejercicios con lado: la voz avisa "Cambio de lado" a la mitad de series por tiempo. */
    val alternateSides: Boolean = false,
) {
    init {
        require(sets >= 1) { "sets" }
        require((reps != null) != (seconds != null)) { "reps o seconds" }
        require(restSeconds >= 0) { "restSeconds" }
    }
    val timed: Boolean get() = seconds != null
}

enum class Phase { GET_READY, WORK, RATE, REST, FINISHED }

/** Avisos que la capa de Android convierte en pitidos o voz. */
sealed class Cue {
    data class Countdown(val secondsLeft: Int) : Cue()
    data class GetReady(val exerciseName: String, val set: Int, val sets: Int, val reps: Int? = null, val seconds: Int? = null) : Cue()
    data class Go(val exerciseName: String, val set: Int, val sets: Int, val reps: Int?, val seconds: Int?) : Cue()
    data class SetDone(val exerciseName: String) : Cue()
    /** [nextSets]/[nextReps]/[nextSeconds]: esquema del siguiente ejercicio cuando cambia; null dentro del mismo. */
    data class Rest(val seconds: Int, val nextName: String?, val nextSets: Int? = null, val nextReps: Int? = null, val nextSeconds: Int? = null) : Cue()
    object Halfway : Cue()
    object TenSecondsLeft : Cue()
    object SwitchSides : Cue()
    object Finished : Cue()
}

/** [reps] y [weightKg] son lo que el usuario confirmó al acabar la serie; null = no registrado (no se sabe). */
data class SetResult(
    val itemIndex: Int, val set: Int, val rpe: Int?, val skipped: Boolean, val workSeconds: Int,
    val reps: Int? = null, val weightKg: Float? = null,
)

data class WorkoutState(
    val phase: Phase,
    val itemIndex: Int,
    /** Serie actual, desde 1. */
    val set: Int,
    /** Segundos que faltan en GET_READY, WORK por tiempo o REST. En reps cuenta hacia arriba el tiempo trabajado. */
    val remaining: Int,
    val paused: Boolean,
)

/**
 * Máquina de estados del entreno, sin Android. La capa de servicio llama a [tick] una vez por segundo
 * de reloj real y a [done], [rate], [skip], [pause], [resume] según los botones.
 * Fases por serie: GET_READY (solo antes de la primera serie de cada ejercicio), WORK, RATE (RPE 1 a 10), REST.
 */
class WorkoutEngine(private val items: List<WorkItem>, private val getReadySeconds: Int = 5) {
    init { require(items.isNotEmpty()) { "rutina vacía" } }

    private var phase = Phase.GET_READY
    private var index = 0
    private var set = 1
    private var remaining = getReadySeconds
    private var paused = false
    private var workElapsed = 0
    private var extraPlannedSets = 0
    private val cues = mutableListOf<Cue>()
    private val results = mutableListOf<SetResult>()

    val setResults: List<SetResult> get() = results.toList()
    val current: WorkItem get() = items[minOf(index, items.lastIndex)]
    val totalSets: Int get() = items.sumOf { it.sets } + extraPlannedSets
    val completedSets: Int get() = results.size

    init { enterGetReady() }

    fun state() = WorkoutState(phase, index, set, remaining, paused)

    /** Devuelve y vacía los avisos pendientes. */
    fun drainCues(): List<Cue> = cues.toList().also { cues.clear() }

    fun pause() { if (phase != Phase.FINISHED) paused = true }
    fun resume() { paused = false }

    /** Avanza un segundo. En pausa no hace nada. */
    fun tick() {
        if (paused || phase == Phase.FINISHED) return
        when (phase) {
            Phase.GET_READY -> {
                countdown()
                if (remaining == 0) enterWork()
            }
            Phase.WORK -> {
                workElapsed++
                val s = current.seconds
                if (s != null) {
                    remaining = s - workElapsed
                    if (remaining in 1..3) cues += Cue.Countdown(remaining)
                    if (remaining == s / 2 && s >= 20) cues += if (current.alternateSides) Cue.SwitchSides else Cue.Halfway
                    if (remaining == 10 && s >= 30) cues += Cue.TenSecondsLeft
                    if (remaining <= 0) finishWork(skipped = false)
                } else {
                    remaining = workElapsed
                }
            }
            Phase.REST -> {
                if (remaining > 0) countdown()
                if (remaining <= 0) nextAfterRest()
            }
            else -> Unit
        }
    }

    /** Cambia solo el descanso actual, sin alterar la rutina ni adelantar de fase. */
    fun adjustRest(delta: Int) {
        if (phase == Phase.REST) remaining = (remaining + delta).coerceIn(0, 600)
    }

    /** Reinicia el ejercicio, conservando resultados reales. La serie abierta no cuenta. */
    fun repeatExercise() {
        if (phase == Phase.FINISHED || phase == Phase.RATE) return
        extraPlannedSets += if (phase == Phase.REST) set else set - 1
        set = 1
        workElapsed = 0
        cues.clear()
        enterGetReady()
    }

    /** El usuario termina la serie por repeticiones (o antes de tiempo). Solo en WORK. */
    fun done() { if (phase == Phase.WORK && !paused) finishWork(skipped = false) }

    /** Salta la fase actual: espera, trabajo (serie marcada como saltada), RPE o descanso. */
    fun skip() {
        if (paused) return
        when (phase) {
            Phase.GET_READY -> enterWork()
            Phase.WORK -> finishWork(skipped = true)
            Phase.RATE -> rate(null)
            Phase.REST -> nextAfterRest()
            else -> Unit
        }
    }

    /** Guarda lo realmente hecho en la última serie (repeticiones y carga opcional). Solo en RATE. */
    fun record(reps: Int?, weightKg: Float?) {
        if (phase != Phase.RATE) return
        require(reps == null || reps in 0..MAX_REPS) { "repeticiones" }
        require(weightKg == null || (weightKg > 0f && weightKg <= MAX_WEIGHT_KG)) { "peso" }
        val last = results.lastIndex
        results[last] = results[last].copy(reps = reps, weightKg = weightKg)
    }

    /** Guarda el RPE (1 a 10) o null si no lo da. Solo en RATE. */
    fun rate(rpe: Int?) {
        if (phase != Phase.RATE) return
        require(rpe == null || rpe in 1..10) { "RPE de 1 a 10" }
        val last = results.lastIndex
        results[last] = results[last].copy(rpe = rpe)
        afterRate()
    }

    companion object {
        const val MAX_REPS = 200
        const val MAX_WEIGHT_KG = 500f

        fun stepReps(reps: Int, delta: Int): Int = (reps + delta).coerceIn(0, MAX_REPS)

        /** Carga en pasos de 0,5 kg; null = sin peso (peso corporal). Bajar de 0,5 vuelve a "sin peso". */
        fun stepWeight(w: Float?, delta: Int): Float? {
            val next = ((w ?: 0f) * 2).let { Math.round(it) + delta } / 2f
            return if (next <= 0f) null else next.coerceAtMost(MAX_WEIGHT_KG)
        }
    }

    private fun countdown() {
        remaining--
        if (remaining == 10) cues += Cue.TenSecondsLeft
        if (remaining in 1..3) cues += Cue.Countdown(remaining)
    }

    private fun enterGetReady() {
        phase = Phase.GET_READY
        remaining = getReadySeconds
        if (getReadySeconds == 0) { enterWork(); return }
        cues += Cue.GetReady(current.name, set, current.sets, current.reps, current.seconds)
    }

    private fun enterWork() {
        phase = Phase.WORK
        workElapsed = 0
        remaining = current.seconds ?: 0
        cues += Cue.Go(current.name, set, current.sets, current.reps, current.seconds)
    }

    private fun finishWork(skipped: Boolean) {
        results += SetResult(index, results.count { it.itemIndex == index } + 1, null, skipped, workElapsed)
        cues += Cue.SetDone(current.name)
        phase = Phase.RATE
        remaining = 0
        if (skipped) rate(null)
    }

    private fun afterRate() {
        val item = items[index]
        val lastSet = set == item.sets
        val lastItem = index == items.lastIndex
        if (lastSet && lastItem) { finish(); return }
        if (item.restSeconds == 0) { nextAfterRest(); return }
        phase = Phase.REST
        remaining = item.restSeconds
        val next = if (lastSet) items[index + 1] else null
        cues += if (next != null) Cue.Rest(item.restSeconds, next.name, next.sets, next.reps, next.seconds)
            else Cue.Rest(item.restSeconds, item.name)
    }

    private fun nextAfterRest() {
        val item = items[index]
        if (set < item.sets) {
            set++
            enterWork()
        } else {
            index++
            set = 1
            enterGetReady()
        }
    }

    private fun finish() {
        phase = Phase.FINISHED
        remaining = 0
        cues += Cue.Finished
    }
}
