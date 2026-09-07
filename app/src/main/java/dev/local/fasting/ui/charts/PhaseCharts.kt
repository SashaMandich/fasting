package dev.local.fasting.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.local.fasting.ui.theme.Viz
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

data class ChartSlice(val label: String, val value: Double, val color: Color)

/**
 * Part-to-whole for the five biological phases (five ordered segments, inside the ≤6 limit where a
 * ring still reads at a glance). Segments are separated by a surface gap rather than a stroke, and
 * every value is also in the card's table view.
 */
@Composable
fun PhaseDonutChart(
    slices: List<ChartSlice>,
    centerValue: String,
    centerCaption: String,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    height: Int = 196,
) {
    val total = slices.sumOf { it.value }.takeIf { it > 0.0 } ?: 0.0

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .pointerInput(slices, total, selectedIndex) {
                    detectTapGestures { tap ->
                        onSelect(hitSlice(tap, size.width.toFloat(), size.height.toFloat(), slices, total, selectedIndex))
                    }
                },
        ) {
            if (total <= 0.0) return@Canvas
            val ringThickness = 28.dp.toPx()
            val selectedExtra = 6.dp.toPx()
            val gapPx = MarkSpec.SURFACE_GAP_DP.dp.toPx()
            val diameter = min(size.width, size.height) - ringThickness - selectedExtra
            val radius = diameter / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset(center.x - radius, center.y - radius)
            // A fixed pixel gap becomes an angular gap at this radius.
            val gapDegrees = (gapPx / (2f * Math.PI.toFloat() * radius)) * 360f

            var startAngle = -90f
            slices.forEachIndexed { index, slice ->
                val sweep = (slice.value / total).toFloat() * 360f
                if (sweep > 0f) {
                    val isSelected = index == selectedIndex
                    val visibleSweep = (sweep - gapDegrees).coerceAtLeast(0.6f)
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle + gapDegrees / 2f,
                        sweepAngle = visibleSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = ringThickness + if (isSelected) selectedExtra else 0f),
                    )
                }
                startAngle += sweep
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerValue,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = centerCaption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun hitSlice(
    tap: Offset,
    width: Float,
    height: Float,
    slices: List<ChartSlice>,
    total: Double,
    selectedIndex: Int?,
): Int? {
    if (total <= 0.0) return null
    val center = Offset(width / 2f, height / 2f)
    val distance = hypot(tap.x - center.x, tap.y - center.y)
    val outerRadius = min(width, height) / 2f
    if (distance > outerRadius || distance < outerRadius * 0.35f) return null

    val degrees = ((Math.toDegrees(
        atan2((tap.y - center.y).toDouble(), (tap.x - center.x).toDouble())
    ) + 450.0) % 360.0)

    var start = 0.0
    slices.forEachIndexed { index, slice ->
        val sweep = slice.value / total * 360.0
        if (degrees >= start && degrees < start + sweep) {
            return if (index == selectedIndex) null else index
        }
        start += sweep
    }
    return null
}

/**
 * Horizontal part-to-whole bar — the alternate reading of the same distribution, better than a ring
 * when several phases hold close values. Segments are separated by a 2px surface gap; a segment is
 * only labelled inline when the text measurably fits.
 */
@Composable
fun PhaseShareBar(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    barHeight: Int = 22,
) {
    val viz = Viz.colors
    val labelStyle = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val total = slices.sumOf { it.value }.takeIf { it > 0.0 } ?: 0.0

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height((barHeight + 4).dp),
    ) {
        if (total <= 0.0) return@Canvas
        val gap = MarkSpec.SURFACE_GAP_DP.dp.toPx()
        val radius = MarkSpec.DATA_END_RADIUS_DP.dp.toPx()
        val h = barHeight.dp.toPx()
        val top = (size.height - h) / 2f
        var x = 0f

        slices.forEachIndexed { index, slice ->
            val width = ((slice.value / total).toFloat() * size.width) - gap
            if (width > 0.5f) {
                val isFirst = index == 0
                val isLast = index == slices.lastIndex
                drawRoundRect(
                    color = slice.color,
                    topLeft = Offset(x, top),
                    size = Size(width, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        if (isFirst || isLast) radius else 1f,
                    ),
                )
                val percentLabel = "${Math.round(slice.value / total * 100)}%"
                val labelWidth = measurer.widthOf(percentLabel, labelStyle)
                if (labelWidth + 12.dp.toPx() < width) {
                    drawLabel(
                        measurer = measurer,
                        text = percentLabel,
                        style = labelStyle,
                        color = viz.inkOn(slice.color),
                        x = x + width / 2f,
                        y = size.height / 2f,
                        anchor = LabelAnchor.Center,
                        verticalCenter = true,
                    )
                }
            }
            x += width + gap
        }
    }
}
