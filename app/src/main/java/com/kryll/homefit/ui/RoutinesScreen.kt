package com.kryll.homefit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import com.kryll.homefit.data.RoutineItem
import com.kryll.homefit.domain.DraftItem
import com.kryll.homefit.domain.RoutineDraft
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.kryll.homefit.data.Exercise
import com.kryll.homefit.data.Routine

/** Filtro del catálogo. Un campo nulo no filtra. */
data class ExerciseFilter(val muscle: String? = null, val difficulty: String? = null, val equipment: String? = null)

fun filterExercises(all: List<Exercise>, equipment: Map<String, Set<String>>, f: ExerciseFilter): List<Exercise> =
    all.filter { e ->
        (f.muscle == null || e.primaryMuscle == f.muscle) &&
            (f.difficulty == null || e.difficulty == f.difficulty) &&
            (f.equipment == null || f.equipment in (equipment[e.id] ?: emptySet()))
    }

private val NAMES = mapOf(
    "biceps" to "bíceps", "triceps" to "tríceps", "gluteos" to "glúteos", "cuadriceps" to "cuádriceps",
    "isquios" to "isquiotibiales", "banda_elastica" to "banda elástica", "ninguno" to "sin material",
)

internal fun label(id: String) = NAMES[id] ?: id.replace('_', ' ')

@Composable
fun RoutinesScreen(
    routines: List<Routine>,
    exercises: List<Exercise>,
    equipment: Map<String, Set<String>>,
    items: Map<Long, List<RoutineItem>> = emptyMap(),
    onSaveRoutine: (Long?, RoutineDraft) -> Unit = { _, _ -> },
    onDeleteRoutine: (Long) -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var editing by rememberSaveable { mutableStateOf<EditorTarget?>(null) }
    editing?.let { target ->
        RoutineEditorScreen(
            initial = target.draft,
            title = stringResource(if (target.id == null) R.string.editor_new else R.string.editor_edit),
            exercises = exercises,
            onSave = { onSaveRoutine(target.id, it); editing = null },
            onCancel = { editing = null },
        )
        return
    }
    fun draftOf(r: Routine, suffix: String = "") = RoutineDraft(
        name = r.name + suffix, goal = r.goal, level = r.level,
        items = (items[r.id] ?: emptyList()).map { DraftItem(it.exerciseId, it.sets, it.reps, it.seconds, it.restSeconds) },
    )
    Column {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.routines_tab)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.exercises_tab)) })
        }
        if (tab == 0) RoutineList(
            routines, items, exercises,
            onNew = { editing = EditorTarget(null, RoutineDraft()) },
            onEdit = { r -> editing = EditorTarget(r.id, draftOf(r)) },
            onDuplicate = { r -> editing = EditorTarget(null, draftOf(r, " (copia)")) },
            onDelete = onDeleteRoutine,
        ) else ExerciseCatalogList(exercises, equipment)
    }
}

private data class EditorTarget(val id: Long?, val draft: RoutineDraft) : java.io.Serializable

