package com.kryll.homefit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kryll.homefit.R
import com.kryll.homefit.data.Routine
import com.kryll.homefit.data.ScheduledSession
import com.kryll.homefit.data.WorkoutRepository
import com.kryll.homefit.data.WorkoutSession
import com.kryll.homefit.domain.Planning
import com.kryll.homefit.domain.ReminderPrefs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val ES = Locale("es", "ES")

fun dayLabel(d: LocalDate): String {
    val dow = d.dayOfWeek.getDisplayName(TextStyle.SHORT, ES).replaceFirstChar { it.uppercase() }
    return "$dow ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, ES)}"
}

/** Semana de siete días desde hoy: qué rutina toca cada día, y el recordatorio diario. */
@Composable
fun PlanScreen(
    today: LocalDate,
    zone: ZoneId,
    daysMask: Int,
    routines: List<Routine>,
    scheduled: List<ScheduledSession>,
    workouts: List<WorkoutSession>,
    prefs: ReminderPrefs,
    notificationsAllowed: Boolean,
    onAssign: (LocalDate, Long?) -> Unit = { _, _ -> },
    onAuto: () -> Unit = {},
    onClear: () -> Unit = {},
    onStart: (Long) -> Unit = {},
    onPrefs: (ReminderPrefs) -> Unit = {},
) {
    var assigning by rememberSaveable { mutableStateOf<Long?>(null) } // epochDay del día que se está editando
    val names = routines.associate { it.id to it.name }
    fun dateOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val days = (0 until 7).map { today.plusDays(it.toLong()) }

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.plan_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp)) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.plan_reminder_title), style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.plan_reminder_switch), Modifier.weight(1f))
                        Switch(checked = prefs.enabled, onCheckedChange = { onPrefs(prefs.copy(enabled = it)) })
                    }
                    if (prefs.enabled) {
                        Text(stringResource(R.string.plan_reminder_time, prefs.hour, prefs.minute))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ onPrefs(Planning.stepHour(prefs, -1)) }) { Text(stringResource(R.string.plan_hour_minus)) }
                            OutlinedButton({ onPrefs(Planning.stepHour(prefs, 1)) }) { Text(stringResource(R.string.plan_hour_plus)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ onPrefs(Planning.stepMinute(prefs, -5)) }) { Text(stringResource(R.string.plan_min_minus)) }
                            OutlinedButton({ onPrefs(Planning.stepMinute(prefs, 5)) }) { Text(stringResource(R.string.plan_min_plus)) }
                        }
                        if (!notificationsAllowed) {
                            Text(stringResource(R.string.plan_notifications_off), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(stringResource(R.string.plan_reminder_note), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onAuto, enabled = routines.isNotEmpty()) { Text(stringResource(R.string.plan_auto)) }
                OutlinedButton(onClear) { Text(stringResource(R.string.plan_clear)) }
            }
            if (routines.isEmpty()) Text(stringResource(R.string.plan_no_routines), style = MaterialTheme.typography.bodySmall)
        }
        items(days, key = { it.toEpochDay() }) { d ->
            val entries = scheduled.filter { dateOf(it.startMillis) == d }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val mark = if (d == today) " · " + stringResource(R.string.plan_today_mark) else ""
                    Text(dayLabel(d) + mark, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (Planning.isTrainingDay(daysMask, d)) R.string.plan_training_day else R.string.plan_rest_day),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (entries.isEmpty()) Text(stringResource(R.string.plan_nothing))
                    entries.forEach { e ->
                        val name = e.routineId?.let { names[it] } ?: ""
                        val done = workouts.any { w -> w.routineId == e.routineId && w.notes != WorkoutRepository.NOTE_INCOMPLETE && dateOf(w.startMillis) == d }
                        Text(if (done) name + " · " + stringResource(R.string.plan_done) else name, style = MaterialTheme.typography.bodyLarge)
                        if (d == today && !done && e.routineId != null) {
                            Button({ onStart(e.routineId) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.plan_train)) }
                        }
                    }
                    TextButton({ assigning = d.toEpochDay() }) { Text(stringResource(R.string.plan_assign)) }
                }
            }
        }
    }

    assigning?.let { epoch ->
        val d = LocalDate.ofEpochDay(epoch)
        AlertDialog(
            onDismissRequest = { assigning = null },
            title = { Text(stringResource(R.string.plan_assign_title, dayLabel(d))) },
            text = {
                LazyColumn {
                    item { TextButton({ onAssign(d, null); assigning = null }) { Text(stringResource(R.string.plan_assign_none)) } }
                    items(routines, key = { it.id }) { r ->
                        TextButton({ onAssign(d, r.id); assigning = null }) { Text(r.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ assigning = null }) { Text(stringResource(R.string.plan_cancel)) } },
        )
    }
}
