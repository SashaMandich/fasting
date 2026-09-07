package dev.local.fasting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GoalEngineTest {

    @Test
    fun `pending goal counts down and never reports overtime`() {
        val progress = GoalEngine.progress(FastingGoal.FAST_16_8, elapsedHours = 12.0)

        assertEquals(16.0, progress.targetHours!!, EPS)
        assertEquals(4.0, progress.hoursRemaining!!, EPS)
        assertEquals(0.75f, progress.fraction, EPS.toFloat())
        assertNull(progress.overtimeHours)
        assertTrue(progress.isPending)
        assertFalse(progress.isReached)
        assertFalse(progress.isOpen)
    }

    @Test
    fun `reaching the target exactly counts as reached`() {
        val progress = GoalEngine.progress(FastingGoal.FAST_18_6, elapsedHours = 18.0)

        assertTrue(progress.isReached)
        assertFalse(progress.isPending)
        assertEquals(0.0, progress.overtimeHours!!, EPS)
        assertEquals(1f, progress.fraction, EPS.toFloat())
        assertNull(progress.hoursRemaining)
    }

    @Test
    fun `past the target the fraction is capped and overtime accumulates`() {
        val progress = GoalEngine.progress(FastingGoal.FAST_20_4, elapsedHours = 26.5)

        assertTrue(progress.isReached)
        assertEquals(6.5, progress.overtimeHours!!, EPS)
        assertEquals(1f, progress.fraction, EPS.toFloat())
    }

    /** The open goal *is* the general timer, so it is neither pending nor reached. */
    @Test
    fun `an open goal has no target to hit`() {
        val progress = GoalEngine.progress(FastingGoal.OPEN, elapsedHours = 40.0)

        assertTrue(progress.isOpen)
        assertFalse(progress.isPending)
        assertFalse(progress.isReached)
        assertNull(progress.targetHours)
        assertNull(progress.hoursRemaining)
        assertNull(progress.overtimeHours)
        assertEquals(40.0, progress.elapsedHours, EPS)
    }

    @Test
    fun `negative elapsed time is clamped to the start of the fast`() {
        val progress = GoalEngine.progress(FastingGoal.FAST_16_8, elapsedHours = -3.0)

        assertEquals(0.0, progress.elapsedHours, EPS)
        assertEquals(16.0, progress.hoursRemaining!!, EPS)
        assertEquals(0f, progress.fraction, EPS.toFloat())
    }

    /**
     * The goal is wall-clock hours: a keto meal pulls the *stage* boundaries in, but a 16-8 target
     * is still sixteen real hours.
     */
    @Test
    fun `the goal target does not move with the pre-fast meal`() {
        val start = Instant.parse("2026-08-19T20:00:00Z")
        val keto = FastRecord(
            start = start,
            meal = MealComposition.KETO_LOW_CARB,
            goal = FastingGoal.FAST_16_8,
        )
        val carb = keto.copy(meal = MealComposition.HIGH_CARB)

        assertEquals(start.plusSeconds(16 * 3600), keto.goalReachedAt)
        assertEquals(keto.goalReachedAt, carb.goalReachedAt)
        // The stage boundaries, by contrast, do move.
        assertTrue(
            keto.stageOnset(FastStage.EARLY_AUTOPHAGY)
                .isBefore(carb.stageOnset(FastStage.EARLY_AUTOPHAGY))
        )
    }

    @Test
    fun `an open fast has no goal instant to schedule against`() {
        val fast = FastRecord(
            start = Instant.parse("2026-08-19T20:00:00Z"),
            goal = FastingGoal.OPEN,
        )

        assertNull(fast.goalReachedAt)
        assertTrue(fast.goalProgress(fast.start.plusSeconds(30 * 3600)).isOpen)
    }

    @Test
    fun `a running fast crosses from pending to reached at its target`() {
        val start = Instant.parse("2026-08-19T20:00:00Z")
        val fast = FastRecord(start = start, goal = FastingGoal.FAST_16_8)

        assertTrue(fast.goalProgress(start.plusSeconds(15 * 3600 + 3599)).isPending)
        assertTrue(fast.goalProgress(start.plusSeconds(16 * 3600)).isReached)
    }

    @Test
    fun `every goal keeps its split consistent with a 24 hour day`() {
        FastingGoal.entries.filterNot { it.isOpen }.forEach { goal ->
            assertEquals(
                "${goal.label} should split a 24h day",
                24.0,
                goal.fastHours!! + goal.eatHours!!,
                EPS,
            )
        }
    }

    private companion object {
        const val EPS = 1e-9
    }
}
