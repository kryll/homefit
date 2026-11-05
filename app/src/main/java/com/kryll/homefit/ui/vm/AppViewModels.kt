package com.kryll.homefit.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kryll.homefit.data.AppContainer
import com.kryll.homefit.data.AppSettings
import com.kryll.homefit.data.Backup
import com.kryll.homefit.data.BackupApplier
import com.kryll.homefit.data.BackupCodec
import com.kryll.homefit.data.BackupRepository
import com.kryll.homefit.data.Exercise
import com.kryll.homefit.data.HomeFitDatabase
import com.kryll.homefit.data.Routine
import com.kryll.homefit.data.RoutineItem
import com.kryll.homefit.data.RoutineRepository
import com.kryll.homefit.data.SettingsStore
import com.kryll.homefit.data.UserEquipment
import com.kryll.homefit.data.UserProfile
import com.kryll.homefit.data.WorkoutRepository
import com.kryll.homefit.domain.Deload
import com.kryll.homefit.domain.DeloadReason
import com.kryll.homefit.domain.Prescribed
import com.kryll.homefit.domain.Progression
import com.kryll.homefit.domain.ProgressionData
import com.kryll.homefit.domain.RoutineDraft
import com.kryll.homefit.domain.SetResult
import com.kryll.homefit.domain.Suggestion
import com.kryll.homefit.domain.WorkItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.kryll.homefit.reminders.reminderPrefs
import com.kryll.homefit.data.BodyMeasurement
import com.kryll.homefit.data.SetLog
import com.kryll.homefit.data.WorkoutSession
import com.kryll.homefit.domain.MeasureCsv
import com.kryll.homefit.domain.MuscleVolume
import com.kryll.homefit.domain.ProgressStats
import com.kryll.homefit.domain.ProgressSummary
import com.kryll.homefit.domain.SessionRow

/** Alta inicial: guarda perfil + equipamiento y marca el alta como hecha. */
class OnboardingViewModel(private val db: HomeFitDatabase, private val settings: SettingsStore) : ViewModel() {
    fun complete(profile: UserProfile, equipment: Collection<String>) {
        viewModelScope.launch {
            val dao = db.profileDao()
            dao.saveProfile(profile)
            dao.clearEquipment()
            dao.addEquipment(equipment.map { UserEquipment(it) })
            settings.update { it.copy(onboardingDone = true) }
        }
    }
}

/** Estado de la sesión de entreno en curso (sobrevive a recomposición y rotación). */
data class WorkoutSessionState(
    val routineId: Long? = null,
    val startMillis: Long = 0,
    val resumeChecked: Boolean = false,
    val resumable: WorkoutRepository.ResumableSession? = null,
    val resumedSets: List<SetResult>? = null,
    val resumePlan: List<WorkItem>? = null,
)

