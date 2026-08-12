package com.noop.features.today.domain

import com.noop.analytics.BaselineState
import com.noop.data.DailyMetric
import kotlin.math.roundToInt

/** UI-facing snapshot. No physiological calculations live here. */
data class TodaySnapshot(
    val day: String,
    val recovery: Int?,
    val strain: Double?,
    val sleepMinutes: Double?,
    val sleepEfficiencyPct: Int?,
    val hrvMs: Double?,
    val restingHrBpm: Int?,
    val hrvVsBaselinePct: Int?,
    val restingHrVsBaselineBpm: Int?,
    val dataComplete: Boolean,
)

object TodaySnapshotMapper {
    fun from(
        daily: DailyMetric,
        hrvBaseline: BaselineState? = null,
        restingHrBaseline: BaselineState? = null,
    ): TodaySnapshot {
        val hrvDeltaPct = if (daily.avgHrv != null && hrvBaseline?.usable == true && hrvBaseline.baseline > 0) {
            (((daily.avgHrv / hrvBaseline.baseline) - 1.0) * 100.0).roundToInt()
        } else null

        val rhrDelta = if (daily.restingHr != null && restingHrBaseline?.usable == true) {
            (daily.restingHr - restingHrBaseline.baseline).roundToInt()
        } else null

        return TodaySnapshot(
            day = daily.day,
            recovery = daily.recovery?.roundToInt()?.coerceIn(0, 100),
            strain = daily.strain,
            sleepMinutes = daily.totalSleepMin,
            sleepEfficiencyPct = daily.efficiency?.let { efficiency ->
                val pct = if (efficiency <= 1.0) efficiency * 100.0 else efficiency
                pct.roundToInt().coerceIn(0, 100)
            },
            hrvMs = daily.avgHrv,
            restingHrBpm = daily.restingHr,
            hrvVsBaselinePct = hrvDeltaPct,
            restingHrVsBaselineBpm = rhrDelta,
            dataComplete = daily.recovery != null && daily.avgHrv != null && daily.restingHr != null && daily.totalSleepMin != null,
        )
    }
}
