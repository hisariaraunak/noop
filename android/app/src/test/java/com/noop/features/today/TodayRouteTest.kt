package com.noop.features.today

import com.noop.data.DailyMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodayRouteTest {
    @Test fun currentDayIsNotFoldedIntoItsOwnBaseline() {
        val days = listOf(
            metric("2026-08-08", hrv = 50.0, rhr = 55),
            metric("2026-08-09", hrv = 50.0, rhr = 55),
            metric("2026-08-10", hrv = 50.0, rhr = 55),
            metric("2026-08-11", hrv = 50.0, rhr = 55),
            metric("2026-08-12", hrv = 60.0, rhr = 50),
        )

        val snapshot = buildTodaySnapshot(days)
        assertEquals(20, snapshot.hrvVsBaselinePct)
        assertEquals(-5, snapshot.restingHrVsBaselineBpm)
    }

    @Test fun emptyHistoryProducesHonestMissingState() {
        val snapshot = buildTodaySnapshot(emptyList())
        assertNull(snapshot.recovery)
        assertEquals(false, snapshot.dataComplete)
    }

    @Test fun nonFiniteSensorValuesDegradeToMissingInsteadOfThrowing() {
        val bad = DailyMetric(
            deviceId = "test",
            day = "2026-08-12",
            totalSleepMin = Double.NaN,
            efficiency = Double.POSITIVE_INFINITY,
            restingHr = 50,
            avgHrv = Double.NaN,
            recovery = Double.NaN,
            strain = Double.NEGATIVE_INFINITY,
        )

        val snapshot = buildTodaySnapshot(listOf(bad))
        assertNull(snapshot.recovery)
        assertNull(snapshot.hrvMs)
        assertNull(snapshot.sleepMinutes)
        assertNull(snapshot.sleepEfficiencyPct)
        assertNull(snapshot.strain)
        assertEquals(false, snapshot.dataComplete)
    }

    private fun metric(day: String, hrv: Double, rhr: Int) = DailyMetric(
        deviceId = "test",
        day = day,
        totalSleepMin = 450.0,
        efficiency = 0.88,
        restingHr = rhr,
        avgHrv = hrv,
        recovery = 70.0,
        strain = 10.0,
    )
}
