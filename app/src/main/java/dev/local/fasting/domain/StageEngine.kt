package dev.local.fasting.domain

/** A stage boundary window in hours-since-start, already adjusted for meal composition. */
data class StageWindow(
    val stage: FastStage,
    val startHours: Double,
    /** `null` for the open-ended final stage. */
    val endHours: Double?,
) {
    fun contains(elapsedHours: Double): Boolean =
        elapsedHours >= startHours && (endHours == null || elapsedHours < endHours)

    /** Hours of overlap between this window and the interval `[0, elapsedHours]`. */
    fun hoursElapsedWithin(elapsedHours: Double): Double {
        val capped = endHours?.let { minOf(elapsedHours, it) } ?: elapsedHours
        return (capped - startHours).coerceAtLeast(0.0)
    }
}

/** Live state of a fast, everything the UI, widget and notification need to render. */
data class FastProgress(
    val elapsedHours: Double,
    val meal: MealComposition,
    val stage: FastStage,
    val stageStartHours: Double,
    val stageEndHours: Double?,
    val nextStage: FastStage?,
    val hoursToNextStage: Double?,
    /** 0f..1f through the current stage; 1f once in the open-ended final stage. */
    val stageProgress: Float,
    val autophagyHours: Double,
) {
    val isInAutophagy: Boolean get() = stage.isAutophagic
}

/**
 * Maps elapsed fasting time onto biological stages.
 *
 * All boundaries after the digestive phase are scaled by [MealComposition.onsetMultiplier], so a
 * keto pre-fast meal at 0.8x reaches Early Autophagy at 14.4h instead of 18h.
 */
object StageEngine {

    /** Stage windows in hours-since-start for the given pre-fast meal. */
    fun schedule(meal: MealComposition): List<StageWindow> =
        FastStage.entries.map { stage ->
            StageWindow(
                stage = stage,
                startHours = scale(stage.baseStartHours, meal),
                endHours = stage.baseEndHours?.let { scale(it, meal) },
            )
        }

    /** Hours from the start of a fast until [stage] begins, for the given meal. */
    fun onsetHours(stage: FastStage, meal: MealComposition): Double =
        scale(stage.baseStartHours, meal)

    fun stageAt(elapsedHours: Double, meal: MealComposition): FastStage =
        windowAt(elapsedHours, meal).stage

    fun windowAt(elapsedHours: Double, meal: MealComposition): StageWindow {
        val hours = elapsedHours.coerceAtLeast(0.0)
        val schedule = schedule(meal)
        return schedule.lastOrNull { hours >= it.startHours } ?: schedule.first()
    }

    /** Full live snapshot for a fast that has been running [elapsedHours]. */
    fun progress(elapsedHours: Double, meal: MealComposition): FastProgress {
        val hours = elapsedHours.coerceAtLeast(0.0)
        val current = windowAt(hours, meal)
        val next = schedule(meal).firstOrNull { it.startHours > hours }
        val span = current.endHours?.minus(current.startHours)
        return FastProgress(
            elapsedHours = hours,
            meal = meal,
            stage = current.stage,
            stageStartHours = current.startHours,
            stageEndHours = current.endHours,
            nextStage = next?.stage,
            hoursToNextStage = next?.let { it.startHours - hours },
            stageProgress = if (span == null || span <= 0.0) {
                1f
            } else {
                ((hours - current.startHours) / span).coerceIn(0.0, 1.0).toFloat()
            },
            autophagyHours = autophagyHours(hours, meal),
        )
    }

    /**
     * Hours of the elapsed time that fall in stages at or past [FastStage.AUTOPHAGY_THRESHOLD] —
     * the numerator of the deep autophagy ratio.
     */
    fun autophagyHours(elapsedHours: Double, meal: MealComposition): Double {
        val threshold = onsetHours(FastStage.AUTOPHAGY_THRESHOLD, meal)
        return (elapsedHours.coerceAtLeast(0.0) - threshold).coerceAtLeast(0.0)
    }

    /** Hours spent in each stage across a completed (or in-progress) fast of [elapsedHours]. */
    fun breakdown(elapsedHours: Double, meal: MealComposition): Map<FastStage, Double> {
        val hours = elapsedHours.coerceAtLeast(0.0)
        return schedule(meal)
            .associate { it.stage to it.hoursElapsedWithin(hours) }
            .filterValues { it > 0.0 }
    }

    private fun scale(baseHours: Double, meal: MealComposition): Double =
        baseHours * meal.onsetMultiplier
}
