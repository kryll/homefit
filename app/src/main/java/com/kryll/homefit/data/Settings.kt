package com.kryll.homefit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class WeightUnit { KG, LB }
enum class LengthUnit { CM, IN }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val lengthUnit: LengthUnit = LengthUnit.CM,
    val beepVolume: Float = 0.8f,
    val voiceEnabled: Boolean = true,
    val ttsRate: Float = 1.0f,
    val ttsVoice: String? = null,
    val defaultRestSeconds: Int = 45,
    val defaultCountdownSeconds: Int = 5,
    val onboardingDone: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 18,
    val reminderMinute: Int = 0,
    val reminderLeadMinutes: Int = 0,
    /** Aviso con alarma exacta (permiso especial); sin él, cae al trabajo inexacto de WorkManager. */
    val reminderExact: Boolean = false,
    /** Día del último aviso diario emitido (estado de ejecución; evita duplicar el aviso entre la
     *  alarma exacta y el respaldo de WorkManager). No forma parte de la copia de seguridad. */
    val reminderFiredEpochDay: Long = -1,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val weightUnit = stringPreferencesKey("weight_unit")
        val lengthUnit = stringPreferencesKey("length_unit")
        val beepVolume = floatPreferencesKey("beep_volume")
        val voiceEnabled = booleanPreferencesKey("voice_enabled")
        val ttsRate = floatPreferencesKey("tts_rate")
        val ttsVoice = stringPreferencesKey("tts_voice")
        val defaultRest = intPreferencesKey("default_rest")
        val defaultCountdown = intPreferencesKey("default_countdown")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val reminderEnabled = booleanPreferencesKey("reminder_enabled")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val reminderLead = intPreferencesKey("reminder_lead")
        val reminderExact = booleanPreferencesKey("reminder_exact")
        val reminderFiredDay = longPreferencesKey("reminder_fired_day")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.theme,
            weightUnit = p[Keys.weightUnit]?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() } ?: d.weightUnit,
            lengthUnit = p[Keys.lengthUnit]?.let { runCatching { LengthUnit.valueOf(it) }.getOrNull() } ?: d.lengthUnit,
            beepVolume = p[Keys.beepVolume] ?: d.beepVolume,
            voiceEnabled = p[Keys.voiceEnabled] ?: d.voiceEnabled,
            ttsRate = p[Keys.ttsRate] ?: d.ttsRate,
            ttsVoice = p[Keys.ttsVoice],
            defaultRestSeconds = p[Keys.defaultRest] ?: d.defaultRestSeconds,
            defaultCountdownSeconds = p[Keys.defaultCountdown] ?: d.defaultCountdownSeconds,
            onboardingDone = p[Keys.onboardingDone] ?: d.onboardingDone,
            reminderEnabled = p[Keys.reminderEnabled] ?: d.reminderEnabled,
            reminderHour = p[Keys.reminderHour] ?: d.reminderHour,
            reminderMinute = p[Keys.reminderMinute] ?: d.reminderMinute,
            reminderLeadMinutes = p[Keys.reminderLead] ?: d.reminderLeadMinutes,
            reminderExact = p[Keys.reminderExact] ?: d.reminderExact,
            reminderFiredEpochDay = p[Keys.reminderFiredDay] ?: d.reminderFiredEpochDay,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val cur = AppSettings(
                theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
                weightUnit = p[Keys.weightUnit]?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() } ?: WeightUnit.KG,
                lengthUnit = p[Keys.lengthUnit]?.let { runCatching { LengthUnit.valueOf(it) }.getOrNull() } ?: LengthUnit.CM,
                beepVolume = p[Keys.beepVolume] ?: 0.8f,
                voiceEnabled = p[Keys.voiceEnabled] ?: true,
                ttsRate = p[Keys.ttsRate] ?: 1.0f,
                ttsVoice = p[Keys.ttsVoice],
                defaultRestSeconds = p[Keys.defaultRest] ?: 45,
                defaultCountdownSeconds = p[Keys.defaultCountdown] ?: 5,
                onboardingDone = p[Keys.onboardingDone] ?: false,
                reminderEnabled = p[Keys.reminderEnabled] ?: false,
                reminderHour = p[Keys.reminderHour] ?: 18,
                reminderMinute = p[Keys.reminderMinute] ?: 0,
                reminderLeadMinutes = p[Keys.reminderLead] ?: 0,
                reminderExact = p[Keys.reminderExact] ?: false,
                reminderFiredEpochDay = p[Keys.reminderFiredDay] ?: -1,
            )
            val n = transform(cur)
            p[Keys.theme] = n.theme.name
            p[Keys.weightUnit] = n.weightUnit.name
            p[Keys.lengthUnit] = n.lengthUnit.name
            p[Keys.beepVolume] = n.beepVolume
            p[Keys.voiceEnabled] = n.voiceEnabled
            p[Keys.ttsRate] = n.ttsRate
            if (n.ttsVoice != null) p[Keys.ttsVoice] = n.ttsVoice else p.remove(Keys.ttsVoice)
            p[Keys.defaultRest] = n.defaultRestSeconds
            p[Keys.defaultCountdown] = n.defaultCountdownSeconds
            p[Keys.onboardingDone] = n.onboardingDone
            p[Keys.reminderEnabled] = n.reminderEnabled
            p[Keys.reminderHour] = n.reminderHour
            p[Keys.reminderMinute] = n.reminderMinute
            p[Keys.reminderLead] = n.reminderLeadMinutes
            p[Keys.reminderExact] = n.reminderExact
            p[Keys.reminderFiredDay] = n.reminderFiredEpochDay
        }
    }
}
