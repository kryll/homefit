package com.kryll.homefit.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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

/** Calendario semanal/mensual navegable: franja compacta de la semana y detalle del día seleccionado. */
@OptIn(ExperimentalLayoutApi::class)
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
    onAuto: (java.time.LocalDate) -> Unit = {},
    onClear: (java.time.LocalDate) -> Unit = {},
    onStart: (Long) -> Unit = {},
    onSkip: (ScheduledSession) -> Unit = {},
    onMove: (ScheduledSession, LocalDate) -> Unit = { _, _ -> },
    onAutoMonth: (java.time.YearMonth) -> Unit = {},
) {
    var assigning by rememberSaveable { mutableStateOf<Long?>(null) } // epochDay del día que se está editando
    var moving by rememberSaveable { mutableStateOf<Long?>(null) } // id de la sesión que se está moviendo
    val names = routines.associate { it.id to it.name }
    fun dateOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun isDone(e: ScheduledSession, d: LocalDate) =
        e.status == "COMPLETED" ||
            workouts.any { w -> w.routineId == e.routineId && w.notes != WorkoutRepository.NOTE_INCOMPLETE && dateOf(w.startMillis) == d }
    var monthly by rememberSaveable { mutableStateOf(false) }
    var anchorEpoch by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var selectedEpoch by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val anchor = LocalDate.ofEpochDay(anchorEpoch)
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    val month = java.time.YearMonth.from(anchor)
    val weekStart = com.kryll.homefit.domain.CalendarPeriod.weekStart(anchor)
    val weekEnd = weekStart.plusDays(6)
    // La selección sigue a la semana visible (a hoy si está en ella; si no, al lunes).
    LaunchedEffect(weekStart, monthly) {
        if (!monthly && (selected.isBefore(weekStart) || selected.isAfter(weekEnd))) {
            selectedEpoch = (if (!today.isBefore(weekStart) && !today.isAfter(weekEnd)) today else weekStart).toEpochDay()
        }
    }

    @Composable
    fun dayCard(d: LocalDate) {
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
                    val dayDone = entries.any { isDone(it, d) }
                    Surface(
                        color = when {
                            dayDone -> MaterialTheme.colorScheme.tertiaryContainer
                            training -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape,
                    ) {
                        Text(
                            stringResource(if (dayDone) R.string.plan_done else if (training) R.string.plan_training_day else R.string.plan_rest_day),
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                dayDone -> MaterialTheme.colorScheme.onTertiaryContainer
                                training -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                if (entries.isEmpty()) Text(stringResource(R.string.plan_nothing))
                entries.forEach { e ->
                    val name = e.routineId?.let { names[it] } ?: ""
                    val status = com.kryll.homefit.domain.sessionStatusOf(e.status)
                    val done = isDone(e, d)
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

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(if (monthly) "Tu mes" else stringResource(R.string.plan_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilterChip(!monthly, { monthly = false }, { Text(stringResource(R.string.calendar_week)) })
                androidx.compose.material3.FilterChip(monthly, { monthly = true }, { Text(stringResource(R.string.calendar_month)) })
                TextButton({ anchorEpoch = today.toEpochDay() }) { Text(stringResource(R.string.calendar_today)) }
            }
            Text(if (monthly) month.month.getDisplayName(TextStyle.FULL, ES).replaceFirstChar { it.uppercase() } + " ${month.year}"
                else dayLabel(weekStart) + " - " + dayLabel(weekEnd) + " ${weekStart.year}", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ anchorEpoch = (if (monthly) anchor.minusMonths(1) else anchor.minusWeeks(1)).toEpochDay() }) { Text(stringResource(R.string.calendar_previous)) }
                OutlinedButton({ anchorEpoch = (if (monthly) anchor.plusMonths(1) else anchor.plusWeeks(1)).toEpochDay() }) { Text(stringResource(R.string.calendar_next)) }
            }
            if (monthly) MonthCalendar(month, anchor, scheduled.map { dateOf(it.startMillis) }.toSet()) { anchorEpoch = it.toEpochDay() }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button({ if (monthly) onAutoMonth(month) else onAuto(weekStart) }) {
                        Text(if (monthly) "Generar este mes" else stringResource(R.string.plan_auto))
                    }
                    // Vaciar el mes no forma parte de esta acción: se conserva la limpieza semanal.
                    if (!monthly) OutlinedButton({ onClear(weekStart) }) { Text(stringResource(R.string.plan_clear)) }
                }
                Text(if (monthly) "${month.month.getDisplayName(TextStyle.FULL, ES)} ${month.year}: solo huecos desde hoy, sin cambiar tu plan ni el historial."
                    else stringResource(R.string.calendar_auto_hint, dayLabel(weekStart), dayLabel(weekEnd)), style = MaterialTheme.typography.bodySmall)
                if (daysMask == 0 && message != stringResource(R.string.plan_no_training_days)) Text(stringResource(R.string.plan_no_training_days), style = MaterialTheme.typography.bodySmall)
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                if (routines.isEmpty()) Text(stringResource(R.string.plan_no_routines), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!monthly) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0 until 7).map { weekStart.plusDays(it.toLong()) }.forEach { d ->
                        val entriesD = scheduled.filter { dateOf(it.startMillis) == d }
                        val done = entriesD.any { isDone(it, d) }
                        val pending = entriesD.any { com.kryll.homefit.domain.sessionStatusOf(it.status) == com.kryll.homefit.domain.SessionStatus.SCHEDULED && !isDone(it, d) }
                        val isToday = d == today
                        val isSel = d == selected
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSel) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                )
                                .border(
                                    width = if (isToday) 2.dp else 0.dp,
                                    color = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .clickable { selectedEpoch = d.toEpochDay() }
                                .semantics { contentDescription = dayLabel(d) }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                d.dayOfWeek.getDisplayName(TextStyle.NARROW, ES).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${d.dayOfMonth}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isToday || isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                                when {
                                    done -> Text("✓", color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
                                    pending -> Box(Modifier.size(7.dp).clip(CircleShape).background(if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary))
                                    else -> Box(Modifier.size(4.dp).clip(CircleShape).background(if (isSel) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant))
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("● " + stringResource(R.string.plan_training_day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("✓ " + stringResource(R.string.plan_done), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    Text("· " + stringResource(R.string.plan_rest_day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { dayCard(if (monthly) anchor else selected) }
    }

    moving?.let { id ->
        val e = scheduled.firstOrNull { it.id == id }
        if (e != null) {
            val from = dateOf(e.startMillis)
            androidx.compose.ui.window.Dialog(onDismissRequest = { moving = null }) {
                MoveSessionContent(from, today, scheduled.filter { it.id != e.id && (it.status == "SCHEDULED" || it.status == "COMPLETED") }
                    .map { dateOf(it.startMillis) }.toSet() + workouts.filter { it.notes != WorkoutRepository.NOTE_INCOMPLETE }.map { dateOf(it.startMillis) },
                    routineName = e.routineId?.let { names[it] }.orEmpty(),
                    time = Instant.ofEpochMilli(e.startMillis).atZone(zone).toLocalTime().toString(),
                    onConfirm = { nd -> onMove(e, nd); moving = null }, onCancel = { moving = null })
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

/** Selector reutilizable: fecha libre, conflicto visible y confirmación explícita antes de mover. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoveSessionContent(from: LocalDate, today: LocalDate, occupied: Set<LocalDate>, routineName: String,
    time: String, onConfirm: (LocalDate) -> Unit, onCancel: () -> Unit) {
    var monthEpoch by rememberSaveable(from) { mutableStateOf(maxOf(from, today).withDayOfMonth(1).toEpochDay()) }
    var selectedEpoch by rememberSaveable(from) { mutableStateOf<Long?>(null) }
    val month = java.time.YearMonth.from(LocalDate.ofEpochDay(monthEpoch))
    val selected = selectedEpoch?.let(LocalDate::ofEpochDay)
    val error = when {
        selected == null -> null
        selected < today -> "Elige hoy o una fecha futura."
        selected == from -> "Elige un día diferente al de origen."
        selected in occupied -> "Ese día ya tiene un entreno. Elige otro día; no se sobrescribirá."
        else -> null
    }
    Surface(shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp - 48).coerceAtLeast(200).dp)
            .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mover entreno", style = MaterialTheme.typography.titleLarge)
            Text("$routineName · $time")
            Text("Desde ${dayLabel(from)} ${from.year}")
            Text("${month.month.getDisplayName(TextStyle.FULL, ES)} ${month.year}", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ monthEpoch = month.minusMonths(1).atDay(1).toEpochDay() }) { Text("Mes anterior") }
                OutlinedButton({ monthEpoch = month.plusMonths(1).atDay(1).toEpochDay() }) { Text("Mes siguiente") }
            }
            MonthCalendar(month, selected ?: from, occupied) { selectedEpoch = it.toEpochDay() }
            selected?.let { Text("Destino: ${dayLabel(it)} ${it.year} · $time") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onCancel) { Text("Cancelar") }
                Button({ selected?.let(onConfirm) }, enabled = selected != null && error == null) { Text("Confirmar traslado") }
            }
        }
    }
}
