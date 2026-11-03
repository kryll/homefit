package com.kryll.homefit.domain

import com.kryll.homefit.data.SetLog
import com.kryll.homefit.data.WorkoutSession

/** Une sesiones y series de la base en [SessionSets], más reciente primero. */
object ProgressionData {
    fun sessionSets(sessions: List<WorkoutSession>, sets: List<SetLog>): List<SessionSets> {
        val bySession = sets.groupBy { it.sessionId }
        return sessions.sortedByDescending { it.startMillis }.map { s ->
            SessionSets(
                s.routineId, s.startMillis,
                (bySession[s.id] ?: emptyList()).groupBy({ it.exerciseId }, { LoggedSet(it.rpe) }),
            )
        }
    }
}
