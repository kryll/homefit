package com.kryll.homefit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryll.homefit.R
import com.kryll.homefit.domain.Cue
import com.kryll.homefit.domain.Phase
import com.kryll.homefit.domain.SetResult
import com.kryll.homefit.domain.WorkItem
import com.kryll.homefit.domain.WorkoutEngine
import com.kryll.homefit.domain.WorkoutState
import kotlinx.coroutines.delay

/** Entreno en curso: el motor marca el ritmo y esta pantalla solo pinta su estado. */
@Composable
fun WorkoutScreen(
    items: List<WorkItem>,
    onExit: (List<SetResult>, finished: Boolean) -> Unit,
    getReadySeconds: Int = 5,
    onCue: (Cue) -> Unit = {},
) {
    val engine = remember(items) { WorkoutEngine(items, getReadySeconds) }
    var version by remember { mutableIntStateOf(0) }
    var state by remember(engine) { mutableStateOf(engine.state()) }
    fun publish() {
        engine.drainCues().forEach(onCue)
        state = engine.state()
        version++
    }
    LaunchedEffect(engine) {
        publish()
        while (engine.state().phase != Phase.FINISHED) {
            delay(1000)
            engine.tick()
            publish()
        }
    }
    WorkoutContent(
        state = state, item = engine.current, items = items, completedSets = engine.completedSets, totalSets = engine.totalSets,
        onDone = { engine.done(); publish() },
        onSkip = { engine.skip(); publish() },
        onPause = { if (state.paused) engine.resume() else engine.pause(); publish() },
        onRate = { engine.rate(it); publish() },
        onRecord = { r, w -> engine.record(r, w); publish() },
        onAdjustRest = { engine.adjustRest(it); publish() },
        onExit = { onExit(engine.setResults, state.phase == Phase.FINISHED) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutContent(
    state: WorkoutState,
    item: WorkItem,
    items: List<WorkItem>,
    completedSets: Int,
    totalSets: Int,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onPause: () -> Unit,
    onRate: (Int?) -> Unit,
    onExit: () -> Unit,
    onRecord: (Int?, Float?) -> Unit = { _, _ -> },
    onAdjustRest: (Int) -> Unit = {},
) {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    var repsDone by remember(state.itemIndex, state.set) { mutableIntStateOf(item.reps ?: 0) }
    var weight by remember(state.itemIndex) { mutableStateOf<Float?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinearProgressIndicator(progress = if (totalSets == 0) 0f else completedSets / totalSets.toFloat(), modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.workout_progress, completedSets, totalSets), style = MaterialTheme.typography.bodySmall)
        if (state.phase == Phase.FINISHED) {
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.workout_finished), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(stringResource(R.string.workout_finished_sets, completedSets), style = MaterialTheme.typography.bodyLarge)
            Button(onExit, Modifier.fillMaxWidth()) { Text(stringResource(R.string.workout_close)) }
            return@Column
        }
        val phaseLabel = when (state.phase) {
            Phase.GET_READY -> R.string.workout_get_ready
            Phase.WORK -> R.string.workout_work
            Phase.RATE -> R.string.workout_rate_title
            else -> R.string.workout_rest
        }
        Text(stringResource(phaseLabel), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(item.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(stringResource(R.string.workout_set_of, state.set, item.sets), style = MaterialTheme.typography.bodyLarge)
        when (state.phase) {
            Phase.GET_READY, Phase.REST -> {
                Text("${state.remaining}", fontSize = 96.sp, style = MaterialTheme.typography.displayLarge)
                Text(stringResource(R.string.workout_seconds_unit))
                if (state.phase == Phase.REST) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ onAdjustRest(-5) }) { Text(stringResource(R.string.workout_rest_minus)) }
                        OutlinedButton({ onAdjustRest(5) }) { Text(stringResource(R.string.workout_rest_plus)) }
                    }
                    val next = if (state.set < item.sets) item.name else items.getOrNull(state.itemIndex + 1)?.name
                    if (next != null) Text(stringResource(R.string.workout_next, next), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Phase.WORK -> if (item.timed) {
                Text("${state.remaining}", fontSize = 96.sp, style = MaterialTheme.typography.displayLarge)
                Text(stringResource(R.string.workout_seconds_unit))
            } else {
                Text("${item.reps}", fontSize = 96.sp, style = MaterialTheme.typography.displayLarge)
                Text(stringResource(R.string.workout_reps_unit))
            }
            Phase.RATE -> {
                if (!item.timed) {
                    Text(stringResource(R.string.workout_reps_done_hint), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.workout_reps_done, repsDone), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ repsDone = WorkoutEngine.stepReps(repsDone, -1) }) { Text(stringResource(R.string.workout_reps_minus)) }
                        OutlinedButton({ repsDone = WorkoutEngine.stepReps(repsDone, 1) }) { Text(stringResource(R.string.workout_reps_plus)) }
                    }
                    val w = weight
                    Text(
                        if (w == null) stringResource(R.string.workout_weight_none)
                        else stringResource(R.string.workout_weight, java.lang.String.format(java.util.Locale("es", "ES"), "%.1f", w)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ weight = WorkoutEngine.stepWeight(weight, -1) }) { Text(stringResource(R.string.workout_weight_minus)) }
                        OutlinedButton({ weight = WorkoutEngine.stepWeight(weight, 1) }) { Text(stringResource(R.string.workout_weight_plus)) }
                    }
                }
                Text(stringResource(R.string.workout_rate_hint), textAlign = TextAlign.Center)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..10).forEach { n ->
                        FilterChip(false, { if (!item.timed) onRecord(repsDone, weight); onRate(n) }, { Text("$n") })
                    }
                }
                OutlinedButton({ if (!item.timed) onRecord(repsDone, weight); onRate(null) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.workout_no_rpe)) }
            }
            else -> Unit
        }
        if (state.phase != Phase.RATE) {
            if (state.phase == Phase.WORK && !item.timed) {
                Button(onDone, Modifier.fillMaxWidth(), enabled = !state.paused) { Text(stringResource(R.string.workout_done)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onPause, Modifier.weight(1f)) {
                    Text(stringResource(if (state.paused) R.string.workout_resume else R.string.workout_pause))
                }
                OutlinedButton(onSkip, Modifier.weight(1f), enabled = !state.paused) { Text(stringResource(R.string.workout_skip)) }
            }
        }
        OutlinedButton(onExit, Modifier.fillMaxWidth()) { Text(stringResource(R.string.workout_exit)) }
    }
}
