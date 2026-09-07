package dev.local.fasting.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.local.fasting.ui.theme.Viz

/** One position on a time axis: the smoothed value plus, optionally, the raw reading behind it. */
data class LinePoint(
    val label: String,
    val value: Double,
    val rawValue: Double? = null,
)

/**
 * Single-measure trend line on one axis.
 *
 * Emphasis form, not categorical: the smoothed trend is the subject and wears the accent, the raw
 * daily readings sit behind it in the de-emphasis grey. Only the final value is direct-labelled —
 * a number on every point goes unread.
 */
@Composable
fun TrendLineChart(
    points: List<LinePoint>,
    valueFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Int = 176,
    lineColor: Color? = null,
    showRawPoints: Boolean = true,
) {
    val viz = Viz.colors
    val measurer = rememberTextMeasurer()
    val tickStyle = MaterialTheme.typography.labelSmall
    val labelStyle = MaterialTheme.typography.labelMedium
    val accent = lineColor ?: viz.emphasis

    if (points.size < 2) {
        EmptyChartHint("At least two measurements are needed for a trendline")
        return
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp),
    ) {
        val values = points.map { it.value } + points.mapNotNull { it.rawValue }
        val rawMin = values.min()
        val rawMax = values.max()
        val pad = ((rawMax - rawMin) * 0.12).takeIf { it > 0.0 } ?: (rawMax * 0.02 + 0.5)
        val minValue = rawMin - pad
        val maxValue = rawMax + pad

        val tickValues = listOf(maxValue, (maxValue + minValue) / 2, minValue)
        val tickTexts = tickValues.map { valueFormatter(it) }
        val gutter = tickTexts.maxOf { measurer.widthOf(it, tickStyle) } + 8.dp.toPx()
        val bottomBand = 18.dp.toPx()
        val topPad = 10.dp.toPx()
        val plotLeft = gutter
        val plotRight = size.width
        val plotTop = topPad
        val plotBottom = size.height - bottomBand
        val plotHeight = plotBottom - plotTop
        val plotWidth = plotRight - plotLeft

        fun yFor(value: Double): Float =
            plotBottom - (((value - minValue) / (maxValue - minValue)).toFloat() * plotHeight)

        fun xFor(index: Int): Float =
            plotLeft + if (points.size == 1) plotWidth / 2f
            else plotWidth * index / (points.size - 1).toFloat()

        // Recessive hairline grid, drawn before the data.
        drawHorizontalGrid(
            values = tickValues.map { yFor(it) },
            color = viz.gridline,
            strokeWidth = MarkSpec.HAIRLINE_DP.dp.toPx(),
            left = plotLeft,
            right = plotRight,
        )
        tickValues.forEachIndexed { index, value ->
            drawLabel(
                measurer = measurer,
                text = tickTexts[index],
                style = tickStyle,
                color = viz.muted,
                x = plotLeft - 6.dp.toPx(),
                y = yFor(value),
                anchor = LabelAnchor.End,
                verticalCenter = true,
            )
        }

        if (showRawPoints) {
            val dotRadius = 3.5.dp.toPx()
            points.forEachIndexed { index, point ->
                point.rawValue?.let { raw ->
                    drawCircle(
                        color = viz.deEmphasis,
                        radius = dotRadius,
                        center = Offset(xFor(index), yFor(raw)),
                    )
                }
            }
        }

        val path = Path()
        points.forEachIndexed { index, point ->
            val x = xFor(index)
            val y = yFor(point.value)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = accent,
            style = Stroke(
                width = MarkSpec.LINE_WIDTH_DP.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )

        // End marker with a surface ring so it stays legible where it crosses the line.
        val markerRadius = MarkSpec.MARKER_DIAMETER_DP.dp.toPx() / 2f
        val endX = xFor(points.lastIndex)
        val endY = yFor(points.last().value)
        drawCircle(
            color = viz.surface,
            radius = markerRadius + MarkSpec.SURFACE_RING_DP.dp.toPx(),
            center = Offset(endX, endY),
        )
        drawCircle(color = accent, radius = markerRadius, center = Offset(endX, endY))

        // Only the endpoint carries a direct label, and only when it fits inside the plot.
        val endLabel = valueFormatter(points.last().value)
        val endLabelWidth = measurer.widthOf(endLabel, labelStyle)
        if (endX - endLabelWidth - 10.dp.toPx() > plotLeft) {
            drawLabel(
                measurer = measurer,
                text = endLabel,
                style = labelStyle,
                color = viz.muted,
                x = endX - markerRadius - 6.dp.toPx(),
                y = endY - 9.dp.toPx(),
                anchor = LabelAnchor.End,
                verticalCenter = true,
            )
        }

        drawXAxisLabels(measurer, points.map { it.label }, tickStyle, viz.muted, plotLeft, plotRight, plotBottom + 5.dp.toPx())
    }
}

