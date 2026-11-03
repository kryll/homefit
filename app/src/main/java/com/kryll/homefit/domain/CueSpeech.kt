package com.kryll.homefit.domain

enum class Beep { NONE, TICK, GO, DONE }

/** Qué suena y qué se dice para cada aviso del motor. Texto en español. */
data class CueSound(val speech: String?, val beep: Beep)

fun Cue.toSound(): CueSound = when (this) {
    is Cue.Countdown -> CueSound(null, Beep.TICK)
    is Cue.GetReady -> CueSound("Prepárate: $exerciseName, serie $set de $sets", Beep.NONE)
    is Cue.Go -> {
        val what = reps?.let { "$it repeticiones" } ?: seconds?.let { "$it segundos" } ?: ""
        CueSound(listOf("¡Ya!", what).filter { it.isNotEmpty() }.joinToString(" "), Beep.GO)
    }
    is Cue.SetDone -> CueSound("Serie hecha", Beep.DONE)
    is Cue.Rest -> CueSound(
        "Descanso, $seconds segundos" + (nextName?.let { ". Siguiente: $it" } ?: ""),
        Beep.NONE,
    )
    Cue.Halfway -> CueSound("Mitad", Beep.NONE)
    Cue.Finished -> CueSound("Entreno terminado. Buen trabajo.", Beep.DONE)
}
