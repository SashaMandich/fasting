package dev.local.fasting.domain

/**
 * Biological phases of a fast, expressed as hour offsets from the start of the fast for a
 * "balanced" pre-fast meal. Real boundaries are produced by [StageEngine], which scales these
 * base offsets by the pre-fast meal's onset multiplier.
 */
enum class FastStage(
    val displayName: String,
    val shortName: String,
    val baseStartHours: Double,
    val baseEndHours: Double?,
    val summary: String,
) {
    DIGESTIVE(
        displayName = "Digestive / Anabolic",
        shortName = "Digestive",
        baseStartHours = 0.0,
        baseEndHours = 12.0,
        summary = "Absorbing the last meal. Insulin elevated, glycogen topped up, storage mode.",
    ),
    GLYCOGEN_DEPLETION(
        displayName = "Glycogen Depletion / Ketosis Onset",
        shortName = "Glycogen",
        baseStartHours = 12.0,
        baseEndHours = 18.0,
        summary = "Liver glycogen draining, insulin falling, first ketones appearing.",
    ),
    EARLY_AUTOPHAGY(
        displayName = "Early Autophagy",
        shortName = "Early Auto",
        baseStartHours = 18.0,
        baseEndHours = 24.0,
        summary = "Cellular recycling switches on as mTOR drops and AMPK rises.",
    ),
    DEEP_AUTOPHAGY(
        displayName = "Deep Autophagy",
        shortName = "Deep Auto",
        baseStartHours = 24.0,
        baseEndHours = 48.0,
        summary = "Sustained cleanup: damaged organelles and misfolded proteins recycled.",
    ),
    EXTENDED_REPAIR(
        displayName = "Extended Repair / Peak Autophagy",
        shortName = "Extended",
        baseStartHours = 48.0,
        baseEndHours = null,
        summary = "Growth hormone elevated, stem-cell signalling and peak recycling.",
    ),
    ;

    /** True from [EARLY_AUTOPHAGY] onward — the stages the deep autophagy ratio counts. */
    val isAutophagic: Boolean get() = ordinal >= EARLY_AUTOPHAGY.ordinal

    companion object {
        /** First stage that counts as autophagy for analytics (base onset 18h). */
        val AUTOPHAGY_THRESHOLD: FastStage = EARLY_AUTOPHAGY

        /** Stage boundary used to mark a day as an "extended fast" on the heatmap. */
        const val EXTENDED_FAST_HOURS: Double = 24.0
    }
}
