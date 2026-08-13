package com.noop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/** New primary Trends surface; detailed Week in Review remains available as a secondary destination. */
@Composable
internal fun TrendsOverviewScreen(
    viewModel: AppViewModel,
    onOpenWeekReview: () -> Unit,
) {
    val days by viewModel.recentDays.collectAsStateWithLifecycle()
    val recent = days.takeLast(14)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = NoopSpacing.screenHorizontal,
            end = NoopSpacing.screenHorizontal,
            top = NoopSpacing.lg,
            bottom = NoopSpacing.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(NoopSpacing.lg),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
                Text("TRENDS", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("See the direction, not the noise", style = NoopType.editorialHeadline)
                Text(
                    "Recent movement against your own history, with deeper review when you want it.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            TrendCard(
                title = "Recovery",
                value = recent.lastOrNull()?.recovery?.let { "${it.roundToInt()}%" } ?: "—",
                values = recent.mapNotNull { it.recovery?.toFloat() },
                insight = trendCopy(recent.mapNotNull { it.recovery }, higherIsBetter = true),
            )
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                CompactTrend(
                    title = "HRV",
                    value = recent.lastOrNull()?.avgHrv?.let { "${it.roundToInt()} ms" } ?: "—",
                    values = recent.mapNotNull { it.avgHrv?.toFloat() },
                    modifier = Modifier.weight(1f),
                )
                CompactTrend(
                    title = "Resting HR",
                    value = recent.lastOrNull()?.restingHr?.let { "$it bpm" } ?: "—",
                    values = recent.mapNotNull { it.restingHr?.toFloat() },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                CompactTrend(
                    title = "Sleep",
                    value = recent.lastOrNull()?.totalSleepMin?.let { formatMinutes(it) } ?: "—",
                    values = recent.mapNotNull { it.totalSleepMin?.toFloat() },
                    modifier = Modifier.weight(1f),
                )
                CompactTrend(
                    title = "Strain",
                    value = recent.lastOrNull()?.strain?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "—",
                    values = recent.mapNotNull { it.strain?.toFloat() },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            NoopSurface(level = NoopSurfaceLevel.Glass, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.md)) {
                    Text("DEEPER REVIEW", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Your week, explained", style = NoopType.editorialHeadline)
                    Text(
                        "Open the detailed review for daily signals, comparisons and the longer analytical view.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onOpenWeekReview) { Text("Open week in review") }
                }
            }
        }
    }
}

@Composable
private fun TrendCard(title: String, value: String, values: List<Float>, insight: String) {
    NoopSurface(level = NoopSurfaceLevel.Elevated, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
            Text(title.uppercase(), style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = NoopType.dataHero)
            if (values.size >= 2) Sparkline(values = values)
            Text(insight, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompactTrend(title: String, value: String, values: List<Float>, modifier: Modifier = Modifier) {
    NoopSurface(level = NoopSurfaceLevel.Standard, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
            Text(title.uppercase(), style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = NoopType.dataDisplay)
            if (values.size >= 2) Sparkline(values = values)
        }
    }
}

private fun trendCopy(values: List<Double>, higherIsBetter: Boolean): String {
    if (values.size < 4) return "More days are needed before calling a direction."
    val midpoint = values.size / 2
    val older = values.take(midpoint).average()
    val newer = values.drop(midpoint).average()
    if (older == 0.0) return "Your recent pattern is still forming."
    val pct = ((newer / older) - 1.0) * 100.0
    if (kotlin.math.abs(pct) < 3.0) return "Broadly stable across the recent window."
    val favorable = if (higherIsBetter) pct > 0 else pct < 0
    val direction = if (pct > 0) "up" else "down"
    return "Recent average is $direction ${kotlin.math.abs(pct).roundToInt()}% versus the earlier half${if (favorable) "." else "; worth watching."}"
}

private fun formatMinutes(minutes: Double): String {
    val total = minutes.roundToInt().coerceAtLeast(0)
    return "${total / 60}h ${total % 60}m"
}
