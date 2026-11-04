package com.kryll.homefit.data

import androidx.room.withTransaction
import com.kryll.homefit.domain.RoutineDraft

/** Rutinas del usuario: crear, editar y borrar. Las predefinidas no se modifican nunca. */
class RoutineRepository(private val db: HomeFitDatabase) {

    /** Devuelve el id guardado, o null si el borrador no es válido o se intentó editar una predefinida. */
    suspend fun save(id: Long?, draft: RoutineDraft): Long? {
        if (draft.errors().isNotEmpty()) return null
        return db.withTransaction {
            val dao = db.routineDao()
            val name = draft.name.trim()
            val rid = if (id == null) {
                dao.insertRoutine(Routine(name = name, goal = draft.goal, level = draft.level, type = "personalizada", predefined = false))
            } else {
                val old = dao.byId(id) ?: return@withTransaction null
                if (old.predefined) return@withTransaction null
                dao.updateRoutine(old.copy(name = name, goal = draft.goal, level = draft.level))
                dao.clearItems(id)
                id
            }
            dao.insertItems(
                draft.items.mapIndexed { pos, i ->
                    RoutineItem(
                        routineId = rid, position = pos, exerciseId = i.exerciseId,
                        sets = i.sets!!, reps = i.reps, seconds = i.seconds, restSeconds = i.restSeconds!!,
                    )
                },
            )
            rid
        }
    }

    suspend fun delete(id: Long) = db.routineDao().deleteUser(id)
}
