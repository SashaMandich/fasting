package dev.local.fasting.ui.charts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText

/**
 * Fixed mark specs, applied by every chart in the app. Values are dp; each chart converts with the
 * ambient density.
 */
object MarkSpec {
    /** Bars never fill their band — the leftover is air. */
    const val MAX_BAR_THICKNESS_DP = 24f
    const val LINE_WIDTH_DP = 2f
    const val MARKER_DIAMETER_DP = 9f
    const val SURFACE_GAP_DP = 2f
    const val SURFACE_RING_DP = 2f
    const val HAIRLINE_DP = 1f
    const val DATA_END_RADIUS_DP = 4f
    const val AREA_FILL_ALPHA = 0.10f
}

enum class LabelAnchor { Start, Center, End }

/** Draws a chart label with the given horizontal anchoring, measuring before placing it. */
fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    color: Color,
    x: Float,
    y: Float,
    anchor: LabelAnchor = LabelAnchor.Start,
    verticalCenter: Boolean = false,
): TextLayoutResult {
    val layout = measurer.measure(AnnotatedString(text), style)
    val left = when (anchor) {
        LabelAnchor.Start -> x
        LabelAnchor.Center -> x - layout.size.width / 2f
        LabelAnchor.End -> x - layout.size.width
    }
    val top = if (verticalCenter) y - layout.size.height / 2f else y
    drawText(layout, color = color, topLeft = Offset(left, top))
    return layout
}

/** Measured width of [text] — used to decide whether a direct label fits before drawing it. */
fun TextMeasurer.widthOf(text: String, style: TextStyle): Float =
    measure(AnnotatedString(text), style).size.width.toFloat()

/** Solid hairline horizontal gridlines, one step off the surface, drawn behind the data. */
fun DrawScope.drawHorizontalGrid(
    values: List<Float>,
    color: Color,
    strokeWidth: Float,
    left: Float,
    right: Float,
) {
    values.forEach { y ->
        drawLine(
            color = color,
            start = Offset(left, y),
            end = Offset(right, y),
            strokeWidth = strokeWidth,
        )
    }
}

/** "Nice" axis maximum so ticks land on readable numbers. */
fun niceCeiling(value: Float): Float {
    if (value <= 0f) return 1f
    val exponent = kotlin.math.floor(kotlin.math.log10(value.toDouble())).toInt()
    val magnitude = Math.pow(10.0, exponent.toDouble()).toFloat()
    val normalized = value / magnitude
    val step = when {
        normalized <= 1f -> 1f
        normalized <= 2f -> 2f
        normalized <= 2.5f -> 2.5f
        normalized <= 5f -> 5f
        else -> 10f
    }
    return step * magnitude
}
