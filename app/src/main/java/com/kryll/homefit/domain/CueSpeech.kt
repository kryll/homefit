package com.kryll.homefit.domain

enum class Beep { NONE, TICK, GO, DONE }

/** Qué suena y qué se dice para cada aviso del motor. Texto en español. */
data class CueSound(val speech: String?, val beep: Beep)

fun Cue.toSound(): CueSound = when (this) {
    is Cue.Countdown -> CueSound(null, Beep.TICK)
    is Cue.GetReady -> {
        val scheme = reps?.let { "$sets series de $it repeticiones" } ?: seconds?.let { "$sets series de $it segundos" }
        CueSound("Prepárate: $exerciseName, " + (scheme ?: "serie $set de $sets"), Beep.NONE)
    }
    is Cue.Go -> {
        val what = reps?.let { "$it repeticiones" } ?: seconds?.let { "$it segundos" } ?: ""
        val last = if (set == sets && sets > 1) "Última serie. " else ""
        CueSound(listOf(last + "¡Ya!", what).filter { it.isNotEmpty() }.joinToString(" "), Beep.GO)
    }
    is Cue.SetDone -> CueSound("Serie hecha", Beep.DONE)
    is Cue.Rest -> {
        val scheme = if (nextSets != null) {
            nextReps?.let { ", $nextSets series de $it repeticiones" } ?: nextSeconds?.let { ", $nextSets series de $it segundos" } ?: ""
        } else ""
        CueSound(
            "Descanso, $seconds segundos" + (nextName?.let { ". Siguiente: $it$scheme" } ?: ""),
            Beep.NONE,
        )
    }
    Cue.Halfway -> CueSound("Mitad", Beep.NONE)
    Cue.TenSecondsLeft -> CueSound("Quedan 10 segundos", Beep.NONE)
    Cue.SwitchSides -> CueSound("Cambio de lado", Beep.NONE)
    Cue.Finished -> CueSound("Entreno terminado. Buen trabajo.", Beep.DONE)
}
