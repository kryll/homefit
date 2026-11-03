package com.kryll.homefit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.kryll.homefit.data.ThemeMode
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
    onChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
    onConfirmImport: () -> Unit = {},
    onCancelImport: () -> Unit = {},
) {
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

        Section(R.string.settings_audio) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.settings_voice), Modifier.weight(1f))
                Switch(checked = settings.voiceEnabled, onCheckedChange = { v -> onChange { it.copy(voiceEnabled = v) } })
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
            Text(stringResource(R.string.version_label, versionName))
            Text(stringResource(R.string.health_disclaimer), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.settings_units_note), style = MaterialTheme.typography.bodySmall)
        }
    }

    if (pendingImport != null) {
        AlertDialog(
            onDismissRequest = onCancelImport,
            title = { Text(stringResource(R.string.backup_import_title)) },
            text = {
                Text(stringResource(R.string.backup_import_summary, pendingImport.routines, pendingImport.workouts, pendingImport.scheduled, pendingImport.measurements))
            },
            confirmButton = { TextButton(onConfirmImport) { Text(stringResource(R.string.backup_import_confirm)) } },
            dismissButton = { TextButton(onCancelImport) { Text(stringResource(R.string.backup_cancel)) } },
        )
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
