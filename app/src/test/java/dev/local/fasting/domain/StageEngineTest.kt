package dev.local.fasting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StageEngineTest {

    @Test
    fun `balanced meal keeps textbook boundaries`() {
        val schedule = StageEngine.schedule(MealComposition.BALANCED)
        assertEquals(0.0, schedule[0].startHours, EPS)
        assertEquals(12.0, schedule[1].startHours, EPS)
        assertEquals(18.0, schedule[2].startHours, EPS)
        assertEquals(24.0, schedule[3].startHours, EPS)
        assertEquals(48.0, schedule[4].startHours, EPS)
        assertNull(schedule[4].endHours)
    }

    @Test
    fun `keto meal pulls autophagy onset earlier and high carb pushes it later`() {
        val keto = StageEngine.onsetHours(FastStage.EARLY_AUTOPHAGY, MealComposition.KETO_LOW_CARB)
        val balanced = StageEngine.onsetHours(FastStage.EARLY_AUTOPHAGY, MealComposition.BALANCED)
        val carb = StageEngine.onsetHours(FastStage.EARLY_AUTOPHAGY, MealComposition.HIGH_CARB)

        assertEquals(14.4, keto, EPS)
        assertEquals(18.0, balanced, EPS)
        assertEquals(21.6, carb, EPS)
        assertTrue(keto < balanced && balanced < carb)
    }

    @Test
    fun `stage lookup respects the scaled boundaries`() {
        // 16h in: already autophagic after keto, still glycogen depletion after a balanced meal.
        assertEquals(
            FastStage.EARLY_AUTOPHAGY,
            StageEngine.stageAt(16.0, MealComposition.KETO_LOW_CARB),
        )
        assertEquals(
            FastStage.GLYCOGEN_DEPLETION,
            StageEngine.stageAt(16.0, MealComposition.BALANCED),
        )
        assertEquals(
            FastStage.DIGESTIVE,
            StageEngine.stageAt(13.0, MealComposition.HIGH_CARB),
        )
    }

    @Test
    fun `negative and zero elapsed clamp to the digestive stage`() {
        assertEquals(FastStage.DIGESTIVE, StageEngine.stageAt(-5.0, MealComposition.BALANCED))
        assertEquals(FastStage.DIGESTIVE, StageEngine.stageAt(0.0, MealComposition.BALANCED))
        assertEquals(0.0, StageEngine.autophagyHours(-5.0, MealComposition.BALANCED), EPS)
    }

    @Test
    fun `progress reports the next stage and the wait for it`() {
        val progress = StageEngine.progress(20.0, MealComposition.BALANCED)

        assertEquals(FastStage.EARLY_AUTOPHAGY, progress.stage)
        assertEquals(FastStage.DEEP_AUTOPHAGY, progress.nextStage)
        assertEquals(4.0, progress.hoursToNextStage!!, EPS)
        assertEquals(2.0, progress.autophagyHours, EPS)
        assertTrue(progress.isInAutophagy)
        // 20h sits 2h into the 6h-wide early autophagy band.
        assertEquals(1f / 3f, progress.stageProgress, 0.001f)
    }

    @Test
    fun `final stage is open ended`() {
        val progress = StageEngine.progress(60.0, MealComposition.BALANCED)

        assertEquals(FastStage.EXTENDED_REPAIR, progress.stage)
        assertNull(progress.nextStage)
        assertNull(progress.hoursToNextStage)
        assertEquals(1f, progress.stageProgress)
        assertEquals(42.0, progress.autophagyHours, EPS)
    }

    @Test
    fun `breakdown splits elapsed time across stages and sums back to the total`() {
        val breakdown = StageEngine.breakdown(30.0, MealComposition.BALANCED)

        assertEquals(12.0, breakdown[FastStage.DIGESTIVE]!!, EPS)
        assertEquals(6.0, breakdown[FastStage.GLYCOGEN_DEPLETION]!!, EPS)
        assertEquals(6.0, breakdown[FastStage.EARLY_AUTOPHAGY]!!, EPS)
        assertEquals(6.0, breakdown[FastStage.DEEP_AUTOPHAGY]!!, EPS)
        assertNull(breakdown[FastStage.EXTENDED_REPAIR])
        assertEquals(30.0, breakdown.values.sum(), EPS)
    }

    @Test
    fun `autophagy hours match the breakdown of autophagic stages`() {
        MealComposition.entries.forEach { meal ->
            listOf(10.0, 18.0, 26.5, 51.0).forEach { hours ->
                val fromBreakdown = StageEngine.breakdown(hours, meal)
                    .filterKeys { it.isAutophagic }
                    .values
                    .sum()
                assertEquals(
                    "meal=$meal hours=$hours",
                    StageEngine.autophagyHours(hours, meal),
                    fromBreakdown,
                    EPS,
                )
            }
        }
    }

    private companion object {
        const val EPS = 1e-9
    }
}
