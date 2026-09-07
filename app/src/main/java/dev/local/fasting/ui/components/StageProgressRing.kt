package dev.local.fasting.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.local.fasting.domain.FastProgress
import dev.local.fasting.domain.GoalProgress
import dev.local.fasting.domain.StageEngine
import dev.local.fasting.ui.charts.MarkSpec
import dev.local.fasting.ui.theme.Viz

/**
 * The fast timer's ring: the five stage bands, sized by their real duration for this fast's meal
 * composition, filled as they are reached.
 *
 * The band widths are themselves information — after a keto meal every band starts earlier, and the
 * ring shows that rather than a generic progress arc.
 *
 * How far the ring spans depends on [goal]. With a goal pending, the ring *is* the goal: it closes
 * exactly when the target is met. Once the target is met — or with no goal at all — it becomes the
 * general timer and grows with the fast, marking where the goal fell.
 */
@Composable
fun StageProgressRing(
    progress: FastProgress,
    modifier: Modifier = Modifier,
    goal: GoalProgress? = null,
    diameter: Int = 248,
    content: @Composable () -> Unit,
) {
    val viz = Viz.colors
    val schedule = StageEngine.schedule(progress.meal)
    val extendedStart = schedule.last().startHours
    // No goal: span up to the start of extended repair, then grow with the fast itself.
    val openScale = maxOf(extendedStart * 1.25, progress.elapsedHours)
    val targetScale = when {
        goal == null || goal.isOpen -> openScale
        goal.isPending -> goal.targetHours!!
        // Goal met: keep growing out from the target rather than snapping to the full stage scale,
        // so handing over to the general timer reads as continuing rather than starting again.
        else -> maxOf(goal.targetHours!!, progress.elapsedHours) * 1.25
    }.coerceAtLeast(1.0)

    // Both the fill and the span animate, so the rescale at the goal slides instead of jumping.
    val animatedElapsed by animateFloatAsState(
        targetValue = progress.elapsedHours.toFloat(),
        animationSpec = tween(durationMillis = 600),
        label = "elapsed-hours",
    )
    val animatedScale by animateFloatAsState(
        targetValue = targetScale.toFloat(),
        animationSpec = tween(durationMillis = 600),
        label = "ring-scale",
    )
    val scaleHours = animatedScale.toDouble().coerceAtLeast(1.0)

    Box(
        modifier = modifier.size(diameter.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(diameter.dp)) {
            val thickness = 16.dp.toPx()
            val inset = thickness / 2f
            val arcSize = Size(size.width - thickness, size.height - thickness)
            val topLeft = Offset(inset, inset)
            val radius = arcSize.width / 2f
            val gapDegrees = (MarkSpec.SURFACE_GAP_DP.dp.toPx() / (2f * Math.PI.toFloat() * radius)) * 360f

            schedule.forEachIndexed { index, window ->
                val start = window.startHours
                val end = window.endHours ?: scaleHours
                if (start >= scaleHours) return@forEachIndexed

                val startAngle = -90f + (start / scaleHours).toFloat() * 360f
                val fullSweep = ((minOf(end, scaleHours) - start) / scaleHours).toFloat() * 360f
                val visibleSweep = (fullSweep - gapDegrees).coerceAtLeast(0f)
                if (visibleSweep <= 0f) return@forEachIndexed

                // Unreached portion of the band: the lighter track of the same ramp.
                drawArc(
                    color = viz.trackFill,
                    startAngle = startAngle + gapDegrees / 2f,
                    sweepAngle = visibleSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = thickness),
                )

                val bandLength = (minOf(end, scaleHours) - start).toFloat()
                val reachedHours = (animatedElapsed - start.toFloat()).coerceIn(0f, bandLength)
                if (reachedHours > 0f) {
                    val reachedSweep = (reachedHours / scaleHours.toFloat()) * 360f
                    drawArc(
                        color = viz.stageRamp[index.coerceIn(viz.stageRamp.indices)],
                        startAngle = startAngle + gapDegrees / 2f,
                        sweepAngle = (reachedSweep - gapDegrees / 2f).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = thickness),
                    )
                }
            }

            // Once the goal is behind us the ring has rescaled past it, so mark where it fell —
            // otherwise the target the user committed to disappears the moment they hit it.
            val goalHours = goal?.takeIf { it.isReached }?.targetHours
            if (goalHours != null && goalHours < scaleHours) {
                val angle = -90f + (goalHours / scaleHours).toFloat() * 360f
                drawArc(
                    color = viz.emphasis,
                    startAngle = angle - GOAL_MARK_DEGREES / 2f,
                    sweepAngle = GOAL_MARK_DEGREES,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = thickness),
                )
            }
        }
        content()
    }
}

/** Width of the "your goal was here" notch on the ring. */
private const val GOAL_MARK_DEGREES = 2.5f
