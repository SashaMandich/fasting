package dev.local.fasting.domain

import java.time.Duration
import java.time.Instant

/** A logged fast. [end] is `null` while the fast is running. */
data class FastRecord(
    val id: Long = 0L,
    val start: Instant,
    val end: Instant? = null,
    val meal: MealComposition = MealComposition.DEFAULT,
    val goal: FastingGoal = FastingGoal.DEFAULT,
    val note: String = "",
) {
    val isActive: Boolean get() = end == null

    /** Duration so far, using [now] for an active fast. */
    fun duration(now: Instant): Duration = Duration.between(start, end ?: now)

    fun durationHours(now: Instant): Double = duration(now).toHoursDecimal()

    fun progress(now: Instant): FastProgress = StageEngine.progress(durationHours(now), meal)

    fun goalProgress(now: Instant): GoalProgress = GoalEngine.progress(goal, durationHours(now))

    /** Instant at which [stage] is (or was) reached for this fast's meal composition. */
    fun stageOnset(stage: FastStage): Instant =
        start.plusMillis((StageEngine.onsetHours(stage, meal) * MILLIS_PER_HOUR).toLong())

    /**
     * Instant the goal is (or was) met, or `null` for an open-ended fast. Unlike [stageOnset] this
     * is not meal-scaled — the goal is a plain wall-clock target.
     */
    val goalReachedAt: Instant?
        get() = goal.fastHours?.let { start.plusMillis((it * MILLIS_PER_HOUR).toLong()) }

    companion object {
        const val MILLIS_PER_HOUR: Double = 3_600_000.0
    }
}

fun Duration.toHoursDecimal(): Double = toMillis() / FastRecord.MILLIS_PER_HOUR

/** Context a weight measurement was taken in — comparing like with like matters more than raw values. */
enum class WeightTag(val displayName: String) {
    MORNING_FASTED("Morning Fasted"),
    POST_REFEED("Post-Refeed"),
    STANDARD("Standard"),
}

/** Display unit. Weights are always persisted in kilograms. */
enum class WeightUnit(val label: String, val perKg: Double) {
    KG("kg", 1.0),
    LBS("lbs", 2.2046226218487757),
    ;

    fun fromKg(kg: Double): Double = kg * perKg

    fun toKg(value: Double): Double = value / perKg
}

data class WeightRecord(
    val id: Long = 0L,
    val timestamp: Instant,
    val weightKg: Double,
    val bodyFatPercent: Double? = null,
    val tag: WeightTag = WeightTag.MORNING_FASTED,
    val note: String = "",
)

/** Subjective markers logged during or after a fast, used by the correlation charts. */
enum class Symptom(val displayName: String, val isPositive: Boolean = false) {
    HEADACHE("Headache"),
    DIZZINESS("Dizziness"),
    IRRITABILITY("Irritability"),
    CRAVINGS("Cravings"),
    NAUSEA("Nausea"),
    POOR_SLEEP("Poor sleep"),
    FEELING_COLD("Feeling cold"),
    MUSCLE_WEAKNESS("Muscle weakness"),
    MENTAL_CLARITY("Mental clarity", isPositive = true),
    EUPHORIA("Euphoria", isPositive = true),
    ;
}

data class SymptomLog(
    val id: Long = 0L,
    val timestamp: Instant,
    /** Fast this log belongs to, when it was recorded during one. */
    val fastId: Long? = null,
    /** 1 (drained) .. 5 (excellent). */
    val energyLevel: Int,
    /** 1 (foggy) .. 5 (sharp). */
    val clarityLevel: Int,
    /** 1 (none) .. 5 (ravenous). */
    val hungerLevel: Int,
    val symptoms: Set<Symptom> = emptySet(),
    val note: String = "",
) {
    companion object {
        val LEVEL_RANGE: IntRange = 1..5
    }
}
