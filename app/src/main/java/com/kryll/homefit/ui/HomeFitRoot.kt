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
import com.kryll.homefit.reminders.reminderPrefs
import kotlinx.coroutines.flow.first
import com.kryll.homefit.HomeFitApp
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.kryll.homefit.domain.WorkItem
import com.kryll.homefit.domain.SetResult
import com.kryll.homefit.audio.CuePlayer
import androidx.compose.runtime.DisposableEffect
import com.kryll.homefit.data.RoutineRepository
import com.kryll.homefit.data.WorkoutRepository
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
                        PlanTab(onStart = { id ->
                            startRequest = id
                            nav.navigate(Tab.Routines.route) { popUpTo(Tab.Today.route); launchSingleTop = true }
                        })
                    } else if (tab == Tab.Progress) {
                        ProgressTab()
                    } else if (tab == Tab.Settings) {
                        SettingsTab()
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
    val db = app.container.database
    val routines by db.routineDao().observeAll().collectAsState(initial = emptyList())
    val exercises by db.exerciseDao().observeAll().collectAsState(initial = emptyList())
    val gear by db.exerciseDao().observeEquipment().collectAsState(initial = emptyList())
    val map = gear.groupBy({ it.exerciseId }, { it.equipment }).mapValues { it.value.toSet() }
    val allItems by db.routineDao().observeAllItems().collectAsState(initial = emptyList())
    val workouts by db.sessionDao().observeWorkouts().collectAsState(initial = emptyList())
    val loggedSets by db.sessionDao().observeAllSets().collectAsState(initial = emptyList())
    val sessionSets = remember(workouts, loggedSets) { com.kryll.homefit.domain.ProgressionData.sessionSets(workouts, loggedSets) }
    val deload = remember(sessionSets) { com.kryll.homefit.domain.Deload.advice(sessionSets, System.currentTimeMillis(), java.time.ZoneId.systemDefault()) }
    val harder = remember(exercises) { exercises.associate { it.id to it.harderVariantId } }
    val suggestions = remember(sessionSets, allItems, harder, deload) {
        buildMap {
            allItems.groupBy { it.routineId }.forEach { (rid, its) ->
                val presc = its.sortedBy { it.position }.map { com.kryll.homefit.domain.Prescribed(it.exerciseId, it.sets, it.reps, it.seconds) }
                com.kryll.homefit.domain.Progression.forRoutine(rid, presc, sessionSets, deload = deload != null) { harder[it] }.forEach { (ex, sg) -> put(rid to ex, sg) }
            }
        }
    }
    val appSettings by app.container.settings.settings.collectAsState(initial = com.kryll.homefit.data.AppSettings())
    val repo = remember { RoutineRepository(db) }
    val scope = rememberCoroutineScope()
    var workoutId by rememberSaveable { mutableStateOf<Long?>(null) }
    var workoutStart by rememberSaveable { mutableStateOf(0L) }
    var resumedSets by remember(workoutId) { mutableStateOf<List<com.kryll.homefit.domain.SetResult>?>(null) }
    var resumePlan by remember(workoutId) { mutableStateOf<List<com.kryll.homefit.domain.WorkItem>?>(null) }
    val workoutRepo = app.container.workoutRepository
    androidx.compose.runtime.LaunchedEffect(startRequest) {
        if (startRequest != null && workoutId == null) {
            workoutId = startRequest; workoutStart = System.currentTimeMillis()
        }
        if (startRequest != null) onStartConsumed()
    }
    val active = workoutId
    if (active != null) {
        val names = exercises.associate { it.id to it.name }
        val plan = (allItems.filter { it.routineId == active }.sortedBy { it.position }).map {
            WorkItem(it.exerciseId, names[it.exerciseId] ?: it.exerciseId, it.sets, it.reps, it.seconds, it.restSeconds, it.alternateSides)
        }
        if (plan.isEmpty()) { workoutId = null } else {
            var resumable by remember(active) { mutableStateOf<com.kryll.homefit.data.WorkoutRepository.ResumableSession?>(null) }
            var resumeChecked by rememberSaveable(active) { mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(active) {
                resumable = workoutRepo.interruptedToday(active, plan, System.currentTimeMillis())
                resumeChecked = true
            }
            if (resumeChecked && resumable == null && resumedSets == null) {
                WorkoutWithCues(plan, workoutStart, active, appSettings.defaultCountdownSeconds, onExit = { results, finished ->
                    val end = System.currentTimeMillis()
                    scope.launch { workoutRepo.save(active, plan, workoutStart, end, finished, results) }
                    workoutId = null
                })
            }
            resumable?.let { r ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { scope.launch { workoutRepo.releaseInterrupted() }; resumable = null },
                    title = { androidx.compose.material3.Text(stringResource(R.string.workout_resume_title)) },
                    text = { androidx.compose.material3.Text(stringResource(R.string.workout_resume_text, r.results.size)) },
                    confirmButton = {
                        androidx.compose.material3.TextButton({
                            val keep = r
                            workoutStart = keep.startMillis
                            resumable = null
                            resumedSets = keep.results
                            resumePlan = keep.plan
                        }) { androidx.compose.material3.Text(stringResource(R.string.workout_resume_continue)) }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton({ scope.launch { workoutRepo.releaseInterrupted() }; resumable = null }) { androidx.compose.material3.Text(stringResource(R.string.workout_resume_fresh)) }
                    },
                )
            }
            val resume = resumedSets
            if (resume != null) {
                WorkoutWithCues(resumePlan ?: plan, workoutStart, active, appSettings.defaultCountdownSeconds, resume = resume, onExit = { results, finished ->
                    val end = System.currentTimeMillis()
                    scope.launch { workoutRepo.save(active, resumePlan ?: plan, workoutStart, end, finished, results) }
                    workoutId = null
                    resumedSets = null
                    resumePlan = null
                })
            }
            return
        }
    }
    RoutinesScreen(
        routines, exercises, map,
        items = allItems.groupBy { it.routineId },
        onSaveRoutine = { id, draft -> scope.launch { repo.save(id, draft) } },
        onDeleteRoutine = { id -> scope.launch { repo.delete(id) } },
        onStart = { workoutId = it; workoutStart = System.currentTimeMillis() },
        suggestions = suggestions,
        deload = deload,
        defaultRest = appSettings.defaultRestSeconds,
    )
}

