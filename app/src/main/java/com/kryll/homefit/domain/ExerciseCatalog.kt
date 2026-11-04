package com.kryll.homefit.domain

import com.kryll.homefit.data.Exercise
import com.kryll.homefit.data.ExerciseEquipment
import com.kryll.homefit.data.ExerciseMuscle
import org.json.JSONArray

/** Catálogo de ejercicios (assets/exercises.json) convertido a entidades de Room. */
data class Catalog(
    val exercises: List<Exercise>,
    val muscles: List<ExerciseMuscle>,
    val equipment: List<ExerciseEquipment>,
)

object ExerciseCatalog {
    const val SOURCE = "HomeFit (texto original, CC0)"
    const val MEDIA_SOURCE = "HomeFit (texto original, CC0). Imágenes: free-exercise-db (Unlicense)"

    fun parse(json: String): Catalog {
        val arr = JSONArray(json)
        val ex = ArrayList<Exercise>()
        val mu = ArrayList<ExerciseMuscle>()
        val eq = ArrayList<ExerciseEquipment>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            fun lines(key: String): String {
                val a = o.getJSONArray(key)
                return (0 until a.length()).joinToString("\n") { a.getString(it) }
            }
            val imgs = o.optJSONArray("images")?.let { a -> (0 until a.length()).joinToString("\n") { a.getString(it) } } ?: ""
            ex += Exercise(
                id = id,
                name = o.getString("name"),
                primaryMuscle = o.getString("primaryMuscle"),
                difficulty = o.getString("difficulty"),
                steps = lines("steps"),
                commonMistakes = lines("commonMistakes"),
                safetyTips = lines("safetyTips"),
                easierVariantId = o.optString("easierVariantId").takeIf { it.isNotEmpty() && !o.isNull("easierVariantId") },
                harderVariantId = o.optString("harderVariantId").takeIf { it.isNotEmpty() && !o.isNull("harderVariantId") },
                easierText = o.optString("easierText").takeIf { it.isNotEmpty() && !o.isNull("easierText") },
                harderText = o.optString("harderText").takeIf { it.isNotEmpty() && !o.isNull("harderText") },
                images = imgs,
                source = if (imgs.isEmpty()) SOURCE else MEDIA_SOURCE,
            )
            mu += ExerciseMuscle(id, o.getString("primaryMuscle"), true)
            val sec = o.getJSONArray("secondaryMuscles")
            for (j in 0 until sec.length()) mu += ExerciseMuscle(id, sec.getString(j), false)
            val e = o.getJSONArray("equipment")
            if (e.length() == 0) eq += ExerciseEquipment(id, "ninguno")
            for (j in 0 until e.length()) eq += ExerciseEquipment(id, e.getString(j))
        }
        return Catalog(ex, mu, eq)
    }
}
