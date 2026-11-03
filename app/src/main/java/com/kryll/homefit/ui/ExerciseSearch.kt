package com.kryll.homefit.ui

import com.kryll.homefit.data.Exercise
import java.text.Normalizer

/** Minúsculas y sin tildes, para comparar texto. */
internal fun plain(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

/** Búsqueda por nombre sin distinguir mayúsculas ni tildes. */
internal fun searchExercises(exercises: List<Exercise>, query: String, limit: Int = 8): List<Exercise> {
    val q = plain(query.trim())
    return exercises.filter { plain(it.name).contains(q) }.take(limit)
}
