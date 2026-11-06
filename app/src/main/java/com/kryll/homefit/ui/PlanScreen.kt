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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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

/** Calendario semanal/mensual navegable con detalle del día y recordatorio diario. */
@Composable
fun PlanScreen(
    today: LocalDate,
    zone: ZoneId,
    daysMask: Int,
    routines: List<Routine>,
    scheduled: List<ScheduledSession>,
    workouts: List<WorkoutSession>,
    message: String? = null,
    onAssign: (LocalDate, Long?) -> Unit = { _, _ -> },
    onAuto: () -> Unit = {},
    onClear: () -> Unit = {},
    onStart: (Long) -> Unit = {},
    onSkip: (ScheduledSession) -> Unit = {},
    onMove: (ScheduledSession, LocalDate) -> Unit = { _, _ -> },
) {
    var assigning by rememberSaveable { mutableStateOf<Long?>(null) } // epochDay del día que se está editando
    var moving by rememberSaveable { mutableStateOf<Long?>(null) } // id de la sesión que se está moviendo
    val names = routines.associate { it.id to it.name }
    fun dateOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    var monthly by rememberSaveable { mutableStateOf(false) }
    var anchorEpoch by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val anchor = LocalDate.ofEpochDay(anchorEpoch)
    val month = java.time.YearMonth.from(anchor)
    val weekStart = com.kryll.homefit.domain.CalendarPeriod.weekStart(anchor)
    val days = if (monthly) listOf(anchor) else (0 until 7).map { weekStart.plusDays(it.toLong()) }

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.plan_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilterChip(!monthly, { monthly = false }, { Text(stringResource(R.string.calendar_week)) })
                androidx.compose.material3.FilterChip(monthly, { monthly = true }, { Text(stringResource(R.string.calendar_month)) })
                TextButton({ anchorEpoch = today.toEpochDay() }) { Text(stringResource(R.string.calendar_today)) }
            }
            Text(if (monthly) month.month.getDisplayName(TextStyle.FULL, ES).replaceFirstChar { it.uppercase() } + " ${month.year}"
                else dayLabel(weekStart) + " - " + dayLabel(weekStart.plusDays(6)) + " ${weekStart.year}", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ anchorEpoch = (if (monthly) anchor.minusMonths(1) else anchor.minusWeeks(1)).toEpochDay() }) { Text(stringResource(R.string.calendar_previous)) }
                OutlinedButton({ anchorEpoch = (if (monthly) anchor.plusMonths(1) else anchor.plusWeeks(1)).toEpochDay() }) { Text(stringResource(R.string.calendar_next)) }
            }
            if (monthly) MonthCalendar(month, anchor, scheduled.map { dateOf(it.startMillis) }.toSet()) { anchorEpoch = it.toEpochDay() }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onAuto, enabled = routines.isNotEmpty() && daysMask != 0) { Text(stringResource(R.string.plan_auto)) }
                    OutlinedButton(onClear) { Text(stringResource(R.string.plan_clear)) }
                }
                Text(stringResource(R.string.calendar_auto_hint, dayLabel(today), dayLabel(today.plusDays(6))), style = MaterialTheme.typography.bodySmall)
                if (routines.isEmpty()) Text(stringResource(R.string.plan_no_routines), style = MaterialTheme.typography.bodySmall)
                if (daysMask == 0) Text(stringResource(R.string.plan_no_training_days), style = MaterialTheme.typography.bodySmall)
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
            }
        }
        items(days, key = { it.toEpochDay() }) { d ->
            val entries = scheduled.filter { dateOf(it.startMillis) == d }
            val training = Planning.isTrainingDay(daysMask, d)
            Card(
                Modifier.fillMaxWidth(),
                colors = if (training) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) else CardDefaults.cardColors(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val mark = if (d == today) " · " + stringResource(R.string.plan_today_mark) else ""
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(dayLabel(d) + mark, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Surface(
                            color = if (training) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = androidx.compose.foundation.shape.CircleShape,
                        ) {
                            Text(
                                stringResource(if (training) R.string.plan_training_day else R.string.plan_rest_day),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (training) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                    if (entries.isEmpty()) Text(stringResource(R.string.plan_nothing))
                    entries.forEach { e ->
                        val name = e.routineId?.let { names[it] } ?: ""
                        val status = com.kryll.homefit.domain.sessionStatusOf(e.status)
                        val done = status == com.kryll.homefit.domain.SessionStatus.COMPLETED ||
                            workouts.any { w -> w.routineId == e.routineId && w.notes != WorkoutRepository.NOTE_INCOMPLETE && dateOf(w.startMillis) == d }
                        val suffix = when {
                            done -> " · " + stringResource(R.string.plan_done)
                            status == com.kryll.homefit.domain.SessionStatus.SKIPPED -> " · " + stringResource(R.string.plan_skipped)
                            status == com.kryll.homefit.domain.SessionStatus.MOVED -> " · " + stringResource(R.string.plan_moved)
                            else -> ""
                        }
                        Text(name + suffix, style = MaterialTheme.typography.bodyLarge)
                        val settled = done || status != com.kryll.homefit.domain.SessionStatus.SCHEDULED
                        if (d == today && !settled && e.routineId != null) {
                            Button({ onStart(e.routineId) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.plan_train)) }
                        }
                        if (!settled) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton({ onSkip(e) }) { Text(stringResource(R.string.plan_skip)) }
                                TextButton({ moving = e.id }) { Text(stringResource(R.string.plan_move)) }
                            }
                        }
                    }
                    TextButton({ assigning = d.toEpochDay() }) { Text(stringResource(R.string.plan_assign)) }
                }
            }
        }
    }

    moving?.let { id ->
        val e = scheduled.firstOrNull { it.id == id }
        if (e != null) {
            val from = dateOf(e.startMillis)
            AlertDialog(
                onDismissRequest = { moving = null },
                title = { Text(stringResource(R.string.plan_move_title, dayLabel(from))) },
                text = {
                    LazyColumn {
                        items((0 until 14).map { today.plusDays(it.toLong()) }.filter { it != from }, key = { it.toEpochDay() }) { nd ->
                            TextButton({ onMove(e, nd); moving = null }) { Text(stringResource(R.string.plan_move_to, dayLabel(nd))) }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton({ moving = null }) { Text(stringResource(R.string.plan_cancel)) } },
            )
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
