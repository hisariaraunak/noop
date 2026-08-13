package com.noop.app

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.noop.data.WorkoutRow
import com.noop.ui.AppViewModel
import com.noop.ui.designsystem.NoopSpacing
import com.noop.ui.designsystem.NoopSurface
import com.noop.ui.designsystem.NoopSurfaceLevel
import com.noop.ui.designsystem.NoopType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
internal fun WorkoutsV2Screen(viewModel: AppViewModel) {
    var workouts by remember { mutableStateOf<List<WorkoutRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val deviceId = viewModel.activeStrapId

    LaunchedEffect(deviceId) {
        loading = true; error = null
        runCatching {
            val now = System.currentTimeMillis() / 1000L
            val from = now - 120L * 86_400L
            val imported = viewModel.repo.workoutsUnion(deviceId, from, now, 500)
            val detected = viewModel.repo.detectedWorkoutsUnion(deviceId, from, now, 500)
            (imported + detected).distinctBy { it.startTs to it.sport }.sortedByDescending { it.startTs }
        }.onSuccess { workouts = it }.onFailure { error = it.message ?: "Workouts could not be loaded." }
        loading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NoopSpacing.screenHorizontal, NoopSpacing.lg, NoopSpacing.screenHorizontal, NoopSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(NoopSpacing.lg),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
                Text("WORKOUTS", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Effort, session by session", style = NoopType.editorialHeadline)
                Text("A single timeline of imported and NOOP-detected activity, grounded in the data already on your device.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when {
            loading -> item { RebuildStateCard("Loading workouts", "Reading recent sessions from your local store.") }
            error != null -> item { RebuildStateCard("Workouts unavailable", error ?: "Unknown error") }
            workouts.isEmpty() -> item { RebuildStateCard("No workouts yet", "Recorded or imported sessions will appear here automatically.") }
            else -> {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                        SummaryMetric("Sessions", workouts.size.toString(), Modifier.weight(1f))
                        val avg = workouts.mapNotNull { it.strain?.takeIf(Double::isFinite) }.average().takeIf(Double::isFinite)
                        SummaryMetric("Avg strain", avg?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "—", Modifier.weight(1f))
                    }
                }
                items(workouts.size) { i -> WorkoutCard(workouts[i]) }
            }
        }
    }
}

@Composable
private fun WorkoutCard(w: WorkoutRow) {
    NoopSurface(level = NoopSurfaceLevel.Standard, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(w.sport.ifBlank { "Workout" }, style = MaterialTheme.typography.bodyLarge)
                    Text(formatDate(w.startTs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(w.strain?.takeIf(Double::isFinite)?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "—", style = NoopType.dataDisplay)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(w.durationS?.takeIf(Double::isFinite)?.let { formatDuration(it) } ?: "Duration —", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(w.avgHr?.let { "$it avg bpm" } ?: "HR —", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(w.energyKcal?.takeIf(Double::isFinite)?.let { "${it.roundToInt()} kcal" } ?: "Energy —", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier) {
    NoopSurface(level = NoopSurfaceLevel.Elevated, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
            Text(label.uppercase(), style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = NoopType.dataDisplay)
        }
    }
}

private fun formatDate(ts: Long): String = Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a"))
private fun formatDuration(seconds: Double): String { val m = (seconds / 60.0).roundToInt().coerceAtLeast(0); return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m" }
