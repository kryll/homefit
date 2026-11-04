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
    version = 3,
    exportSchema = true,
)
abstract class HomeFitDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun sessionDao(): SessionDao
    abstract fun backupDao(): BackupDao

    companion object {
        fun build(context: Context): HomeFitDatabase =
            Room.databaseBuilder(context, HomeFitDatabase::class.java, "homefit.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}

/** v1 -> v2: snapshot del plan en workout_session y ocurrencia del ejercicio en set_log (filas viejas: planJson null, itemIndex -1). */
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_session ADD COLUMN planJson TEXT")
        db.execSQL("ALTER TABLE set_log ADD COLUMN itemIndex INTEGER NOT NULL DEFAULT -1")
    }
}

/** v2 -> v3: modulación de variante por texto en exercise (filas viejas: easierText/harderText null). */
val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exercise ADD COLUMN easierText TEXT")
        db.execSQL("ALTER TABLE exercise ADD COLUMN harderText TEXT")
    }
}