@Composable
private fun WorkoutWithCues(plan: List<WorkItem>, sessionKey: Long, routineId: Long?, getReadySeconds: Int, resume: List<com.kryll.homefit.domain.SetResult> = emptyList(), onExit: (List<SetResult>, Boolean) -> Unit) {
    val context = LocalContext.current.applicationContext
    val runner = remember(sessionKey) { com.kryll.homefit.audio.WorkoutHost.begin(sessionKey, plan, routineId, getReadySeconds, resume) }
    androidx.compose.runtime.LaunchedEffect(runner) { com.kryll.homefit.audio.WorkoutService.start(context, null) }
    val askNotifications = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33) askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    val snap by runner.snapshot.collectAsState()
    // Cerrada desde la notificación (Terminar): el servicio ya la guardó, así que solo salimos de la pantalla.
    androidx.compose.runtime.LaunchedEffect(snap.closed) {
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
private fun PlanTab(onStart: (Long) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HomeFitApp
    val db = app.container.database
    val scope = rememberCoroutineScope()
    val zone = remember { java.time.ZoneId.systemDefault() }
    val today = remember { java.time.LocalDate.now(zone) }
    val settings by app.container.settings.settings.collectAsState(initial = com.kryll.homefit.data.AppSettings())
    val profile by db.profileDao().observeProfile().collectAsState(initial = null)
    val routines by db.routineDao().observeAll().collectAsState(initial = emptyList())
    val scheduled by db.sessionDao().observeAllScheduled().collectAsState(initial = emptyList())
    val workouts by db.sessionDao().observeWorkouts().collectAsState(initial = emptyList())
    var refresh by remember { mutableStateOf(0) }
    val notificationsAllowed = remember(refresh, settings.reminderEnabled) {
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val askNotifications = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { refresh++ }
    val prefs = settings.reminderPrefs()
    val mask = profile?.daysMask ?: 0

    fun startOf(d: java.time.LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
    fun at(d: java.time.LocalDate) = d.atTime(prefs.hour, prefs.minute).atZone(zone).toInstant().toEpochMilli()

    PlanScreen(
        today = today, zone = zone, daysMask = mask, routines = routines, scheduled = scheduled, workouts = workouts,
        prefs = prefs, notificationsAllowed = notificationsAllowed,
        onAssign = { d, rid ->
            scope.launch {
                val dao = db.sessionDao()
                dao.deleteScheduledBetween(startOf(d), startOf(d.plusDays(1)))
                if (rid != null) dao.insertScheduled(com.kryll.homefit.data.ScheduledSession(routineId = rid, startMillis = at(d), status = "SCHEDULED"))
            }
        },
        onAuto = {
            val p = profile
            val pool = routines.filter { p != null && it.goal == p.goal && it.level == p.level }
                .ifEmpty { routines.filter { p != null && it.goal == p.goal } }
                .ifEmpty { routines }
            scope.launch {
                val dao = db.sessionDao()
                dao.deleteScheduledBetween(startOf(today), startOf(today.plusDays(7)))
                com.kryll.homefit.domain.Planning.autoPlan(mask, pool.map { it.id }, today).forEach {
                    dao.insertScheduled(com.kryll.homefit.data.ScheduledSession(routineId = it.routineId, startMillis = at(it.date), status = "SCHEDULED"))
                }
            }
        },
        onClear = { scope.launch { db.sessionDao().deleteScheduledBetween(startOf(today), startOf(today.plusDays(7))) } },
        onSkip = { e -> scope.launch { db.sessionDao().setScheduledStatus(e.id, "SKIPPED") } },
        onMove = { e, nd ->
            scope.launch {
                val dao = db.sessionDao()
                dao.setScheduledStatus(e.id, "MOVED")
                dao.insertScheduled(e.copy(id = 0, startMillis = com.kryll.homefit.domain.movedStartMillis(e.startMillis, zone, nd), status = "SCHEDULED"))
            }
        },
        onStart = onStart,
        onPrefs = { n ->
            if (n.enabled && !prefs.enabled && android.os.Build.VERSION.SDK_INT >= 33) askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            scope.launch {
                app.container.settings.update { it.copy(reminderEnabled = n.enabled, reminderHour = n.hour, reminderMinute = n.minute, reminderLeadMinutes = n.leadMinutes) }
                com.kryll.homefit.reminders.ReminderScheduler.sync(app)
            }
        },
    )
}

@Composable
private fun SettingsTab() {
    val context = LocalContext.current
    val app = context.applicationContext as HomeFitApp
    val scope = rememberCoroutineScope()
    val settings by app.container.settings.settings.collectAsState(initial = com.kryll.homefit.data.AppSettings())
    val repo = remember { com.kryll.homefit.data.BackupRepository(app.container.database) }
    val profile by app.container.database.profileDao().observeProfile().collectAsState(initial = null)
    val equipmentStored by app.container.database.profileDao().observeEquipment().collectAsState(initial = emptyList())
    val measurementsAll by app.container.database.measurementDao().observeAll().collectAsState(initial = emptyList())
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<com.kryll.homefit.data.Backup?>(null) }
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
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
            message = runCatching {
                val backup = repo.export(settings, System.currentTimeMillis())
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(com.kryll.homefit.data.BackupCodec.toJson(backup).toByteArray(Charsets.UTF_8)) }
                }
                context.getString(R.string.backup_exported)
            }.getOrElse { context.getString(R.string.backup_export_failed) }
        }
    }

    val exportWorkouts = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) scope.launch {
            message = runCatching {
                val csv = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val db = app.container.database
                    val names = db.routineDao().observeAll().first().associate { it.id to it.name }
                    val pairs = db.sessionDao().observeWorkouts().first().map { it to db.sessionDao().setsOf(it.id) }
                    com.kryll.homefit.domain.WorkoutCsv.build(pairs, names)
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                }
                context.getString(R.string.backup_exported)
            }.getOrElse { context.getString(R.string.backup_export_failed) }
        }
    }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            message = null
            runCatching {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { String(it.readBytes(), Charsets.UTF_8) }
                }
                val backup = com.kryll.homefit.data.BackupCodec.fromJson(text)
                repo.validate(backup)
                backup
            }.onSuccess { pending = it }.onFailure { message = errorText(it) }
        }
    }
    val p = pending
    val voiceNames = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(listOf<String>()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        voiceNames.value = com.kryll.homefit.audio.listSpanishVoices(context)
    }
    SettingsScreen(
        settings = settings, versionName = versionName, message = message,
        pendingImport = p?.let { ImportSummary(it.routines.size, it.workouts.size, it.scheduled.size, it.measurements.size) },
        availableVoices = voiceNames.value,
        profile = profile,
        equipment = equipmentStored.toSet(),
        latestWeightKg = measurementsAll.filter { it.weightKg != null }.maxByOrNull { it.epochDay }?.weightKg,
        onSaveProfile = { prof, equip ->
            scope.launch {
                app.container.database.profileDao().saveProfile(prof)
                app.container.database.profileDao().clearEquipment()
                app.container.database.profileDao().addEquipment(equip.map { com.kryll.homefit.data.UserEquipment(it) })
                message = context.getString(R.string.settings_profile_saved)
            }
        },
        onChange = { t -> scope.launch { app.container.settings.update(t) } },
        onExport = { export.launch("homefit-copia-${java.time.LocalDate.now()}.json") },
        onExportWorkouts = { exportWorkouts.launch("homefit-entrenos-${java.time.LocalDate.now()}.csv") },
        onImport = { pick.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        onCancelImport = { pending = null },
        onConfirmImport = {
            val b = pending
            pending = null
            if (b != null) scope.launch {
                message = runCatching {
                    com.kryll.homefit.data.BackupApplier(
                        repo,
                        currentSettings = { app.container.settings.settings.first() },
                        writeSettings = { n -> app.container.settings.update { n } },
                        afterApply = { com.kryll.homefit.reminders.ReminderScheduler.sync(app) },
                    ).apply(b)
                    context.getString(R.string.backup_imported)
                }.getOrElse { if (it is com.kryll.homefit.data.BackupException) errorText(it) else context.getString(R.string.backup_err_failed) }
            }
        },
    )
}

