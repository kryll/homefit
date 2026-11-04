package com.kryll.homefit.domain

/** Serie registrada de una sesión interrumpida, tal como sale del historial. */
data class InterruptedSet(val exerciseId: String, val setIndex: Int, val reps: Int?, val seconds: Int?, val rpe: Int?, val weightKg: Float?, val itemIndex: Int = -1)

object WorkoutResume {
    /**
     * Reexpresa las series de una sesión interrumpida como resultados del plan actual, en orden de plan.
     * Las series saltadas no se registraron, así que esas series se vuelven a ofrecer al reanudar.
     * Null si el plan ya no encaja con lo registrado (rutina editada entretanto): mejor empezar de nuevo.
     */
    fun fromLogs(plan: List<WorkItem>, logs: List<InterruptedSet>): List<SetResult>? {
        if (logs.isEmpty()) return null
        // Un ejercicio repetido en el plan no se puede asignar sin ambigüedad: las series no guardan la ocurrencia.
        if (plan.map { it.exerciseId }.toSet().size != plan.size) return null
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

    /**
     * Reexpresa series con ocurrencia conocida (itemIndex >= 0) como resultados del plan dado (el snapshot de la sesión).
     * Null si una serie apunta fuera del plan, no coincide con el ejercicio de su posición o la numeración tiene huecos.
     */
    fun fromLogsPositional(plan: List<WorkItem>, logs: List<InterruptedSet>): List<SetResult>? {
        if (logs.isEmpty()) return null
        if (logs.any { it.itemIndex !in plan.indices }) return null
        if (logs.any { plan[it.itemIndex].exerciseId != it.exerciseId }) return null
        val out = mutableListOf<SetResult>()
        plan.forEachIndexed { i, _ ->
            val mine = logs.filter { it.itemIndex == i }.sortedBy { it.setIndex }
            mine.forEachIndexed { k, l ->
                if (l.setIndex != k + 1) return null
                out += SetResult(i, k + 1, l.rpe, skipped = false, workSeconds = l.seconds ?: 0, reps = l.reps, weightKg = l.weightKg)
            }
        }
        return if (out.size == logs.size) out else null
    }
}
