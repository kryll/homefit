package com.kryll.homefit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observeProfile(): Flow<UserProfile?>

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

    @Insert
    suspend fun insertWorkout(session: WorkoutSession): Long

    @Insert
    suspend fun insertSets(items: List<SetLog>)

    @Query("SELECT * FROM workout_session ORDER BY startMillis DESC")
    fun observeWorkouts(): Flow<List<WorkoutSession>>
}
