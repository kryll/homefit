package com.kryll.homefit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kryll.homefit.R
import com.kryll.homefit.data.BodyMeasurement
import com.kryll.homefit.data.LengthUnit
import com.kryll.homefit.data.WeightUnit
import com.kryll.homefit.domain.Units
import com.kryll.homefit.domain.MeasureField
import com.kryll.homefit.domain.MeasureInput
import com.kryll.homefit.domain.MeasureResult
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

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

/** Valores de un campo en orden cronológico (un valor por registro que lo tenga). */
fun fieldPoints(items: List<BodyMeasurement>, f: MeasureField): List<Pair<Long, Float>> =
    items.sortedWith(compareBy({ it.epochDay }, { it.id })).mapNotNull { m -> MeasureInput.stored(m, f)?.let { m.epochDay to it } }

/** Pesos en orden cronológico (un valor por registro con peso). */
fun weightSeries(items: List<BodyMeasurement>): List<Float> = weightPoints(items).map { it.second }

/** (epochDay, kg) en orden cronológico. */
fun weightPoints(items: List<BodyMeasurement>): List<Pair<Long, Float>> =
    items.sortedWith(compareBy({ it.epochDay }, { it.id })).mapNotNull { m -> m.weightKg?.let { m.epochDay to it } }

/** Posición horizontal 0..1 según la fecha real; un solo día queda en el centro. */
fun chartX(day: Long, first: Long, last: Long): Float = if (last == first) 0.5f else (day - first).toFloat() / (last - first).toFloat()

private fun dayText(day: Long): String = DateTimeFormatter.ofPattern("d MMM yyyy", es).format(LocalDate.ofEpochDay(day))

