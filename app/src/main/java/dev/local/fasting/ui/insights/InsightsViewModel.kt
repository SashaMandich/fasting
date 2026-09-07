package dev.local.fasting.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.local.fasting.data.AppSettings
import dev.local.fasting.data.FastingRepository
import dev.local.fasting.data.SettingsStore
import dev.local.fasting.domain.AnalyticsEngine
import dev.local.fasting.domain.AnalyticsRange
import dev.local.fasting.domain.DayActivity
import dev.local.fasting.domain.MealCompositionStats
import dev.local.fasting.domain.MonthActivity
import dev.local.fasting.domain.MonthlyAutophagy
import dev.local.fasting.domain.PhaseDistribution
import dev.local.fasting.domain.SymptomPoint
import dev.local.fasting.domain.WeightAutophagyPoint
import dev.local.fasting.ui.containerViewModelFactory
import dev.local.fasting.ui.tickerFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** What the user is currently looking at — one filter row scopes the whole screen. */
data class InsightsFilters(
    val range: AnalyticsRange = AnalyticsRange.MONTH,
    /** Month shown by the commitment calendar; null means "the current month". */
    val calendarMonth: YearMonth? = null,
    val distributionAsBar: Boolean = false,
    val selectedStageIndex: Int? = null,
    val selectedDay: DayActivity? = null,
    val selectedSymptomIndex: Int? = null,
)

data class InsightsUiState(
    val now: Instant = Instant.now(),
    val filters: InsightsFilters = InsightsFilters(),
    val settings: AppSettings = AppSettings(),
    val currentMonth: MonthlyAutophagy? = null,
    val distribution: PhaseDistribution = PhaseDistribution.EMPTY,
    val monthly: List<MonthlyAutophagy> = emptyList(),
    val calendar: MonthActivity? = null,
    val loggedMonths: List<YearMonth> = emptyList(),
    val weightVsAutophagy: List<WeightAutophagyPoint> = emptyList(),
    val symptomPoints: List<SymptomPoint> = emptyList(),
    val mealStats: List<MealCompositionStats> = emptyList(),
    val weightAutophagyCorrelation: Double? = null,
    val energyLengthCorrelation: Double? = null,
    val fastCountInRange: Int = 0,
    val longestFastHours: Double = 0.0,
) {
    val hasData: Boolean
        get() = distribution.totalHours > 0.0 || (calendar?.totalFastedHours ?: 0.0) > 0.0

    val canGoBackAMonth: Boolean
        get() {
            val current = calendar?.month ?: return false
            return loggedMonths.firstOrNull()?.isBefore(current) == true
        }

    val canGoForwardAMonth: Boolean
        get() {
            val current = calendar?.month ?: return false
            return loggedMonths.lastOrNull()?.isAfter(current) == true
        }
}

class InsightsViewModel(
    private val repository: FastingRepository,
    settings: SettingsStore,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val filters = MutableStateFlow(InsightsFilters())

    val state: StateFlow<InsightsUiState> = combine(
        tickerFlow(intervalSeconds = 60),
        repository.fasts,
        repository.weights,
        repository.symptoms,
        combine(filters, settings.settings) { f, s -> f to s },
    ) { now, fasts, weights, symptoms, (currentFilters, appSettings) ->
        val inRange = AnalyticsEngine.fastsInRange(fasts, currentFilters.range, now)
        val monthly = AnalyticsEngine.monthlyAutophagy(fasts, zone, now)
        val overlay = AnalyticsEngine.weightVsAutophagy(
            fasts = fasts,
            weights = weights,
            zone = zone,
            now = now,
            periodDays = appSettings.emaPeriodDays,
        )
        val points = AnalyticsEngine.symptomPoints(fasts, symptoms, zone, now)
        val months = AnalyticsEngine.loggedMonths(fasts, zone, now)
        val shownMonth = currentFilters.calendarMonth ?: months.last()

        InsightsUiState(
            now = now,
            filters = currentFilters,
            settings = appSettings,
            currentMonth = AnalyticsEngine.currentMonth(fasts, zone, now),
            distribution = AnalyticsEngine.phaseDistribution(fasts, currentFilters.range, now),
            monthly = monthly,
            calendar = AnalyticsEngine.monthActivity(fasts, shownMonth, zone, now),
            loggedMonths = months,
            weightVsAutophagy = overlay,
            symptomPoints = points,
            mealStats = AnalyticsEngine.statsByMeal(points),
            weightAutophagyCorrelation = AnalyticsEngine.monthlyEffortVsTrendChange(overlay)
                .let { pairs ->
                    AnalyticsEngine.pearson(pairs.map { it.first }, pairs.map { it.second })
                },
            energyLengthCorrelation = AnalyticsEngine.pearson(
                xs = points.map { it.elapsedHours },
                ys = points.map { it.energyLevel.toDouble() },
            ),
            fastCountInRange = inRange.size,
            longestFastHours = inRange.maxOfOrNull { it.durationHours(now) } ?: 0.0,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = InsightsUiState(),
        )

    fun setRange(range: AnalyticsRange) {
        filters.value = filters.value.copy(range = range, selectedStageIndex = null)
    }

    /** Steps the commitment calendar by [months], clamped to the range that actually has logs. */
    fun shiftCalendarMonth(months: Long) {
        val state = state.value
        val current = state.calendar?.month ?: return
        val available = state.loggedMonths
        val target = current.plusMonths(months)
        if (available.isEmpty() || target.isBefore(available.first()) || target.isAfter(available.last())) return
        filters.value = filters.value.copy(calendarMonth = target, selectedDay = null)
    }

    fun setDistributionAsBar(asBar: Boolean) {
        filters.value = filters.value.copy(distributionAsBar = asBar)
    }

    fun selectStage(index: Int?) {
        filters.value = filters.value.copy(selectedStageIndex = index)
    }

    fun selectDay(day: DayActivity?) {
        filters.value = filters.value.copy(selectedDay = day)
    }

    fun selectSymptomPoint(index: Int?) {
        filters.value = filters.value.copy(selectedSymptomIndex = index)
    }

    companion object {
        val Factory = containerViewModelFactory { container ->
            InsightsViewModel(container.repository, container.settings)
        }
    }
}
