package dev.local.fasting.domain

/**
 * Composition of the meal eaten immediately before the fast.
 *
 * A carb-heavy meal refills liver glycogen, so every metabolic transition after the digestive
 * phase lands later; a ketogenic meal leaves glycogen partly depleted, so they land earlier. The
 * [onsetMultiplier] scales the stage boundaries in [StageEngine] accordingly.
 */
enum class MealComposition(
    val displayName: String,
    val onsetMultiplier: Double,
    val description: String,
) {
    HIGH_CARB(
        displayName = "High Carb",
        onsetMultiplier = 1.20,
        description = "Glycogen refilled — autophagy onset pushed ~20% later.",
    ),
    BALANCED(
        displayName = "Balanced",
        onsetMultiplier = 1.00,
        description = "Mixed meal — textbook stage timings.",
    ),
    KETO_LOW_CARB(
        displayName = "Keto / Low Carb",
        onsetMultiplier = 0.80,
        description = "Glycogen already low — autophagy onset pulled ~20% earlier.",
    ),
    ;

    companion object {
        val DEFAULT: MealComposition = BALANCED
    }
}
