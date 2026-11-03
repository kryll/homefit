package com.kryll.homefit.domain

import com.kryll.homefit.data.AppSettings

/** Lo que el reproductor de avisos necesita de los ajustes. */
data class CueSettings(val speech: Boolean, val beepPercent: Int, val speechRate: Float) {
    val beep: Boolean get() = beepPercent > 0

    companion object {
        fun from(s: AppSettings) = CueSettings(
            speech = s.voiceEnabled,
            beepPercent = Math.round(s.beepVolume.coerceIn(0f, 1f) * 100),
            speechRate = s.ttsRate.coerceIn(SettingsRules.RATE_MIN, SettingsRules.RATE_MAX),
        )
    }
}

object SettingsRules {
    const val RATE_MIN = 0.5f
    const val RATE_MAX = 2.0f

    /** Volumen de pitidos en pasos del 10 %: 0 = sin pitidos. */
    fun stepVolume(v: Float, delta: Int): Float = (Math.round(v * 10) + delta).coerceIn(0, 10) / 10f

    /** Velocidad de voz en pasos de 0,25. */
    fun stepRate(r: Float, delta: Int): Float = ((Math.round(r * 4) + delta) / 4f).coerceIn(RATE_MIN, RATE_MAX)
}
