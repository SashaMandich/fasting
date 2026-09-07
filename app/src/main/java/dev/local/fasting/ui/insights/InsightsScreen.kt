package dev.local.fasting.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.local.fasting.domain.AnalyticsRange
import dev.local.fasting.domain.DayActivity
import dev.local.fasting.domain.FastStage
import dev.local.fasting.domain.MealComposition
import dev.local.fasting.domain.WeightUnit
import dev.local.fasting.ui.charts.ChartCard
import dev.local.fasting.ui.charts.ChartLegend
import dev.local.fasting.ui.charts.ChartSlice
import dev.local.fasting.ui.charts.ChartTableRow
import dev.local.fasting.ui.charts.ColumnChart
import dev.local.fasting.ui.charts.CommitmentCalendar
import dev.local.fasting.ui.charts.LegendItem
import dev.local.fasting.ui.charts.LinePoint
import dev.local.fasting.ui.charts.MarkShape
import dev.local.fasting.ui.charts.PhaseDonutChart
import dev.local.fasting.ui.charts.PhaseShareBar
import dev.local.fasting.ui.charts.ScatterChart
import dev.local.fasting.ui.charts.ScatterPoint
import dev.local.fasting.ui.charts.TrendLineChart
import dev.local.fasting.ui.components.DetailRow
import dev.local.fasting.ui.components.EmptyState
import dev.local.fasting.ui.components.RatioMeter
import dev.local.fasting.ui.components.SectionHeader
import dev.local.fasting.ui.components.SegmentedFilterRow
import dev.local.fasting.ui.components.StatTile
import dev.local.fasting.ui.format.formatDate
import dev.local.fasting.ui.format.formatHoursDecimal
import dev.local.fasting.ui.format.formatHoursMinutes
import dev.local.fasting.ui.format.formatMonth
import dev.local.fasting.ui.format.formatMonthShort
import dev.local.fasting.ui.format.formatPercent
import dev.local.fasting.ui.format.formatWeight
import dev.local.fasting.ui.theme.Viz

/**
 * Analytics suite. One filter row scopes the phase analytics; the heatmap carries its own labelled
 * window because 30/90/365-day consistency is a different question from "what did this week look
 * like". Every card ships a table view, and no chart plots two measures on two y-scales.
 */
@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onSetRange: (AnalyticsRange) -> Unit,
    onShiftCalendarMonth: (Long) -> Unit,
    onSetDistributionAsBar: (Boolean) -> Unit,
    onSelectStage: (Int?) -> Unit,
    onSelectDay: (DayActivity?) -> Unit,
    onSelectSymptomPoint: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Everything derived on-device from your own logs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SegmentedFilterRow(
            options = AnalyticsRange.entries.toList(),
            selected = state.filters.range,
            labelOf = { it.label },
            onSelect = onSetRange,
        )

        if (!state.hasData) {
            EmptyState(
                icon = Icons.Outlined.Insights,
                title = "Nothing to analyse yet",
                message = "Log a fast or two and the phase distribution, autophagy ratio and heatmap will fill in.",
            )
            return@Column
        }

        DeepAutophagyRatioCard(state)
        SummaryTiles(state)
        PhaseDistributionCard(state, onSetDistributionAsBar, onSelectStage)
        CommitmentCalendarCard(state, onShiftCalendarMonth, onSelectDay)
        WeightAutophagyCard(state)
        SymptomCorrelationCard(state, onSelectSymptomPoint)
        MealCompositionCard(state)

        Spacer(Modifier.height(24.dp))
    }
}

