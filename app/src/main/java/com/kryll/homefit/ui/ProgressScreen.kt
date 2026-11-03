package com.kryll.homefit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.kryll.homefit.domain.ProgressCharts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kryll.homefit.R
import com.kryll.homefit.domain.ProgressSummary
import com.kryll.homefit.domain.SessionRow
import com.kryll.homefit.domain.SetRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProgressContent(
    summary: ProgressSummary,
    sessions: List<SessionRow>,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.progress_title), style = MaterialTheme.typography.headlineSmall) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(stringResource(R.string.progress_sessions), summary.totalSessions.toString(), Modifier.weight(1f))
                    Stat(stringResource(R.string.progress_week), summary.weekSessions.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(stringResource(R.string.progress_streak), summary.streakDays.toString(), Modifier.weight(1f))
                    Stat(stringResource(R.string.progress_minutes), summary.totalMinutes.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(stringResource(R.string.progress_sets), summary.totalSets.toString(), Modifier.weight(1f))
                    Stat(
                        stringResource(R.string.progress_rpe),
                        summary.avgRpe?.let { String.format(Locale("es", "ES"), "%.1f", it) } ?: stringResource(R.string.progress_rpe_none),
                        Modifier.weight(1f),
                    )
                }
            }
        }
        item { WeeklyChart(sessions, zone) }
        item { ExerciseChart(sessions, zone) }
        item { Text(stringResource(R.string.progress_history), style = MaterialTheme.typography.titleMedium) }
        if (sessions.isEmpty()) {
            item { Text(stringResource(R.string.progress_empty), style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(sessions, key = { it.id }) { SessionCard(it, zone) }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SessionCard(s: SessionRow, zone: ZoneId) {
    var open by rememberSaveable(s.id) { mutableStateOf(false) }
    val date = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale("es", "ES")).format(Instant.ofEpochMilli(s.startMillis).atZone(zone))
    Card(Modifier.fillMaxWidth().clickable { open = !open }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(s.routineName ?: stringResource(R.string.progress_free_workout), style = MaterialTheme.typography.titleSmall)
            Text(date, style = MaterialTheme.typography.bodySmall)
            Text(androidx.compose.ui.platform.LocalContext.current.resources.getQuantityString(R.plurals.progress_session_summary, s.sets.size, s.durationSeconds / 60, s.sets.size), style = MaterialTheme.typography.bodyMedium)
            if (s.incomplete) {
                Text(stringResource(R.string.progress_incomplete), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
            if (open) {
                if (s.sets.isEmpty()) Text(stringResource(R.string.progress_no_sets), style = MaterialTheme.typography.bodySmall)
                s.sets.forEach { Text(setText(it), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun setText(r: SetRow): String {
    val base = if (r.seconds != null) stringResource(R.string.progress_set_seconds, r.exerciseName, r.setIndex, r.seconds)
    else stringResource(R.string.progress_set_reps, r.exerciseName, r.setIndex, r.reps ?: 0)
    return base + (r.rpe?.let { " · " + stringResource(R.string.progress_set_rpe, it) } ?: "")
}

@Composable
private fun WeeklyChart(sessions: List<SessionRow>, zone: ZoneId) {
    val weeks = ProgressCharts.weeklySessions(sessions, java.time.LocalDate.now(zone), zone)
    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale("es", "ES"))
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val desc = weeks.joinToString("; ") { ctx.getString(R.string.charts_weekly_desc, fmt.format(it.first), it.second) }
    val bar = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outline
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.charts_weekly_title), style = MaterialTheme.typography.titleMedium)
            if (sessions.isEmpty()) Text(stringResource(R.string.charts_weekly_empty), style = MaterialTheme.typography.bodyMedium)
            else {
                val max = weeks.maxOf { it.second }.coerceAtLeast(1)
                androidx.compose.foundation.Canvas(
                    Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = desc },
                ) {
                    val base = size.height - 4.dp.toPx()
                    val slot = size.width / weeks.size
                    val w = slot * 0.6f
                    drawLine(axis, androidx.compose.ui.geometry.Offset(0f, base), androidx.compose.ui.geometry.Offset(size.width, base), strokeWidth = 1.dp.toPx())
                    weeks.forEachIndexed { i, (_, n) ->
                        val h = (base - 8.dp.toPx()) * n / max
                        drawRect(bar, androidx.compose.ui.geometry.Offset(i * slot + (slot - w) / 2, base - h), androidx.compose.ui.geometry.Size(w, h))
                    }
                }
                val short = DateTimeFormatter.ofPattern("d/M", Locale("es", "ES"))
                Row(Modifier.fillMaxWidth()) {
                    weeks.forEach { (d, n) ->
                        Column(Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            Text(n.toString(), style = MaterialTheme.typography.labelLarge)
                            Text(short.format(d), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text(stringResource(R.string.charts_weekly_note), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ExerciseChart(sessions: List<SessionRow>, zone: ZoneId) {
    val names = remember(sessions) { ProgressCharts.exercisesWithData(sessions) }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val name = picked?.takeIf { it in names } ?: names.firstOrNull()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.charts_exercise_title), style = MaterialTheme.typography.titleMedium)
            if (name == null) {
                Text(stringResource(R.string.charts_exercise_empty), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEach { n -> FilterChip(selected = n == name, onClick = { picked = n }, label = { Text(n) }) }
            }
            val series = ProgressCharts.exerciseSeries(sessions, name, zone) ?: return@Column
            val unit = stringResource(if (series.seconds) R.string.charts_unit_seconds else R.string.charts_unit_reps)
            if (series.points.size < 2) {
                Text(stringResource(R.string.charts_exercise_need_two, series.points.first().best, unit), style = MaterialTheme.typography.bodyMedium)
            } else {
                val pts = series.points.map { it.epochDay to it.best.toFloat() }
                val fmt = DateTimeFormatter.ofPattern("d MMM", Locale("es", "ES"))
                val desc = series.points.joinToString("; ") { fmt.format(java.time.LocalDate.ofEpochDay(it.epochDay)) + ": " + it.best + " " + unit }
                LineChart(pts, Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = desc })
                series.points.forEach {
                    Text(
                        it.weightKg?.let { w ->
                            stringResource(
                                R.string.charts_exercise_point_weight, fmt.format(java.time.LocalDate.ofEpochDay(it.epochDay)), it.best, unit,
                                java.lang.String.format(Locale("es", "ES"), "%.1f", w).trimEnd('0').trimEnd(','),
                            )
                        } ?: stringResource(R.string.charts_exercise_point, fmt.format(java.time.LocalDate.ofEpochDay(it.epochDay)), it.best, unit),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    stringResource(R.string.charts_exercise_range, series.points.first().best, series.points.last().best, unit, series.points.size),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(stringResource(R.string.charts_exercise_note), style = MaterialTheme.typography.bodySmall)
            val loads = ProgressCharts.weightSeries(sessions, name, zone)
            Text(stringResource(R.string.charts_load_title), style = MaterialTheme.typography.titleSmall)
            if (loads.size < 2) {
                Text(stringResource(R.string.charts_load_empty), style = MaterialTheme.typography.bodySmall)
            } else {
                val fmt = DateTimeFormatter.ofPattern("d MMM", Locale("es", "ES"))
                val desc = loads.joinToString("; ") { fmt.format(java.time.LocalDate.ofEpochDay(it.first)) + ": " + it.second + " kg" }
                LineChart(loads, Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = desc })
                loads.forEach { (day, kg) ->
                    Text(stringResource(R.string.charts_load_point, fmt.format(java.time.LocalDate.ofEpochDay(day)), java.lang.String.format(Locale("es", "ES"), "%.1f", kg)), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.charts_load_note), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
