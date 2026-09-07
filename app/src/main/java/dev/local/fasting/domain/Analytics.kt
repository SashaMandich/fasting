package dev.local.fasting.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Selectable window for the analytics screens. */
enum class AnalyticsRange(val label: String, val days: Int?) {
    WEEK("7d", 7),
    MONTH("30d", 30),
    QUARTER("90d", 90),
    ALL("All", null),
}

/** A calendar month of daily activity, ready to lay out as a grid. */
data class MonthActivity(
    val month: YearMonth,
    val days: List<DayActivity>,
) {
    /** Weekday column (0 = Monday) the 1st of the month falls in. */
    val firstDayColumn: Int get() = month.atDay(1).dayOfWeek.value - 1

    val activeDayCount: Int get() = days.count { it.fastedHours > 0.25 }

    val extendedDayCount: Int get() = days.count { it.hasExtendedFast }

    val totalFastedHours: Double get() = days.sumOf { it.fastedHours }
}

data class PhaseDistribution(val hoursByStage: Map<FastStage, Double>) {
    val totalHours: Double get() = hoursByStage.values.sum()

    fun fractionOf(stage: FastStage): Double {
        val total = totalHours
        return if (total <= 0.0) 0.0 else (hoursByStage[stage] ?: 0.0) / total
    }

    val autophagyHours: Double
        get() = hoursByStage.filterKeys { it.isAutophagic }.values.sum()

    /** Deep autophagy ratio for this distribution: autophagic hours / all fasted hours. */
    val autophagyRatio: Double
        get() = if (totalHours <= 0.0) 0.0 else autophagyHours / totalHours

    companion object {
        val EMPTY = PhaseDistribution(emptyMap())
    }
}

data class MonthlyAutophagy(
    val month: YearMonth,
    val fastCount: Int,
    val totalFastedHours: Double,
    val autophagyHours: Double,
) {
    /** Hours at or past the Early Autophagy boundary, over total fasted hours. */
    val ratio: Double get() = if (totalFastedHours <= 0.0) 0.0 else autophagyHours / totalFastedHours
}

data class DayActivity(
    val date: LocalDate,
    val fastedHours: Double,
    val hasExtendedFast: Boolean,
    val longestFastHours: Double,
)

/** A month of fasting effort paired with the weight trend at month end. */
data class WeightAutophagyPoint(
    val month: YearMonth,
    val autophagyHours: Double,
    val cumulativeAutophagyHours: Double,
    val emaKg: Double?,
)

/** One symptom log positioned against how deep into a fast it was recorded. */
data class SymptomPoint(
    val date: LocalDate,
    val elapsedHours: Double,
    val meal: MealComposition,
    val stage: FastStage,
    val energyLevel: Int,
    val clarityLevel: Int,
    val hungerLevel: Int,
    val symptoms: Set<Symptom>,
)

/** Mean subjective scores grouped by pre-fast meal composition. */
data class MealCompositionStats(
    val meal: MealComposition,
    val logCount: Int,
    val averageEnergy: Double,
    val averageClarity: Double,
    val averageHunger: Double,
    val averageFastHours: Double,
)

/**
 * Derives every analytics view from the raw logs.
 *
 * Stage attribution always runs through [StageEngine], so a fast's meal composition is respected
 * everywhere: two 20-hour fasts contribute different autophagy hours if one followed a keto meal
 * and the other a carb-heavy one.
 */
object AnalyticsEngine {

    /** Fasts overlapping the range, newest first. An open fast is measured up to [now]. */
    fun fastsInRange(
        fasts: List<FastRecord>,
        range: AnalyticsRange,
        now: Instant,
    ): List<FastRecord> {
        val days = range.days ?: return fasts
        val cutoff = now.minus(Duration.ofDays(days.toLong()))
        return fasts.filter { (it.end ?: now).isAfter(cutoff) }
    }

