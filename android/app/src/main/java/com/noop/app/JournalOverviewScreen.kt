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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.data.JournalEntry
import com.noop.ui.AppViewModel
import com.noop.ui.designsystem.NoopSpacing
import com.noop.ui.designsystem.NoopSurface
import com.noop.ui.designsystem.NoopSurfaceLevel
import com.noop.ui.designsystem.NoopType
import java.time.LocalDate

@Composable
internal fun JournalOverviewScreen(viewModel: AppViewModel, onOpenJournal: () -> Unit) {
    val days by viewModel.recentDays.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val today = LocalDate.now().toString()
    var entries by remember { mutableStateOf<List<JournalEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) reload++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(today, reload) {
        loading = true; error = null
        runCatching {
            val imported = viewModel.repo.journal("my-whoop", today, today)
            val native = viewModel.repo.journal("noop-journal", today, today)
            // imported first, native last: associateBy keeps the user's local edit on collisions.
            (imported + native).associateBy { it.question }.values.toList()
        }.onSuccess { entries = it }.onFailure { error = it.message ?: "Journal could not be loaded." }
        loading = false
    }

    val answered = entries.count { it.answeredYes || it.numericValue != null }
    val latest = days.lastOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NoopSpacing.screenHorizontal, NoopSpacing.lg, NoopSpacing.screenHorizontal, NoopSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(NoopSpacing.lg),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
                Text("JOURNAL", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Notice what shapes you", style = NoopType.editorialHeadline)
                Text("Log context around your day, then connect it back to recovery, sleep and strain.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (loading) item { RebuildStateCard("Loading today's context", "Reading your local journal.") }
        if (error != null) item { RebuildStateCard("Journal unavailable", error ?: "Unknown error") }
        item {
            NoopSurface(level = NoopSurfaceLevel.Glass, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.md)) {
                    Text("TODAY'S CHECK-IN", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (answered > 0) "$answered signals logged today" else "Add context to today's numbers", style = NoopType.editorialHeadline)
                    Text(if (answered > 0) "You can add, edit or review today's entries at any time." else "Habits and numeric context become more useful when logged consistently.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onOpenJournal) { Text(if (answered > 0) "Review today's journal" else "Log today's journal") }
                }
            }
        }
        item { Text("Today in context", style = NoopType.editorialHeadline) }
        if (latest == null) item { RebuildStateCard("No health context yet", "Journal entries are still available; health metrics will appear after a successful sync.") }
        else {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                    ContextMetric("Recovery", latest.recovery?.takeIf(Double::isFinite)?.let { "${it.toInt()}%" } ?: "—", Modifier.weight(1f))
                    ContextMetric("Sleep", latest.totalSleepMin?.takeIf(Double::isFinite)?.let(::formatMinutes) ?: "—", Modifier.weight(1f))
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                    ContextMetric("HRV", latest.avgHrv?.takeIf(Double::isFinite)?.let { "${it.toInt()} ms" } ?: "—", Modifier.weight(1f))
                    ContextMetric("Strain", latest.strain?.takeIf(Double::isFinite)?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "—", Modifier.weight(1f))
                }
            }
        }
        item {
            NoopSurface(level = NoopSurfaceLevel.Standard, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
                    Text("WHY JOURNAL", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Patterns need context", style = MaterialTheme.typography.bodyLarge)
                    Text("A metric tells you what changed. Repeated journal entries help explain what may have moved with it over time.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ContextMetric(label: String, value: String, modifier: Modifier = Modifier) {
    NoopSurface(modifier = modifier, level = NoopSurfaceLevel.Standard) { Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) { Text(label.uppercase(), style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = NoopType.dataDisplay) } }
}

private fun formatMinutes(minutes: Double): String { val total = minutes.takeIf(Double::isFinite)?.toInt()?.coerceAtLeast(0) ?: return "—"; return "${total / 60}h ${total % 60}m" }
