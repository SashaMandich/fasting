package dev.local.fasting.ui.format

import dev.local.fasting.domain.WeightUnit
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
private val dayTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")

/** "18h 42m", or "2d 4h" once past a day. Negative input is clamped to zero. */
fun formatHoursMinutes(hours: Double): String {
    val safe = hours.coerceAtLeast(0.0)
    val totalMinutes = (safe * 60).roundToInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h >= 24 -> "${h / 24}d ${h % 24}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

/** Precise elapsed clock for the in-app timer: "18:42:07". */
fun formatClock(duration: Duration): String {
    val total = duration.seconds.coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
}

/** "18.5h" — compact hour totals for charts and stat tiles. */
fun formatHoursDecimal(hours: Double, decimals: Int = 1): String =
    String.format(Locale.getDefault(), "%.${decimals}fh", hours.coerceAtLeast(0.0))

fun formatPercent(fraction: Double, decimals: Int = 0): String =
    String.format(Locale.getDefault(), "%.${decimals}f%%", fraction * 100)

fun formatWeight(weightKg: Double, unit: WeightUnit, decimals: Int = 1): String =
    String.format(Locale.getDefault(), "%.${decimals}f %s", unit.fromKg(weightKg), unit.label)

fun formatWeightDelta(deltaKg: Double, unit: WeightUnit): String {
    val value = unit.fromKg(deltaKg)
    val sign = if (value > 0) "+" else if (value < 0) "−" else ""
    return String.format(Locale.getDefault(), "%s%.2f %s", sign, abs(value), unit.label)
}

fun formatTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    timeFormatter.format(instant.atZone(zone))

fun formatDay(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    dayFormatter.format(instant.atZone(zone))

fun formatDayTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    dayTimeFormatter.format(instant.atZone(zone))

fun formatDate(date: LocalDate): String = dayFormatter.format(date)

fun formatMonth(month: YearMonth): String =
    "${month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${month.year}"

fun formatMonthShort(month: YearMonth): String =
    month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
