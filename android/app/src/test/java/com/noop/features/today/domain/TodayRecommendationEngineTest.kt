package com.noop.features.today.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TodayRecommendationEngineTest {
    @Test fun highRecoveryWithGoodSignalsPushes() {
        val result = TodayRecommendationEngine.recommend(sample(recovery = 72, sleep = 462.0, hrvDelta = 8, rhrDelta = -3))
        assertEquals(RecommendationLevel.PUSH, result.level)
    }

    @Test fun shortSleepOverridesHighRecovery() {
        val result = TodayRecommendationEngine.recommend(sample(recovery = 75, sleep = 330.0, hrvDelta = 4, rhrDelta = 0))
        assertEquals(RecommendationLevel.RECOVER, result.level)
    }

    @Test fun missingRecoveryDoesNotInventAdvice() {
        val result = TodayRecommendationEngine.recommend(sample(recovery = null, sleep = null, hrvDelta = null, rhrDelta = null))
        assertEquals(RecommendationLevel.INCOMPLETE, result.level)
    }

    private fun sample(recovery: Int?, sleep: Double?, hrvDelta: Int?, rhrDelta: Int?) = TodaySnapshot(
        day = "2026-08-12",
        recovery = recovery,
        strain = 4.0,
        sleepMinutes = sleep,
        sleepEfficiencyPct = 85,
        hrvMs = if (hrvDelta == null) null else 55.0,
        restingHrBpm = 54,
        hrvVsBaselinePct = hrvDelta,
        restingHrVsBaselineBpm = rhrDelta,
        dataComplete = recovery != null && sleep != null && hrvDelta != null,
    )
}
