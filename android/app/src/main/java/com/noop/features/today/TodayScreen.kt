package com.noop.features.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noop.features.today.domain.TodayRecommendationEngine
import com.noop.features.today.domain.TodaySnapshot
import com.noop.ui.designsystem.DeltaDirection
import com.noop.ui.designsystem.MetricCard
import com.noop.ui.designsystem.MetricDeltaModel
import com.noop.ui.designsystem.NoopSpacing
import com.noop.ui.designsystem.NoopSurface
import com.noop.ui.designsystem.NoopSurfaceLevel
import com.noop.ui.designsystem.NoopType
import com.noop.ui.designsystem.RecoveryHero
import kotlin.math.abs
import kotlin.math.roundToInt

/** Step-4 prototype only. Deliberately takes an immutable snapshot and has no repository/ViewModel wiring. */
@Composable
fun TodayScreen(
    snapshot: TodaySnapshot,
    modifier: Modifier = Modifier,
) {
    val recommendation = TodayRecommendationEngine.recommend(snapshot)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = NoopSpacing.screenHorizontal,
            end = NoopSpacing.screenHorizontal,
            top = NoopSpacing.lg,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(NoopSpacing.lg),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
                Text("TODAY", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("How your body is showing up", style = NoopType.editorialHeadline)
            }
        }

        item {
            RecoveryHero(
                score = snapshot.recovery,
                headline = recommendation.headline,
                recommendation = recommendation.action,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            NoopSurface(level = NoopSurfaceLevel.Elevated, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                    Text("WHAT'S DRIVING THIS", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    recommendation.reasons.forEach { reason ->
                        Text("• $reason", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        item {
            Text("Your signals", style = NoopType.editorialHeadline)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                MetricCard(
                    label = "HRV",
                    value = snapshot.hrvMs?.roundToInt()?.toString() ?: "—",
                    unit = "ms",
                    delta = snapshot.hrvVsBaselinePct?.let { pct ->
                        MetricDeltaModel(
                            text = "${abs(pct)}% vs base",
                            direction = if (pct >= 0) DeltaDirection.Positive else DeltaDirection.Negative,
                            favorable = pct >= 0,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "Resting HR",
                    value = snapshot.restingHrBpm?.toString() ?: "—",
                    unit = "bpm",
                    delta = snapshot.restingHrVsBaselineBpm?.let { bpm ->
                        MetricDeltaModel(
                            text = "${abs(bpm)} vs base",
                            direction = if (bpm >= 0) DeltaDirection.Positive else DeltaDirection.Negative,
                            favorable = bpm <= 0,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                MetricCard(
                    label = "Sleep",
                    value = formatSleep(snapshot.sleepMinutes),
                    supportingText = snapshot.sleepEfficiencyPct?.let { "$it% efficiency" },
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "Strain",
                    value = snapshot.strain?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "—",
                    supportingText = "Today's load",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (!snapshot.dataComplete) {
            item {
                NoopSurface(level = NoopSurfaceLevel.Standard, modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
                        Text("INCOMPLETE DATA", style = NoopType.labelCaps)
                        Text(
                            "Some overnight signals are still missing. Available measurements are shown without filling gaps.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatSleep(minutes: Double?): String {
    if (minutes == null) return "—"
    val total = minutes.roundToInt().coerceAtLeast(0)
    return "${total / 60}h ${total % 60}m"
}
