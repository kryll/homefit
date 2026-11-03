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
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kryll.homefit.R
import com.kryll.homefit.HomeFitApp
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.kryll.homefit.domain.WorkItem
import com.kryll.homefit.data.RoutineRepository
import com.kryll.homefit.data.UserEquipment
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

private enum class Tab(val route: String, @StringRes val label: Int, @StringRes val placeholder: Int, val icon: ImageVector) {
    Today("today", R.string.tab_today, R.string.placeholder_today, Icons.Filled.CalendarMonth),
    Routines("routines", R.string.tab_routines, R.string.placeholder_routines, Icons.Filled.FitnessCenter),
    Progress("progress", R.string.tab_progress, R.string.placeholder_progress, Icons.Filled.QueryStats),
    Settings("settings", R.string.tab_settings, R.string.placeholder_settings, Icons.Filled.Settings),
}

@Composable
fun HomeFitRoot() {
    val app = LocalContext.current.applicationContext as HomeFitApp
    val scope = rememberCoroutineScope()
    val settings by app.container.settings.settings.collectAsState(initial = null)
    val s = settings ?: return
    if (!s.onboardingDone) {
        OnboardingScreen { profile, equipment ->
            scope.launch {
                val dao = app.container.database.profileDao()
                dao.saveProfile(profile)
                dao.clearEquipment()
                dao.addEquipment(equipment.map { UserEquipment(it) })
                app.container.settings.update { it.copy(onboardingDone = true) }
            }
        }
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
    val db = app.container.database
    val routines by db.routineDao().observeAll().collectAsState(initial = emptyList())
    val exercises by db.exerciseDao().observeAll().collectAsState(initial = emptyList())
    val gear by db.exerciseDao().observeEquipment().collectAsState(initial = emptyList())
    val map = gear.groupBy({ it.exerciseId }, { it.equipment }).mapValues { it.value.toSet() }
    val allItems by db.routineDao().observeAllItems().collectAsState(initial = emptyList())
    val repo = remember { RoutineRepository(db) }
    val scope = rememberCoroutineScope()
    var workoutId by rememberSaveable { mutableStateOf<Long?>(null) }
    val active = workoutId
    if (active != null) {
        val names = exercises.associate { it.id to it.name }
        val plan = (allItems.filter { it.routineId == active }.sortedBy { it.position }).map {
            WorkItem(it.exerciseId, names[it.exerciseId] ?: it.exerciseId, it.sets, it.reps, it.seconds, it.restSeconds)
        }
        if (plan.isEmpty()) { workoutId = null } else {
            WorkoutScreen(plan, onExit = { _, _ -> workoutId = null })
            return
        }
    }
    RoutinesScreen(
        routines, exercises, map,
        items = allItems.groupBy { it.routineId },
        onSaveRoutine = { id, draft -> scope.launch { repo.save(id, draft) } },
        onDeleteRoutine = { id -> scope.launch { repo.delete(id) } },
        onStart = { workoutId = it },
    )
}
