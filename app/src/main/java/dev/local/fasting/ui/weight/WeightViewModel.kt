package dev.local.fasting.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.local.fasting.data.AppSettings
import dev.local.fasting.data.FastingRepository
import dev.local.fasting.data.SettingsStore
import dev.local.fasting.domain.TrendPoint
import dev.local.fasting.domain.WeightRecord
import dev.local.fasting.domain.WeightTag
import dev.local.fasting.domain.WeightTrend
import dev.local.fasting.domain.WeightUnit
import dev.local.fasting.ui.containerViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class WeightUiState(
    val entries: List<WeightRecord> = emptyList(),
    val trend: List<TrendPoint> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val message: String? = null,
) {
    val latest: WeightRecord? get() = entries.maxByOrNull { it.timestamp }
    val currentEma: Double? get() = trend.lastOrNull()?.emaKg

    /** Change in the smoothed trend over the last 7 measured days — raw day-to-day noise is not news. */
    val emaDeltaKg: Double?
        get() {
            val last = trend.lastOrNull() ?: return null
            val reference = trend.lastOrNull { !it.date.isAfter(last.date.minusDays(7)) }
                ?: trend.firstOrNull()
            if (reference == null || reference.date == last.date) return null
            return last.emaKg - reference.emaKg
        }
}

class WeightViewModel(
    private val repository: FastingRepository,
    private val settings: SettingsStore,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<WeightUiState> = combine(
        repository.weights,
        settings.settings,
        message,
    ) { weights, appSettings, msg ->
        WeightUiState(
            entries = weights.sortedBy { it.timestamp },
            trend = WeightTrend.trend(weights, zone, appSettings.emaPeriodDays),
            settings = appSettings,
            message = msg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WeightUiState(),
    )

    /**
     * Saves a measurement entered in the currently displayed unit. Storage is always kilograms, so
     * switching units never rewrites history.
     */
    fun saveEntry(
        id: Long = 0L,
        displayValue: Double,
        unit: WeightUnit,
        bodyFatPercent: Double?,
        tag: WeightTag,
        timestamp: Instant,
        note: String,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.saveWeight(
                    WeightRecord(
                        id = id,
                        timestamp = timestamp,
                        weightKg = unit.toKg(displayValue),
                        bodyFatPercent = bodyFatPercent,
                        tag = tag,
                        note = note,
                    )
                )
            }
                .onSuccess { message.value = if (id == 0L) "Weight logged" else "Entry updated" }
                .onFailure { message.value = it.message ?: "Could not save that entry" }
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            repository.deleteWeight(id)
            message.value = "Entry deleted"
        }
    }

    fun setUnit(unit: WeightUnit) = settings.setWeightUnit(unit)

    fun setEmaPeriod(days: Int) = settings.setEmaPeriodDays(days)

    fun consumeMessage() {
        message.value = null
    }

    /** Trend points limited to the trailing [days], for the chart window. */
    fun trendWindow(days: Int?): List<TrendPoint> {
        val trend = state.value.trend
        if (days == null) return trend
        val cutoff = LocalDate.now(zone).minusDays(days.toLong())
        return trend.filter { !it.date.isBefore(cutoff) }
    }

    companion object {
        val Factory = containerViewModelFactory { container ->
            WeightViewModel(container.repository, container.settings)
        }
    }
}