@Composable
fun MeasuresContent(
    items: List<BodyMeasurement>,
    today: Long,
    onSave: (BodyMeasurement) -> Unit,
    onDelete: (Long) -> Unit = {},
    weightUnit: WeightUnit = WeightUnit.KG,
    lengthUnit: LengthUnit = LengthUnit.CM,
    onExport: (() -> Unit)? = null,
    exportMessage: String? = null,
) {
    val texts = remember { mutableStateMapOf<MeasureField, String>() }
    var editing by remember { mutableStateOf<BodyMeasurement?>(null) }
    var prefill by remember { mutableStateOf<Map<MeasureField, String>>(emptyMap()) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val uiScope = androidx.compose.runtime.rememberCoroutineScope()
    val wl = Units.weightLabel(weightUnit)
    val ll = Units.lengthLabel(lengthUnit)
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    val errEmpty = stringResource(R.string.measures_err_empty)
    val errInvalidFmt = stringResource(R.string.measures_err_invalid)
    val labels = MeasureField.entries.associateWith { f ->
        val unit = when (f) { MeasureField.WEIGHT -> wl; MeasureField.FAT -> "%"; else -> ll }
        stringResource(R.string.measure_label_unit, stringResource(label(f)), unit)
    }
    var chartField by remember { mutableStateOf(MeasureField.WEIGHT) }
    val chartUnit = when (chartField) { MeasureField.WEIGHT -> wl; MeasureField.FAT -> "%"; else -> ll }
    val points = fieldPoints(items, chartField).map { (d, v) ->
        d to when (chartField) {
            MeasureField.WEIGHT -> Units.weightToDisplay(v, weightUnit)
            MeasureField.FAT -> v
            else -> Units.lengthToDisplay(v, lengthUnit)
        }
    }
    val series = points.map { it.second }

    LazyColumn(
        Modifier.fillMaxSize(), state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.measures_title), style = MaterialTheme.typography.headlineSmall) }
        item { Text(stringResource(R.string.measures_hint, wl, ll), style = MaterialTheme.typography.bodySmall) }
        editing?.let { e -> item { Text(stringResource(R.string.measures_editing, dayText(e.epochDay)), style = MaterialTheme.typography.titleSmall) } }
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
                        when (val r = MeasureInput.build(today, texts.toMap(), weightUnit, lengthUnit, editing, prefill)) {
                            is MeasureResult.Ok -> { onSave(r.measurement); texts.clear(); editing = null; prefill = emptyMap(); error = null; saved = true }
                            is MeasureResult.Invalid -> { error = errInvalidFmt.format(labels.getValue(r.field)); saved = false }
                            MeasureResult.Empty -> { error = errEmpty; saved = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(if (editing != null) R.string.measures_save_edit else R.string.measures_save)) }
                if (editing != null) androidx.compose.material3.TextButton(onClick = { editing = null; prefill = emptyMap(); texts.clear(); error = null }) {
                    Text(stringResource(R.string.measures_edit_cancel))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (saved) Text(stringResource(R.string.measures_saved), style = MaterialTheme.typography.bodySmall)
            }
        }
        item { Text(stringResource(R.string.measures_chart_title), style = MaterialTheme.typography.titleMedium) }
        item {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MeasureField.entries.forEach { f ->
                    FilterChip(selected = f == chartField, onClick = { chartField = f }, label = { Text(stringResource(label(f))) })
                }
            }
        }
        item {
            if (series.size < 2) Text(stringResource(R.string.measures_chart_need_two, stringResource(label(chartField)).lowercase()), style = MaterialTheme.typography.bodyMedium)
            else {
                val first = points.first().first
                val last = points.last().first
                val desc = points.joinToString("; ") { dayText(it.first) + ": " + num(it.second) + " " + chartUnit }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LineChart(points, Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = desc })
                    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(dayText(first), style = MaterialTheme.typography.bodySmall)
                        Text(dayText(last), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        stringResource(R.string.measures_chart_range, num(series.min()), num(series.max()), (last - first).toInt(), chartUnit),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    points.forEach { Text(dayText(it.first) + ": " + num(it.second) + " " + chartUnit, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item { Text(stringResource(R.string.measures_history), style = MaterialTheme.typography.titleMedium) }
        if (onExport != null && items.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.measures_export)) }
                exportMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (items.isEmpty()) {
            item { Text(stringResource(R.string.measures_empty), style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(items, key = { it.id }) { m ->
                MeasureCard(m, onDelete, weightUnit, lengthUnit, onEdit = { e ->
                    editing = e
                    prefill = MeasureInput.prefill(e, weightUnit, lengthUnit)
                    texts.clear(); texts.putAll(prefill)
                    saved = false; error = null
                    uiScope.launch { listState.animateScrollToItem(0) }
                })
            }
        }
    }
}

@Composable
private fun MeasureCard(m: BodyMeasurement, onDelete: (Long) -> Unit, wu: WeightUnit, lu: LengthUnit, onEdit: (BodyMeasurement) -> Unit) {
    var confirming by remember(m.id) { mutableStateOf(false) }
    val date = DateTimeFormatter.ofPattern("d MMM yyyy", es).format(LocalDate.ofEpochDay(m.epochDay))
    val parts = buildList {
        m.weightKg?.let { add(stringResource(R.string.measures_item_weight, num(Units.weightToDisplay(it, wu)), Units.weightLabel(wu))) }
        m.waistCm?.let { add(stringResource(R.string.measures_item_waist, num(Units.lengthToDisplay(it, lu)), Units.lengthLabel(lu))) }
        m.chestCm?.let { add(stringResource(R.string.measures_item_chest, num(Units.lengthToDisplay(it, lu)), Units.lengthLabel(lu))) }
        m.armCm?.let { add(stringResource(R.string.measures_item_arm, num(Units.lengthToDisplay(it, lu)), Units.lengthLabel(lu))) }
        m.thighCm?.let { add(stringResource(R.string.measures_item_thigh, num(Units.lengthToDisplay(it, lu)), Units.lengthLabel(lu))) }
        m.hipCm?.let { add(stringResource(R.string.measures_item_hip, num(Units.lengthToDisplay(it, lu)), Units.lengthLabel(lu))) }
        m.bodyFatPct?.let { add(stringResource(R.string.measures_item_fat, num(it))) }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(date, style = MaterialTheme.typography.titleSmall)
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            if (!confirming) {
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.TextButton(onClick = { onEdit(m) }) { Text(stringResource(R.string.measures_edit)) }
                    androidx.compose.material3.TextButton(onClick = { confirming = true }) { Text(stringResource(R.string.measures_delete)) }
                }
            } else {
                Text(stringResource(R.string.measures_delete_confirm), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.measures_cancel)) }
                    androidx.compose.material3.TextButton(onClick = { onDelete(m.id) }) { Text(stringResource(R.string.measures_delete_yes)) }
                }
            }
        }
    }
}

@Composable
fun LineChart(points: List<Pair<Long, Float>>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        val pad = 12.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        val values = points.map { it.second }
        val lo = values.min()
        val hi = values.max()
        val span = if (hi - lo < 0.01f) 1f else hi - lo
        val first = points.first().first
        val last = points.last().first
        drawLine(axis, Offset(pad, size.height - pad), Offset(size.width - pad, size.height - pad), strokeWidth = 1.dp.toPx())
        val pts = points.map { (d, v) ->
            Offset(pad + w * chartX(d, first, last), pad + h * (1f - (v - lo) / span))
        }
        pts.forEach { drawLine(axis, Offset(it.x, size.height - pad), Offset(it.x, size.height - pad + 5.dp.toPx()), strokeWidth = 1.dp.toPx()) }
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
    onDelete: (Long) -> Unit = {},
    weightUnit: WeightUnit = WeightUnit.KG,
    lengthUnit: LengthUnit = LengthUnit.CM,
    onExport: (() -> Unit)? = null,
    exportMessage: String? = null,
) {
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        androidx.compose.material3.TabRow(selectedTabIndex = tab) {
            androidx.compose.material3.Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.measures_tab_summary)) })
            androidx.compose.material3.Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.measures_tab_measures)) })
        }
        if (tab == 0) ProgressContent(summary, rows)
        else MeasuresContent(measurements, LocalDate.now().toEpochDay(), onSave, onDelete, weightUnit, lengthUnit, onExport, exportMessage)
    }
}
