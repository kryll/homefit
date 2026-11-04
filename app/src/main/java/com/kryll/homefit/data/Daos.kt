package com.kryll.homefit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observeProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun profile(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: UserProfile)

    @Query("SELECT equipment FROM user_equipment")
    fun observeEquipment(): Flow<List<String>>

    @Query("DELETE FROM user_equipment")
    suspend fun clearEquipment()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addEquipment(items: List<UserEquipment>)
}

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM body_measurement ORDER BY epochDay DESC")
    fun observeAll(): Flow<List<BodyMeasurement>>

    @Insert
    suspend fun insert(measurement: BodyMeasurement): Long

    @androidx.room.Update
    suspend fun update(measurement: BodyMeasurement)

    @Query("DELETE FROM body_measurement WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ExerciseDao {
    @Query("SELECT COUNT(*) FROM exercise")
    suspend fun count(): Int

    @Query("SELECT * FROM exercise ORDER BY name")
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun byId(id: String): Exercise?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Exercise>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMuscles(items: List<ExerciseMuscle>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEquipment(items: List<ExerciseEquipment>)

    @Query("SELECT * FROM exercise_equipment")
    fun observeEquipment(): Flow<List<ExerciseEquipment>>
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routine ORDER BY predefined DESC, name")
    fun observeAll(): Flow<List<Routine>>

    @Query("SELECT * FROM routine_item WHERE routineId = :routineId ORDER BY position")
    fun observeItems(routineId: Long): Flow<List<RoutineItem>>

    @Query("SELECT * FROM routine_item ORDER BY routineId, position")
    fun observeAllItems(): Flow<List<RoutineItem>>

    @Query("SELECT * FROM routine WHERE id = :id")
    suspend fun byId(id: Long): Routine?

    @androidx.room.Update
    suspend fun updateRoutine(routine: Routine)

    @Query("DELETE FROM routine_item WHERE routineId = :id")
    suspend fun clearItems(id: Long)

    /** Solo borra rutinas del usuario; las predefinidas se ignoran. */
    @Query("DELETE FROM routine WHERE id = :id AND predefined = 0")
    suspend fun deleteUser(id: Long)

    @Insert
    suspend fun insertRoutine(routine: Routine): Long

    @Insert
    suspend fun insertItems(items: List<RoutineItem>)
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insertScheduled(session: ScheduledSession): Long

    @Query("SELECT * FROM scheduled_session WHERE startMillis BETWEEN :from AND :to ORDER BY startMillis")
    fun observeScheduled(from: Long, to: Long): Flow<List<ScheduledSession>>

    @Query("SELECT * FROM scheduled_session ORDER BY startMillis")
    fun observeAllScheduled(): Flow<List<ScheduledSession>>

    @Query("SELECT * FROM scheduled_session WHERE startMillis >= :from AND startMillis < :to ORDER BY startMillis")
    suspend fun scheduledBetween(from: Long, to: Long): List<ScheduledSession>

    @Query("DELETE FROM scheduled_session WHERE startMillis >= :from AND startMillis < :to")
    suspend fun deleteScheduledBetween(from: Long, to: Long)

    @Query("UPDATE scheduled_session SET status = :status WHERE id = :id")
    suspend fun setScheduledStatus(id: Long, status: String)

    @Query("SELECT * FROM workout_session WHERE startMillis >= :from AND startMillis < :to ORDER BY startMillis")
    suspend fun workoutsBetween(from: Long, to: Long): List<WorkoutSession>

    @Insert
    suspend fun insertWorkout(session: WorkoutSession): Long

    @Insert
    suspend fun insertSets(items: List<SetLog>)

    @Update
    suspend fun updateWorkout(session: WorkoutSession)

    @Query("DELETE FROM set_log WHERE sessionId = :sessionId")
    suspend fun deleteSets(sessionId: Long)

    @Query("SELECT * FROM workout_session ORDER BY startMillis DESC")
    fun observeWorkouts(): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM set_log ORDER BY id")
    fun observeAllSets(): Flow<List<SetLog>>

    @Query("SELECT * FROM set_log WHERE sessionId = :sessionId ORDER BY id")
    suspend fun setsOf(sessionId: Long): List<SetLog>

    /** La sesión incompleta más reciente de esa rutina empezada hoy (candidata a reanudar). */
    @Query("SELECT * FROM workout_session WHERE routineId = :routineId AND notes = 'incompleto' AND startMillis >= :from ORDER BY startMillis DESC LIMIT 1")
    suspend fun latestIncomplete(routineId: Long, from: Long): WorkoutSession?

    /** Marca una sesión interrumpida como descartada para que no se vuelva a ofrecer. */
    @Query("UPDATE workout_session SET notes = 'descartado' WHERE id = :id")
    suspend fun discardSession(id: Long)
}
