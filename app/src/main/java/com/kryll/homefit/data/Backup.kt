package com.kryll.homefit.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class BackupException(val kind: Kind, val detail: String = "") : Exception("$kind $detail") {
    enum class Kind { NOT_BACKUP, VERSION, CORRUPT, INVALID, UNKNOWN_EXERCISE }
}

/** Rutina del usuario con su id original (solo sirve para enlazar sesiones del mismo archivo). */
data class BackupRoutine(val oldId: Long, val routine: Routine, val items: List<RoutineItem>)

/** [routineRef]: "U:<id original>" (rutina del usuario) o "P:<nombre>" (predefinida), o null. */
data class BackupWorkout(val session: WorkoutSession, val routineRef: String?, val sets: List<SetLog>)
data class BackupScheduled(val session: ScheduledSession, val routineRef: String?)

data class Backup(
    val exportedAt: Long,
    val profile: UserProfile?,
    val equipment: List<String>,
    val routines: List<BackupRoutine>,
    val workouts: List<BackupWorkout>,
    val scheduled: List<BackupScheduled>,
    val measurements: List<BodyMeasurement>,
    val settings: AppSettings,
)

/** Copia de seguridad en JSON legible, con versión de formato. No incluye el catálogo (viene en la app). */
object BackupCodec {
    const val FORMAT = "homefit-backup"
    const val VERSION = 1

