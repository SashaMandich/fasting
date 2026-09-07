package dev.local.fasting.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.local.fasting.data.FastingRepository
import dev.local.fasting.domain.FastRecord
import dev.local.fasting.domain.FastStage
import dev.local.fasting.domain.FastingGoal
import dev.local.fasting.domain.MealComposition
import dev.local.fasting.domain.StageEngine
import dev.local.fasting.domain.SymptomLog
import dev.local.fasting.tracking.FastTracker
import dev.local.fasting.ui.containerViewModelFactory
import dev.local.fasting.ui.tickerFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/** A logged fast plus the derived numbers the list shows. */
data class FastListItem(
    val record: FastRecord,
    val durationHours: Double,
    val autophagyHours: Double,
    val stageReached: FastStage,
    val symptomLogs: List<SymptomLog>,
)

data class HistoryUiState(
    val now: Instant = Instant.now(),
    val items: List<FastListItem> = emptyList(),
    val message: String? = null,
) {
    val hasEntries: Boolean get() = items.isNotEmpty()
}

/**
 * Full CRUD over the fasting log. Every timestamp is editable after the fact — a tracker that can
 * only record "now" is useless the moment you forget to press start.
 */
class HistoryViewModel(
    private val repository: FastingRepository,
    private val tracker: FastTracker,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<HistoryUiState> = combine(
        tickerFlow(intervalSeconds = 30),
        repository.fasts,
        repository.symptoms,
        message,
    ) { now, fasts, symptoms, msg ->
        HistoryUiState(
            now = now,
            items = fasts.map { fast ->
                val hours = fast.durationHours(now)
                FastListItem(
                    record = fast,
                    durationHours = hours,
                    autophagyHours = StageEngine.autophagyHours(hours, fast.meal),
                    stageReached = StageEngine.stageAt(hours, fast.meal),
                    symptomLogs = symptoms.filter { it.fastId == fast.id },
                )
            },
            message = msg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    /** Creates or edits a fast. An [end] of null leaves the fast running. */
    fun saveFast(
        id: Long,
        start: Instant,
        end: Instant?,
        meal: MealComposition,
        goal: FastingGoal,
        note: String,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.saveFast(
                    FastRecord(
                        id = id,
                        start = start,
                        end = end,
                        meal = meal,
                        goal = goal,
                        note = note,
                    )
                )
            }
                .onSuccess {
                    tracker.sync()
                    message.value = if (id == 0L) "Fast added" else "Fast updated"
                }
                .onFailure { message.value = it.message ?: "Could not save that fast" }
        }
    }

    fun deleteFast(id: Long) {
        viewModelScope.launch {
            repository.deleteFast(id)
            tracker.sync()
            message.value = "Fast deleted"
        }
    }

    fun deleteSymptomLog(id: Long) {
        viewModelScope.launch {
            repository.deleteSymptomLog(id)
            message.value = "Log deleted"
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    companion object {
        val Factory = containerViewModelFactory { container ->
            HistoryViewModel(container.repository, container.tracker)
        }
    }
}
