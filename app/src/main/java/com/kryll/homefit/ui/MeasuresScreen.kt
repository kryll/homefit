package com.kryll.homefit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kryll.homefit.R
import com.kryll.homefit.data.BodyMeasurement
import com.kryll.homefit.domain.MeasureField
import com.kryll.homefit.domain.MeasureInput
import com.kryll.homefit.domain.MeasureResult
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val es = Locale("es", "ES")
private fun num(v: Float): String = String.format(es, "%.1f", v).removeSuffix(",0")

private fun label(f: MeasureField): Int = when (f) {
    MeasureField.WEIGHT -> R.string.measure_weight
    MeasureField.WAIST -> R.string.measure_waist
    MeasureField.CHEST -> R.string.measure_chest
    MeasureField.ARM -> R.string.measure_arm
    MeasureField.THIGH -> R.string.measure_thigh
    MeasureField.HIP -> R.string.measure_hip
    MeasureField.FAT -> R.string.measure_fat
}

/** Pesos en orden cronológico (un valor por registro con peso). */
fun weightSeries(items: List<BodyMeasurement>): List<Float> =
    items.sortedWith(compareBy({ it.epochDay }, { it.id })).mapNotNull { it.weightKg }

@Composable
fun MeasuresContent(
    items: List<BodyMeasurement>,
    today: Long,
    onSave: (BodyMeasurement) -> Unit,
) {
    val texts = remember { mutableStateMapOf<MeasureField, String>() }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    val errEmpty = stringResource(R.string.measures_err_empty)
    val errInvalidFmt = stringResource(R.string.measures_err_invalid)
    val labels = MeasureField.entries.associateWith { stringResource(label(it)) }
    val series = weightSeries(items)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.measures_title), style = MaterialTheme.typography.headlineSmall) }
        item { Text(stringResource(R.string.measures_hint), style = MaterialTheme.typography.bodySmall) }
        items(MeasureField.entries.toList(), key = { it.name }) { f ->
            OutlinedTextField(
                value = texts[f].orEmpty(),
                onValueChange = { texts[f] = it; saved = false; error = null },
                label = { Text(labels.getValue(f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = {
                        when (val r = MeasureInput.build(today, texts.toMap())) {
                            is MeasureResult.Ok -> { onSave(r.measurement); texts.clear(); error = null; saved = true }
                            is MeasureResult.Invalid -> { error = errInvalidFmt.format(labels.getValue(r.field)); saved = false }
                            MeasureResult.Empty -> { error = errEmpty; saved = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.measures_save)) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (saved) Text(stringResource(R.string.measures_saved), style = MaterialTheme.typography.bodySmall)
            }
        }
        item { Text(stringResource(R.string.measures_chart_title), style = MaterialTheme.typography.titleMedium) }
        item {
            if (series.size < 2) Text(stringResource(R.string.measures_chart_need_two), style = MaterialTheme.typography.bodyMedium)
            else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LineChart(series, Modifier.fillMaxWidth().height(160.dp))
                    Text(
                        num(series.min()) + " – " + num(series.max()) + " kg",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item { Text(stringResource(R.string.measures_history), style = MaterialTheme.typography.titleMedium) }
        if (items.isEmpty()) {
            item { Text(stringResource(R.string.measures_empty), style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(items, key = { it.id }) { m -> MeasureCard(m) }
        }
    }
}

@Composable
private fun MeasureCard(m: BodyMeasurement) {
    val date = DateTimeFormatter.ofPattern("d MMM yyyy", es).format(LocalDate.ofEpochDay(m.epochDay))
    val parts = buildList {
        m.weightKg?.let { add(stringResource(R.string.measures_item_weight, num(it))) }
        m.waistCm?.let { add(stringResource(R.string.measures_item_waist, num(it))) }
        m.chestCm?.let { add(stringResource(R.string.measures_item_chest, num(it))) }
        m.armCm?.let { add(stringResource(R.string.measures_item_arm, num(it))) }
        m.thighCm?.let { add(stringResource(R.string.measures_item_thigh, num(it))) }
        m.hipCm?.let { add(stringResource(R.string.measures_item_hip, num(it))) }
        m.bodyFatPct?.let { add(stringResource(R.string.measures_item_fat, num(it))) }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(date, style = MaterialTheme.typography.titleSmall)
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun LineChart(values: List<Float>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        val pad = 12.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        val lo = values.min()
        val hi = values.max()
        val span = if (hi - lo < 0.01f) 1f else hi - lo
        drawLine(axis, Offset(pad, size.height - pad), Offset(size.width - pad, size.height - pad), strokeWidth = 1.dp.toPx())
        val pts = values.mapIndexed { i, v ->
            Offset(pad + w * i / (values.size - 1), pad + h * (1f - (v - lo) / span))
        }
        pts.zipWithNext().forEach { (a, b) -> drawLine(line, a, b, strokeWidth = 3.dp.toPx()) }
        pts.forEach { drawCircle(line, radius = 4.dp.toPx(), center = it) }
    }
}

@Composable
fun ProgressWithMeasures(
    summary: com.kryll.homefit.domain.ProgressSummary,
    rows: List<com.kryll.homefit.domain.SessionRow>,
    measurements: List<BodyMeasurement>,
    onSave: (BodyMeasurement) -> Unit,
) {
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        androidx.compose.material3.TabRow(selectedTabIndex = tab) {
            androidx.compose.material3.Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.measures_tab_summary)) })
            androidx.compose.material3.Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.measures_tab_measures)) })
        }
        if (tab == 0) ProgressContent(summary, rows)
        else MeasuresContent(measurements, LocalDate.now().toEpochDay(), onSave)
    }
}
