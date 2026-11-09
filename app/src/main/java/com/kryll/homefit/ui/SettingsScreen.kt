package com.kryll.homefit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kryll.homefit.R
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import com.kryll.homefit.data.AppSettings
import com.kryll.homefit.data.LengthUnit
import com.kryll.homefit.data.ThemeMode
import com.kryll.homefit.data.WeightUnit
import com.kryll.homefit.data.UserProfile
import com.kryll.homefit.domain.Bmi
import com.kryll.homefit.domain.ProfileInput
import com.kryll.homefit.domain.SettingsRules
import java.util.Locale

/** Resumen de una copia pendiente de confirmar. */
data class ImportSummary(val routines: Int, val workouts: Int, val scheduled: Int, val measurements: Int)

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun SettingsScreen(
    settings: AppSettings,
    versionName: String,
    message: String?,
    pendingImport: ImportSummary?,
    availableVoices: List<String> = emptyList(),
    onChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onExport: () -> Unit = {},
    onExportWorkouts: () -> Unit = {},
    onImport: () -> Unit = {},
    onConfirmImport: () -> Unit = {},
    onCancelImport: () -> Unit = {},
    updateState: com.kryll.homefit.domain.UpdateState = com.kryll.homefit.domain.UpdateState.Idle,
    onCheckUpdates: () -> Unit = {},
    onOpenUpdate: (String) -> Unit = {},
    downloadState: com.kryll.homefit.update.DownloadUi = com.kryll.homefit.update.DownloadUi(),
    onInstallUpdate: () -> Unit = {},
    profile: UserProfile? = null,
    equipment: Set<String> = emptySet(),
    latestWeightKg: Float? = null,
    onSaveProfile: (UserProfile, Set<String>) -> Unit = { _, _ -> },
    reminderPrefs: com.kryll.homefit.domain.ReminderPrefs = com.kryll.homefit.domain.ReminderPrefs(),
    reminderNotificationsAllowed: Boolean = true,
    reminderExactAllowed: Boolean = true,
    onReminderPrefs: (com.kryll.homefit.domain.ReminderPrefs) -> Unit = {},
) {
    val choosingVoice = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val editingProfile = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        Section(R.string.settings_profile) {
            val prof = profile
            if (prof == null) {
                Text(stringResource(R.string.settings_profile_empty), style = MaterialTheme.typography.bodySmall)
            } else {
                val weightNow = latestWeightKg ?: prof.weightKg
                Text(stringResource(R.string.settings_profile_summary, prof.name, prof.heightCm, weightNow))
                Bmi.compute(weightNow, prof.heightCm)?.let { bmi ->
                    Text(stringResource(R.string.settings_profile_bmi, bmi, ProfileInput.bmiCategory(bmi)), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = { editingProfile.value = true }) { Text(stringResource(R.string.settings_profile_edit)) }
            }
        }

        Section(R.string.settings_theme) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ThemeMode.SYSTEM to R.string.theme_system,
                    ThemeMode.LIGHT to R.string.theme_light,
                    ThemeMode.DARK to R.string.theme_dark,
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = settings.theme == mode,
                        onClick = { onChange { it.copy(theme = mode) } },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        }

        Section(R.string.settings_units) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(WeightUnit.KG to R.string.unit_kg, WeightUnit.LB to R.string.unit_lb).forEach { (u, label) ->
                    FilterChip(
                        selected = settings.weightUnit == u,
                        onClick = { onChange { it.copy(weightUnit = u) } },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(LengthUnit.CM to R.string.unit_cm, LengthUnit.IN to R.string.unit_in).forEach { (u, label) ->
                    FilterChip(
                        selected = settings.lengthUnit == u,
                        onClick = { onChange { it.copy(lengthUnit = u) } },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            Text(stringResource(R.string.settings_units_note), style = MaterialTheme.typography.bodySmall)
        }

        Section(R.string.settings_workout) {
            Text(stringResource(R.string.settings_default_rest, settings.defaultRestSeconds))
            Stepper(
                onMinus = { onChange { it.copy(defaultRestSeconds = SettingsRules.stepRest(it.defaultRestSeconds, -1)) } },
                onPlus = { onChange { it.copy(defaultRestSeconds = SettingsRules.stepRest(it.defaultRestSeconds, 1)) } },
            )
            Text(stringResource(R.string.settings_default_rest_note), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.settings_countdown, settings.defaultCountdownSeconds))
            Stepper(
                onMinus = { onChange { it.copy(defaultCountdownSeconds = SettingsRules.stepCountdown(it.defaultCountdownSeconds, -1)) } },
                onPlus = { onChange { it.copy(defaultCountdownSeconds = SettingsRules.stepCountdown(it.defaultCountdownSeconds, 1)) } },
            )
            Text(stringResource(R.string.settings_countdown_note), style = MaterialTheme.typography.bodySmall)
        }

        Section(R.string.settings_reminder) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.plan_reminder_switch), Modifier.weight(1f))
                Switch(checked = reminderPrefs.enabled, onCheckedChange = { onReminderPrefs(reminderPrefs.copy(enabled = it)) })
            }
            if (reminderPrefs.enabled) {
                Text(stringResource(R.string.plan_reminder_time, reminderPrefs.hour, reminderPrefs.minute))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ onReminderPrefs(com.kryll.homefit.domain.Planning.stepHour(reminderPrefs, -1)) }) { Text(stringResource(R.string.plan_hour_minus)) }
                    OutlinedButton({ onReminderPrefs(com.kryll.homefit.domain.Planning.stepHour(reminderPrefs, 1)) }) { Text(stringResource(R.string.plan_hour_plus)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ onReminderPrefs(com.kryll.homefit.domain.Planning.stepMinute(reminderPrefs, -5)) }) { Text(stringResource(R.string.plan_min_minus)) }
                    OutlinedButton({ onReminderPrefs(com.kryll.homefit.domain.Planning.stepMinute(reminderPrefs, 5)) }) { Text(stringResource(R.string.plan_min_plus)) }
                }
                val lead = reminderPrefs.leadMinutes
                val notifyAt = com.kryll.homefit.domain.Planning.notifyTime(reminderPrefs)
                Text(
                    if (lead == 0) stringResource(R.string.plan_reminder_lead_none)
                    else stringResource(R.string.plan_reminder_lead, lead, notifyAt.hour, notifyAt.minute),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ onReminderPrefs(com.kryll.homefit.domain.Planning.stepLead(reminderPrefs, -1)) }) { Text(stringResource(R.string.plan_lead_less)) }
                    OutlinedButton({ onReminderPrefs(com.kryll.homefit.domain.Planning.stepLead(reminderPrefs, 1)) }) { Text(stringResource(R.string.plan_lead_more)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.plan_reminder_exact), Modifier.weight(1f))
                    Switch(checked = reminderPrefs.exact, onCheckedChange = { onReminderPrefs(reminderPrefs.copy(exact = it)) })
                }
                if (reminderPrefs.exact && !reminderExactAllowed) {
                    Text(stringResource(R.string.plan_reminder_exact_denied), style = MaterialTheme.typography.bodySmall)
                }
                if (!reminderNotificationsAllowed) {
                    Text(stringResource(R.string.plan_notifications_off), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(stringResource(R.string.plan_reminder_note), style = MaterialTheme.typography.bodySmall)
            if (reminderPrefs.enabled && !reminderPrefs.exact) {
                Text(stringResource(R.string.plan_reminder_note_delay), style = MaterialTheme.typography.bodySmall)
            } else if (reminderPrefs.enabled && reminderPrefs.exact && reminderExactAllowed) {
                // Sin promesa absoluta de puntualidad; la denegación ya avisa arriba (sin duplicar).
                Text(stringResource(R.string.plan_reminder_note_exact), style = MaterialTheme.typography.bodySmall)
            }
        }

        Section(R.string.settings_audio) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.settings_voice), Modifier.weight(1f))
                Switch(checked = settings.voiceEnabled, onCheckedChange = { v -> onChange { it.copy(voiceEnabled = v) } })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.settings_voice_select), Modifier.weight(1f))
                TextButton({ choosingVoice.value = true }) {
                    Text(settings.ttsVoice ?: stringResource(R.string.settings_voice_default))
                }
            }
            Text(stringResource(R.string.settings_voice_rate, String.format(Locale("es", "ES"), "%.2f", settings.ttsRate).trimEnd('0').trimEnd(',')))
            Stepper(
                onMinus = { onChange { it.copy(ttsRate = SettingsRules.stepRate(it.ttsRate, -1)) } },
                onPlus = { onChange { it.copy(ttsRate = SettingsRules.stepRate(it.ttsRate, 1)) } },
            )
            val pct = Math.round(settings.beepVolume * 100)
            Text(if (pct == 0) stringResource(R.string.settings_beep_off) else stringResource(R.string.settings_beep_volume, pct))
            Stepper(
                onMinus = { onChange { it.copy(beepVolume = SettingsRules.stepVolume(it.beepVolume, -1)) } },
                onPlus = { onChange { it.copy(beepVolume = SettingsRules.stepVolume(it.beepVolume, 1)) } },
            )
            Text(stringResource(R.string.settings_audio_note), style = MaterialTheme.typography.bodySmall)
        }

        Section(R.string.settings_backup) {
            Text(stringResource(R.string.settings_backup_note), style = MaterialTheme.typography.bodySmall)
            // FlowRow: en pantallas estrechas o fuentes grandes los botones pasan a otra línea
            // en lugar de aplastar el último en una tira vertical.
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onExport) { Text(stringResource(R.string.backup_export)) }
                OutlinedButton(onImport) { Text(stringResource(R.string.backup_import)) }
                OutlinedButton(onExportWorkouts) { Text(stringResource(R.string.backup_export_workouts_csv)) }
            }
            if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium)
        }

        Section(R.string.settings_about) {
            UpdateInstallContent(versionName, updateState, downloadState, onCheckUpdates, onOpenUpdate, onInstallUpdate)
            Text(stringResource(R.string.health_disclaimer), style = MaterialTheme.typography.bodySmall)
        }
    }

    val profForEdit = profile
    if (editingProfile.value && profForEdit != null) {
        ProfileEditDialog(
            profile = profForEdit,
            storedEquipment = equipment,
            onDismiss = { editingProfile.value = false },
            onSave = { edited, equip -> editingProfile.value = false; onSaveProfile(edited, equip) },
        )
    }

    if (choosingVoice.value) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { choosingVoice.value = false },
            title = { Text(stringResource(R.string.settings_voice_dialog)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TextButton({
                        onChange { it.copy(ttsVoice = null) }
                        choosingVoice.value = false
                    }) { Text(stringResource(R.string.settings_voice_default)) }
                    availableVoices.forEach { v ->
                        TextButton({
                            onChange { it.copy(ttsVoice = v) }
                            choosingVoice.value = false
                        }) { Text(v) }
                    }
                }
            },
            confirmButton = {},
        )
    }

    if (pendingImport != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = onCancelImport) {
            ImportConfirmContent(pendingImport, onConfirmImport, onCancelImport)
        }
    }
}

