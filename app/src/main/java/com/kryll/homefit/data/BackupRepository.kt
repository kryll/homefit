package com.kryll.homefit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.withTransaction

@Dao
interface BackupDao {
    @Query("SELECT * FROM routine WHERE predefined = 0 ORDER BY id") suspend fun userRoutines(): List<Routine>
    @Query("SELECT * FROM routine WHERE predefined = 1") suspend fun predefinedRoutines(): List<Routine>
    @Query("SELECT * FROM routine_item ORDER BY routineId, position") suspend fun allItems(): List<RoutineItem>
    @Query("SELECT * FROM workout_session ORDER BY startMillis") suspend fun workouts(): List<WorkoutSession>
    @Query("SELECT * FROM set_log ORDER BY id") suspend fun sets(): List<SetLog>
    @Query("SELECT * FROM scheduled_session ORDER BY startMillis") suspend fun scheduled(): List<ScheduledSession>
    @Query("SELECT * FROM body_measurement ORDER BY epochDay") suspend fun measurements(): List<BodyMeasurement>
    @Query("SELECT equipment FROM user_equipment") suspend fun equipment(): List<String>
    @Query("SELECT id FROM exercise") suspend fun exerciseIds(): List<String>
    @Query("SELECT * FROM user_profile WHERE id = 1") suspend fun profile(): UserProfile?

    @Query("DELETE FROM set_log") suspend fun deleteSets()
    @Query("DELETE FROM workout_session") suspend fun deleteWorkouts()
    @Query("DELETE FROM scheduled_session") suspend fun deleteScheduled()
    @Query("DELETE FROM body_measurement") suspend fun deleteMeasurements()
    @Query("DELETE FROM routine WHERE predefined = 0") suspend fun deleteUserRoutines()

    @Insert suspend fun insertMeasurements(items: List<BodyMeasurement>)
}

/** Exporta e importa los datos del usuario. El catálogo de ejercicios y las rutinas predefinidas no se tocan. */
class BackupRepository(private val db: HomeFitDatabase) {
    suspend fun export(settings: AppSettings, nowMillis: Long): Backup = db.withTransaction {
        val dao = db.backupDao()
        val predefined = dao.predefinedRoutines().associate { it.id to it.name }
        val users = dao.userRoutines()
        val items = dao.allItems().groupBy { it.routineId }
        val setsBySession = dao.sets().groupBy { it.sessionId }
        fun ref(id: Long?): String? = when {
            id == null -> null
            id in predefined -> "P:" + predefined.getValue(id)
            users.any { it.id == id } -> "U:$id"
            else -> null
        }
        Backup(
            exportedAt = nowMillis,
            profile = dao.profile(),
            equipment = dao.equipment().sorted(),
            routines = users.map { BackupRoutine(it.id, it, items[it.id].orEmpty()) },
            workouts = dao.workouts().map { w -> BackupWorkout(w, ref(w.routineId), setsBySession[w.id].orEmpty()) },
            scheduled = dao.scheduled().map { BackupScheduled(it, ref(it.routineId)) },
            measurements = dao.measurements(),
            settings = settings,
        )
    }

    /** Comprueba el contenido contra la app actual sin tocar nada. Lanza [BackupException] si no es válido. */
    suspend fun validate(b: Backup) {
        val known = db.backupDao().exerciseIds().toSet()
        fun bad(msg: String): Nothing = throw BackupException(BackupException.Kind.INVALID, msg)
        b.profile?.let { p ->
            if (p.daysMask !in 0..127) bad("daysMask")
            if (p.name.isBlank()) bad("name")
        }
        b.routines.forEach { r ->
            if (r.routine.name.isBlank()) bad("routine")
            r.items.forEach { i ->
                if (i.exerciseId !in known) throw BackupException(BackupException.Kind.UNKNOWN_EXERCISE, i.exerciseId)
                if (i.sets < 1) bad("sets")
            }
        }
        b.workouts.forEach { w ->
            w.sets.forEach { s ->
                if (s.exerciseId !in known) throw BackupException(BackupException.Kind.UNKNOWN_EXERCISE, s.exerciseId)
                if (s.rpe != null && s.rpe !in 1..10) bad("rpe")
            }
        }
    }

    /** Sustituye los datos del usuario por los del archivo, todo o nada. Los ajustes los aplica quien llama. */
    suspend fun restore(b: Backup) {
        validate(b)
        db.withTransaction {
            val dao = db.backupDao()
            val routineDao = db.routineDao()
            val sessionDao = db.sessionDao()
            dao.deleteSets(); dao.deleteWorkouts(); dao.deleteScheduled(); dao.deleteMeasurements(); dao.deleteUserRoutines()

            val newIds = HashMap<Long, Long>()
            b.routines.forEach { r ->
                val id = routineDao.insertRoutine(r.routine.copy(id = 0, predefined = false))
                newIds[r.oldId] = id
                routineDao.insertItems(r.items.map { it.copy(id = 0, routineId = id) })
            }
            val predefined = dao.predefinedRoutines().associate { it.name to it.id }
            fun resolve(ref: String?): Long? = when {
                ref == null -> null
                ref.startsWith("U:") -> ref.removePrefix("U:").toLongOrNull()?.let { newIds[it] }
                ref.startsWith("P:") -> predefined[ref.removePrefix("P:")]
                else -> null
            }
            b.workouts.forEach { w ->
                val sid = sessionDao.insertWorkout(w.session.copy(id = 0, routineId = resolve(w.routineRef)))
                if (w.sets.isNotEmpty()) sessionDao.insertSets(w.sets.map { it.copy(id = 0, sessionId = sid) })
            }
            b.scheduled.forEach { s -> sessionDao.insertScheduled(s.session.copy(id = 0, routineId = resolve(s.routineRef))) }
            if (b.measurements.isNotEmpty()) dao.insertMeasurements(b.measurements.map { it.copy(id = 0) })
            b.profile?.let { p ->
                val profileDao = db.profileDao()
                profileDao.saveProfile(p.copy(id = 1))
                profileDao.clearEquipment()
                profileDao.addEquipment(b.equipment.map { UserEquipment(it) })
            }
        }
    }
}
