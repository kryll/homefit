package com.kryll.homefit.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kryll.homefit.HomeFitApp
import com.kryll.homefit.R
import com.kryll.homefit.domain.SetResult
import com.kryll.homefit.domain.WorkItem
import com.kryll.homefit.ui.vm.AppViewModelFactory
import com.kryll.homefit.ui.vm.OnboardingViewModel
import com.kryll.homefit.ui.vm.RoutinesViewModel

private enum class Tab(val route: String, @StringRes val label: Int, @StringRes val placeholder: Int, val icon: ImageVector) {
    Today("today", R.string.tab_today, R.string.placeholder_today, Icons.Filled.CalendarMonth),
    Routines("routines", R.string.tab_routines, R.string.placeholder_routines, Icons.Filled.FitnessCenter),
    Progress("progress", R.string.tab_progress, R.string.placeholder_progress, Icons.Filled.QueryStats),
    Settings("settings", R.string.tab_settings, R.string.placeholder_settings, Icons.Filled.Settings),
}

@Composable
fun HomeFitRoot() {
    val app = LocalContext.current.applicationContext as HomeFitApp
    val settings by app.container.settings.settings.collectAsState(initial = null)
    val s = settings ?: return
    if (!s.onboardingDone) {
        val vm: OnboardingViewModel = viewModel(factory = AppViewModelFactory(app.container))
        OnboardingScreen { profile, equipment -> vm.complete(profile, equipment) }
        return
    }
    MainTabs()
}

@Composable
private fun MainTabs() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(Tab.Today.route)
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.Today.route, modifier = Modifier.padding(padding)) {
            Tab.entries.forEach { tab ->
                composable(tab.route) {
                    if (tab == Tab.Routines) {
                        RoutinesTab()
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(tab.placeholder))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutinesTab() {
    val app = LocalContext.current.applicationContext as HomeFitApp
    val vm: RoutinesViewModel = viewModel(factory = AppViewModelFactory(app.container))
    val routines by vm.routines.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val map by vm.gearByExercise.collectAsState()
    val itemsByRoutine by vm.itemsByRoutine.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val deload by vm.deload.collectAsState()
    val session by vm.session.collectAsState()
    val active = session.routineId
    if (active != null) {
        val names = exercises.associate { it.id to it.name }
        val plan = (itemsByRoutine[active] ?: emptyList()).sortedBy { it.position }.map {
            WorkItem(it.exerciseId, names[it.exerciseId] ?: it.exerciseId, it.sets, it.reps, it.seconds, it.restSeconds, it.alternateSides)
        }
        if (plan.isEmpty()) {
            vm.cancelWorkout()
        } else {
            LaunchedEffect(active) { vm.checkResume(plan, System.currentTimeMillis()) }
            if (session.resumeChecked && session.resumable == null && session.resumedSets == null) {
                WorkoutWithCues(plan, session.startMillis, active, onExit = { results, finished ->
                    vm.finishWorkout(plan, System.currentTimeMillis(), finished, results)
                })
            }
            session.resumable?.let { r ->
                AlertDialog(
                    onDismissRequest = { vm.discardResume() },
                    title = { Text(stringResource(R.string.workout_resume_title)) },
                    text = { Text(stringResource(R.string.workout_resume_text, r.results.size)) },
                    confirmButton = {
                        TextButton(onClick = { vm.continueResume() }) { Text(stringResource(R.string.workout_resume_continue)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { vm.discardResume() }) { Text(stringResource(R.string.workout_resume_fresh)) }
                    },
                )
            }
            val resume = session.resumedSets
            if (resume != null) {
                WorkoutWithCues(session.resumePlan ?: plan, session.startMillis, active, resume = resume, onExit = { results, finished ->
                    vm.finishWorkout(session.resumePlan ?: plan, System.currentTimeMillis(), finished, results)
                })
            }
            return
        }
    }
    RoutinesScreen(
        routines, exercises, map,
        items = itemsByRoutine,
        onSaveRoutine = { id, draft -> vm.saveRoutine(id, draft) },
        onDeleteRoutine = { id -> vm.deleteRoutine(id) },
        onStart = { vm.startWorkout(it, System.currentTimeMillis()) },
        suggestions = suggestions,
        deload = deload,
    )
}

@Composable
private fun WorkoutWithCues(plan: List<WorkItem>, sessionKey: Long, routineId: Long?, resume: List<SetResult> = emptyList(), onExit: (List<SetResult>, Boolean) -> Unit) {
    val context = LocalContext.current.applicationContext
    val runner = remember(sessionKey) { com.kryll.homefit.audio.WorkoutHost.begin(sessionKey, plan, routineId, resume = resume) }
    LaunchedEffect(runner) { com.kryll.homefit.audio.WorkoutService.start(context, null) }
    val askNotifications = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33) askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    val snap by runner.snapshot.collectAsState()
    // Cerrada desde la notificación (Terminar): el servicio ya la guardó, así que solo salimos de la pantalla.
    LaunchedEffect(snap.closed) {
        if (snap.closed) onExit(emptyList(), false)
    }
    WorkoutContent(
        state = snap.state, item = snap.item, items = plan, completedSets = snap.completedSets, totalSets = snap.totalSets,
        onDone = { runner.done() },
        onSkip = { runner.skip() },
        onPause = { runner.togglePause() },
        onRate = { runner.rate(it) },
        onRecord = { r, w -> runner.record(r, w) },
        onAdjustRest = { runner.adjustRest(it) },
        onRepeat = { runner.repeatExercise() },
        onExit = {
            val r = runner.exit()
            if (r != null) {
                com.kryll.homefit.audio.WorkoutService.stop(context)
                com.kryll.homefit.audio.WorkoutHost.clear()
                onExit(r.first, r.second)
            }
        },
    )
}
