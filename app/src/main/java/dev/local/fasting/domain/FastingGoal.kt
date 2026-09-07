package dev.local.fasting.domain

/**
 * A fasting schedule, expressed as the classic "fasting hours – eating hours" split.
 *
 * The target is wall-clock hours and is deliberately *not* scaled by [MealComposition]: 16-8 means
 * sixteen real hours whatever was eaten first. Only the biological stage boundaries in [StageEngine]
 * move with the pre-fast meal — a goal is a commitment the user made, not a metabolic prediction.
 *
 * [OPEN] is the general timer: a fast with no target, which is also what every fast becomes once its
 * target is met.
 */
enum class FastingGoal(
    val label: String,
    /** Target fasting hours, or `null` for [OPEN]. */
    val fastHours: Double?,
    /** The eating half of the split. Shown next to the target; nothing is tracked against it. */
    val eatHours: Double?,
    val description: String,
) {
    FAST_16_8(
        label = "16-8",
        fastHours = 16.0,
        eatHours = 8.0,
        description = "Fast 16h, eat within 8h. The everyday rhythm.",
    ),
    FAST_18_6(
        label = "18-6",
        fastHours = 18.0,
        eatHours = 6.0,
        description = "Fast 18h, eat within 6h. Lands in early autophagy most days.",
    ),
    FAST_20_4(
        label = "20-4",
        fastHours = 20.0,
        eatHours = 4.0,
        description = "Fast 20h, eat within 4h. A narrow single-meal window.",
    ),
    OPEN(
        label = "Open",
        fastHours = null,
        eatHours = null,
        description = "No target — the timer just counts up.",
    ),
    ;

    val isOpen: Boolean get() = fastHours == null

    /** "fast 16h · eat 8h", or the open-ended equivalent. */
    val splitSummary: String
        get() = if (fastHours == null || eatHours == null) {
            "no target"
        } else {
            "fast ${fastHours.toInt()}h · eat ${eatHours.toInt()}h"
        }

    companion object {
        val DEFAULT: FastingGoal = FAST_16_8
    }
}

/** Live progress toward a [FastingGoal] for a fast that has been running [elapsedHours]. */
data class GoalProgress(
    val goal: FastingGoal,
    val elapsedHours: Double,
    /** Target hours, or `null` for an open-ended fast. */
    val targetHours: Double?,
    /** 0f..1f toward the target. 1f once reached, and 1f throughout an open fast. */
    val fraction: Float,
    /** Hours still to go, or `null` when open or already reached. */
    val hoursRemaining: Double?,
    /** Hours past the target, or `null` until it is reached. This is the general timer's figure. */
    val overtimeHours: Double?,
) {
    val isOpen: Boolean get() = targetHours == null

    /**
     * True once the target has been met. An open fast is never "reached": it has no target to meet
     * and is already running as the general timer.
     */
    val isReached: Boolean get() = overtimeHours != null

    /** True while there is still a target ahead — the only state that counts down. */
    val isPending: Boolean get() = hoursRemaining != null
}

/** Maps elapsed fasting time onto a chosen [FastingGoal]. */
object GoalEngine {

    fun progress(goal: FastingGoal, elapsedHours: Double): GoalProgress {
        val hours = elapsedHours.coerceAtLeast(0.0)
        val target = goal.fastHours
        if (target == null || target <= 0.0) {
            return GoalProgress(
                goal = goal,
                elapsedHours = hours,
                targetHours = null,
                fraction = 1f,
                hoursRemaining = null,
                overtimeHours = null,
            )
        }
        val reached = hours >= target
        return GoalProgress(
            goal = goal,
            elapsedHours = hours,
            targetHours = target,
            fraction = (hours / target).coerceIn(0.0, 1.0).toFloat(),
            hoursRemaining = if (reached) null else target - hours,
            overtimeHours = if (reached) hours - target else null,
        )
    }
}
