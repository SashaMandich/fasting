package dev.local.fasting.ui.fast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.local.fasting.data.AppSettings
import dev.local.fasting.data.FastingRepository
import dev.local.fasting.data.SettingsStore
import dev.local.fasting.domain.FastProgress
import dev.local.fasting.domain.FastRecord
import dev.local.fasting.domain.FastingGoal
import dev.local.fasting.domain.GoalProgress
import dev.local.fasting.domain.MealComposition
import dev.local.fasting.domain.StageEngine
import dev.local.fasting.domain.Symptom
import dev.local.fasting.domain.SymptomLog
import dev.local.fasting.tracking.FastTracker
import dev.local.fasting.ui.containerViewModelFactory
import dev.local.fasting.ui.format.formatHoursMinutes
import dev.local.fasting.ui.tickerFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

data class FastUiState(
    val now: Instant = Instant.now(),
    val activeFast: FastRecord? = null,
    val progress: FastProgress? = null,
    /** Goal progress of the running fast; `null` when nothing is running. */
    val goalProgress: GoalProgress? = null,
    val plannedMeal: MealComposition = MealComposition.DEFAULT,
    val plannedGoal: FastingGoal = FastingGoal.DEFAULT,
    val settings: AppSettings = AppSettings(),
    val recentFasts: List<FastRecord> = emptyList(),
    val message: String? = null,
) {
    val isFasting: Boolean get() = activeFast != null

    /** The goal on show: the running fast's, or the one queued up for the next fast. */
    val displayGoal: FastingGoal get() = activeFast?.goal ?: plannedGoal
}

class FastViewModel(
    private val repository: FastingRepository,
    private val tracker: FastTracker,
    private val settings: SettingsStore,
) : ViewModel() {

    private val plannedMeal = MutableStateFlow<MealComposition?>(null)
    private val plannedGoal = MutableStateFlow<FastingGoal?>(null)
    private val message = MutableStateFlow<String?>(null)

    /** Held together so the outer [combine] stays within its five-source arity. */
    private data class Pending(
        val meal: MealComposition?,
        val goal: FastingGoal?,
        val message: String?,
    )

    val state: StateFlow<FastUiState> = combine(
        tickerFlow(),
        repository.activeFast,
        repository.fasts,
        settings.settings,
        combine(plannedMeal, plannedGoal, message, ::Pending),
    ) { now, active, fasts, appSettings, pending ->
        FastUiState(
            now = now,
            activeFast = active,
            progress = active?.progress(now),
            goalProgress = active?.goalProgress(now),
            plannedMeal = pending.meal ?: active?.meal ?: appSettings.defaultMeal,
            plannedGoal = pending.goal ?: active?.goal ?: appSettings.defaultGoal,
            settings = appSettings,
            recentFasts = fasts.take(5),
            message = pending.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FastUiState(),
    )

    fun selectMeal(meal: MealComposition) {
        plannedMeal.value = meal
    }

    fun selectGoal(goal: FastingGoal) {
        plannedGoal.value = goal
    }

    fun startFast() {
        val meal = state.value.plannedMeal
        val goal = state.value.plannedGoal
        viewModelScope.launch {
            tracker.startFast(meal, goal)
            plannedMeal.value = null
            plannedGoal.value = null
            message.value = if (goal.isOpen) {
                "Fast started — open timer, ${meal.displayName.lowercase()} pre-fast meal"
            } else {
                "Fast started — ${goal.label} goal, ${meal.displayName.lowercase()} pre-fast meal"
            }
        }
    }

    fun endFast() {
        viewModelScope.launch {
            val closed = tracker.endFast()
            message.value = closed?.let { fast ->
                val hours = fast.durationHours(Instant.now())
                val autophagy = StageEngine.autophagyHours(hours, fast.meal)
                val target = fast.goal.fastHours
                val verdict = when {
                    target == null -> ""
                    hours >= target -> " · ${fast.goal.label} goal met"
                    else -> " · ${formatHoursMinutes(target - hours)} short of ${fast.goal.label}"
                }
                "Fast logged: ${"%.1f".format(hours)}h, of which " +
                    "${"%.1f".format(autophagy)}h autophagic$verdict"
            }
        }
    }

    /** Changes the meal composition of the running fast, re-scaling every stage boundary. */
    fun updateActiveMeal(meal: MealComposition) {
        val active = state.value.activeFast ?: return
        viewModelScope.launch {
            repository.saveFast(active.copy(meal = meal))
            tracker.sync()
        }
    }

    /**
     * Retargets the running fast. Switching to a shorter goal that is already behind the elapsed
     * time is allowed — it simply lands the fast in the reached state, which is the general timer.
     */
    fun updateActiveGoal(goal: FastingGoal) {
        val active = state.value.activeFast ?: return
        viewModelScope.launch {
            repository.saveFast(active.copy(goal = goal))
            tracker.sync()
        }
    }

    /** Nudges the running fast's start time, for when logging happened after the fact. */
    fun adjustActiveStart(start: Instant) {
        val active = state.value.activeFast ?: return
        viewModelScope.launch {
            runCatching { repository.saveFast(active.copy(start = start)) }
                .onFailure { message.value = it.message }
                .onSuccess { tracker.sync() }
        }
    }

    fun logHowIFeel(
        energy: Int,
        clarity: Int,
        hunger: Int,
        symptoms: Set<Symptom>,
        note: String,
    ) {
        val active = state.value.activeFast
        viewModelScope.launch {
            runCatching {
                repository.saveSymptomLog(
                    SymptomLog(
                        timestamp = Instant.now(),
                        fastId = active?.id,
                        energyLevel = energy,
                        clarityLevel = clarity,
                        hungerLevel = hunger,
                        symptoms = symptoms,
                        note = note,
                    )
                )
            }
                .onSuccess { message.value = "Logged how this fast feels" }
                .onFailure { message.value = it.message }
        }
    }

    fun setDefaultMeal(meal: MealComposition) = settings.setDefaultMeal(meal)

    fun setDefaultGoal(goal: FastingGoal) = settings.setDefaultGoal(goal)

    /**
     * Deletes every fast, weight entry and symptom log. Irreversible — there is no backend copy —
     * so the settings sheet asks twice before calling this.
     */
    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
            tracker.sync()
            message.value = "All history deleted"
        }
    }

    /** Toggling the live notification takes effect immediately for the running fast. */
    fun setLiveNotification(enabled: Boolean) {
        settings.setLiveNotificationEnabled(enabled)
        viewModelScope.launch { tracker.sync() }
    }

    fun consumeMessage() {
        message.value = null
    }

    companion object {
        val Factory = containerViewModelFactory { container ->
            FastViewModel(container.repository, container.tracker, container.settings)
        }
    }
}
