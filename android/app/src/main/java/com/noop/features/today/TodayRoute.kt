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

@Composable
fun TodayRoute(
    viewModel: AppViewModel,
    onRecovery: () -> Unit = {},
    onSleep: () -> Unit = {},
    onStrain: () -> Unit = {},
) {
    val deviceId = viewModel.activeStrapId
    val daysFlow = remember(deviceId) { viewModel.repo.recentDaysMergedFlow(deviceId) }
    val days by daysFlow.collectAsState(initial = emptyList())
    val snapshot = remember(days) { buildTodaySnapshot(days) }
    TodayScreen(snapshot = snapshot, onRecovery = onRecovery, onSleep = onSleep, onStrain = onStrain)
}

internal fun buildTodaySnapshot(days: List<com.noop.data.DailyMetric>): TodaySnapshot {
    val current = days.lastOrNull()
        ?: return TodaySnapshot(
            day = LocalDate.now().toString(), recovery = null, strain = null, sleepMinutes = null,
            sleepEfficiencyPct = null, hrvMs = null, restingHrBpm = null, hrvVsBaselinePct = null,
            restingHrVsBaselineBpm = null, dataComplete = false,
        )
    val history = days.dropLast(1)
    val hrvBaseline = Baselines.foldHistory(history.map { it.avgHrv }, Baselines.hrvCfg)
    val rhrBaseline = Baselines.foldHistory(history.map { it.restingHr?.toDouble() }, Baselines.restingHRCfg)
    return TodaySnapshotMapper.from(current, hrvBaseline, rhrBaseline)
}
