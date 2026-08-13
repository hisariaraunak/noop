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
        // Sensor/import pipelines can occasionally surface NaN/Infinity. Never allow those values to
        // reach roundToInt() or UI math: Kotlin throws IllegalArgumentException for NaN rounding.
        val recovery = daily.recovery?.takeIf { it.isFinite() }
        val strain = daily.strain?.takeIf { it.isFinite() }
        val sleepMinutes = daily.totalSleepMin?.takeIf { it.isFinite() }
        val efficiency = daily.efficiency?.takeIf { it.isFinite() }
        val hrv = daily.avgHrv?.takeIf { it.isFinite() }

        val hrvBaselineValue = hrvBaseline?.baseline?.takeIf { it.isFinite() && it > 0.0 }
        val restingHrBaselineValue = restingHrBaseline?.baseline?.takeIf { it.isFinite() }

        val hrvDeltaPct = if (hrv != null && hrvBaseline?.usable == true && hrvBaselineValue != null) {
            (((hrv / hrvBaselineValue) - 1.0) * 100.0)
                .takeIf { it.isFinite() }
                ?.roundToInt()
        } else null

        val rhrDelta = if (daily.restingHr != null && restingHrBaseline?.usable == true && restingHrBaselineValue != null) {
            (daily.restingHr - restingHrBaselineValue)
                .takeIf { it.isFinite() }
                ?.roundToInt()
        } else null

        return TodaySnapshot(
            day = daily.day,
            recovery = recovery?.roundToInt()?.coerceIn(0, 100),
            strain = strain,
            sleepMinutes = sleepMinutes,
            sleepEfficiencyPct = efficiency?.let { value ->
                val pct = if (value <= 1.0) value * 100.0 else value
                pct.takeIf { it.isFinite() }?.roundToInt()?.coerceIn(0, 100)
            },
            hrvMs = hrv,
            restingHrBpm = daily.restingHr,
            hrvVsBaselinePct = hrvDeltaPct,
            restingHrVsBaselineBpm = rhrDelta,
            dataComplete = recovery != null && hrv != null && daily.restingHr != null && sleepMinutes != null,
        )
    }
}