    fun phaseDistribution(
        fasts: List<FastRecord>,
        range: AnalyticsRange,
        now: Instant,
    ): PhaseDistribution {
        val totals = mutableMapOf<FastStage, Double>()
        for (fast in fastsInRange(fasts, range, now)) {
            StageEngine.breakdown(fast.durationHours(now), fast.meal).forEach { (stage, hours) ->
                totals[stage] = (totals[stage] ?: 0.0) + hours
            }
        }
        return PhaseDistribution(totals.filterValues { it > 0.0 })
    }

    /**
     * Monthly deep autophagy ratio, oldest month first. A fast is attributed to the month it
     * started in, so a fast crossing midnight on the 1st stays in one bucket.
     */
    fun monthlyAutophagy(
        fasts: List<FastRecord>,
        zone: ZoneId,
        now: Instant,
    ): List<MonthlyAutophagy> =
        fasts
            .groupBy { YearMonth.from(it.start.atZone(zone)) }
            .toSortedMap()
            .map { (month, monthFasts) ->
                MonthlyAutophagy(
                    month = month,
                    fastCount = monthFasts.size,
                    totalFastedHours = monthFasts.sumOf { it.durationHours(now) },
                    autophagyHours = monthFasts.sumOf {
                        StageEngine.autophagyHours(it.durationHours(now), it.meal)
                    },
                )
            }

    /** Current-month ratio, or an empty month if nothing has been logged yet. */
    fun currentMonth(
        fasts: List<FastRecord>,
        zone: ZoneId,
        now: Instant,
    ): MonthlyAutophagy {
        val month = YearMonth.from(now.atZone(zone))
        return monthlyAutophagy(fasts, zone, now).firstOrNull { it.month == month }
            ?: MonthlyAutophagy(month, 0, 0.0, 0.0)
    }

    /**
     * Per-day fasted hours for a date range, oldest first. Unlike the monthly rollup this splits a
     * fast across the days it actually spans, so an overnight fast shows on both days.
     */
    fun dailyActivity(
        fasts: List<FastRecord>,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId,
        now: Instant,
    ): List<DayActivity> {
        val dayCount = (java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1)
            .coerceAtLeast(0L)
        return (0 until dayCount).map { offset ->
            val date = from.plusDays(offset)
            val dayStart = date.atStartOfDay(zone).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
            var hours = 0.0
            var longest = 0.0
            var extended = false
            for (fast in fasts) {
                val overlap = overlapHours(fast.start, fast.end ?: now, dayStart, dayEnd)
                if (overlap <= 0.0) continue
                hours += overlap
                val total = fast.durationHours(now)
                longest = maxOf(longest, total)
                if (total >= FastStage.EXTENDED_FAST_HOURS) extended = true
            }
            DayActivity(
                date = date,
                fastedHours = hours.coerceAtMost(24.0),
                hasExtendedFast = extended,
                longestFastHours = longest,
            )
        }
    }

    /** One calendar month of daily activity, for the commitment calendar. */
    fun monthActivity(
        fasts: List<FastRecord>,
        month: YearMonth,
        zone: ZoneId,
        now: Instant,
    ): MonthActivity = MonthActivity(
        month = month,
        days = dailyActivity(fasts, month.atDay(1), month.atEndOfMonth(), zone, now),
    )

    /** Months that contain at least one logged fast, oldest first — the calendar's navigable range. */
    fun loggedMonths(fasts: List<FastRecord>, zone: ZoneId, now: Instant): List<YearMonth> {
        if (fasts.isEmpty()) return listOf(YearMonth.from(now.atZone(zone)))
        val first = fasts.minOf { it.start }.atZone(zone).let(YearMonth::from)
        val last = YearMonth.from(now.atZone(zone))
        val months = mutableListOf<YearMonth>()
        var cursor = first
        while (!cursor.isAfter(last)) {
            months += cursor
            cursor = cursor.plusMonths(1)
        }
        return months
    }

