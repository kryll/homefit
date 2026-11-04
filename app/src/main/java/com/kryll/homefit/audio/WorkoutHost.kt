package com.kryll.homefit.audio

import android.os.SystemClock
import com.kryll.homefit.domain.WorkItem
import com.kryll.homefit.domain.WorkoutRunner

/** Sesión de entreno activa a nivel de proceso: sobrevive a la pantalla apagada y a recrear la actividad. */
object WorkoutHost {
    @Volatile var runner: WorkoutRunner? = null
        private set
    @Volatile private var key: Long? = null
    /** Id de ruta y hora de inicio (ms de reloj de pared, es también la clave) para los puntos de control. */
    @Volatile var routineId: Long? = null
        private set
    val startMillis: Long get() = key ?: 0L

    @Synchronized
    fun begin(sessionKey: Long, plan: List<WorkItem>, routine: Long? = null, resume: List<com.kryll.homefit.domain.SetResult> = emptyList()): WorkoutRunner {
        val cur = runner
        if (cur != null && key == sessionKey && !cur.isClosed) return cur
        return WorkoutRunner(plan, now = { SystemClock.elapsedRealtime() }, resume = resume).also { runner = it; key = sessionKey; routineId = routine }
    }

    @Synchronized
    fun clear() { runner = null; key = null }
}