    fun toJson(b: Backup): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("exportedAt", b.exportedAt)
        put("profile", b.profile?.let(::profile))
        put("equipment", JSONArray(b.equipment))
        put("routines", JSONArray(b.routines.map { r ->
            JSONObject().put("oldId", r.oldId).put("name", r.routine.name).put("goal", r.routine.goal).put("level", r.routine.level)
                .put("type", r.routine.type).put("notes", r.routine.notes)
                .put("items", JSONArray(r.items.map { i ->
                    JSONObject().put("position", i.position).put("exerciseId", i.exerciseId).put("sets", i.sets)
                        .put("reps", i.reps ?: JSONObject.NULL).put("seconds", i.seconds ?: JSONObject.NULL)
                        .put("restSeconds", i.restSeconds).put("alternateSides", i.alternateSides)
                }))
        }))
        put("workouts", JSONArray(b.workouts.map { w ->
            JSONObject().put("routineRef", w.routineRef ?: JSONObject.NULL).put("startMillis", w.session.startMillis)
                .put("endMillis", w.session.endMillis).put("durationSeconds", w.session.durationSeconds).put("notes", w.session.notes)
                .put("sets", JSONArray(w.sets.map { s ->
                    JSONObject().put("exerciseId", s.exerciseId).put("setIndex", s.setIndex).put("reps", s.reps ?: JSONObject.NULL)
                        .put("weightKg", s.weightKg?.toDouble() ?: JSONObject.NULL).put("seconds", s.seconds ?: JSONObject.NULL)
                        .put("rpe", s.rpe ?: JSONObject.NULL)
                }))
        }))
        put("scheduled", JSONArray(b.scheduled.map { s ->
            JSONObject().put("routineRef", s.routineRef ?: JSONObject.NULL).put("startMillis", s.session.startMillis)
                .put("status", s.session.status).put("reminderMinutesBefore", s.session.reminderMinutesBefore)
        }))
        put("measurements", JSONArray(b.measurements.map { m ->
            JSONObject().put("epochDay", m.epochDay).put("weightKg", d(m.weightKg)).put("waistCm", d(m.waistCm))
                .put("chestCm", d(m.chestCm)).put("armCm", d(m.armCm)).put("thighCm", d(m.thighCm))
                .put("hipCm", d(m.hipCm)).put("bodyFatPct", d(m.bodyFatPct))
        }))
        put("settings", settings(b.settings))
    }.toString(2)

    fun fromJson(text: String): Backup {
        val root = try { JSONObject(text) } catch (e: JSONException) { throw BackupException(BackupException.Kind.NOT_BACKUP) }
        if (root.optString("format") != FORMAT) throw BackupException(BackupException.Kind.NOT_BACKUP)
        val v = root.optInt("version", -1)
        if (v != VERSION) throw BackupException(BackupException.Kind.VERSION, v.toString())
        try {
            return Backup(
                exportedAt = root.getLong("exportedAt"),
                profile = root.optJSONObject("profile")?.let(::readProfile),
                equipment = root.getJSONArray("equipment").strings(),
                routines = root.getJSONArray("routines").objects().map { r ->
                    BackupRoutine(
                        r.getLong("oldId"),
                        Routine(name = r.getString("name"), goal = r.getString("goal"), level = r.getString("level"),
                            type = r.getString("type"), predefined = false, notes = r.optString("notes", "")),
                        r.getJSONArray("items").objects().map { i ->
                            RoutineItem(
                                routineId = 0, position = i.getInt("position"), exerciseId = i.getString("exerciseId"),
                                sets = i.getInt("sets"), reps = i.intOrNull("reps"), seconds = i.intOrNull("seconds"),
                                restSeconds = i.getInt("restSeconds"), alternateSides = i.optBoolean("alternateSides", false),
                            )
                        },
                    )
                },
                workouts = root.getJSONArray("workouts").objects().map { w ->
                    BackupWorkout(
                        WorkoutSession(routineId = null, startMillis = w.getLong("startMillis"), endMillis = w.getLong("endMillis"),
                            durationSeconds = w.getInt("durationSeconds"), notes = w.optString("notes", "")),
                        w.stringOrNull("routineRef"),
                        w.getJSONArray("sets").objects().map { s ->
                            SetLog(sessionId = 0, exerciseId = s.getString("exerciseId"), setIndex = s.getInt("setIndex"),
                                reps = s.intOrNull("reps"), weightKg = s.floatOrNull("weightKg"), seconds = s.intOrNull("seconds"), rpe = s.intOrNull("rpe"))
                        },
                    )
                },
                scheduled = root.getJSONArray("scheduled").objects().map { s ->
                    BackupScheduled(
                        ScheduledSession(routineId = null, startMillis = s.getLong("startMillis"), status = s.getString("status"),
                            reminderMinutesBefore = s.optInt("reminderMinutesBefore", 0)),
                        s.stringOrNull("routineRef"),
                    )
                },
                measurements = root.getJSONArray("measurements").objects().map { m ->
                    BodyMeasurement(epochDay = m.getLong("epochDay"), weightKg = m.floatOrNull("weightKg"), waistCm = m.floatOrNull("waistCm"),
                        chestCm = m.floatOrNull("chestCm"), armCm = m.floatOrNull("armCm"), thighCm = m.floatOrNull("thighCm"),
                        hipCm = m.floatOrNull("hipCm"), bodyFatPct = m.floatOrNull("bodyFatPct"))
                },
                settings = readSettings(root.getJSONObject("settings")),
            )
        } catch (e: JSONException) {
            throw BackupException(BackupException.Kind.CORRUPT, e.message.orEmpty())
        }
    }

    private fun d(f: Float?): Any = f?.toDouble() ?: JSONObject.NULL

    private fun profile(p: UserProfile) = JSONObject().put("name", p.name).put("sex", p.sex).put("birthEpochDay", p.birthEpochDay)
        .put("heightCm", p.heightCm.toDouble()).put("weightKg", p.weightKg.toDouble()).put("goal", p.goal).put("level", p.level)
        .put("daysMask", p.daysMask).put("minutesPerSession", p.minutesPerSession)

    private fun readProfile(o: JSONObject) = UserProfile(
        name = o.getString("name"), sex = o.getString("sex"), birthEpochDay = o.getLong("birthEpochDay"),
        heightCm = o.getDouble("heightCm").toFloat(), weightKg = o.getDouble("weightKg").toFloat(), goal = o.getString("goal"),
        level = o.getString("level"), daysMask = o.getInt("daysMask"), minutesPerSession = o.getInt("minutesPerSession"),
    )

    private fun settings(s: AppSettings) = JSONObject().put("theme", s.theme.name).put("weightUnit", s.weightUnit.name)
        .put("lengthUnit", s.lengthUnit.name).put("beepVolume", s.beepVolume.toDouble()).put("voiceEnabled", s.voiceEnabled)
        .put("ttsRate", s.ttsRate.toDouble()).put("ttsVoice", s.ttsVoice ?: JSONObject.NULL)
        .put("defaultRestSeconds", s.defaultRestSeconds).put("defaultCountdownSeconds", s.defaultCountdownSeconds)
        .put("reminderEnabled", s.reminderEnabled).put("reminderHour", s.reminderHour).put("reminderMinute", s.reminderMinute).put("reminderLeadMinutes", s.reminderLeadMinutes)
        .put("reminderExact", s.reminderExact)

    /** Los ajustes ausentes o con valores desconocidos toman el valor por defecto. */
    private fun readSettings(o: JSONObject): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = runCatching { ThemeMode.valueOf(o.optString("theme")) }.getOrDefault(d.theme),
            weightUnit = runCatching { WeightUnit.valueOf(o.optString("weightUnit")) }.getOrDefault(d.weightUnit),
            lengthUnit = runCatching { LengthUnit.valueOf(o.optString("lengthUnit")) }.getOrDefault(d.lengthUnit),
            beepVolume = o.optDouble("beepVolume", d.beepVolume.toDouble()).toFloat().coerceIn(0f, 1f),
            voiceEnabled = o.optBoolean("voiceEnabled", d.voiceEnabled),
            ttsRate = o.optDouble("ttsRate", d.ttsRate.toDouble()).toFloat().coerceIn(0.5f, 2.0f),
            ttsVoice = o.stringOrNull("ttsVoice"),
            defaultRestSeconds = o.optInt("defaultRestSeconds", d.defaultRestSeconds),
            defaultCountdownSeconds = o.optInt("defaultCountdownSeconds", d.defaultCountdownSeconds),
            reminderEnabled = o.optBoolean("reminderEnabled", d.reminderEnabled),
            reminderHour = o.optInt("reminderHour", d.reminderHour).coerceIn(0, 23),
            reminderMinute = o.optInt("reminderMinute", d.reminderMinute).coerceIn(0, 59),
            reminderLeadMinutes = o.optInt("reminderLeadMinutes", d.reminderLeadMinutes).coerceIn(0, 120),
            reminderExact = o.optBoolean("reminderExact", d.reminderExact),
        )
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
    private fun JSONObject.intOrNull(k: String): Int? = if (isNull(k)) null else getInt(k)
    private fun JSONObject.floatOrNull(k: String): Float? = if (isNull(k)) null else getDouble(k).toFloat()
    private fun JSONObject.stringOrNull(k: String): String? = if (isNull(k)) null else getString(k)
}
