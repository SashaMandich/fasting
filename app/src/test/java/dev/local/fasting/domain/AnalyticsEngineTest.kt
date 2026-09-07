package dev.local.fasting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class AnalyticsEngineTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val now: Instant = instant("2026-08-18", LocalTime.of(12, 0))

    @Test
    fun `phase distribution accumulates hours per stage across fasts`() {
        val fasts = listOf(
            fast("2026-08-17", LocalTime.of(20, 0), hours = 20.0, meal = MealComposition.BALANCED),
            fast("2026-08-15", LocalTime.of(20, 0), hours = 13.0, meal = MealComposition.BALANCED),
        )

        val distribution = AnalyticsEngine.phaseDistribution(fasts, AnalyticsRange.MONTH, now)

        assertEquals(24.0, distribution.hoursByStage[FastStage.DIGESTIVE]!!, EPS)
        assertEquals(7.0, distribution.hoursByStage[FastStage.GLYCOGEN_DEPLETION]!!, EPS)
        assertEquals(2.0, distribution.hoursByStage[FastStage.EARLY_AUTOPHAGY]!!, EPS)
        assertEquals(33.0, distribution.totalHours, EPS)
        assertEquals(2.0 / 33.0, distribution.autophagyRatio, EPS)
    }

    @Test
    fun `range filter keeps fasts that overlap the window and drops older ones`() {
        val fasts = listOf(
            fast("2026-08-17", LocalTime.of(6, 0), hours = 18.0),
            fast("2026-06-01", LocalTime.of(6, 0), hours = 18.0),
        )

        assertEquals(1, AnalyticsEngine.fastsInRange(fasts, AnalyticsRange.WEEK, now).size)
        assertEquals(2, AnalyticsEngine.fastsInRange(fasts, AnalyticsRange.ALL, now).size)
    }

    @Test
    fun `monthly ratio scales the autophagy boundary by meal composition`() {
        val fasts = listOf(
            // 20h after keto: autophagy from 14.4h, so 5.6 autophagic hours.
            fast("2026-08-02", LocalTime.of(8, 0), hours = 20.0, meal = MealComposition.KETO_LOW_CARB),
            // 20h after high carb: autophagy from 21.6h, so none.
            fast("2026-08-05", LocalTime.of(8, 0), hours = 20.0, meal = MealComposition.HIGH_CARB),
        )

        val month = AnalyticsEngine.monthlyAutophagy(fasts, zone, now).single()

        assertEquals(YearMonth.of(2026, 8), month.month)
        assertEquals(2, month.fastCount)
        assertEquals(40.0, month.totalFastedHours, EPS)
        assertEquals(5.6, month.autophagyHours, 1e-9)
        assertEquals(5.6 / 40.0, month.ratio, 1e-9)
    }

    @Test
    fun `a fast crossing midnight is attributed to its start month but split across calendar days`() {
        val fast = fast("2026-07-31", LocalTime.of(20, 0), hours = 8.0)

        val month = AnalyticsEngine.monthlyAutophagy(listOf(fast), zone, now).single()
        assertEquals(YearMonth.of(2026, 7), month.month)

        val july = AnalyticsEngine.monthActivity(listOf(fast), YearMonth.of(2026, 7), zone, now)
        val august = AnalyticsEngine.monthActivity(listOf(fast), YearMonth.of(2026, 8), zone, now)
        val july31 = july.days.single { it.date == LocalDate.parse("2026-07-31") }
        val august1 = august.days.single { it.date == LocalDate.parse("2026-08-01") }

        assertEquals(4.0, july31.fastedHours, EPS)
        assertEquals(4.0, august1.fastedHours, EPS)
        assertFalse(july31.hasExtendedFast)
    }

    @Test
    fun `calendar flags extended fasts and caps a day at 24 hours`() {
        val fast = fast("2026-08-10", LocalTime.of(0, 0), hours = 72.0)

        val august = AnalyticsEngine.monthActivity(listOf(fast), YearMonth.of(2026, 8), zone, now)
        val middleDay = august.days.single { it.date == LocalDate.parse("2026-08-11") }

        assertEquals(24.0, middleDay.fastedHours, EPS)
        assertTrue(middleDay.hasExtendedFast)
        assertEquals(72.0, middleDay.longestFastHours, EPS)
    }

    @Test
    fun `month activity spans exactly the days of that calendar month`() {
        val february = AnalyticsEngine.monthActivity(emptyList(), YearMonth.of(2024, 2), zone, now)
        assertEquals(29, february.days.size)
        assertEquals(LocalDate.parse("2024-02-01"), february.days.first().date)
        assertEquals(LocalDate.parse("2024-02-29"), february.days.last().date)

        val august = AnalyticsEngine.monthActivity(emptyList(), YearMonth.of(2026, 8), zone, now)
        assertEquals(31, august.days.size)
    }

    @Test
    fun `logged months run from the first fast to the current month`() {
        val fasts = listOf(fast("2026-06-10", LocalTime.of(8, 0), hours = 20.0))

        val months = AnalyticsEngine.loggedMonths(fasts, zone, now)

        assertEquals(
            listOf(YearMonth.of(2026, 6), YearMonth.of(2026, 7), YearMonth.of(2026, 8)),
            months,
        )
    }

    @Test
    fun `logged months falls back to the current month when nothing is logged`() {
        assertEquals(
            listOf(YearMonth.of(2026, 8)),
            AnalyticsEngine.loggedMonths(emptyList(), zone, now),
        )
    }

    @Test
    fun `weight overlay pairs each month with the trend at month end`() {
        val fasts = listOf(fast("2026-07-05", LocalTime.of(8, 0), hours = 24.0))
        val weights = listOf(
            WeightRecord(timestamp = instant("2026-07-10", LocalTime.NOON), weightKg = 80.0),
            WeightRecord(timestamp = instant("2026-08-15", LocalTime.NOON), weightKg = 78.0),
        )

        val overlay = AnalyticsEngine.weightVsAutophagy(fasts, weights, zone, now)

        assertEquals(1, overlay.size)
        assertEquals(YearMonth.of(2026, 7), overlay.single().month)
        assertEquals(6.0, overlay.single().autophagyHours, EPS)
        assertEquals(6.0, overlay.single().cumulativeAutophagyHours, EPS)
        // Only the July measurement is on or before 31 July.
        assertEquals(80.0, overlay.single().emaKg!!, EPS)
    }

    @Test
    fun `symptom logs are positioned by how far into their fast they were recorded`() {
        val fast = fast("2026-08-17", LocalTime.of(8, 0), hours = 24.0, id = 7L)
        val explicit = SymptomLog(
            timestamp = instant("2026-08-17", LocalTime.of(22, 0)),
            fastId = 7L,
            energyLevel = 4,
            clarityLevel = 5,
            hungerLevel = 2,
            symptoms = setOf(Symptom.MENTAL_CLARITY),
        )
        val byTime = explicit.copy(fastId = null, timestamp = instant("2026-08-18", LocalTime.of(2, 0)))

        val points = AnalyticsEngine.symptomPoints(listOf(fast), listOf(explicit, byTime), zone, now)

        assertEquals(2, points.size)
        assertEquals(14.0, points[0].elapsedHours, EPS)
        assertEquals(FastStage.GLYCOGEN_DEPLETION, points[0].stage)
        assertEquals(18.0, points[1].elapsedHours, EPS)
        assertEquals(FastStage.EARLY_AUTOPHAGY, points[1].stage)
    }

    @Test
    fun `symptom logs outside any fast are dropped`() {
        val fast = fast("2026-08-17", LocalTime.of(8, 0), hours = 4.0, id = 3L)
        val orphan = SymptomLog(
            timestamp = instant("2026-08-01", LocalTime.NOON),
            energyLevel = 3,
            clarityLevel = 3,
            hungerLevel = 3,
        )

        assertTrue(AnalyticsEngine.symptomPoints(listOf(fast), listOf(orphan), zone, now).isEmpty())
    }

    @Test
    fun `meal stats average the logs of each composition`() {
        val keto = fast("2026-08-16", LocalTime.of(8, 0), hours = 20.0, meal = MealComposition.KETO_LOW_CARB, id = 1L)
        val carb = fast("2026-08-17", LocalTime.of(8, 0), hours = 20.0, meal = MealComposition.HIGH_CARB, id = 2L)
        val logs = listOf(
            SymptomLog(timestamp = keto.start.plus(Duration.ofHours(10)), fastId = 1L, energyLevel = 5, clarityLevel = 4, hungerLevel = 1),
            SymptomLog(timestamp = keto.start.plus(Duration.ofHours(12)), fastId = 1L, energyLevel = 3, clarityLevel = 4, hungerLevel = 3),
            SymptomLog(timestamp = carb.start.plus(Duration.ofHours(10)), fastId = 2L, energyLevel = 2, clarityLevel = 2, hungerLevel = 5),
        )

        val stats = AnalyticsEngine.statsByMeal(
            AnalyticsEngine.symptomPoints(listOf(keto, carb), logs, zone, now)
        )

        val ketoStats = stats.single { it.meal == MealComposition.KETO_LOW_CARB }
        assertEquals(2, ketoStats.logCount)
        assertEquals(4.0, ketoStats.averageEnergy, EPS)
        assertEquals(2.0, ketoStats.averageHunger, EPS)
        assertEquals(11.0, ketoStats.averageFastHours, EPS)
    }

    @Test
    fun `effort is compared against month-on-month trend change, not the cumulative total`() {
        val points = listOf(
            WeightAutophagyPoint(YearMonth.of(2026, 5), 10.0, 10.0, 90.0),
            WeightAutophagyPoint(YearMonth.of(2026, 6), 20.0, 30.0, 89.0),
            WeightAutophagyPoint(YearMonth.of(2026, 7), 30.0, 60.0, 87.5),
            // A month with no weight measurement drops out rather than inventing a delta.
            WeightAutophagyPoint(YearMonth.of(2026, 8), 40.0, 100.0, null),
        )

        val pairs = AnalyticsEngine.monthlyEffortVsTrendChange(points)

        assertEquals(2, pairs.size)
        assertEquals(20.0, pairs[0].first, EPS)
        assertEquals(-1.0, pairs[0].second, EPS)
        assertEquals(30.0, pairs[1].first, EPS)
        assertEquals(-1.5, pairs[1].second, EPS)

        // The cumulative series would correlate at exactly -1 with any falling trend — the reason
        // the engine reports month-on-month change instead.
        val spurious = AnalyticsEngine.pearson(
            points.dropLast(1).map { it.cumulativeAutophagyHours },
            points.dropLast(1).mapNotNull { it.emaKg },
        )
        assertEquals(-1.0, spurious!!, 0.02)
    }

    @Test
    fun `pearson needs three varying points and reports perfect correlation exactly`() {
        assertNull(AnalyticsEngine.pearson(listOf(1.0, 2.0), listOf(1.0, 2.0)))
        assertNull(AnalyticsEngine.pearson(listOf(1.0, 1.0, 1.0), listOf(1.0, 2.0, 3.0)))
        assertEquals(1.0, AnalyticsEngine.pearson(listOf(1.0, 2.0, 3.0), listOf(2.0, 4.0, 6.0))!!, 1e-12)
        assertEquals(-1.0, AnalyticsEngine.pearson(listOf(1.0, 2.0, 3.0), listOf(6.0, 4.0, 2.0))!!, 1e-12)
    }

    @Test
    fun `an active fast is measured up to now`() {
        val running = FastRecord(
            id = 9L,
            start = now.minus(Duration.ofHours(10)),
            end = null,
            meal = MealComposition.BALANCED,
        )

        val distribution = AnalyticsEngine.phaseDistribution(listOf(running), AnalyticsRange.WEEK, now)

        assertEquals(10.0, distribution.totalHours, EPS)
    }

    private fun fast(
        date: String,
        time: LocalTime,
        hours: Double,
        meal: MealComposition = MealComposition.BALANCED,
        id: Long = 0L,
    ): FastRecord {
        val start = instant(date, time)
        return FastRecord(
            id = id,
            start = start,
            end = start.plusMillis((hours * FastRecord.MILLIS_PER_HOUR).toLong()),
            meal = meal,
        )
    }

    private fun instant(date: String, time: LocalTime): Instant =
        LocalDate.parse(date).atTime(time).atZone(zone).toInstant()

    private companion object {
        const val EPS = 1e-9
    }
}