@Composable
private fun ProgressTab() {
    val app = LocalContext.current.applicationContext as HomeFitApp
    val db = app.container.database
    val workouts by db.sessionDao().observeWorkouts().collectAsState(initial = emptyList())
    val sets by db.sessionDao().observeAllSets().collectAsState(initial = emptyList())
    val routines by db.routineDao().observeAll().collectAsState(initial = emptyList())
    val exercises by db.exerciseDao().observeAll().collectAsState(initial = emptyList())
    val rows = remember(workouts, sets, routines, exercises) {
        com.kryll.homefit.domain.ProgressStats.rows(
            workouts, sets, routines.associate { it.id to it.name }, exercises.associate { it.id to it.name },
        )
    }
    val summary = remember(rows) {
        com.kryll.homefit.domain.ProgressStats.summary(rows, System.currentTimeMillis(), java.time.ZoneId.systemDefault())
    }
    val volume = remember(rows, exercises) {
        val primary = exercises.associate { it.id to it.primaryMuscle }
        com.kryll.homefit.domain.ProgressStats.volumeByMuscle(rows) { primary[it] }
    }
    val measurements by db.measurementDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val unitSettings by (context.applicationContext as HomeFitApp).container.settings.settings
        .collectAsState(initial = com.kryll.homefit.data.AppSettings())
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val exportCsv = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) scope.launch {
            exportMessage = runCatching {
                val csv = com.kryll.homefit.domain.MeasureCsv.build(measurements, unitSettings.weightUnit, unitSettings.lengthUnit)
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
            exportMessage = runCatching {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { String(it.readBytes(), Charsets.UTF_8) }
                }
                val parsed = com.kryll.homefit.domain.MeasureCsv.parse(text, unitSettings.weightUnit, unitSettings.lengthUnit)
                val existing = measurements.map { it.epochDay }.toSet()
                val fresh = parsed.rows.filter { it.epochDay !in existing }
                fresh.forEach { db.measurementDao().insert(it) }
                if (parsed.errors.isEmpty()) context.getString(R.string.measures_imported, fresh.size, parsed.rows.size - fresh.size)
                else context.getString(R.string.measures_imported_with_errors, fresh.size, parsed.rows.size - fresh.size, parsed.errors.size)
            }.getOrElse { context.getString(R.string.measures_import_failed) }
        }
    }
    ProgressWithMeasures(
        summary, rows, measurements, volume = volume,
        onSave = { m -> scope.launch { if (m.id != 0L) db.measurementDao().update(m) else db.measurementDao().insert(m) } },
        onDelete = { id -> scope.launch { db.measurementDao().deleteById(id) } },
        weightUnit = unitSettings.weightUnit, lengthUnit = unitSettings.lengthUnit,
        onExport = { exportCsv.launch("homefit-medidas-${java.time.LocalDate.now()}.csv") },
        onImport = { importCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")) },
        exportMessage = exportMessage,
    )
}
