package com.kryll.homefit.data

import android.content.Context
import com.kryll.homefit.domain.ExerciseCatalog

/** Carga el catálogo en Room la primera vez (o si está vacío). Idempotente. */
object ExerciseSeeder {
    suspend fun seedIfEmpty(context: Context, db: HomeFitDatabase) {
        val dao = db.exerciseDao()
        if (dao.count() > 0) return
        val json = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
        val c = ExerciseCatalog.parse(json)
        dao.insertAll(c.exercises)
        dao.insertMuscles(c.muscles)
        dao.insertEquipment(c.equipment)
    }
}
