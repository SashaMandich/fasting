package dev.local.fasting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WeightTrendTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    @Test
    fun `alpha follows the standard two over n plus one`() {
        assertEquals(0.25, WeightTrend.alpha(7), EPS)
        assertEquals(0.4, WeightTrend.alpha(4), EPS)
    }

    @Test
    fun `same day measurements are averaged before smoothing`() {
        val entries = listOf(
            entry("2026-08-01", LocalTime.of(7, 0), 80.0),
            entry("2026-08-01", LocalTime.of(21, 0), 82.0),
        )

        val daily = WeightTrend.dailyAverages(entries, zone)

        assertEquals(1, daily.size)
        assertEquals(81.0, daily.single().rawKg, EPS)
    }

    @Test
    fun `ema seeds on the first value and lags the raw series`() {
        val entries = listOf(
            entry("2026-08-01", LocalTime.NOON, 80.0),
            entry("2026-08-02", LocalTime.NOON, 79.0),
            entry("2026-08-03", LocalTime.NOON, 78.0),
        )

        val trend = WeightTrend.trend(entries, zone, periodDays = 7)

        assertEquals(80.0, trend[0].emaKg, EPS)
        assertEquals(0.25 * 79.0 + 0.75 * 80.0, trend[1].emaKg, EPS)
        assertEquals(0.25 * 78.0 + 0.75 * trend[1].emaKg, trend[2].emaKg, EPS)
        // The trend is above the falling raw values — that is the point of smoothing.
        assertTrue(trend.last().emaKg > trend.last().rawKg)
    }

    @Test
    fun `a gap in measurements does not decay the trend`() {
        val contiguous = WeightTrend.trend(
            listOf(
                entry("2026-08-01", LocalTime.NOON, 80.0),
                entry("2026-08-02", LocalTime.NOON, 78.0),
            ),
            zone,
        )
        val withGap = WeightTrend.trend(
            listOf(
                entry("2026-08-01", LocalTime.NOON, 80.0),
                entry("2026-08-20", LocalTime.NOON, 78.0),
            ),
            zone,
        )

        assertEquals(contiguous.last().emaKg, withGap.last().emaKg, EPS)
    }

    @Test
    fun `ema as of a date carries the last known value forward`() {
        val trend = WeightTrend.trend(
            listOf(
                entry("2026-08-01", LocalTime.NOON, 80.0),
                entry("2026-08-10", LocalTime.NOON, 78.0),
            ),
            zone,
        )

        assertNull(WeightTrend.emaAsOf(trend, LocalDate.parse("2026-07-31")))
        assertEquals(80.0, WeightTrend.emaAsOf(trend, LocalDate.parse("2026-08-05"))!!, EPS)
        assertEquals(
            trend.last().emaKg,
            WeightTrend.emaAsOf(trend, LocalDate.parse("2026-09-01"))!!,
            EPS,
        )
    }

    @Test
    fun `body fat readings survive daily aggregation`() {
        val trend = WeightTrend.trend(
            listOf(
                entry("2026-08-01", LocalTime.of(7, 0), 80.0, bodyFat = 20.0),
                entry("2026-08-01", LocalTime.of(8, 0), 80.0, bodyFat = 22.0),
            ),
            zone,
        )

        assertEquals(21.0, trend.single().bodyFatPercent!!, EPS)
    }

    private fun entry(
        date: String,
        time: LocalTime,
        weightKg: Double,
        bodyFat: Double? = null,
    ): WeightRecord = WeightRecord(
        timestamp = instant(date, time),
        weightKg = weightKg,
        bodyFatPercent = bodyFat,
    )

    private fun instant(date: String, time: LocalTime): Instant =
        LocalDate.parse(date).atTime(time).atZone(zone).toInstant()

    private companion object {
        const val EPS = 1e-9
    }
}
