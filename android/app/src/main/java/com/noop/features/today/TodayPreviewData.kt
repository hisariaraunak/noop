package com.noop.features.today

import com.noop.features.today.domain.TodaySnapshot

/** Fixed states used only by previews/tests until Step 5 real-data wiring is approved. */
object TodayPreviewData {
    val ready = TodaySnapshot(
        day = "2026-08-12",
        recovery = 72,
        strain = 4.8,
        sleepMinutes = 462.0,
        sleepEfficiencyPct = 87,
        hrvMs = 58.0,
        restingHrBpm = 52,
        hrvVsBaselinePct = 8,
        restingHrVsBaselineBpm = -3,
        dataComplete = true,
    )

    val lowRecovery = TodaySnapshot(
        day = "2026-08-12",
        recovery = 28,
        strain = 3.1,
        sleepMinutes = 318.0,
        sleepEfficiencyPct = 76,
        hrvMs = 41.0,
        restingHrBpm = 61,
        hrvVsBaselinePct = -18,
        restingHrVsBaselineBpm = 7,
        dataComplete = true,
    )

    val incomplete = TodaySnapshot(
        day = "2026-08-12",
        recovery = null,
        strain = 2.0,
        sleepMinutes = null,
        sleepEfficiencyPct = null,
        hrvMs = null,
        restingHrBpm = 55,
        hrvVsBaselinePct = null,
        restingHrVsBaselineBpm = null,
        dataComplete = false,
    )
}
