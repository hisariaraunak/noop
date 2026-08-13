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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.data.JournalEntry
import com.noop.ui.AppViewModel
import com.noop.ui.designsystem.NoopSpacing
import com.noop.ui.designsystem.NoopSurface
import com.noop.ui.designsystem.NoopSurfaceLevel
import com.noop.ui.designsystem.NoopType
import java.time.LocalDate

/** New primary Journal surface. The mature editor remains one level deeper during migration. */
@Composable
internal fun JournalOverviewScreen(
    viewModel: AppViewModel,
    onOpenJournal: () -> Unit,
) {
    val days by viewModel.recentDays.collectAsStateWithLifecycle()
    val today = LocalDate.now().toString()
    var entries by remember { mutableStateOf<List<JournalEntry>>(emptyList()) }

    LaunchedEffect(today) {
        val imported = viewModel.repo.journal("my-whoop", today, today)
        val native = viewModel.repo.journal("noop-journal", today, today)
        entries = (imported + native).distinctBy { it.question }
    }

    val answered = entries.count { it.answeredYes || it.numericValue != null }
    val latest = days.lastOrNull()

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
                Text("JOURNAL", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Notice what shapes you", style = NoopType.editorialHeadline)
                Text(
                    "Log context around your day, then connect it back to recovery, sleep and strain.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            NoopSurface(level = NoopSurfaceLevel.Glass, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.md)) {
                    Text("TODAY'S CHECK-IN", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (answered > 0) "$answered signals logged today" else "Add context to today's numbers",
                        style = NoopType.editorialHeadline,
                    )
                    Text(
                        if (answered > 0) "You can add, edit or review today's entries at any time."
                        else "Mood, caffeine, habits and custom questions become more useful when logged consistently.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onOpenJournal) {
                        Text(if (answered > 0) "Review today's journal" else "Log today's journal")
                    }
                }
            }
        }

        item {
            Text("Today in context", style = NoopType.editorialHeadline)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                ContextMetric("Recovery", latest?.recovery?.let { "${it.toInt()}%" } ?: "—", Modifier.weight(1f))
                ContextMetric("Sleep", latest?.totalSleepMin?.let { formatMinutes(it) } ?: "—", Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                ContextMetric("HRV", latest?.avgHrv?.let { "${it.toInt()} ms" } ?: "—", Modifier.weight(1f))
                ContextMetric("Strain", latest?.strain?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "—", Modifier.weight(1f))
            }
        }

        item {
            NoopSurface(level = NoopSurfaceLevel.Standard, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
                    Text("WHY JOURNAL", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Patterns need context", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "A metric tells you what changed. Repeated journal entries help explain what may have moved with it over time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextMetric(label: String, value: String, modifier: Modifier = Modifier) {
    NoopSurface(modifier = modifier, level = NoopSurfaceLevel.Standard) {
        Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
            Text(label.uppercase(), style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = NoopType.dataDisplay)
        }
    }
}

private fun formatMinutes(minutes: Double): String {
    val total = minutes.toInt().coerceAtLeast(0)
    return "${total / 60}h ${total % 60}m"
}
