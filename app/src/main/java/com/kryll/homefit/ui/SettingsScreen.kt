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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kryll.homefit.R
import com.kryll.homefit.data.AppSettings
import com.kryll.homefit.data.LengthUnit
import com.kryll.homefit.data.ThemeMode
import com.kryll.homefit.data.WeightUnit
import com.kryll.homefit.domain.SettingsRules
import java.util.Locale

/** Resumen de una copia pendiente de confirmar. */
data class ImportSummary(val routines: Int, val workouts: Int, val scheduled: Int, val measurements: Int)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    versionName: String,
    message: String?,
    pendingImport: ImportSummary?,
    availableVoices: List<String> = emptyList(),
    onChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
    onConfirmImport: () -> Unit = {},
    onCancelImport: () -> Unit = {},
    updateState: com.kryll.homefit.domain.UpdateState = com.kryll.homefit.domain.UpdateState.Idle,
    onCheckUpdates: () -> Unit = {},
    onOpenUpdate: (String) -> Unit = {},
    downloadState: com.kryll.homefit.update.DownloadUi = com.kryll.homefit.update.DownloadUi(),
    onInstallUpdate: () -> Unit = {},
) {
    val choosingVoice = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onExport) { Text(stringResource(R.string.backup_export)) }
                OutlinedButton(onImport) { Text(stringResource(R.string.backup_import)) }
            }
            if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium)
        }

        Section(R.string.settings_about) {
            UpdateInstallContent(versionName, updateState, downloadState, onCheckUpdates, onOpenUpdate, onInstallUpdate)
            Text(stringResource(R.string.health_disclaimer), style = MaterialTheme.typography.bodySmall)
        }
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