/** Contenido del diálogo de importar; separado para poder renderizarlo y revisarlo en una captura. */
@Composable
fun ImportConfirmContent(summary: ImportSummary, onConfirm: () -> Unit, onCancel: () -> Unit) {
    androidx.compose.material3.Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.backup_import_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.backup_import_summary, summary.routines, summary.workouts, summary.scheduled, summary.measurements))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onCancel) { Text(stringResource(R.string.backup_cancel)) }
                TextButton(onConfirm) { Text(stringResource(R.string.backup_import_confirm)) }
            }
        }
    }
}

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Stepper(onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onMinus) { Text(stringResource(R.string.settings_minus)) }
        OutlinedButton(onPlus) { Text(stringResource(R.string.settings_plus)) }
    }
}

/** Panel aislado para revisar todo el flujo de actualización sin capturas recortadas. */
@Composable
fun UpdateInstallContent(
    versionName: String,
    updateState: com.kryll.homefit.domain.UpdateState,
    downloadState: com.kryll.homefit.update.DownloadUi,
    onCheckUpdates: () -> Unit = {},
    onOpenUpdate: (String) -> Unit = {},
    onInstallUpdate: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.version_label, versionName))
            OutlinedButton(onClick = onCheckUpdates, enabled = updateState !is com.kryll.homefit.domain.UpdateState.Checking) {
                Text(stringResource(R.string.updates_check))
            }
            when (val u = updateState) {
                com.kryll.homefit.domain.UpdateState.Checking -> Text(stringResource(R.string.updates_checking), style = MaterialTheme.typography.bodySmall)
                com.kryll.homefit.domain.UpdateState.UpToDate -> Text(stringResource(R.string.updates_latest), style = MaterialTheme.typography.bodySmall)
                com.kryll.homefit.domain.UpdateState.Failed -> Text(stringResource(R.string.updates_error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                is com.kryll.homefit.domain.UpdateState.Available -> {
                    Text(stringResource(R.string.updates_new, u.info.version), style = MaterialTheme.typography.bodyMedium)
                    if (u.info.apkUrl == null) Text(stringResource(R.string.updates_no_apk))
                    else when {
                        downloadState.busy -> Text(stringResource(R.string.updates_downloading, downloadState.percent?.let { "$it%" } ?: "…"))
                        downloadState.file != null -> {
                            Text(stringResource(R.string.updates_install_help), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = onInstallUpdate) { Text(stringResource(R.string.updates_install)) }
                        }
                        else -> {
                            if (downloadState.failed) Text(stringResource(R.string.updates_download_failed), color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = { onOpenUpdate(u.info.apkUrl) }) { Text(stringResource(R.string.updates_download)) }
                        }
                    }
                }
                com.kryll.homefit.domain.UpdateState.Idle -> {}
            }
    }
}
private val PROFILE_LABELS = mapOf("silla_banco" to "silla / banco", "bandas_elasticas" to "bandas elásticas", "prefiero_no_decir" to "prefiero no decir")

