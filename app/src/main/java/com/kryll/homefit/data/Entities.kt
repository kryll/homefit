package com.kryll.homefit.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Perfil único (id = 1). Fechas como epochDay; alturas en cm y pesos en kg (unidades internas SI). */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val sex: String,
    val birthEpochDay: Long,
    val heightCm: Float,
    val weightKg: Float,
    val goal: String,
    val level: String,
    /** Bits 0..6 = lunes..domingo. */
    val daysMask: Int,
    val minutesPerSession: Int,
)

@Entity(tableName = "user_equipment")
data class UserEquipment(@PrimaryKey val equipment: String)

@Entity(tableName = "body_measurement", indices = [Index("epochDay")])
data class BodyMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val weightKg: Float? = null,
    val waistCm: Float? = null,
    val chestCm: Float? = null,
    val armCm: Float? = null,
    val thighCm: Float? = null,
    val hipCm: Float? = null,
    val bodyFatPct: Float? = null,
)

@Entity(tableName = "exercise", indices = [Index("primaryMuscle"), Index("difficulty")])
data class Exercise(
    @PrimaryKey val id: String,
    val name: String,
    val primaryMuscle: String,
    val difficulty: String,
    /** Listas de texto separadas por salto de línea. */
    val steps: String,
    val commonMistakes: String,
    val safetyTips: String,
    val easierVariantId: String? = null,
    val harderVariantId: String? = null,
    /** Modulación del propio ejercicio cuando no hay variante encadenada (apoyo, recorrido, tempo, unilateral). */
    val easierText: String? = null,
    val harderText: String? = null,
    /** Rutas de assets separadas por salto de línea. */
    val images: String = "",
    val videoUrl: String? = null,
    val source: String = "",
)

@Entity(
    tableName = "exercise_muscle",
    primaryKeys = ["exerciseId", "muscle"],
    foreignKeys = [ForeignKey(Exercise::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE)],
)
data class ExerciseMuscle(val exerciseId: String, val muscle: String, val primary: Boolean)

@Entity(
    tableName = "exercise_equipment",
    primaryKeys = ["exerciseId", "equipment"],
    foreignKeys = [ForeignKey(Exercise::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE)],
)
data class ExerciseEquipment(val exerciseId: String, val equipment: String)

@Entity(tableName = "routine")
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val goal: String,
    val level: String,
    val type: String,
    val predefined: Boolean,
    val notes: String = "",
)

@Entity(
    tableName = "routine_item",
    foreignKeys = [ForeignKey(Routine::class, ["id"], ["routineId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("routineId")],
)
data class RoutineItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val position: Int,
    val exerciseId: String,
    val sets: Int,
    val reps: Int? = null,
    val seconds: Int? = null,
    val restSeconds: Int,
    val alternateSides: Boolean = false,
)

@Entity(tableName = "scheduled_session", indices = [Index("startMillis")])
data class ScheduledSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long?,
    val startMillis: Long,
    /** SCHEDULED, COMPLETED, SKIPPED o MOVED. */
    val status: String,
    val reminderMinutesBefore: Int = 0,
)

@Entity(tableName = "workout_session", indices = [Index("startMillis")])
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long?,
    val startMillis: Long,
    val endMillis: Long,
    val durationSeconds: Int,
    val notes: String = "",
    /** Plan con el que empezó la sesión (reanudación fiel aunque la rutina cambie); null = filas de antes de la v2. */
    val planJson: String? = null,
)

@Entity(
    tableName = "set_log",
    foreignKeys = [ForeignKey(WorkoutSession::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SetLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val setIndex: Int,
    val reps: Int? = null,
    val weightKg: Float? = null,
    val seconds: Int? = null,
    /** Esfuerzo percibido 1-10. */
    val rpe: Int? = null,
    /** Posición del ejercicio en el plan de la sesión; -1 = filas de antes de la v2 (se ubican por exerciseId). */
    val itemIndex: Int = -1,
)
