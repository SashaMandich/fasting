package dev.local.fasting.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.local.fasting.domain.DayActivity
import dev.local.fasting.domain.MonthActivity
import dev.local.fasting.ui.format.formatMonth
import dev.local.fasting.ui.theme.Viz
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * Commitment calendar: a real month grid, one cell per day, coloured on the same green → red scale as
 * the biological phases — so one glance shows both consistency and how deep each day went.
 *
 * Days that were part of a 24h+ fast carry a dot as a second, non-colour cue. Tapping a day reveals
 * its exact hours; the card's table view lists every day, so nothing is tap-gated.
 */
@Composable
fun CommitmentCalendar(
    month: MonthActivity,
    selected: DayActivity?,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (DayActivity?) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonthArrow(
                icon = Icons.Outlined.ChevronLeft,
                description = "Previous month",
                enabled = canGoBack,
                onClick = onPreviousMonth,
            )
            Text(
                text = formatMonth(month.month),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            MonthArrow(
                icon = Icons.Outlined.ChevronRight,
                description = "Next month",
                enabled = canGoForward,
                onClick = onNextMonth,
            )
        }

        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    text = day.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Leading blanks so the 1st lands under its real weekday.
        val cells: List<DayActivity?> = List(month.firstDayColumn) { null } + month.days
        cells.chunked(7).forEach { week ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                week.forEach { day ->
                    CalendarCell(
                        day = day,
                        isSelected = day != null && day == selected,
                        isToday = day?.date == today,
                        onClick = { onSelectDay(if (day == selected) null else day) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Text(
            text = "${month.activeDayCount} of ${month.days.size} days fasted · " +
                "${month.extendedDayCount} touched a 24h+ fast",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CalendarScale()
    }
}

@Composable
private fun MonthArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        )
    }
}

@Composable
private fun CalendarCell(
    day: DayActivity?,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viz = Viz.colors
    if (day == null) {
        Box(modifier.aspectRatio(1f))
        return
    }
    val fill = bucketColor(day.fastedHours, viz.heatmapEmpty, viz.heatmapRamp)
    val hasFast = day.fastedHours > 0.25
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(fill)
            .then(
                when {
                    isSelected -> Modifier.border(2.dp, viz.emphasis, shape)
                    isToday -> Modifier.border(1.dp, viz.muted, shape)
                    else -> Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                ),
                // The label sits inside a filled cell, so it takes ink by the fill's luminance.
                color = if (hasFast) viz.inkOn(fill) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (day.hasExtendedFast) {
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(viz.inkOn(fill)),
                )
            }
        }
    }
}

/** Scale legend: the ramp read left (no fasting) to right (a full day fasted), plus the 24h+ dot. */
@Composable
fun CalendarScale(modifier: Modifier = Modifier) {
    val viz = Viz.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "0h",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        (listOf(viz.heatmapEmpty) + viz.heatmapRamp).forEach { color ->
            Box(
                Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
        Text(
            text = "24h",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Canvas(Modifier.size(12.dp)) {
            drawRoundRect(color = viz.heatmapRamp.last(), cornerRadius = CornerRadius(3.dp.toPx()))
            drawCircle(color = viz.surface, radius = size.width * 0.16f, center = center)
        }
        Text(
            text = "24h+ fast",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun EmptyChartHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun bucketColor(hours: Double, empty: Color, ramp: List<Color>): Color = when {
    hours <= 0.25 -> empty
    hours < 6 -> ramp[0]
    hours < 12 -> ramp[1]
    hours < 18 -> ramp[2]
    else -> ramp[3]
}