/** Pestaña Rutinas: catálogo observable, progresión (doble progresión + descarga), alta/baja y ciclo de sesión. */
class RoutinesViewModel(
    db: HomeFitDatabase,
    private val repo: RoutineRepository,
    private val workoutRepo: WorkoutRepository,
    private val settings: SettingsStore,
) : ViewModel() {
    /** Ajustes que condicionan el entreno (descanso y cuenta atrás por defecto). */
    val appSettings: StateFlow<AppSettings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val routines: StateFlow<List<Routine>> =
        db.routineDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val exercises: StateFlow<List<Exercise>> =
        db.exerciseDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val gearByExercise: StateFlow<Map<String, Set<String>>> =
        db.exerciseDao().observeEquipment()
            .map { list -> list.groupBy({ it.exerciseId }, { it.equipment }).mapValues { it.value.toSet() } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    private val allItems: StateFlow<List<RoutineItem>> =
        db.routineDao().observeAllItems().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val itemsByRoutine: StateFlow<Map<Long, List<RoutineItem>>> =
        allItems.map { list -> list.groupBy { it.routineId } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val sessionSets = combine(
        db.sessionDao().observeWorkouts(), db.sessionDao().observeAllSets(),
    ) { w, s -> ProgressionData.sessionSets(w, s) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val deload: StateFlow<DeloadReason?> = sessionSets.map {
        Deload.advice(it, System.currentTimeMillis(), java.time.ZoneId.systemDefault())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Sugerencias de progresión por (rutina, ejercicio), mismas reglas que antes en el composable. */
    val suggestions: StateFlow<Map<Pair<Long, String>, Suggestion>> =
        combine(sessionSets, allItems, exercises, deload) { sets, items, exs, dl ->
            val harder = exs.associate { it.id to it.harderVariantId }
            buildMap {
                items.groupBy { it.routineId }.forEach { (rid, its) ->
                    val presc = its.sortedBy { it.position }.map { Prescribed(it.exerciseId, it.sets, it.reps, it.seconds) }
                    Progression.forRoutine(rid, presc, sets, deload = dl != null) { harder[it] }.forEach { (ex, sg) -> put(rid to ex, sg) }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun saveRoutine(id: Long?, draft: RoutineDraft) = viewModelScope.launch { repo.save(id, draft) }
    fun deleteRoutine(id: Long) = viewModelScope.launch { repo.delete(id) }

    private val _session = MutableStateFlow(WorkoutSessionState())
    val session: StateFlow<WorkoutSessionState> = _session

    fun startWorkout(routineId: Long, nowMillis: Long) {
        _session.value = WorkoutSessionState(routineId = routineId, startMillis = nowMillis)
    }

    fun cancelWorkout() {
        _session.value = WorkoutSessionState()
    }

    /** Comprueba una vez por sesión si hay un entreno interrumpido adoptable para esta rutina. */
    fun checkResume(plan: List<WorkItem>, nowMillis: Long) {
        val s = _session.value
        val id = s.routineId ?: return
        if (s.resumeChecked) return
        _session.update { it.copy(resumeChecked = true) }
        viewModelScope.launch {
            val r = workoutRepo.interruptedToday(id, plan, nowMillis)
            _session.update { if (it.routineId == id) it.copy(resumable = r) else it }
        }
    }

    fun continueResume() {
        val s = _session.value
        val r = s.resumable ?: return
        _session.value = s.copy(resumable = null, resumedSets = r.results, resumePlan = r.plan, startMillis = r.startMillis)
    }

    fun discardResume() {
        _session.update { it.copy(resumable = null) }
        viewModelScope.launch { workoutRepo.releaseInterrupted() }
    }

    /** Guarda el entreno (el repositorio deduce parcial/completo) y cierra la sesión en pantalla. */
    fun finishWorkout(plan: List<WorkItem>, endMillis: Long, finished: Boolean, results: List<SetResult>) {
        val s = _session.value
        val id = s.routineId ?: return
        val usePlan = s.resumePlan ?: plan
        val start = s.startMillis
        _session.value = WorkoutSessionState()
        viewModelScope.launch { workoutRepo.save(id, usePlan, start, endMillis, finished, results) }
    }
}

/** Factoría manual sobre el contenedor de la app (sin frameworks de DI). */
class AppViewModelFactory(private val app: com.kryll.homefit.HomeFitApp) : ViewModelProvider.Factory {
    private val container = app.container
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        OnboardingViewModel::class.java -> OnboardingViewModel(container.database, container.settings)
        RoutinesViewModel::class.java -> RoutinesViewModel(container.database, RoutineRepository(container.database), container.workoutRepository, container.settings)
        SettingsViewModel::class.java -> SettingsViewModel(container.database, container.settings, BackupRepository(container.database)) { com.kryll.homefit.reminders.ReminderScheduler.sync(app) }
        ProgressViewModel::class.java -> ProgressViewModel(container.database, container.settings)
        PlanViewModel::class.java -> PlanViewModel(container.database, container.settings)
        else -> throw IllegalArgumentException("ViewModel desconocido: ${modelClass.name}")
    } as T
}

/** Preferencias de recordatorio tal como las consume la pantalla de ajustes. */
data class ReminderPrefsUi(val enabled: Boolean, val hour: Int, val minute: Int, val leadMinutes: Int, val exact: Boolean = false)

/** Resultado visible de las acciones de la semana; la UI lo traduce a texto y lo limpia con [PlanViewModel.clearMessage]. */
sealed interface PlanMessage {
    data class Generated(val count: Int) : PlanMessage
    data class Cleared(val count: Int) : PlanMessage
    data object NoRoutines : PlanMessage
    data object NoTrainingDays : PlanMessage
}

/** Pestaña Hoy: planificación semanal (asignar, autocompletar, limpiar, saltar, mover). */
class PlanViewModel(
    private val db: HomeFitDatabase,
    private val settings: SettingsStore,
) : ViewModel() {
    private val zone: java.time.ZoneId = java.time.ZoneId.systemDefault()

    data class PlanUi(
        val today: java.time.LocalDate,
        val zone: java.time.ZoneId,
        val daysMask: Int,
        val routines: List<Routine>,
        val scheduled: List<com.kryll.homefit.data.ScheduledSession>,
        val workouts: List<com.kryll.homefit.data.WorkoutSession>,
        val prefs: ReminderPrefsUi,
    )

    val ui: StateFlow<PlanUi?> = combine(
        settings.settings,
        db.profileDao().observeProfile(),
        db.routineDao().observeAll(),
        db.sessionDao().observeAllScheduled(),
        db.sessionDao().observeWorkouts(),
    ) { s, p, r, sch, w ->
        val prefs = s.reminderPrefs()
        PlanUi(
            today = java.time.LocalDate.now(zone), zone = zone,
            daysMask = p?.daysMask ?: 0, routines = r, scheduled = sch, workouts = w,
            prefs = ReminderPrefsUi(prefs.enabled, prefs.hour, prefs.minute, prefs.leadMinutes, prefs.exact),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Mensaje de resultado de las acciones de la semana (visible hasta que la UI lo limpia). */
    val message = MutableStateFlow<PlanMessage?>(null)
    fun clearMessage() { message.value = null }

    private fun startOf(d: java.time.LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun at(d: java.time.LocalDate, hour: Int, minute: Int) = d.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    fun assign(date: java.time.LocalDate, routineId: Long?) = viewModelScope.launch {
        val dao = db.sessionDao()
        dao.deleteScheduledBetween(startOf(date), startOf(date.plusDays(1)))
        if (routineId != null) {
            val prefs = settings.settings.first().reminderPrefs()
            dao.insertScheduled(com.kryll.homefit.data.ScheduledSession(routineId = routineId, startMillis = at(date, prefs.hour, prefs.minute), status = "SCHEDULED"))
        }
    }

    fun autoPlan() = viewModelScope.launch {
        val today = java.time.LocalDate.now(zone)
        val profile = db.profileDao().profile()
        val routines = db.routineDao().observeAll().first()
        if (routines.isEmpty()) { message.value = PlanMessage.NoRoutines; return@launch }
        val daysMask = profile?.daysMask ?: 0
        val pool = routines.filter { profile != null && it.goal == profile.goal && it.level == profile.level }
            .ifEmpty { routines.filter { profile != null && it.goal == profile.goal } }
            .ifEmpty { routines }
        val planned = com.kryll.homefit.domain.Planning.autoPlan(daysMask, pool.map { it.id }, today)
        // Sin días de entreno no se planifica nada: no borrar lo que ya hubiera planificado.
        if (planned.isEmpty()) { message.value = PlanMessage.NoTrainingDays; return@launch }
        val prefs = settings.settings.first().reminderPrefs()
        val dao = db.sessionDao()
        dao.deleteScheduledBetween(startOf(today), startOf(today.plusDays(7)))
        planned.forEach {
            dao.insertScheduled(com.kryll.homefit.data.ScheduledSession(routineId = it.routineId, startMillis = at(it.date, prefs.hour, prefs.minute), status = "SCHEDULED"))
        }
        message.value = PlanMessage.Generated(planned.size)
    }

    fun clearWeek() = viewModelScope.launch {
        val today = java.time.LocalDate.now(zone)
        val n = db.sessionDao().deleteScheduledBetween(startOf(today), startOf(today.plusDays(7)))
        message.value = PlanMessage.Cleared(n)
    }

    fun skip(sessionId: Long) = viewModelScope.launch { db.sessionDao().setScheduledStatus(sessionId, "SKIPPED") }

    fun move(entry: com.kryll.homefit.data.ScheduledSession, newDate: java.time.LocalDate) = viewModelScope.launch {
        val dao = db.sessionDao()
        dao.setScheduledStatus(entry.id, "MOVED")
        dao.insertScheduled(entry.copy(id = 0, startMillis = com.kryll.homefit.domain.movedStartMillis(entry.startMillis, zone, newDate), status = "SCHEDULED"))
    }

}

/** Pestaña Ajustes: preferencias, perfil/equipamiento, peso actual y copia de seguridad (estado y datos; la E/S de archivos queda en la UI). */
class SettingsViewModel(
    private val db: HomeFitDatabase,
    private val settings: SettingsStore,
    private val backupRepo: BackupRepository,
    private val reminderSync: suspend () -> Unit = {},
) : ViewModel() {
    val appSettings: StateFlow<AppSettings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val profile: StateFlow<UserProfile?> =
        db.profileDao().observeProfile().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val equipment: StateFlow<Set<String>> =
        db.profileDao().observeEquipment().map { it.toSet() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val latestWeightKg: StateFlow<Float?> =
        db.measurementDao().observeAll().map { list -> list.filter { it.weightKg != null }.maxByOrNull { it.epochDay }?.weightKg }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun setMessage(text: String?) { _message.value = text }

    private val _pendingImport = MutableStateFlow<Backup?>(null)
    val pendingImport: StateFlow<Backup?> = _pendingImport
    fun setPendingImport(backup: Backup?) { _pendingImport.value = backup }

    fun changeSettings(transform: (AppSettings) -> AppSettings) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { settings.update(transform) }

    fun saveReminderPrefs(n: ReminderPrefsUi) = viewModelScope.launch {
        settings.update { it.copy(reminderEnabled = n.enabled, reminderHour = n.hour, reminderMinute = n.minute, reminderLeadMinutes = n.leadMinutes, reminderExact = n.exact) }
        reminderSync()
    }

    /** Realinea el mecanismo del aviso (p. ej. al volver de conceder el permiso de alarma). */
    fun syncReminders() = viewModelScope.launch { reminderSync() }

    fun saveProfile(profile: UserProfile, equipment: Set<String>) = viewModelScope.launch {
        val dao = db.profileDao()
        dao.saveProfile(profile)
        dao.clearEquipment()
        dao.addEquipment(equipment.map { UserEquipment(it) })
    }

    /** Copia completa en JSON (el guardado a archivo lo hace la UI con su launcher). */
    suspend fun exportBackupJson(nowMillis: Long): String =
        BackupCodec.toJson(backupRepo.export(appSettings.value, nowMillis))

    /** Entrenos en CSV para hojas de cálculo. */
    suspend fun workoutsCsv(): String {
        val names = db.routineDao().observeAll().first().associate { it.id to it.name }
        val pairs = db.sessionDao().observeWorkouts().first().map { it to db.sessionDao().setsOf(it.id) }
        return com.kryll.homefit.domain.WorkoutCsv.build(pairs, names)
    }

    /** Parsea y valida una copia; lanza BackupException u otra excepción si no sirve. */
    suspend fun validateImport(text: String): Backup {
        val backup = BackupCodec.fromJson(text)
        backupRepo.validate(backup)
        return backup
    }

    /** Aplica la copia pendiente; la UI pasa el enganche posterior (reprogramar recordatorios). */
    suspend fun applyPendingImport(afterApply: suspend () -> Unit) {
        val b = _pendingImport.value ?: return
        _pendingImport.value = null
        BackupApplier(
            backupRepo,
            currentSettings = { settings.settings.first() },
            writeSettings = { n -> settings.update { n } },
            afterApply = afterApply,
        ).apply(b)
    }
}


/** Resultado de importar medidas desde CSV: insertadas, omitidas por duplicar el día y errores de parseo. */
data class MeasureImportResult(val imported: Int, val skipped: Int, val errors: List<String>)

/** Pestaña Progreso: historial (filas, resumen, volumen por músculo) y medidas corporales con exportación/importación CSV. La escritura del fichero y los textos del mensaje viven en la capa UI. */
class ProgressViewModel(
    private val db: HomeFitDatabase,
    settings: SettingsStore,
) : ViewModel() {
    val appSettings: StateFlow<AppSettings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val workouts: StateFlow<List<WorkoutSession>> =
        db.sessionDao().observeWorkouts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sets: StateFlow<List<SetLog>> =
        db.sessionDao().observeAllSets().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val routines: StateFlow<List<Routine>> =
        db.routineDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val exercises: StateFlow<List<Exercise>> =
        db.exerciseDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val measurements: StateFlow<List<BodyMeasurement>> =
        db.measurementDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Filas del historial derivadas de sesiones y series. */
    val rows: StateFlow<List<SessionRow>> =
        combine(workouts, sets, routines, exercises) { w, s, r, e ->
            ProgressStats.rows(w, s, r.associate { it.id to it.name }, e.associate { it.id to it.name })
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Resumen (totales, semana, racha, RPE medio) recalculado al cambiar el historial. */
    val summary: StateFlow<ProgressSummary> =
        rows.map { ProgressStats.summary(it, System.currentTimeMillis(), java.time.ZoneId.systemDefault()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ProgressStats.summary(emptyList(), System.currentTimeMillis(), java.time.ZoneId.systemDefault()))

    /** Volumen agrupado por músculo primario. */
    val volume: StateFlow<List<MuscleVolume>> =
        combine(rows, exercises) { r, e ->
            val primary = e.associate { it.id to it.primaryMuscle }
            ProgressStats.volumeByMuscle(r) { primary[it] }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Mensaje de exportación/importación para mostrar en pantalla; lo fija la capa UI con sus recursos de texto. */
    val message = MutableStateFlow<String?>(null)

    fun saveMeasurement(measurement: BodyMeasurement) {
        viewModelScope.launch {
            if (measurement.id != 0L) db.measurementDao().update(measurement) else db.measurementDao().insert(measurement)
        }
    }

    fun deleteMeasurement(id: Long) {
        viewModelScope.launch { db.measurementDao().deleteById(id) }
    }

    /** CSV de medidas en las unidades actuales. */
    fun measuresCsv(): String =
        MeasureCsv.build(measurements.value, appSettings.value.weightUnit, appSettings.value.lengthUnit)

    /** Importa medidas de un CSV: inserta solo las de días sin medida y devuelve el resultado para el mensaje. */
    fun importMeasuresCsv(text: String): MeasureImportResult {
        val parsed = MeasureCsv.parse(text, appSettings.value.weightUnit, appSettings.value.lengthUnit)
        val existing = measurements.value.map { it.epochDay }.toSet()
        val fresh = parsed.rows.filter { it.epochDay !in existing }
        viewModelScope.launch { fresh.forEach { db.measurementDao().insert(it) } }
        return MeasureImportResult(fresh.size, parsed.rows.size - fresh.size, parsed.errors)
    }
}
