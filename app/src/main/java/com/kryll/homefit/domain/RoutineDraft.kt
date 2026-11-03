package com.kryll.homefit.domain

import java.io.Serializable

enum class DraftError { NAME_EMPTY, NAME_TOO_LONG, NO_ITEMS, BAD_SETS, BAD_VOLUME, BAD_REST }

/** Una fila del editor: o repeticiones o segundos (nunca las dos). */
data class DraftItem(
    val exerciseId: String,
    val sets: Int? = 3,
    val reps: Int? = 10,
    val seconds: Int? = null,
    val restSeconds: Int? = 45,
) : Serializable {
    val timed: Boolean get() = seconds != null
    fun asTimed() = copy(reps = null, seconds = seconds ?: 30)
    fun asReps() = copy(seconds = null, reps = reps ?: 10)

    companion object {
        /** Ejercicios que se prescriben por tiempo (isométricos, estiramientos, cardio), con sus segundos iniciales. */
        val TIMED_DEFAULTS: Map<String, Int> = mapOf(
            "plancha" to 30, "plancha_rodillas" to 30, "plancha_lateral" to 25, "hollow_hold" to 20,
            "estiramiento_isquios" to 30, "estiramiento_cadera" to 30, "estiramiento_pecho" to 30,
            "postura_nino" to 45,
            "jumping_jacks" to 40, "mountain_climbers" to 40, "burpees_sin_salto" to 40, "sentadilla_salto" to 40,
            "patinador" to 40, "rodillas_altas" to 45, "skipping_lateral" to 45, "shadow_boxing" to 45,
        )

        /** Modo y valor iniciales según la prescripción del ejercicio. */
        fun forExercise(id: String): DraftItem {
            val secs = TIMED_DEFAULTS[id]
            return if (secs != null) DraftItem(id, reps = null, seconds = secs) else DraftItem(id)
        }
    }
}

/** Rutina en edición. Se valida aquí, sin depender de Android. */
data class RoutineDraft(
    val name: String = "",
    val goal: String = "tonificar",
    val level: String = "principiante",
    val items: List<DraftItem> = emptyList(),
) : Serializable {

    fun errors(): Set<DraftError> {
        val e = linkedSetOf<DraftError>()
        if (name.isBlank()) e += DraftError.NAME_EMPTY
        if (name.trim().length > MAX_NAME) e += DraftError.NAME_TOO_LONG
        if (items.isEmpty()) e += DraftError.NO_ITEMS
        items.forEach { i ->
            if (i.sets == null || i.sets !in 1..MAX_SETS) e += DraftError.BAD_SETS
            val volumeOk = (i.reps != null && i.seconds == null && i.reps in 1..MAX_REPS) ||
                (i.seconds != null && i.reps == null && i.seconds in 5..MAX_SECONDS)
            if (!volumeOk) e += DraftError.BAD_VOLUME
            if (i.restSeconds == null || i.restSeconds !in 0..MAX_REST) e += DraftError.BAD_REST
        }
        return e
    }

    fun add(exerciseId: String) = copy(items = items + DraftItem.forExercise(exerciseId))
    fun remove(index: Int) = copy(items = items.filterIndexed { i, _ -> i != index })
    fun update(index: Int, item: DraftItem) = copy(items = items.mapIndexed { i, old -> if (i == index) item else old })

    fun move(index: Int, delta: Int): RoutineDraft {
        val to = index + delta
        if (index !in items.indices || to !in items.indices) return this
        val m = items.toMutableList()
        m.add(to, m.removeAt(index))
        return copy(items = m)
    }

    companion object {
        const val MAX_NAME = 60
        const val MAX_SETS = 20
        const val MAX_REPS = 100
        const val MAX_SECONDS = 600
        const val MAX_REST = 600
    }
}
