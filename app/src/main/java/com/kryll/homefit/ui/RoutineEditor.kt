package com.kryll.homefit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kryll.homefit.R
import com.kryll.homefit.data.Exercise
import com.kryll.homefit.domain.DraftError
import com.kryll.homefit.domain.RoutineDraft
import java.text.Normalizer

private val GOALS = listOf("perder_grasa", "tonificar", "ganar_fuerza", "resistencia")
private val LEVELS = listOf("principiante", "intermedio", "avanzado")

internal fun plain(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

private fun digits(s: String): Int? = s.filter { it.isDigit() }.take(4).toIntOrNull()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutineEditorScreen(
    initial: RoutineDraft,
    title: String,
    exercises: List<Exercise>,
    onSave: (RoutineDraft) -> Unit,
    onCancel: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(initial) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val names = remember(exercises) { exercises.associate { it.id to it.name } }
    val errors = draft.errors()

    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(title, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = draft.name, onValueChange = { draft = draft.copy(name = it.take(RoutineDraft.MAX_NAME + 10)) },
                label = { Text(stringResource(R.string.editor_name)) }, singleLine = true,
                isError = showErrors && (DraftError.NAME_EMPTY in errors || DraftError.NAME_TOO_LONG in errors),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.onb_goal), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GOALS.forEach { g -> FilterChip(draft.goal == g, { draft = draft.copy(goal = g) }, { Text(label(g)) }) }
            }
            Text(stringResource(R.string.onb_level), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LEVELS.forEach { l -> FilterChip(draft.level == l, { draft = draft.copy(level = l) }, { Text(l) }) }
            }
        }
        itemsIndexed(draft.items) { index, item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${index + 1}. ${names[item.exerciseId] ?: item.exerciseId}", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(stringResource(R.string.editor_sets), item.sets, Modifier.weight(1f)) { draft = draft.update(index, item.copy(sets = it)) }
                        if (item.timed) {
                            NumberField(stringResource(R.string.editor_seconds), item.seconds, Modifier.weight(1f)) { draft = draft.update(index, item.copy(seconds = it)) }
                        } else {
                            NumberField(stringResource(R.string.editor_reps), item.reps, Modifier.weight(1f)) { draft = draft.update(index, item.copy(reps = it)) }
                        }
                        NumberField(stringResource(R.string.editor_rest), item.restSeconds, Modifier.weight(1f)) { draft = draft.update(index, item.copy(restSeconds = it)) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                        FilterChip(!item.timed, { draft = draft.update(index, item.asReps()) }, { Text(stringResource(R.string.editor_by_reps)) })
                        FilterChip(item.timed, { draft = draft.update(index, item.asTimed()) }, { Text(stringResource(R.string.editor_by_time)) })
                        TextButton({ draft = draft.move(index, -1) }, enabled = index > 0) { Text(stringResource(R.string.editor_up)) }
                        TextButton({ draft = draft.move(index, 1) }, enabled = index < draft.items.lastIndex) { Text(stringResource(R.string.editor_down)) }
                        TextButton({ draft = draft.remove(index) }) { Text(stringResource(R.string.editor_remove)) }
                    }
                }
            }
        }
        item {
            OutlinedButton({ picking = !picking }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.editor_add_exercise)) }
            if (picking) ExercisePicker(exercises, onPick = { draft = draft.add(it); picking = false })
            if (showErrors && errors.isNotEmpty()) {
                errors.forEach { Text(stringResource(errorText(it)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onCancel, Modifier.weight(1f)) { Text(stringResource(R.string.editor_cancel)) }
                Button({ if (errors.isEmpty()) onSave(draft) else showErrors = true }, Modifier.weight(1f)) { Text(stringResource(R.string.editor_save)) }
            }
        }
    }
}

private fun errorText(e: DraftError) = when (e) {
    DraftError.NAME_EMPTY -> R.string.err_name_empty
    DraftError.NAME_TOO_LONG -> R.string.err_name_long
    DraftError.NO_ITEMS -> R.string.err_no_items
    DraftError.BAD_SETS -> R.string.err_sets
    DraftError.BAD_VOLUME -> R.string.err_volume
    DraftError.BAD_REST -> R.string.err_rest
}

@Composable
private fun NumberField(label: String, value: Int?, modifier: Modifier, onChange: (Int?) -> Unit) {
    OutlinedTextField(
        value = value?.toString() ?: "", onValueChange = { onChange(digits(it)) },
        label = { Text(label, maxLines = 1) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = modifier,
    )
}

/** Búsqueda por nombre sin distinguir mayúsculas ni tildes. */
internal fun searchExercises(exercises: List<Exercise>, query: String, limit: Int = 8): List<Exercise> {
    val q = plain(query.trim())
    return exercises.filter { plain(it.name).contains(q) }.take(limit)
}

@Composable
private fun ExercisePicker(exercises: List<Exercise>, onPick: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(exercises, q) { searchExercises(exercises, query) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.editor_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            shown.forEach { e ->
                TextButton({ onPick(e.id) }, Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(e.name)
                        Text("${label(e.primaryMuscle)} · ${e.difficulty}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