/** The headline monthly metric: hours at or past the autophagy boundary over all fasted hours. */
@Composable
private fun DeepAutophagyRatioCard(state: InsightsUiState) {
    val viz = Viz.colors
    val month = state.currentMonth ?: return

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Deep autophagy ratio · ${formatMonth(month.month)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = formatPercent(month.ratio, decimals = 1),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            RatioMeter(fraction = month.ratio.toFloat(), fillColor = viz.stageRamp[3])
            DetailRow("Autophagic hours (18h+ equivalent)", formatHoursDecimal(month.autophagyHours))
            DetailRow("Total fasted hours", formatHoursDecimal(month.totalFastedHours))
            DetailRow("Fasts this month", month.fastCount.toString())
            Text(
                text = "The 18h boundary is scaled per fast by its pre-fast meal, so a keto fast banks autophagic hours sooner.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryTiles(state: InsightsUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(
            label = "Fasts in range",
            value = state.fastCountInRange.toString(),
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "Longest fast",
            value = formatHoursMinutes(state.longestFastHours),
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "Autophagic share",
            value = formatPercent(state.distribution.autophagyRatio),
            caption = "in ${state.filters.range.label}",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PhaseDistributionCard(
    state: InsightsUiState,
    onSetAsBar: (Boolean) -> Unit,
    onSelectStage: (Int?) -> Unit,
) {
    val viz = Viz.colors
    val distribution = state.distribution
    val slices = FastStage.entries.map { stage ->
        ChartSlice(
            label = stage.displayName,
            value = distribution.hoursByStage[stage] ?: 0.0,
            color = viz.forStage(stage),
        )
    }
    val selected = state.filters.selectedStageIndex?.let { FastStage.entries.getOrNull(it) }

    ChartCard(
        title = "Biological phase distribution",
        subtitle = "Where your fasted time actually went · ${state.filters.range.label}",
        legend = FastStage.entries.map { stage ->
            LegendItem(
                label = stage.shortName,
                color = viz.forStage(stage),
                valueLabel = formatHoursDecimal(distribution.hoursByStage[stage] ?: 0.0),
            )
        },
        tableHeaders = listOf("Phase", "Hours", "Share"),
        tableRows = FastStage.entries.map { stage ->
            ChartTableRow(
                label = stage.displayName,
                values = listOf(
                    formatHoursDecimal(distribution.hoursByStage[stage] ?: 0.0),
                    formatPercent(distribution.fractionOf(stage), decimals = 1),
                ),
            )
        },
        readout = selected?.let { stage ->
            "${stage.displayName}: ${formatHoursDecimal(distribution.hoursByStage[stage] ?: 0.0)} " +
                "(${formatPercent(distribution.fractionOf(stage), decimals = 1)}) — ${stage.summary}"
        },
        footnote = "Phases run green (digesting) to red (extended repair). Because a green→red ramp is " +
            "hard to separate under colour-blind vision, every phase is also named with its hours here " +
            "and in the table view.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = !state.filters.distributionAsBar,
                    onClick = { onSetAsBar(false) },
                    label = { Text("Ring") },
                )
                FilterChip(
                    selected = state.filters.distributionAsBar,
                    onClick = { onSetAsBar(true) },
                    label = { Text("Stacked bar") },
                )
            }
            if (state.filters.distributionAsBar) {
                PhaseShareBar(slices = slices.filter { it.value > 0.0 })
            } else {
                PhaseDonutChart(
                    slices = slices.filter { it.value > 0.0 },
                    centerValue = formatHoursDecimal(distribution.totalHours),
                    centerCaption = "fasted",
                    selectedIndex = state.filters.selectedStageIndex,
                    onSelect = onSelectStage,
                )
            }
        }
    }
}

@Composable
private fun CommitmentCalendarCard(
    state: InsightsUiState,
    onShiftMonth: (Long) -> Unit,
    onSelectDay: (DayActivity?) -> Unit,
) {
    val calendar = state.calendar ?: return

    ChartCard(
        title = "Commitment calendar",
        subtitle = "Fasted hours per day, month by month",
        tableHeaders = listOf("Day", "Fasted", "24h+"),
        tableRows = calendar.days
            .filter { it.fastedHours > 0.25 }
            .sortedByDescending { it.date }
            .map { day ->
                ChartTableRow(
                    label = formatDate(day.date),
                    values = listOf(
                        formatHoursMinutes(day.fastedHours),
                        if (day.hasExtendedFast) "yes" else "—",
                    ),
                )
            },
        readout = state.filters.selectedDay?.let { day ->
            "${formatDate(day.date)}: ${formatHoursMinutes(day.fastedHours)} fasted" +
                if (day.hasExtendedFast) " · part of a ${formatHoursMinutes(day.longestFastHours)} fast" else ""
        },
    ) {
        CommitmentCalendar(
            month = calendar,
            selected = state.filters.selectedDay,
            canGoBack = state.canGoBackAMonth,
            canGoForward = state.canGoForwardAMonth,
            onPreviousMonth = { onShiftMonth(-1L) },
            onNextMonth = { onShiftMonth(1L) },
            onSelectDay = onSelectDay,
        )
    }
}

/**
 * Weight trend against fasting effort, as two panels sharing one month axis.
 *
 * Deliberately *not* a dual-axis overlay: kilograms and hours have no common scale, and aligning two
 * y-axes invents a correlation the data does not contain. Stacked panels let the eye compare shapes
 * month by month while each measure keeps an honest axis, and the coefficient is stated numerically.
 */
@Composable
private fun WeightAutophagyCard(state: InsightsUiState) {
    val viz = Viz.colors
    val unit: WeightUnit = state.settings.weightUnit
    val points = state.weightVsAutophagy
    if (points.isEmpty()) return

    val emaPoints = points.mapNotNull { point ->
        point.emaKg?.let {
            LinePoint(label = formatMonthShort(point.month), value = unit.fromKg(it))
        }
    }
    val correlation = state.weightAutophagyCorrelation

    ChartCard(
        title = "Weight trend vs autophagy hours",
        subtitle = "Two panels, one month axis — never two y-scales on one plot",
        legend = listOf(
            LegendItem("${state.settings.emaPeriodDays}-day weight EMA", viz.emphasis, shape = MarkShape.Line),
            LegendItem("Autophagic hours per month", viz.stageRamp[2]),
        ),
        tableHeaders = listOf("Month", "Trend", "Autophagic", "Cumulative"),
        tableRows = points.reversed().map { point ->
            ChartTableRow(
                label = formatMonth(point.month),
                values = listOf(
                    point.emaKg?.let { formatWeight(it, unit) } ?: "—",
                    formatHoursDecimal(point.autophagyHours),
                    formatHoursDecimal(point.cumulativeAutophagyHours),
                ),
            )
        },
        readout = correlation?.let {
            "Pearson r = ${"%.2f".format(it)} between a month's autophagic hours and that month's " +
                "change in the weight trend. Association only — a single-person log cannot show causation."
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Weight trend (${unit.label})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TrendLineChart(
                points = emaPoints,
                valueFormatter = { "%.1f".format(it) },
                showRawPoints = false,
                height = 140,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Autophagic hours per month",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ColumnChart(
                labels = points.map { formatMonthShort(it.month) },
                values = points.map { it.autophagyHours },
                valueFormatter = { "${it.toInt()}h" },
                barColor = viz.stageRamp[2],
            )
        }
    }
}