/** Diálogo de edición del perfil guardado. Misma validación que el onboarding ([ProfileInput.validate]). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditDialog(
    profile: UserProfile,
    storedEquipment: Set<String>,
    onDismiss: () -> Unit,
    onSave: (UserProfile, Set<String>) -> Unit,
) {
    fun trimNum(f: Float) = if (f == f.toInt().toFloat()) f.toInt().toString() else f.toString()
    val birthDate = java.time.LocalDate.ofEpochDay(profile.birthEpochDay)
    var name by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(profile.name) }
    var sex by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(profile.sex) }
    var bDay by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(birthDate.dayOfMonth.toString()) }
    var bMonth by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(birthDate.monthValue.toString()) }
    var bYear by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(birthDate.year.toString()) }
    var height by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(trimNum(profile.heightCm)) }
    var weight by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(trimNum(profile.weightKg)) }
    var goal by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(profile.goal) }
    var level by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(profile.level) }
    var days by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(profile.daysMask) }
    var minutes by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(profile.minutesPerSession.toString()) }
    var equipmentSel by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(EQUIPMENT_OPTIONS.filter { o -> o.second.all { it in storedEquipment } }.map { it.first }.toSet())
    }
    var submitted by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }

    val h = height.replace(',', '.').toFloatOrNull()
    val w = weight.replace(',', '.').toFloatOrNull()
    val birth = ProfileInput.birthEpochDay(bDay.toIntOrNull(), bMonth.toIntOrNull(), bYear.toIntOrNull())
    val errors = ProfileInput.validate(name, sex, h, w, birth, days, minutes.toIntOrNull())
    fun err(f: ProfileInput.Field) = submitted && f in errors

    @Composable
    fun editField(label: Int, value: String, onChange: (String) -> Unit, error: Boolean, type: KeyboardType) {
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text(stringResource(label)) }, isError = error,
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = type), modifier = Modifier.fillMaxWidth(),
        )
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                editField(R.string.onb_name, name, { name = it }, err(ProfileInput.Field.NAME), KeyboardType.Text)
                Text(stringResource(R.string.onb_sex))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileInput.SEXES.forEach { o -> FilterChip(selected = sex == o, onClick = { sex = o }, label = { Text(PROFILE_LABELS[o] ?: o.replace('_', ' ')) }) }
                }
                Text(stringResource(R.string.onb_birth))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = bDay, onValueChange = { bDay = it }, label = { Text(stringResource(R.string.onb_day)) }, isError = err(ProfileInput.Field.BIRTH), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = bMonth, onValueChange = { bMonth = it }, label = { Text(stringResource(R.string.onb_month)) }, isError = err(ProfileInput.Field.BIRTH), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = bYear, onValueChange = { bYear = it }, label = { Text(stringResource(R.string.onb_year)) }, isError = err(ProfileInput.Field.BIRTH), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                editField(R.string.onb_height, height, { height = it }, err(ProfileInput.Field.HEIGHT), KeyboardType.Decimal)
                editField(R.string.onb_weight, weight, { weight = it }, err(ProfileInput.Field.WEIGHT), KeyboardType.Decimal)
                Text(stringResource(R.string.onb_goal))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileInput.GOALS.forEach { o -> FilterChip(selected = goal == o, onClick = { goal = o }, label = { Text(PROFILE_LABELS[o] ?: o.replace('_', ' ')) }) }
                }
                Text(stringResource(R.string.onb_level))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("principiante", "intermedio", "avanzado").forEach { o -> FilterChip(selected = level == o, onClick = { level = o }, label = { Text(PROFILE_LABELS[o] ?: o.replace('_', ' ')) }) }
                }
                Text(stringResource(R.string.onb_days))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, d ->
                        FilterChip(selected = days and (1 shl i) != 0, onClick = { days = days xor (1 shl i) }, label = { Text(d) })
                    }
                }
                if (err(ProfileInput.Field.DAYS)) Text(stringResource(R.string.onb_days), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                editField(R.string.onb_minutes, minutes, { minutes = it }, err(ProfileInput.Field.MINUTES), KeyboardType.Number)
                Text(stringResource(R.string.onb_equipment))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EQUIPMENT_OPTIONS.map { it.first }.forEach { o ->
                        FilterChip(selected = o in equipmentSel, onClick = { equipmentSel = if (o in equipmentSel) equipmentSel - o else equipmentSel + o }, label = { Text(PROFILE_LABELS[o] ?: o.replace('_', ' ')) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton({
                submitted = true
                if (errors.isEmpty()) {
                    val expanded = EQUIPMENT_OPTIONS.filter { it.first in equipmentSel }.flatMap { it.second }.toSet()
                    onSave(UserProfile(1, name.trim(), sex, birth!!, h!!, w!!, goal, level, days, minutes.toInt()), expanded)
                }
            }) { Text(stringResource(R.string.profile_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) } },
    )
}
