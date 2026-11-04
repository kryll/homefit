package com.kryll.homefit.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kryll.homefit.data.AppContainer
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
) : ViewModel() {
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
class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        OnboardingViewModel::class.java -> OnboardingViewModel(container.database, container.settings)
        RoutinesViewModel::class.java -> RoutinesViewModel(container.database, RoutineRepository(container.database), container.workoutRepository)
        else -> throw IllegalArgumentException("ViewModel desconocido: ${modelClass.name}")
    } as T
}
