package com.noop.features.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TodayScreen(
    snapshot: TodaySnapshot,
    modifier: Modifier = Modifier,
    onRecovery: () -> Unit = {},
    onSleep: () -> Unit = {},
    onStrain: () -> Unit = {},
) {
    val recommendation = TodayRecommendationEngine.recommend(snapshot)
    val greeting = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    val date = runCatching { LocalDate.parse(snapshot.day).format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())) }
        .getOrElse { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(NoopSpacing.screenHorizontal, NoopSpacing.lg, NoopSpacing.screenHorizontal, NoopSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(NoopSpacing.lg),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
                Text(date.uppercase(), style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(greeting, style = NoopType.editorialHeadline)
                Text("Your body, translated into a plan for today.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            RecoveryHero(
                score = snapshot.recovery,
                headline = recommendation.headline,
                recommendation = recommendation.action,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onRecovery),
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                Text("Today's signals", style = NoopType.editorialHeadline)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                    MetricCard(
                        label = "HRV",
                        value = snapshot.hrvMs?.takeIf(Double::isFinite)?.roundToInt()?.toString() ?: "—",
                        unit = "ms",
                        delta = snapshot.hrvVsBaselinePct?.let { pct -> MetricDeltaModel("${abs(pct)}% vs base", if (pct >= 0) DeltaDirection.Positive else DeltaDirection.Negative, pct >= 0) },
                        modifier = Modifier.weight(1f).clickable(onClick = onRecovery),
                    )
                    MetricCard(
                        label = "Resting HR",
                        value = snapshot.restingHrBpm?.toString() ?: "—",
                        unit = "bpm",
                        delta = snapshot.restingHrVsBaselineBpm?.let { bpm -> MetricDeltaModel("${abs(bpm)} vs base", if (bpm >= 0) DeltaDirection.Positive else DeltaDirection.Negative, bpm <= 0) },
                        modifier = Modifier.weight(1f).clickable(onClick = onRecovery),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                    MetricCard(label = "Sleep", value = formatSleep(snapshot.sleepMinutes), supportingText = snapshot.sleepEfficiencyPct?.let { "$it% efficiency" }, modifier = Modifier.weight(1f).clickable(onClick = onSleep))
                    MetricCard(label = "Strain", value = snapshot.strain?.takeIf(Double::isFinite)?.let { String.format(Locale.US, "%.1f", it) } ?: "—", supportingText = "Today's load", modifier = Modifier.weight(1f).clickable(onClick = onStrain))
                }
            }
        }
        item {
            NoopSurface(level = NoopSurfaceLevel.Elevated, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.md)) {
                    Text("WHAT'S DRIVING TODAY", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    recommendation.reasons.forEachIndexed { index, reason ->
                        Row(horizontalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                            Text("0${index + 1}", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.primary)
                            Text(reason, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item {
            NoopSurface(level = NoopSurfaceLevel.Standard, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
                    Text("TODAY'S GUIDANCE", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(recommendation.headline, style = MaterialTheme.typography.bodyLarge)
                    Text(recommendation.action, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!snapshot.dataComplete) item { NoopSurface(level = NoopSurfaceLevel.Standard, modifier = Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) { Text("INCOMPLETE DATA", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("Some overnight signals are still missing. Available measurements are shown without filling gaps.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
    }
}

private fun formatSleep(minutes: Double?): String {
    val total = minutes?.takeIf(Double::isFinite)?.roundToInt()?.coerceAtLeast(0) ?: return "—"
    return "${total / 60}h ${total % 60}m"
}
