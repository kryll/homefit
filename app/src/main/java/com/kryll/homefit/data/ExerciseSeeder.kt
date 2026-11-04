package com.kryll.homefit.data

import android.content.Context
import com.kryll.homefit.domain.ExerciseCatalog

/** Carga el catálogo la primera vez y actualiza su contenido en cada arranque. Idempotente. */
object ExerciseSeeder {
    suspend fun refresh(context: Context, db: HomeFitDatabase) {
        val json = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
        refreshFromJson(json, db)
    }

    /**
     * Inserta los ejercicios nuevos y ACTUALIZA los existentes con el contenido del asset (variantes,
     * pasos, media), sin tocar datos del usuario: rutinas, planificación e historial referencian exerciseId
     * como texto. Se actualiza en lugar de borrar porque exercise_muscle/equipment tienen CASCADE y un
     * REPLACE eliminaría la fila (y sus uniones). Las uniones se reconstruyen por ejercicio.
     */
    suspend fun refreshFromJson(json: String, db: HomeFitDatabase) {
        val dao = db.exerciseDao()
        val c = ExerciseCatalog.parse(json)
        dao.insertAllIgnore(c.exercises)
        dao.updateAll(c.exercises)
        c.exercises.forEach {
            dao.deleteMusclesOf(it.id)
            dao.deleteEquipmentOf(it.id)
        }
        dao.insertMuscles(c.muscles)
        dao.insertEquipment(c.equipment)
    }
}