@Composable
private fun SymptomCorrelationCard(
    state: InsightsUiState,
    onSelect: (Int?) -> Unit,
) {
    val viz = Viz.colors
    val points = state.symptomPoints
    val shapes = mapOf(
        MealComposition.HIGH_CARB to MarkShape.Circle,
        MealComposition.BALANCED to MarkShape.Square,
        MealComposition.KETO_LOW_CARB to MarkShape.Triangle,
    )
    val scatter = points.map { point ->
        ScatterPoint(
            x = point.elapsedHours,
            y = point.energyLevel.toDouble(),
            color = viz.forMeal(point.meal),
            shape = shapes[point.meal] ?: MarkShape.Circle,
        )
    }
    val selected = state.filters.selectedSymptomIndex?.let { points.getOrNull(it) }
    val xMax = points.maxOfOrNull { it.elapsedHours }?.coerceAtLeast(6.0) ?: 24.0

    ChartCard(
        title = "Energy vs fast length",
        subtitle = "One mark per log, shaped and coloured by the pre-fast meal",
        legend = MealComposition.entries.map { meal ->
            LegendItem(
                label = meal.displayName,
                color = viz.forMeal(meal),
                shape = shapes[meal] ?: MarkShape.Circle,
            )
        },
        tableHeaders = listOf("Logged", "Into fast", "Energy", "Hunger"),
        tableRows = points.sortedByDescending { it.date }.take(40).map { point ->
            ChartTableRow(
                label = "${formatDate(point.date)} · ${point.meal.displayName}",
                values = listOf(
                    formatHoursMinutes(point.elapsedHours),
                    "${point.energyLevel}/5",
                    "${point.hungerLevel}/5",
                ),
            )
        },
        readout = selected?.let { point ->
            buildString {
                append("${formatDate(point.date)} · ${formatHoursMinutes(point.elapsedHours)} into a ")
                append("${point.meal.displayName.lowercase()} fast (${point.stage.shortName}): ")
                append("energy ${point.energyLevel}/5, clarity ${point.clarityLevel}/5, hunger ${point.hungerLevel}/5")
                if (point.symptoms.isNotEmpty()) {
                    append(" · ${point.symptoms.joinToString { it.displayName }}")
                }
            }
        } ?: state.energyLengthCorrelation?.let {
            "Pearson r = ${"%.2f".format(it)} between fast length and logged energy."
        },
        footnote = "Marks carry both hue and shape, so meal type stays readable without relying on colour.",
    ) {
        ScatterChart(
            points = scatter,
            xMax = xMax,
            yTicks = listOf(1.0 to "1", 3.0 to "3", 5.0 to "5"),
            xTickFormatter = { "${it.toInt()}h" },
            selectedIndex = state.filters.selectedSymptomIndex,
            onSelect = onSelect,
        )
    }
}

/** Mean subjective scores per pre-fast meal — three measures, each on its own 1–5 track. */
@Composable
private fun MealCompositionCard(state: InsightsUiState) {
    val viz = Viz.colors
    val stats = state.mealStats
    if (stats.isEmpty()) return

    ChartCard(
        title = "How each pre-fast meal felt",
        subtitle = "Averages of your own logs, grouped by meal composition",
        tableHeaders = listOf("Meal", "Energy", "Clarity", "Hunger"),
        tableRows = stats.map { stat ->
            ChartTableRow(
                label = "${stat.meal.displayName} (${stat.logCount})",
                values = listOf(
                    "%.1f".format(stat.averageEnergy),
                    "%.1f".format(stat.averageClarity),
                    "%.1f".format(stat.averageHunger),
                ),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            stats.forEach { stat ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(viz.forMeal(stat.meal)),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stat.meal.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${stat.logCount} logs · avg ${formatHoursMinutes(stat.averageFastHours)} in",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ScoreRow("Energy", stat.averageEnergy, viz.forMeal(stat.meal))
                    ScoreRow("Clarity", stat.averageClarity, viz.forMeal(stat.meal))
                    ScoreRow("Hunger", stat.averageHunger, viz.forMeal(stat.meal))
                }
            }
        }
    }
}

@Composable
private fun ScoreRow(label: String, value: Double, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.width(64.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RatioMeter(
            fraction = (value / 5.0).toFloat(),
            modifier = Modifier.weight(1f),
            fillColor = color,
            height = 8,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "%.1f".format(value),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
