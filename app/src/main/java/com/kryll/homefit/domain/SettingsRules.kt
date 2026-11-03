package com.kryll.homefit.domain

import com.kryll.homefit.data.AppSettings

/** Lo que el reproductor de avisos necesita de los ajustes. */
data class CueSettings(val speech: Boolean, val beepPercent: Int, val speechRate: Float, val voiceName: String? = null) {
    val beep: Boolean get() = beepPercent > 0

    companion object {
        fun from(s: AppSettings) = CueSettings(
            speech = s.voiceEnabled,
            beepPercent = Math.round(s.beepVolume.coerceIn(0f, 1f) * 100),
            speechRate = s.ttsRate.coerceIn(SettingsRules.RATE_MIN, SettingsRules.RATE_MAX),
            voiceName = s.ttsVoice,
        )
    }
}

object SettingsRules {
    const val RATE_MIN = 0.5f
    const val RATE_MAX = 2.0f
    const val REST_MIN = 0
    const val REST_STEP = 5
    const val COUNTDOWN_MIN = 0
    const val COUNTDOWN_MAX = 15

    /** Descanso por defecto en pasos de 5 s, dentro de lo que admite el editor de rutinas. */
    fun stepRest(s: Int, delta: Int): Int = (s + delta * REST_STEP).coerceIn(REST_MIN, RoutineDraft.MAX_REST)

    /** Cuenta atrás previa a cada ejercicio, en pasos de 1 s. */
    fun stepCountdown(s: Int, delta: Int): Int = (s + delta).coerceIn(COUNTDOWN_MIN, COUNTDOWN_MAX)

    /** Volumen de pitidos en pasos del 10 %: 0 = sin pitidos. */
    fun stepVolume(v: Float, delta: Int): Float = (Math.round(v * 10) + delta).coerceIn(0, 10) / 10f

    /** Velocidad de voz en pasos de 0,25. */
    fun stepRate(r: Float, delta: Int): Float = ((Math.round(r * 4) + delta) / 4f).coerceIn(RATE_MIN, RATE_MAX)

    /** La voz guardada solo se usa si sigue instalada; si no, suena la predeterminada del sistema. */
    fun resolveVoice(available: List<String>, saved: String?): String? = saved?.takeIf { it in available }
}
