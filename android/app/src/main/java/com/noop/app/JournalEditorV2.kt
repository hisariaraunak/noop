package com.noop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.noop.data.JournalEntry
import com.noop.ui.AppViewModel
import com.noop.ui.designsystem.NoopSpacing
import com.noop.ui.designsystem.NoopSurface
import com.noop.ui.designsystem.NoopSurfaceLevel
import com.noop.ui.designsystem.NoopType
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val defaultJournalQuestions = listOf("Alcohol", "Late meal", "High stress", "Meditation", "Outdoor time")

@Composable
internal fun JournalEditorV2(viewModel: AppViewModel) {
    val day = LocalDate.now().toString()
    val answers = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()
    var caffeine by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var initialized by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(day) {
        loading = true
        initialized = false
        loadError = null
        runCatching {
            val imported = viewModel.repo.journal("my-whoop", day, day)
            val native = viewModel.repo.journal("noop-journal", day, day)
            val merged = (imported + native).associateBy { it.question }
            defaultJournalQuestions.forEach { q -> answers[q] = merged[q]?.answeredYes ?: false }
            caffeine = merged["Caffeine"]?.numericValue?.takeIf(Double::isFinite)
                ?.let { String.format(java.util.Locale.US, "%.0f", it) } ?: ""
        }.onFailure { loadError = it.message ?: "Journal could not be loaded." }
        loading = false
        initialized = loadError == null
    }

    // Numeric entry is intentionally debounced: typing "150" should produce one local DB write, not
    // three overlapping writes for 1, 15 and 150. The initial value loaded from Room is not re-saved.
    LaunchedEffect(caffeine, initialized) {
        if (!initialized) return@LaunchedEffect
        delay(400)
        val value = caffeine.toDoubleOrNull()?.takeIf(Double::isFinite)
        runCatching {
            if (value == null) {
                viewModel.repo.deleteJournalEntry("noop-journal", day, "Caffeine")
            } else {
                viewModel.repo.upsertJournal(
                    listOf(JournalEntry("noop-journal", day, "Caffeine", true, numericValue = value)),
                )
            }
        }.onSuccess { saveError = null }
            .onFailure { saveError = it.message ?: "Could not save caffeine." }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            NoopSpacing.screenHorizontal,
            NoopSpacing.lg,
            NoopSpacing.screenHorizontal,
            NoopSpacing.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(NoopSpacing.lg),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
                Text("TODAY'S JOURNAL", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Add context, not judgment", style = NoopType.editorialHeadline)
                Text(
                    "Your entries stay on-device and become useful when patterns repeat over time.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            loading -> item { RebuildStateCard("Loading journal", "Reading today's entries from your local store.") }
            loadError != null -> item { RebuildStateCard("Journal unavailable", loadError ?: "Unknown error") }
            else -> {
                items(defaultJournalQuestions.size) { index ->
                    val question = defaultJournalQuestions[index]
                    JournalToggle(question, answers[question] == true) { enabled ->
                        answers[question] = enabled
                        scope.launch {
                            runCatching {
                                viewModel.repo.upsertJournal(
                                    listOf(JournalEntry("noop-journal", day, question, enabled)),
                                )
                            }.onSuccess { saveError = null }
                                .onFailure { saveError = it.message ?: "Could not save journal entry." }
                        }
                    }
                }
                item {
                    NoopSurface(level = NoopSurfaceLevel.Standard, modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                            Text("CAFFEINE", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = caffeine,
                                onValueChange = { next -> caffeine = next.filter { it.isDigit() || it == '.' } },
                                label = { Text("mg today") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                saveError?.let { message ->
                    item { RebuildStateCard("Not saved yet", message) }
                }
            }
        }
    }
}

@Composable
private fun JournalToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    NoopSurface(level = NoopSurfaceLevel.Standard, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
