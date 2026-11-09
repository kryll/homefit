package com.kryll.homefit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kryll.homefit.R
import com.kryll.homefit.domain.CalendarPeriod
import java.time.LocalDate
import java.time.YearMonth

/** Fechas reales, lunes primero; los puntos indican sesiones planificadas, no resultados. */
@Composable
fun MonthCalendar(month: YearMonth, selected: LocalDate, planned: Set<LocalDate>, onSelect: (LocalDate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf(R.string.calendar_mon, R.string.calendar_tue, R.string.calendar_wed, R.string.calendar_thu,
                R.string.calendar_fri, R.string.calendar_sat, R.string.calendar_sun).forEach {
                Text(stringResource(it), Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        CalendarPeriod.monthCells(month).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    if (day == null) Spacer(Modifier.weight(1f).height(48.dp))
                    else TextButton(onClick = { onSelect(day) }, modifier = Modifier.weight(1f).height(48.dp).semantics { contentDescription = "Fecha $day" },
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = if (day == selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            containerColor = if (day == selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)) {
                        Text(day.dayOfMonth.toString() + if (day in planned) "•" else "", maxLines = 1)
                    }
                }
            }
        }
        Text(stringResource(R.string.calendar_dot_hint), style = MaterialTheme.typography.bodySmall)
    }
}
