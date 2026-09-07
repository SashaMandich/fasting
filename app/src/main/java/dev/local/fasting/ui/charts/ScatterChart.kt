package dev.local.fasting.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.local.fasting.ui.theme.Viz
import kotlin.math.hypot

data class ScatterPoint(
    val x: Double,
    val y: Double,
    val color: Color,
    val shape: MarkShape,
)

/**
 * Subjective score against fast length, one mark per log.
 *
 * Marks carry both hue and shape for their meal composition: an all-pairs scatter is exactly the
 * case where identity must survive colour-vision deficiency, so shape backs colour up rather than
 * decorating it. Every mark's hit area is a ~24dp nearest-point catch, not the 9dp mark itself.
 */
@Composable
fun ScatterChart(
    points: List<ScatterPoint>,
    xMax: Double,
    yTicks: List<Pair<Double, String>>,
    xTickFormatter: (Double) -> String,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    height: Int = 196,
) {
    val viz = Viz.colors
    val measurer = rememberTextMeasurer()
    val tickStyle = MaterialTheme.typography.labelSmall

    if (points.isEmpty()) {
        EmptyChartHint("Log how a fast felt to start plotting this")
        return
    }

    val yMin = yTicks.minOf { it.first }
    val yMax = yTicks.maxOf { it.first }
    val safeXMax = xMax.takeIf { it > 0.0 } ?: 1.0

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .pointerInput(points, selectedIndex, safeXMax) {
                detectTapGestures { tap ->
                    val gutter = yTicks.maxOf { measurer.widthOf(it.second, tickStyle) } + 8.dp.toPx()
                    val plotLeft = gutter
                    val plotRight = size.width - 6.dp.toPx()
                    val plotTop = 10.dp.toPx()
                    val plotBottom = size.height - 18.dp.toPx()
                    val tolerance = 24.dp.toPx()

                    var bestIndex: Int? = null
                    var bestDistance = Float.MAX_VALUE
                    points.forEachIndexed { index, point ->
                        val px = plotLeft + ((point.x / safeXMax).toFloat().coerceIn(0f, 1f)) * (plotRight - plotLeft)
                        val py = plotBottom - (((point.y - yMin) / (yMax - yMin)).toFloat().coerceIn(0f, 1f)) * (plotBottom - plotTop)
                        val distance = hypot(tap.x - px, tap.y - py)
                        if (distance < bestDistance) {
                            bestDistance = distance
                            bestIndex = index
                        }
                    }
                    onSelect(
                        if (bestDistance > tolerance || bestIndex == selectedIndex) null else bestIndex
                    )
                }
            },
    ) {
        val gutter = yTicks.maxOf { measurer.widthOf(it.second, tickStyle) } + 8.dp.toPx()
        val plotLeft = gutter
        val plotRight = size.width - 6.dp.toPx()
        val plotTop = 10.dp.toPx()
        val plotBottom = size.height - 18.dp.toPx()

        drawHorizontalGrid(
            values = yTicks.map { plotBottom - (((it.first - yMin) / (yMax - yMin)).toFloat()) * (plotBottom - plotTop) },
            color = viz.gridline,
            strokeWidth = MarkSpec.HAIRLINE_DP.dp.toPx(),
            left = plotLeft,
            right = plotRight,
        )
        yTicks.forEach { (value, label) ->
            drawLabel(
                measurer = measurer,
                text = label,
                style = tickStyle,
                color = viz.muted,
                x = plotLeft - 6.dp.toPx(),
                y = plotBottom - (((value - yMin) / (yMax - yMin)).toFloat()) * (plotBottom - plotTop),
                anchor = LabelAnchor.End,
                verticalCenter = true,
            )
        }

        listOf(0.0, safeXMax / 2, safeXMax).forEachIndexed { index, value ->
            val x = plotLeft + (value / safeXMax).toFloat() * (plotRight - plotLeft)
            drawLabel(
                measurer = measurer,
                text = xTickFormatter(value),
                style = tickStyle,
                color = viz.muted,
                x = x,
                y = plotBottom + 5.dp.toPx(),
                anchor = when (index) {
                    0 -> LabelAnchor.Start
                    2 -> LabelAnchor.End
                    else -> LabelAnchor.Center
                },
            )
        }

        val radius = MarkSpec.MARKER_DIAMETER_DP.dp.toPx() / 2f
        val ring = MarkSpec.SURFACE_RING_DP.dp.toPx()
        points.forEachIndexed { index, point ->
            val px = plotLeft + ((point.x / safeXMax).toFloat().coerceIn(0f, 1f)) * (plotRight - plotLeft)
            val py = plotBottom - (((point.y - yMin) / (yMax - yMin)).toFloat().coerceIn(0f, 1f)) * (plotBottom - plotTop)
            val isSelected = index == selectedIndex
            val markRadius = if (isSelected) radius * 1.35f else radius

            drawMark(point.shape, viz.surface, Offset(px, py), markRadius + ring)
            drawMark(point.shape, point.color, Offset(px, py), markRadius)
            if (isSelected) {
                drawCircle(
                    color = viz.emphasis,
                    radius = markRadius + ring * 2f,
                    center = Offset(px, py),
                    style = Stroke(width = ring),
                )
            }
        }
    }
}

private fun DrawScope.drawMark(shape: MarkShape, color: Color, center: Offset, radius: Float) {
    when (shape) {
        MarkShape.Circle, MarkShape.Line -> drawCircle(color, radius, center)
        MarkShape.Square -> drawRect(
            color = color,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
        )
        MarkShape.Triangle -> {
            val path = Path().apply {
                moveTo(center.x, center.y - radius)
                lineTo(center.x + radius, center.y + radius * 0.8f)
                lineTo(center.x - radius, center.y + radius * 0.8f)
                close()
            }
            drawPath(path, color)
        }
    }
}
