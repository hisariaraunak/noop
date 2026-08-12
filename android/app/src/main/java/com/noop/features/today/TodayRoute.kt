package com.noop.features.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.noop.analytics.Baselines
import com.noop.features.today.domain.TodaySnapshot
import com.noop.features.today.domain.TodaySnapshotMapper
import com.noop.ui.AppViewModel
import java.time.LocalDate

/**
 * Production data adapter for the rebuilt Today screen.
 *
 * Reads the existing merged Room-backed daily flow and reuses the production personal-baseline engine.
 * It intentionally does not recalculate Recovery/Sleep/Strain; those remain owned by StrandAnalytics.
 */
@Composable
fun TodayRoute(viewModel: AppViewModel) {
    val deviceId = viewModel.activeStrapId
    val daysFlow = remember(deviceId) { viewModel.repo.recentDaysMergedFlow(deviceId) }
    val days by daysFlow.collectAsState(initial = emptyList())

    val snapshot = remember(days) { buildTodaySnapshot(days) }
    TodayScreen(snapshot = snapshot)
}

internal fun buildTodaySnapshot(days: List<com.noop.data.DailyMetric>): TodaySnapshot {
    val current = days.lastOrNull()
        ?: return TodaySnapshot(
            day = LocalDate.now().toString(),
            recovery = null,
            strain = null,
            sleepMinutes = null,
            sleepEfficiencyPct = null,
            hrvMs = null,
            restingHrBpm = null,
            hrvVsBaselinePct = null,
            restingHrVsBaselineBpm = null,
            dataComplete = false,
        )

    // Baselines must be historical. Including the current reading in its own comparison would damp the
    // displayed delta and make "vs baseline" circular, so fold only the nights preceding the current row.
    val history = days.dropLast(1)
    val hrvBaseline = Baselines.foldHistory(history.map { it.avgHrv }, Baselines.hrvCfg)
    val rhrBaseline = Baselines.foldHistory(history.map { it.restingHr?.toDouble() }, Baselines.restingHRCfg)

    return TodaySnapshotMapper.from(
        daily = current,
        hrvBaseline = hrvBaseline,
        restingHrBaseline = rhrBaseline,
    )
}
