package dev.local.fasting.domain

import java.time.LocalDate
import java.time.ZoneId

/** One day of measurement plus its smoothed value. */
data class TrendPoint(
    val date: LocalDate,
    val rawKg: Double,
    val emaKg: Double,
    val bodyFatPercent: Double?,
)

/**
 * Exponential moving average over daily weight measurements.
 *
 * Multiple measurements on the same calendar day are averaged first, then the EMA advances one
 * *measured* day at a time (days without a measurement do not decay the trend — a missed weigh-in
 * is missing data, not evidence of a change).
 */
object WeightTrend {

    const val DEFAULT_PERIOD_DAYS: Int = 7

    /** Standard EMA smoothing factor: 2 / (N + 1). */
    fun alpha(periodDays: Int): Double {
        require(periodDays >= 1) { "periodDays must be >= 1" }
        return 2.0 / (periodDays + 1.0)
    }

    /** Collapses raw entries into one averaged measurement per local calendar day, ascending. */
    fun dailyAverages(entries: List<WeightRecord>, zone: ZoneId): List<TrendPoint> =
        entries
            .groupBy { it.timestamp.atZone(zone).toLocalDate() }
            .toSortedMap()
            .map { (date, dayEntries) ->
                val weight = dayEntries.sumOf { it.weightKg } / dayEntries.size
                val fatReadings = dayEntries.mapNotNull { it.bodyFatPercent }
                TrendPoint(
                    date = date,
                    rawKg = weight,
                    emaKg = weight,
                    bodyFatPercent = fatReadings.takeIf { it.isNotEmpty() }?.average(),
                )
            }

    /** Daily averages with an [periodDays]-day EMA trendline attached. */
    fun trend(
        entries: List<WeightRecord>,
        zone: ZoneId,
        periodDays: Int = DEFAULT_PERIOD_DAYS,
    ): List<TrendPoint> {
        val daily = dailyAverages(entries, zone)
        if (daily.isEmpty()) return emptyList()
        val a = alpha(periodDays)
        var ema = daily.first().rawKg
        return daily.mapIndexed { index, point ->
            ema = if (index == 0) point.rawKg else a * point.rawKg + (1 - a) * ema
            point.copy(emaKg = ema)
        }
    }

    /** Smoothed weight as of [date], carrying the last known EMA forward. Null before the first entry. */
    fun emaAsOf(trend: List<TrendPoint>, date: LocalDate): Double? =
        trend.lastOrNull { !it.date.isAfter(date) }?.emaKg
}
