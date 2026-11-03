package com.kryll.homefit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

private fun label(id: String) = NAMES[id] ?: id.replace('_', ' ')

@Composable
fun RoutinesScreen(routines: List<Routine>, exercises: List<Exercise>, equipment: Map<String, Set<String>>) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Column {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.routines_tab)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.exercises_tab)) })
        }
        if (tab == 0) RoutineList(routines) else ExerciseCatalogList(exercises, equipment)
    }
}

@Composable
private fun RoutineList(routines: List<Routine>) {
    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(routines, key = { it.id }) { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(r.name, style = MaterialTheme.typography.titleMedium)
                    Text("${label(r.goal)} · ${r.level} · ${r.type}", style = MaterialTheme.typography.bodySmall)
                    if (r.notes.isNotBlank()) Text(r.notes, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseCatalogList(all: List<Exercise>, equipment: Map<String, Set<String>>) {
    var filter by rememberSaveable { mutableStateOf(ExerciseFilterState()) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val muscles = all.map { it.primaryMuscle }.distinct().sorted()
    val gear = equipment.values.flatten().distinct().sorted()
    val shown = filterExercises(all, equipment, filter.toFilter())
    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
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
            Text(stringResource(R.string.exercise_count, shown.size), style = MaterialTheme.typography.labelLarge)
        }
        items(shown, key = { it.id }) { e ->
            Card(Modifier.fillMaxWidth().clickable { openId = if (openId == e.id) null else e.id }) {
                Column(Modifier.padding(16.dp)) {
                    Text(e.name, style = MaterialTheme.typography.titleMedium)
                    Text("${label(e.primaryMuscle)} · ${e.difficulty}", style = MaterialTheme.typography.bodySmall)
                    if (openId == e.id) {
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
