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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.kryll.homefit.ui.vm.PlanViewModel
import com.kryll.homefit.ui.vm.ReminderPrefsUi
import com.kryll.homefit.ui.vm.RoutinesViewModel
import com.kryll.homefit.ui.vm.SettingsViewModel
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
    val settings by app.container.settings.settings.collectAsState(initial = null)
    val s = settings ?: return
    if (!s.onboardingDone) {
        val vm: OnboardingViewModel = viewModel(factory = AppViewModelFactory(app))
        OnboardingScreen { profile, equipment -> vm.complete(profile, equipment) }
        return
    }
    val updater: com.kryll.homefit.update.UpdateViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    androidx.compose.runtime.LaunchedEffect(updater) { updater.checkOnLaunch() }
    val updateNotice by updater.launchNotice.collectAsState()
    updateNotice?.let { info ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { updater.dismissNotice() },
            title = { Text(stringResource(R.string.updates_new, info.version)) },
            text = { Text(stringResource(R.string.updates_launch_notice)) },
            confirmButton = { androidx.compose.material3.TextButton({ updater.dismissNotice() }) { Text(stringResource(R.string.updates_acknowledge)) } },
        )
    }
    MainTabs(updater)
}

@Composable
private fun MainTabs(updater: com.kryll.homefit.update.UpdateViewModel) {
    val nav = rememberNavController()
    var startRequest by rememberSaveable { mutableStateOf<Long?>(null) }
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
                        RoutinesTab(startRequest) { startRequest = null }
                    } else if (tab == Tab.Today) {
                        PlanTab(
                            onStart = { id ->
                                startRequest = id
                                nav.navigate(Tab.Routines.route) { popUpTo(Tab.Today.route); launchSingleTop = true }
                            },
                            onOpenSettings = { nav.navigate(Tab.Settings.route) { popUpTo(Tab.Today.route); launchSingleTop = true } },
                        )
                    } else if (tab == Tab.Progress) {
                        ProgressTab()
                    } else if (tab == Tab.Settings) {
                        SettingsTab(updater)
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
private fun RoutinesTab(startRequest: Long? = null, onStartConsumed: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as HomeFitApp
    val vm: RoutinesViewModel = viewModel(factory = AppViewModelFactory(app))
    val routines by vm.routines.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val map by vm.gearByExercise.collectAsState()
    val itemsByRoutine by vm.itemsByRoutine.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val deload by vm.deload.collectAsState()
    val appSettings by vm.appSettings.collectAsState()
    val session by vm.session.collectAsState()
    LaunchedEffect(startRequest) {
        if (startRequest != null) {
            if (vm.session.value.routineId == null) vm.startWorkout(startRequest, System.currentTimeMillis())
            onStartConsumed()
        }
    }
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
                WorkoutWithCues(plan, session.startMillis, active, appSettings.defaultCountdownSeconds, onExit = { results, finished ->
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
                WorkoutWithCues(session.resumePlan ?: plan, session.startMillis, active, appSettings.defaultCountdownSeconds, resume = resume, onExit = { results, finished ->
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
        defaultRest = appSettings.defaultRestSeconds,
    )
}

@Composable
private fun WorkoutWithCues(plan: List<WorkItem>, sessionKey: Long, routineId: Long?, getReadySeconds: Int, resume: List<SetResult> = emptyList(), onExit: (List<SetResult>, Boolean) -> Unit) {
    val context = LocalContext.current.applicationContext
    val runner = remember(sessionKey) { com.kryll.homefit.audio.WorkoutHost.begin(sessionKey, plan, routineId, getReadySeconds, resume) }
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


@Composable
private fun PlanTab(onStart: (Long) -> Unit, onOpenSettings: () -> Unit) {
    val app = LocalContext.current.applicationContext as HomeFitApp
    val vm: PlanViewModel = viewModel(factory = AppViewModelFactory(app))
    val ui by vm.ui.collectAsState()
    val message by vm.message.collectAsState()
    LaunchedEffect(message) {
        if (message != null) { kotlinx.coroutines.delay(5000); vm.clearMessage() }
    }
    val u = ui ?: return
    val text = when (val m = message) {
        is com.kryll.homefit.ui.vm.PlanMessage.Generated -> stringResource(R.string.plan_generated, m.count)
        is com.kryll.homefit.ui.vm.PlanMessage.Cleared -> stringResource(if (m.count == 0) R.string.plan_cleared_none else R.string.plan_cleared, m.count)
        com.kryll.homefit.ui.vm.PlanMessage.NoRoutines -> stringResource(R.string.plan_no_routines)
        com.kryll.homefit.ui.vm.PlanMessage.NoTrainingDays -> stringResource(R.string.plan_no_training_days)
        null -> null
    }
    PlanScreen(
        today = u.today, zone = u.zone, daysMask = u.daysMask, routines = u.routines, scheduled = u.scheduled, workouts = u.workouts,
        message = text,
        onAssign = { d, rid -> vm.assign(d, rid) },
        onAuto = { vm.autoPlan() },
        onClear = { vm.clearWeek() },
        onSkip = { e -> vm.skip(e.id) },
        onMove = { e, nd -> vm.move(e, nd) },
        onStart = onStart,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
private fun SettingsTab(updater: com.kryll.homefit.update.UpdateViewModel) {
    val context = LocalContext.current
    val updateState by updater.updates.collectAsState()
    val downloadState by updater.download.collectAsState()
    fun installDownloaded() {
        val file = downloadState.file ?: return
        try {
            com.kryll.homefit.update.ApkDownloader.verify(context, file)
            if (android.os.Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:${context.packageName}")))
            } else {
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        } catch (e: Exception) { updater.installFailed() }
    }
    val app = context.applicationContext as HomeFitApp
    val vm: SettingsViewModel = viewModel(factory = AppViewModelFactory(app))
    val settings by vm.appSettings.collectAsState()
    val profile by vm.profile.collectAsState()
    val equipment by vm.equipment.collectAsState()
    val latestWeightKg by vm.latestWeightKg.collectAsState()
    val message by vm.message.collectAsState()
    val pending by vm.pendingImport.collectAsState()
    val scope = rememberCoroutineScope()
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    val voiceNames = remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(Unit) { voiceNames.value = com.kryll.homefit.audio.listSpanishVoices(context) }

    fun errorText(e: Throwable): String = if (e is com.kryll.homefit.data.BackupException) {
        when (e.kind) {
            com.kryll.homefit.data.BackupException.Kind.NOT_BACKUP -> context.getString(R.string.backup_err_not_backup)
            com.kryll.homefit.data.BackupException.Kind.VERSION -> context.getString(R.string.backup_err_version, e.detail)
            com.kryll.homefit.data.BackupException.Kind.CORRUPT -> context.getString(R.string.backup_err_corrupt)
            com.kryll.homefit.data.BackupException.Kind.INVALID -> context.getString(R.string.backup_err_invalid, e.detail)
            com.kryll.homefit.data.BackupException.Kind.UNKNOWN_EXERCISE -> context.getString(R.string.backup_err_unknown_exercise, e.detail)
        }
    } else context.getString(R.string.backup_err_read)

    val export = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            vm.setMessage(runCatching {
                val json = vm.exportBackupJson(System.currentTimeMillis())
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                context.getString(R.string.backup_exported)
            }.getOrElse { context.getString(R.string.backup_export_failed) })
        }
    }

    val exportWorkouts = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) scope.launch {
            vm.setMessage(runCatching {
                val csv = vm.workoutsCsv()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                }
                context.getString(R.string.backup_exported)
            }.getOrElse { context.getString(R.string.backup_export_failed) })
        }
    }

    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            vm.setMessage(null)
            runCatching {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { String(it.readBytes(), Charsets.UTF_8) }
                }
                vm.validateImport(text)
            }.onSuccess { vm.setPendingImport(it) }.onFailure { vm.setMessage(errorText(it)) }
        }
    }

    val reminderPrefs = com.kryll.homefit.domain.ReminderPrefs(
        settings.reminderEnabled, settings.reminderHour, settings.reminderMinute, settings.reminderLeadMinutes, settings.reminderExact,
    )
    var refresh by remember { mutableStateOf(0) }
    val notificationsAllowed = remember(refresh, settings.reminderEnabled) {
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val askNotifications = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { refresh++ }
    var exactRefresh by remember { mutableStateOf(0) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        exactRefresh++
        vm.syncReminders()
    }
    val exactAllowed = remember(exactRefresh) { com.kryll.homefit.reminders.ExactReminders.canSchedule(context) }

    SettingsScreen(
        settings = settings, versionName = versionName, message = message,
        reminderPrefs = reminderPrefs,
        reminderNotificationsAllowed = notificationsAllowed,
        reminderExactAllowed = exactAllowed,
        onReminderPrefs = { n ->
            if (n.enabled && !reminderPrefs.enabled && android.os.Build.VERSION.SDK_INT >= 33) askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            if (n.exact && !reminderPrefs.exact) com.kryll.homefit.reminders.ExactReminders.permissionIntent(context)?.let { context.startActivity(it) }
            vm.saveReminderPrefs(ReminderPrefsUi(n.enabled, n.hour, n.minute, n.leadMinutes, n.exact))
        },
        updateState = updateState,
        onCheckUpdates = { updater.check() },
        onOpenUpdate = { updater.fetch() },
        downloadState = downloadState,
        onInstallUpdate = { installDownloaded() },
        pendingImport = pending?.let { ImportSummary(it.routines.size, it.workouts.size, it.scheduled.size, it.measurements.size) },
        availableVoices = voiceNames.value,
        profile = profile,
        equipment = equipment,
        latestWeightKg = latestWeightKg,
        onSaveProfile = { prof, equip ->
            scope.launch {
                vm.saveProfile(prof, equip)
                vm.setMessage(context.getString(R.string.settings_profile_saved))
            }
        },
        onChange = { t -> vm.changeSettings(t) },
        onExport = { export.launch("homefit-copia-" + java.time.LocalDate.now() + ".json") },
        onExportWorkouts = { exportWorkouts.launch("homefit-entrenos-" + java.time.LocalDate.now() + ".csv") },
        onImport = { pick.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        onCancelImport = { vm.setPendingImport(null) },
        onConfirmImport = {
            scope.launch {
                vm.setMessage(runCatching {
                    vm.applyPendingImport { com.kryll.homefit.reminders.ReminderScheduler.sync(app) }
                    context.getString(R.string.backup_imported)
                }.getOrElse { if (it is com.kryll.homefit.data.BackupException) errorText(it) else context.getString(R.string.backup_err_failed) })
            }
        },
    )
}

@Composable
private fun ProgressTab() {
    val app = LocalContext.current.applicationContext as HomeFitApp
    val vm: com.kryll.homefit.ui.vm.ProgressViewModel = viewModel(factory = com.kryll.homefit.ui.vm.AppViewModelFactory(app))
    val summary by vm.summary.collectAsState()
    val rows by vm.rows.collectAsState()
    val volume by vm.volume.collectAsState()
    val measurements by vm.measurements.collectAsState()
    val appSettings by vm.appSettings.collectAsState()
    val message by vm.message.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val exportCsv = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) scope.launch {
            vm.message.value = runCatching {
                val csv = vm.measuresCsv()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                }
                context.getString(R.string.measures_exported)
            }.getOrElse { context.getString(R.string.measures_export_failed) }
        }
    }
    val importCsv = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            vm.message.value = runCatching {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { String(it.readBytes(), Charsets.UTF_8) }
                }
                val result = vm.importMeasuresCsv(text)
                if (result.errors.isEmpty()) context.getString(R.string.measures_imported, result.imported, result.skipped)
                else context.getString(R.string.measures_imported_with_errors, result.imported, result.skipped, result.errors.size)
            }.getOrElse { context.getString(R.string.measures_import_failed) }
        }
    }
    ProgressWithMeasures(
        summary, rows, measurements, volume = volume,
        onSave = vm::saveMeasurement, onDelete = vm::deleteMeasurement,
        weightUnit = appSettings.weightUnit, lengthUnit = appSettings.lengthUnit,
        onExport = { exportCsv.launch("homefit-medidas-${java.time.LocalDate.now()}.csv") },
        onImport = { importCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")) },
        exportMessage = message,
    )
}