@Composable
private fun RoutineList(
    routines: List<Routine>,
    items: Map<Long, List<RoutineItem>>,
    exercises: List<Exercise>,
    onNew: () -> Unit,
    onEdit: (Routine) -> Unit,
    onDuplicate: (Routine) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    val names = exercises.associate { it.id to it.name }
    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Button(onNew, Modifier.fillMaxWidth()) { Text(stringResource(R.string.editor_new)) } }
        items(routines, key = { it.id }) { r ->
            val its = items[r.id] ?: emptyList()
            Card(Modifier.fillMaxWidth().clickable { openId = if (openId == r.id) null else r.id }) {
                Column(Modifier.padding(16.dp)) {
                    Text(r.name, style = MaterialTheme.typography.titleMedium)
                    Text("${label(r.goal)} · ${r.level} · ${r.type}", style = MaterialTheme.typography.bodySmall)
                    if (r.notes.isNotBlank()) Text(r.notes, style = MaterialTheme.typography.bodyMedium)
                    if (its.isNotEmpty()) Text(stringResource(R.string.routine_exercise_count, its.size), style = MaterialTheme.typography.bodySmall)
                    if (openId == r.id) {
                        its.forEach {
                            val vol = it.reps?.let { n -> "$n reps" } ?: "${it.seconds} s"
                            Text("• ${names[it.exerciseId] ?: it.exerciseId}: ${it.sets} × $vol, descanso ${it.restSeconds} s")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton({ onDuplicate(r) }) { Text(stringResource(R.string.editor_duplicate)) }
                            if (!r.predefined) {
                                TextButton({ onEdit(r) }) { Text(stringResource(R.string.editor_edit_action)) }
                                TextButton({ confirmDelete = r.id }) { Text(stringResource(R.string.editor_delete)) }
                            }
                        }
                    }
                }
            }
        }
    }
    confirmDelete?.let { id ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.editor_delete_title)) },
            text = { Text(stringResource(R.string.editor_delete_text)) },
            confirmButton = { TextButton({ onDelete(id); confirmDelete = null; openId = null }) { Text(stringResource(R.string.editor_delete)) } },
            dismissButton = { TextButton({ confirmDelete = null }) { Text(stringResource(R.string.editor_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseCatalogList(all: List<Exercise>, equipment: Map<String, Set<String>>) {
    var filter by rememberSaveable { mutableStateOf(ExerciseFilterState()) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val muscles = all.map { it.primaryMuscle }.distinct().sorted()
    val gear = equipment.values.flatten().distinct().sorted()
    val shown = filterExercises(all, equipment, filter.toFilter())
    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            val active = listOfNotNull(filter.muscle?.let { label(it) }, filter.difficulty, filter.equipment?.let { label(it) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showFilters = !showFilters }) {
                    Text(stringResource(if (showFilters) R.string.filters_hide else R.string.filters_show, active.size))
                }
                if (active.isNotEmpty()) TextButton(onClick = { filter = ExerciseFilterState() }) { Text(stringResource(R.string.filters_clear)) }
            }
            if (active.isNotEmpty()) Text(active.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            if (showFilters) {
            Text(stringResource(R.string.filter_muscle))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                muscles.forEach { m -> FilterChip(filter.muscle == m, { filter = filter.copy(muscle = if (filter.muscle == m) null else m) }, { Text(label(m)) }) }
            }
            Text(stringResource(R.string.filter_difficulty))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("principiante", "intermedio", "avanzado").forEach { d -> FilterChip(filter.difficulty == d, { filter = filter.copy(difficulty = if (filter.difficulty == d) null else d) }, { Text(d) }) }
            }
            Text(stringResource(R.string.filter_equipment))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                gear.forEach { g -> FilterChip(filter.equipment == g, { filter = filter.copy(equipment = if (filter.equipment == g) null else g) }, { Text(label(g)) }) }
            }
            }
            Text(stringResource(R.string.exercise_count, shown.size), style = MaterialTheme.typography.labelLarge)
        }
        items(shown, key = { it.id }) { e ->
            Card(Modifier.fillMaxWidth().clickable { openId = if (openId == e.id) null else e.id }) {
                Column(Modifier.padding(16.dp)) {
                    Text(e.name, style = MaterialTheme.typography.titleMedium)
                    Text("${label(e.primaryMuscle)} · ${e.difficulty}", style = MaterialTheme.typography.bodySmall)
                    if (openId == e.id) {
                        ExerciseImages(e.images, e.name)
                        Text(stringResource(R.string.steps), style = MaterialTheme.typography.labelLarge)
                        e.steps.lines().forEachIndexed { i, s -> Text("${i + 1}. $s") }
                        if (e.commonMistakes.isNotBlank()) {
                            Text(stringResource(R.string.mistakes), style = MaterialTheme.typography.labelLarge)
                            e.commonMistakes.lines().forEach { Text("• $it") }
                        }
                        if (e.safetyTips.isNotBlank()) {
                            Text(stringResource(R.string.safety), style = MaterialTheme.typography.labelLarge)
                            e.safetyTips.lines().forEach { Text("• $it") }
                        }
                        e.easierVariantId?.let { v -> Text(stringResource(R.string.easier, all.firstOrNull { it.id == v }?.name ?: v)) }
                        e.harderVariantId?.let { v -> Text(stringResource(R.string.harder, all.firstOrNull { it.id == v }?.name ?: v)) }
                    }
                }
            }
        }
    }
}

/** Versión serializable por rememberSaveable del filtro. */
private data class ExerciseFilterState(val muscle: String? = null, val difficulty: String? = null, val equipment: String? = null) : java.io.Serializable {
    fun toFilter() = ExerciseFilter(muscle, difficulty, equipment)
}

@Composable
private fun ExerciseImages(images: String, name: String) {
    val paths = images.lines().filter { it.isNotBlank() }
    if (paths.isEmpty()) return
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        paths.forEachIndexed { i, path ->
            val bmp = androidx.compose.runtime.remember(path) {
                runCatching { context.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull()
            }
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = stringResource(if (i == 0) R.string.media_start else R.string.media_end, name),
                    modifier = Modifier.weight(1f),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            }
        }
    }
}
