package com.kryll.homefit.ui

import androidx.compose.foundation.clickable
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
            Text(stringResource(R.string.progress_session_summary, s.durationSeconds / 60, s.sets.size), style = MaterialTheme.typography.bodyMedium)
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
