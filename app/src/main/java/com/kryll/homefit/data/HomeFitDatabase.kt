package com.kryll.homefit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserProfile::class, UserEquipment::class, BodyMeasurement::class,
        Exercise::class, ExerciseMuscle::class, ExerciseEquipment::class,
        Routine::class, RoutineItem::class,
        ScheduledSession::class, WorkoutSession::class, SetLog::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class HomeFitDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun sessionDao(): SessionDao

    companion object {
        fun build(context: Context): HomeFitDatabase =
            Room.databaseBuilder(context, HomeFitDatabase::class.java, "homefit.db").build()
    }
}
