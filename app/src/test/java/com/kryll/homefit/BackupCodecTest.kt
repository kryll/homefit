package com.kryll.homefit

import com.kryll.homefit.data.AppSettings
import com.kryll.homefit.data.Backup
import com.kryll.homefit.data.BackupCodec
import com.kryll.homefit.data.BackupException
import com.kryll.homefit.data.BackupRoutine
import com.kryll.homefit.data.BackupScheduled
import com.kryll.homefit.data.BackupWorkout
import com.kryll.homefit.data.BodyMeasurement
import com.kryll.homefit.data.Routine
import com.kryll.homefit.data.RoutineItem
import com.kryll.homefit.data.ScheduledSession
import com.kryll.homefit.data.SetLog
import com.kryll.homefit.data.ThemeMode
import com.kryll.homefit.data.UserProfile
import com.kryll.homefit.data.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {
    private val sample = Backup(
        exportedAt = 123,
        profile = UserProfile(name = "Ana", sex = "female", birthEpochDay = 9000, heightCm = 165.5f, weightKg = 60.25f, goal = "tonificar", level = "principiante", daysMask = 21, minutesPerSession = 30),
        equipment = listOf("esterilla", "mancuernas"),
        routines = listOf(
            BackupRoutine(7, Routine(name = "Mi rutina", goal = "tonificar", level = "principiante", type = "personalizada", predefined = false, notes = "n"),
                listOf(RoutineItem(routineId = 7, position = 0, exerciseId = "plancha", sets = 3, seconds = 30, restSeconds = 30),
                    RoutineItem(routineId = 7, position = 1, exerciseId = "sentadilla_goblet", sets = 3, reps = 10, restSeconds = 45, alternateSides = true))),
        ),
        workouts = listOf(
            BackupWorkout(WorkoutSession(routineId = 7, startMillis = 1000, endMillis = 2000, durationSeconds = 1, notes = "incompleto"), "U:7",
                listOf(SetLog(sessionId = 1, exerciseId = "plancha", setIndex = 0, reps = 12, weightKg = 4.5f, seconds = 30, rpe = 7), SetLog(sessionId = 1, exerciseId = "plancha", setIndex = 1))),
            BackupWorkout(WorkoutSession(routineId = 3, startMillis = 3000, endMillis = 4000, durationSeconds = 1), "P:Fuerza", emptyList()),
        ),
        scheduled = listOf(BackupScheduled(ScheduledSession(routineId = 7, startMillis = 5000, status = "SCHEDULED", reminderMinutesBefore = 10), "U:7")),
        measurements = listOf(BodyMeasurement(epochDay = 20000, weightKg = 60f, waistCm = 70f), BodyMeasurement(epochDay = 20010)),
        settings = AppSettings(theme = ThemeMode.DARK, beepVolume = 0.4f, voiceEnabled = false, ttsRate = 1.25f, ttsVoice = "es-x"),
    )

    @Test fun roundTripKeepsEverything() {
        val back = BackupCodec.fromJson(BackupCodec.toJson(sample))
        assertEquals(sample.exportedAt, back.exportedAt)
        assertEquals(sample.profile, back.profile)
        assertEquals(sample.equipment, back.equipment)
        assertEquals(1, back.routines.size)
        assertEquals(7L, back.routines[0].oldId)
        assertEquals("Mi rutina", back.routines[0].routine.name)
        assertEquals("n", back.routines[0].routine.notes)
        assertEquals(sample.routines[0].items.map { it.copy(routineId = 0) }, back.routines[0].items)
        assertEquals(listOf("U:7", "P:Fuerza"), back.workouts.map { it.routineRef })
        assertEquals("incompleto", back.workouts[0].session.notes)
        assertEquals(sample.workouts[0].sets.map { it.copy(sessionId = 0) }, back.workouts[0].sets)
        assertNull(back.workouts[0].sets[1].rpe)
        assertEquals(10, back.scheduled[0].session.reminderMinutesBefore)
        assertEquals("U:7", back.scheduled[0].routineRef)
        assertEquals(sample.measurements, back.measurements)
        assertEquals(sample.settings, back.settings)
    }

    @Test fun backupWithoutProfileIsAccepted() {
        val back = BackupCodec.fromJson(BackupCodec.toJson(sample.copy(profile = null)))
        assertNull(back.profile)
    }

    private fun kind(text: String): BackupException.Kind {
        try { BackupCodec.fromJson(text) } catch (e: BackupException) { return e.kind }
        fail("debía fallar"); throw IllegalStateException()
    }

    @Test fun notJsonIsNotABackup() = assertEquals(BackupException.Kind.NOT_BACKUP, kind("hola"))

    @Test fun otherJsonIsNotABackup() = assertEquals(BackupException.Kind.NOT_BACKUP, kind("""{"a":1}"""))

    @Test fun unknownVersionIsRejected() {
        val v2 = BackupCodec.toJson(sample).replace("\"version\": 1", "\"version\": 2")
        assertEquals(BackupException.Kind.VERSION, kind(v2))
    }

    @Test fun missingSectionIsCorrupt() {
        val broken = org.json.JSONObject(BackupCodec.toJson(sample)).apply { remove("workouts") }.toString()
        assertEquals(BackupException.Kind.CORRUPT, kind(broken))
    }

    @Test fun wrongFieldTypeIsCorrupt() {
        val broken = BackupCodec.toJson(sample).replace("\"exportedAt\": 123", "\"exportedAt\": \"x\"")
        assertEquals(BackupException.Kind.CORRUPT, kind(broken))
    }

    @Test fun unknownSettingValuesFallBackToDefaults() {
        val j = org.json.JSONObject(BackupCodec.toJson(sample))
        j.getJSONObject("settings").put("theme", "NEON").put("beepVolume", 7.0)
        val s = BackupCodec.fromJson(j.toString()).settings
        assertEquals(ThemeMode.DARK, s.theme)
        assertEquals(1f, s.beepVolume, 0.0001f)
    }

    @Test fun reminderLeadSurvivesRoundTripAndOldBackupsDefaultToZero() {
        val json = BackupCodec.toJson(sample.copy(settings = com.kryll.homefit.data.AppSettings(reminderLeadMinutes = 30)))
        assertEquals(30, BackupCodec.fromJson(json).settings.reminderLeadMinutes)
        val old = org.json.JSONObject(json)
        old.getJSONObject("settings").remove("reminderLeadMinutes")
        assertEquals(0, BackupCodec.fromJson(old.toString()).settings.reminderLeadMinutes)
    }
}
