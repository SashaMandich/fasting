package dev.local.fasting.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.local.fasting.domain.FastStage
import dev.local.fasting.domain.MealComposition

/**
 * Chart palette. Both modes are *selected*, not flipped: each is stepped for its own surface and
 * validated against it (see `docs/visualization.md`).
 *
 * - Fasting stages use a **green → red semantic-heat ramp**: green while digesting, red at extended
 *   repair. It is an ordered scale carried by hue rotation plus lightness, and it is the measured
 *   best-separated 5-step green→red ramp available (worst adjacent pair: CVD ΔE 7.5 light / 9.0
 *   dark, normal-vision 9.1 / 11.9). A green→red ramp cannot reach the ΔE 15 normal-vision target
 *   at five steps, so stage identity is never left to colour alone: every chart carries stage names
 *   and hour values in its legend, inline percentages on the stacked bar, and a full table view.
 * - Meal compositions are **nominal identity**, so they use the first three categorical slots,
 *   which clear the all-pairs CVD gates needed for scatter plots.
 * - Text never wears a series color; identity comes from the swatch beside the label.
 */
data class VizColors(
    val surface: Color,
    val gridline: Color,
    val axis: Color,
    val muted: Color,
    val emphasis: Color,
    val deEmphasis: Color,
    val stageRamp: List<Color>,
    val heatmapEmpty: Color,
    val heatmapRamp: List<Color>,
    val mealSlots: List<Color>,
    val trackFill: Color,
) {
    fun forStage(stage: FastStage): Color = stageRamp[stage.ordinal.coerceIn(stageRamp.indices)]

    fun forMeal(meal: MealComposition): Color = mealSlots[meal.ordinal.coerceIn(mealSlots.indices)]

    /** Ink that stays legible on top of [fill] — for labels set inside a filled mark. */
    fun inkOn(fill: Color): Color =
        if (fill.luminance() > 0.45f) Color(0xFF0B0B0B) else Color(0xFFFFFFFF)

    private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
}

// Light mode: green → red stepped for the light chart surface. The amber mid-step sits below 3:1
// against the surface, which is why the distribution charts always label their segments.
private val LightViz = VizColors(
    surface = Color(0xFFFBFDFC),
    gridline = Color(0xFFE1E7E5),
    axis = Color(0xFFC3CCC9),
    muted = Color(0xFF6C7976),
    emphasis = Color(0xFF16705E),
    deEmphasis = Color(0xFFB6C2BF),
    stageRamp = listOf(
        Color(0xFF1CA862), // digestive — green
        Color(0xFF5F8A24), // glycogen depletion
        Color(0xFFEFB63C), // early autophagy — amber
        Color(0xFFCF6B22), // deep autophagy — orange
        Color(0xFF9E2130), // extended repair — red
    ),
    heatmapEmpty = Color(0xFFE8EDEB),
    heatmapRamp = listOf(
        Color(0xFF1CA862),
        Color(0xFFEFB63C),
        Color(0xFFCF6B22),
        Color(0xFF9E2130),
    ),
    mealSlots = listOf(
        Color(0xFF2A78D6), // high carb
        Color(0xFFEB6834), // balanced
        Color(0xFF1BAF7A), // keto / low carb
    ),
    trackFill = Color(0xFFE3E9E7),
)

// Dark mode: the same green → red journey re-stepped for the dark surface (brighter, all steps
// clear 3:1), never an automatic flip of the light ramp.
private val DarkViz = VizColors(
    surface = Color(0xFF101416),
    gridline = Color(0xFF232B2E),
    axis = Color(0xFF39433F),
    muted = Color(0xFF97A6A2),
    emphasis = Color(0xFF7BE3C3),
    deEmphasis = Color(0xFF4A5551),
    stageRamp = listOf(
        Color(0xFF1DD980), // digestive — green
        Color(0xFF93B414), // glycogen depletion
        Color(0xFFFEC930), // early autophagy — amber
        Color(0xFFF97D14), // deep autophagy — orange
        Color(0xFFD02B31), // extended repair — red
    ),
    heatmapEmpty = Color(0xFF1B2225),
    heatmapRamp = listOf(
        Color(0xFF1DD980),
        Color(0xFFFEC930),
        Color(0xFFF97D14),
        Color(0xFFD02B31),
    ),
    mealSlots = listOf(
        Color(0xFF3987E5),
        Color(0xFFD95926),
        Color(0xFF199E70),
    ),
    trackFill = Color(0xFF232B2E),
)

fun vizColors(darkTheme: Boolean): VizColors = if (darkTheme) DarkViz else LightViz

val LocalVizColors = staticCompositionLocalOf { LightViz }

object Viz {
    val colors: VizColors
        @Composable @ReadOnlyComposable
        get() = LocalVizColors.current
}