/**
 * Column chart for a single measure over time. Used as the lower panel of the correlation view —
 * two panels sharing one x-axis, never two y-scales on one plot.
 */
@Composable
fun ColumnChart(
    labels: List<String>,
    values: List<Double>,
    valueFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Int = 132,
    barColor: Color? = null,
) {
    val viz = Viz.colors
    val measurer = rememberTextMeasurer()
    val tickStyle = MaterialTheme.typography.labelSmall
    val color = barColor ?: viz.stageRamp[2]

    if (values.isEmpty()) {
        EmptyChartHint("Nothing logged in this window yet")
        return
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp),
    ) {
        val maxValue = niceCeiling(values.max().toFloat()).toDouble()
        val tickValues = listOf(maxValue, maxValue / 2, 0.0)
        val tickTexts = tickValues.map { valueFormatter(it) }
        val gutter = tickTexts.maxOf { measurer.widthOf(it, tickStyle) } + 8.dp.toPx()
        val bottomBand = 18.dp.toPx()
        val plotLeft = gutter
        val plotRight = size.width
        val plotTop = 8.dp.toPx()
        val plotBottom = size.height - bottomBand
        val plotHeight = plotBottom - plotTop
        val band = (plotRight - plotLeft) / values.size

        drawHorizontalGrid(
            values = tickValues.map { plotBottom - (it / maxValue).toFloat() * plotHeight },
            color = viz.gridline,
            strokeWidth = MarkSpec.HAIRLINE_DP.dp.toPx(),
            left = plotLeft,
            right = plotRight,
        )
        tickValues.forEachIndexed { index, value ->
            drawLabel(
                measurer = measurer,
                text = tickTexts[index],
                style = tickStyle,
                color = viz.muted,
                x = plotLeft - 6.dp.toPx(),
                y = plotBottom - (value / maxValue).toFloat() * plotHeight,
                anchor = LabelAnchor.End,
                verticalCenter = true,
            )
        }

        val barWidth = minOf(MarkSpec.MAX_BAR_THICKNESS_DP.dp.toPx(), band * 0.55f)
        val corner = CornerRadius(MarkSpec.DATA_END_RADIUS_DP.dp.toPx())
        values.forEachIndexed { index, value ->
            val barHeight = ((value / maxValue).toFloat() * plotHeight).coerceAtLeast(0f)
            if (barHeight <= 0f) return@forEachIndexed
            val left = plotLeft + band * index + (band - barWidth) / 2f
            val top = plotBottom - barHeight
            // Rounded data-end, square at the baseline: round rect, then square off the foot.
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = corner,
            )
            val footHeight = minOf(corner.y, barHeight)
            drawRect(
                color = color,
                topLeft = Offset(left, plotBottom - footHeight),
                size = Size(barWidth, footHeight),
            )
        }

        drawXAxisLabels(measurer, labels, tickStyle, viz.muted, plotLeft, plotRight, plotBottom + 5.dp.toPx(), bandCentred = true)
    }
}

/**
 * Places first / middle / last x labels only — enough to orient, without a label per column.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawXAxisLabels(
    measurer: androidx.compose.ui.text.TextMeasurer,
    labels: List<String>,
    style: TextStyle,
    color: Color,
    plotLeft: Float,
    plotRight: Float,
    y: Float,
    bandCentred: Boolean = false,
) {
    if (labels.isEmpty()) return
    val plotWidth = plotRight - plotLeft
    val indices = when {
        labels.size <= 2 -> labels.indices.toList()
        else -> listOf(0, labels.size / 2, labels.lastIndex)
    }.distinct()

    indices.forEach { index ->
        val x = if (bandCentred) {
            val band = plotWidth / labels.size
            plotLeft + band * index + band / 2f
        } else {
            plotLeft + if (labels.size == 1) plotWidth / 2f else plotWidth * index / (labels.size - 1).toFloat()
        }
        val anchor = when (index) {
            0 -> LabelAnchor.Start
            labels.lastIndex -> LabelAnchor.End
            else -> LabelAnchor.Center
        }
        drawLabel(measurer, labels[index], style, color, x, y, anchor)
    }
}
