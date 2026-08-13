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
import kotlin.math.sqrt

@Composable
internal fun TrendsOverviewScreen(viewModel: AppViewModel) {
    val days by viewModel.recentDays.collectAsStateWithLifecycle()
    var window by remember { mutableIntStateOf(30) }
    val recent = days.takeLast(window)

    val recoveryValues = recent.mapNotNull { it.recovery?.takeIf(Double::isFinite)?.toFloat() }
    val hrvValues = recent.mapNotNull { it.avgHrv?.takeIf(Double::isFinite)?.toFloat() }
    val rhrValues = recent.mapNotNull { it.restingHr?.toFloat() }
    val sleepValues = recent.mapNotNull { it.totalSleepMin?.takeIf(Double::isFinite)?.toFloat() }
    val strainValues = recent.mapNotNull { it.strain?.takeIf(Double::isFinite)?.toFloat() }
    val insights = remember(recent) { correlationInsights(recent) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NoopSpacing.screenHorizontal, NoopSpacing.md, NoopSpacing.screenHorizontal, NoopSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(NoopSpacing.md),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xxs)) {
                Text("TRENDS", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Your patterns", style = NoopType.screenTitle)
                Text("Movement across your own baseline.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
                listOf(7, 30, 90).forEach { n -> FilterChip(selected = window == n, onClick = { window = n }, label = { Text("${n}d") }) }
            }
        }
        if (recent.isEmpty()) {
            item { RebuildStateCard("No trend data yet", "Sync several days of history to start building your trend view.") }
            item {
                NoopSurface(level = NoopSurfaceLevel.Standard, modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                        Text("WHAT WILL APPEAR HERE", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Recovery, HRV, resting HR, sleep and strain", style = NoopType.sectionTitle)
                        Text("After 7 overlapping days, NOOP can also surface relationships between signals instead of leaving this screen empty.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            item { TrendCard("Recovery", recoveryValues.lastOrNull()?.let { "${it.roundToInt()}%" } ?: "—", recoveryValues, trendCopy(recoveryValues.map(Float::toDouble), true)) }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                    CompactTrend("HRV", hrvValues.lastOrNull()?.let { "${it.roundToInt()} ms" } ?: "—", hrvValues, Modifier.weight(1f))
                    CompactTrend("Resting HR", rhrValues.lastOrNull()?.let { "${it.roundToInt()} bpm" } ?: "—", rhrValues, Modifier.weight(1f))
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                    CompactTrend("Sleep", sleepValues.lastOrNull()?.let { formatMinutes(it.toDouble()) } ?: "—", sleepValues, Modifier.weight(1f))
                    CompactTrend("Strain", strainValues.lastOrNull()?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "—", strainValues, Modifier.weight(1f))
                }
            }
            item { Text("Relationships", style = NoopType.sectionTitle) }
            if (insights.isEmpty()) item { RebuildStateCard("Patterns are still forming", "At least seven overlapping readings are required before NOOP surfaces a relationship.") }
            else items(insights.size) { i -> InsightCard(insights[i]) }
        }
    }
}

private data class CorrelationInsight(val title: String, val body: String, val r: Double, val n: Int)

@Composable
private fun InsightCard(insight: CorrelationInsight) {
    NoopSurface(level = NoopSurfaceLevel.Standard, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
            Text(insight.title, style = NoopType.sectionTitle)
            Text(insight.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("r=${String.format(java.util.Locale.US, "%.2f", insight.r)} · n=${insight.n}", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun correlationInsights(days: List<DailyMetric>): List<CorrelationInsight> {
    fun build(title: String, a: (DailyMetric) -> Double?, b: (DailyMetric) -> Double?, positiveCopy: String, negativeCopy: String): CorrelationInsight? {
        val pairs = days.mapNotNull { d ->
            val x = a(d)?.takeIf(Double::isFinite)
            val y = b(d)?.takeIf(Double::isFinite)
            if (x != null && y != null) x to y else null
        }
        return insightFromPairs(title, pairs, positiveCopy, negativeCopy)
    }
    fun buildNextDay(title: String, today: (DailyMetric) -> Double?, nextDay: (DailyMetric) -> Double?, positiveCopy: String, negativeCopy: String): CorrelationInsight? {
        val pairs = days.zipWithNext().mapNotNull { (current, following) ->
            val x = today(current)?.takeIf(Double::isFinite)
            val y = nextDay(following)?.takeIf(Double::isFinite)
            if (x != null && y != null) x to y else null
        }
        return insightFromPairs(title, pairs, positiveCopy, negativeCopy)
    }
    return listOfNotNull(
        build("Sleep ↔ recovery", { it.totalSleepMin }, { it.recovery }, "Longer sleep has tended to move with higher recovery.", "Longer sleep has not translated into higher recovery in this window."),
        build("HRV ↔ recovery", { it.avgHrv }, { it.recovery }, "Higher HRV has tended to move with higher recovery.", "HRV and recovery have moved in opposite directions recently."),
        build("Resting HR ↔ recovery", { it.restingHr?.toDouble() }, { it.recovery }, "Higher resting HR has moved with higher recovery in this sample.", "Lower resting HR has tended to move with higher recovery."),
        buildNextDay("Strain ↔ next-day recovery", { it.strain }, { it.recovery }, "Higher load has tended to precede higher next-day recovery in this sample.", "Higher load has tended to precede lower next-day recovery."),
    ).sortedByDescending { kotlin.math.abs(it.r) }
}

private fun insightFromPairs(title: String, pairs: List<Pair<Double, Double>>, positiveCopy: String, negativeCopy: String): CorrelationInsight? {
    if (pairs.size < 7) return null
    val r = pearson(pairs) ?: return null
    if (kotlin.math.abs(r) < 0.2) return null
    return CorrelationInsight(title, if (r >= 0) positiveCopy else negativeCopy, r, pairs.size)
}

private fun pearson(pairs: List<Pair<Double, Double>>): Double? {
    if (pairs.size < 2) return null
    val mx = pairs.map { it.first }.average(); val my = pairs.map { it.second }.average()
    var num = 0.0; var dx = 0.0; var dy = 0.0
    for ((x, y) in pairs) { val a = x - mx; val b = y - my; num += a * b; dx += a * a; dy += b * b }
    val denom = sqrt(dx * dy)
    return if (denom > 0 && denom.isFinite()) (num / denom).takeIf(Double::isFinite) else null
}

@Composable
private fun TrendCard(title: String, value: String, values: List<Float>, insight: String) {
    NoopSurface(level = NoopSurfaceLevel.Elevated, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
            Text(title.uppercase(), style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = NoopType.dataHero)
            if (values.size >= 2) Sparkline(values)
            Text(insight, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompactTrend(title: String, value: String, values: List<Float>, modifier: Modifier = Modifier) {
    NoopSurface(level = NoopSurfaceLevel.Standard, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xxs)) {
            Text(title.uppercase(), style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = NoopType.dataDisplay)
            if (values.size >= 2) Sparkline(values)
        }
    }
}

private fun trendCopy(values: List<Double>, higherIsBetter: Boolean): String {
    val finite = values.filter(Double::isFinite)
    if (finite.size < 4) return "More days are needed before calling a direction."
    val midpoint = finite.size / 2
    val older = finite.take(midpoint).average(); val newer = finite.drop(midpoint).average()
    if (!older.isFinite() || !newer.isFinite() || older == 0.0) return "Your recent pattern is still forming."
    val pct = ((newer / older) - 1.0) * 100.0
    if (!pct.isFinite() || kotlin.math.abs(pct) < 3.0) return "Broadly stable across the recent window."
    val favorable = if (higherIsBetter) pct > 0 else pct < 0
    return "Recent average is ${if (pct > 0) "up" else "down"} ${kotlin.math.abs(pct).roundToInt()}%${if (favorable) "." else "; worth watching."}"
}

private fun formatMinutes(minutes: Double): String {
    val t = minutes.takeIf(Double::isFinite)?.roundToInt()?.coerceAtLeast(0) ?: return "—"
    return "${t / 60}h ${t % 60}m"
}
