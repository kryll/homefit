package com.kryll.homefit.audio

import android.os.SystemClock
import com.kryll.homefit.domain.WorkItem
import com.kryll.homefit.domain.WorkoutRunner

/** Sesión de entreno activa a nivel de proceso: sobrevive a la pantalla apagada y a recrear la actividad. */
object WorkoutHost {
    @Volatile var runner: WorkoutRunner? = null
        private set
    @Volatile private var key: Long? = null

    @Synchronized
    fun begin(sessionKey: Long, plan: List<WorkItem>): WorkoutRunner {
        val cur = runner
        if (cur != null && key == sessionKey && !cur.isClosed) return cur
        return WorkoutRunner(plan, now = { SystemClock.elapsedRealtime() }).also { runner = it; key = sessionKey }
    }

    @Synchronized
    fun clear() { runner = null; key = null }
}
