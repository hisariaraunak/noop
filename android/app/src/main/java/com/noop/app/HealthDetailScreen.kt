package com.noop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.data.DailyMetric
import com.noop.ui.AppViewModel
import com.noop.ui.designsystem.NoopSpacing
import com.noop.ui.designsystem.NoopSurface
import com.noop.ui.designsystem.NoopSurfaceLevel
import com.noop.ui.designsystem.NoopType
import com.noop.ui.designsystem.Sparkline
import kotlin.math.roundToInt

enum class HealthMetric { Recovery, Sleep, Strain }

@Composable
internal fun HealthDetailScreen(viewModel: AppViewModel, metric: HealthMetric) {
    val days by viewModel.recentDays.collectAsStateWithLifecycle()
    var window by remember { mutableIntStateOf(30) }
    val recent = days.takeLast(window)
    val values = recent.mapNotNull { metricValue(metric, it)?.takeIf(Double::isFinite) }
    val latest = recent.lastOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NoopSpacing.screenHorizontal, NoopSpacing.lg, NoopSpacing.screenHorizontal, NoopSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(NoopSpacing.lg),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
                Text(metric.name.uppercase(), style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(detailHeadline(metric), style = NoopType.editorialHeadline)
                Text(detailSubtitle(metric), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
                listOf(7, 30, 90).forEach { n ->
                    FilterChip(selected = window == n, onClick = { window = n }, label = { Text("${n}d") })
                }
            }
        }
        if (values.isEmpty()) {
            item { RebuildStateCard("No ${metric.name.lowercase()} data yet", "Sync more history and this view will populate automatically.") }
        } else {
            item {
                NoopSurface(level = NoopSurfaceLevel.Elevated, modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                        Text("CURRENT", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatMetric(metric, values.last()), style = NoopType.dataHero)
                        if (values.size >= 2) Sparkline(values.map { it.toFloat() })
                        Text("${window}d average ${formatMetric(metric, values.average())}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            latest?.let { day -> item { DriverSection(metric, day) } }
        }
    }
}

@Composable
private fun DriverSection(metric: HealthMetric, day: DailyMetric) {
    NoopSurface(level = NoopSurfaceLevel.Standard, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.md)) {
            Text("DETAILS", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (metric) {
                HealthMetric.Recovery -> {
                    DetailRow("HRV", day.avgHrv?.finiteText("ms") ?: "—")
                    DetailRow("Resting HR", day.restingHr?.let { "$it bpm" } ?: "—")
                    DetailRow("Sleep", day.totalSleepMin?.takeIf(Double::isFinite)?.let(::formatMinutes) ?: "—")
                    DetailRow("Sleep efficiency", day.efficiency?.takeIf(Double::isFinite)?.let { formatPct(it) } ?: "—")
                }
                HealthMetric.Sleep -> {
                    DetailRow("Total sleep", day.totalSleepMin?.takeIf(Double::isFinite)?.let(::formatMinutes) ?: "—")
                    DetailRow("Deep", day.deepMin?.takeIf(Double::isFinite)?.let(::formatMinutes) ?: "—")
                    DetailRow("REM", day.remMin?.takeIf(Double::isFinite)?.let(::formatMinutes) ?: "—")
                    DetailRow("Light", day.lightMin?.takeIf(Double::isFinite)?.let(::formatMinutes) ?: "—")
                    DetailRow("Disturbances", day.disturbances?.toString() ?: "—")
                }
                HealthMetric.Strain -> {
                    DetailRow("Exercise sessions", day.exerciseCount?.toString() ?: "—")
                    DetailRow("Steps", day.steps?.toString() ?: "—")
                    DetailRow("Active energy", day.activeKcalEst?.takeIf(Double::isFinite)?.let { "${it.roundToInt()} kcal" } ?: "—")
                    DetailRow("Recovery", day.recovery?.takeIf(Double::isFinite)?.let { "${it.roundToInt()}%" } ?: "—")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun metricValue(metric: HealthMetric, d: DailyMetric): Double? = when (metric) {
    HealthMetric.Recovery -> d.recovery
    HealthMetric.Sleep -> d.totalSleepMin
    HealthMetric.Strain -> d.strain
}

private fun formatMetric(metric: HealthMetric, value: Double): String = when (metric) {
    HealthMetric.Recovery -> "${value.roundToInt()}%"
    HealthMetric.Sleep -> formatMinutes(value)
    HealthMetric.Strain -> String.format(java.util.Locale.US, "%.1f", value)
}

private fun detailHeadline(metric: HealthMetric) = when (metric) {
    HealthMetric.Recovery -> "Your capacity today"
    HealthMetric.Sleep -> "How your night restored you"
    HealthMetric.Strain -> "How much load you carried"
}

private fun detailSubtitle(metric: HealthMetric) = when (metric) {
    HealthMetric.Recovery -> "Track recovery against your own recent pattern and inspect the signals behind it."
    HealthMetric.Sleep -> "Duration, stages and consistency together tell the fuller story of rest."
    HealthMetric.Strain -> "See daily load alongside activity and recovery so effort stays in context."
}

private fun Double.finiteText(unit: String): String = if (isFinite()) "${roundToInt()} $unit" else "—"
private fun formatMinutes(minutes: Double): String { val t = minutes.roundToInt().coerceAtLeast(0); return "${t / 60}h ${t % 60}m" }
private fun formatPct(v: Double): String { val p = if (v <= 1.0) v * 100 else v; return "${p.roundToInt()}%" }
