package com.kryll.homefit.data

import android.content.Context
import com.kryll.homefit.domain.RoutineCatalog
import kotlinx.coroutines.flow.first

/** Inserta las rutinas predefinidas si aún no hay ninguna. Requiere que los ejercicios ya estén cargados. */
object RoutineSeeder {
    suspend fun seedIfEmpty(context: Context, db: HomeFitDatabase) {
        val dao = db.routineDao()
        if (dao.observeAll().first().any { it.predefined }) return
        val json = context.assets.open("routines.json").bufferedReader().use { it.readText() }
        for (r in RoutineCatalog.parse(json)) {
            val id = dao.insertRoutine(Routine(name = r.name, goal = r.goal, level = r.level, type = r.type, predefined = true, notes = r.notes))
            dao.insertItems(r.items.mapIndexed { i, it ->
                RoutineItem(routineId = id, position = i, exerciseId = it.exerciseId, sets = it.sets, reps = it.reps, seconds = it.seconds, restSeconds = it.restSeconds)
            })
        }
    }
}