    /** Weight EMA at each month end overlaid on that month's autophagy hours. */
    fun weightVsAutophagy(
        fasts: List<FastRecord>,
        weights: List<WeightRecord>,
        zone: ZoneId,
        now: Instant,
        periodDays: Int = WeightTrend.DEFAULT_PERIOD_DAYS,
    ): List<WeightAutophagyPoint> {
        val trend = WeightTrend.trend(weights, zone, periodDays)
        var cumulative = 0.0
        return monthlyAutophagy(fasts, zone, now).map { month ->
            cumulative += month.autophagyHours
            WeightAutophagyPoint(
                month = month.month,
                autophagyHours = month.autophagyHours,
                cumulativeAutophagyHours = cumulative,
                emaKg = WeightTrend.emaAsOf(trend, month.month.atEndOfMonth()),
            )
        }
    }

    /**
     * Pairs each month's autophagic hours with the *change* in the weight trend over that month.
     *
     * Correlating against the cumulative hours instead would be spurious: a running total and a
     * steadily falling weight are both monotone, so they correlate at nearly ±1 whatever the real
     * relationship is. Month-on-month change is the honest comparison.
     */
    fun monthlyEffortVsTrendChange(points: List<WeightAutophagyPoint>): List<Pair<Double, Double>> =
        points.zipWithNext().mapNotNull { (previous, current) ->
            val from = previous.emaKg ?: return@mapNotNull null
            val to = current.emaKg ?: return@mapNotNull null
            current.autophagyHours to (to - from)
        }

    /**
     * Positions each symptom log against how far into its fast it was recorded. Logs are matched to
     * their explicit fast id first, then to whichever fast was running at the time.
     */
    fun symptomPoints(
        fasts: List<FastRecord>,
        logs: List<SymptomLog>,
        zone: ZoneId,
        now: Instant,
    ): List<SymptomPoint> {
        val byId = fasts.associateBy { it.id }
        return logs.mapNotNull { log ->
            val fast = byId[log.fastId] ?: fasts.firstOrNull { fast ->
                !log.timestamp.isBefore(fast.start) && log.timestamp.isBefore(fast.end ?: now)
            } ?: return@mapNotNull null
            val elapsed = Duration.between(fast.start, log.timestamp).toHoursDecimal()
            if (elapsed < 0.0) return@mapNotNull null
            SymptomPoint(
                date = log.timestamp.atZone(zone).toLocalDate(),
                elapsedHours = elapsed,
                meal = fast.meal,
                stage = StageEngine.stageAt(elapsed, fast.meal),
                energyLevel = log.energyLevel,
                clarityLevel = log.clarityLevel,
                hungerLevel = log.hungerLevel,
                symptoms = log.symptoms,
            )
        }
    }

    /** Mean subjective scores per pre-fast meal composition, for the meal-type correlation chart. */
    fun statsByMeal(points: List<SymptomPoint>): List<MealCompositionStats> =
        points.groupBy { it.meal }.map { (meal, group) ->
            MealCompositionStats(
                meal = meal,
                logCount = group.size,
                averageEnergy = group.map { it.energyLevel }.average(),
                averageClarity = group.map { it.clarityLevel }.average(),
                averageHunger = group.map { it.hungerLevel }.average(),
                averageFastHours = group.map { it.elapsedHours }.average(),
            )
        }.sortedBy { it.meal.ordinal }

    /**
     * Pearson correlation coefficient, or `null` when there is too little variation to be meaningful.
     * Used to annotate the correlation charts rather than to make claims.
     */
    fun pearson(xs: List<Double>, ys: List<Double>): Double? {
        if (xs.size != ys.size || xs.size < 3) return null
        val meanX = xs.average()
        val meanY = ys.average()
        var covariance = 0.0
        var varX = 0.0
        var varY = 0.0
        for (i in xs.indices) {
            val dx = xs[i] - meanX
            val dy = ys[i] - meanY
            covariance += dx * dy
            varX += dx * dx
            varY += dy * dy
        }
        if (varX <= 0.0 || varY <= 0.0) return null
        return covariance / kotlin.math.sqrt(varX * varY)
    }

    private fun overlapHours(
        startA: Instant,
        endA: Instant,
        startB: Instant,
        endB: Instant,
    ): Double {
        val start = maxOf(startA, startB)
        val end = minOf(endA, endB)
        if (!end.isAfter(start)) return 0.0
        return Duration.between(start, end).toHoursDecimal()
    }
}
