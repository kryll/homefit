package com.kryll.homefit.domain

/** Serie registrada de una sesión interrumpida, tal como sale del historial. */
data class InterruptedSet(val exerciseId: String, val setIndex: Int, val reps: Int?, val seconds: Int?, val rpe: Int?, val weightKg: Float?)

object WorkoutResume {
    /**
     * Reexpresa las series de una sesión interrumpida como resultados del plan actual, en orden de plan.
     * Las series saltadas no se registraron, así que esas series se vuelven a ofrecer al reanudar.
     * Null si el plan ya no encaja con lo registrado (rutina editada entretanto): mejor empezar de nuevo.
     */
    fun fromLogs(plan: List<WorkItem>, logs: List<InterruptedSet>): List<SetResult>? {
        if (logs.isEmpty()) return null
        val byExercise = logs.groupBy { it.exerciseId }
        if (byExercise.keys.any { id -> plan.none { it.exerciseId == id } }) return null
        val out = mutableListOf<SetResult>()
        plan.forEachIndexed { i, item ->
            val mine = byExercise[item.exerciseId].orEmpty().sortedBy { it.setIndex }
            mine.forEachIndexed { k, l ->
                if (l.setIndex != k + 1) return null
                out += SetResult(i, k + 1, l.rpe, skipped = false, workSeconds = l.seconds ?: 0, reps = l.reps, weightKg = l.weightKg)
            }
        }
        return if (out.size == logs.size) out else null
    }
}
