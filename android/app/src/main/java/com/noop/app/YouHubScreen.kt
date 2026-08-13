package com.noop.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.noop.ui.designsystem.NoopSpacing
import com.noop.ui.designsystem.NoopSurface
import com.noop.ui.designsystem.NoopSurfaceLevel
import com.noop.ui.designsystem.NoopType

@Composable
internal fun YouHubScreen(
    onDevices: () -> Unit,
    onDataSources: () -> Unit,
    onNotifications: () -> Unit,
    onSettings: () -> Unit,
    onMoreTools: () -> Unit,
) {
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
                Text("YOU", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Your NOOP", style = NoopType.editorialHeadline)
                Text(
                    "Your hardware, data and preferences — kept on your device.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            NoopSurface(level = NoopSurfaceLevel.Glass, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
                    Text("PRIVATE BY DEFAULT", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Your health data stays yours", style = NoopType.editorialHeadline)
                    Text(
                        "NOOP is designed around local storage and direct device connections rather than an account-first cloud model.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                Text("DEVICE & DATA", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                YouRow("Devices", "Pair and manage sensors", Icons.Filled.Sensors, onDevices)
                YouRow("Data sources", "Imports, Health Connect and provenance", Icons.Filled.Storage, onDataSources)
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                Text("PREFERENCES", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                YouRow("Notifications", "Choose what NOOP can surface", Icons.Filled.Notifications, onNotifications)
                YouRow("Settings", "Profile, units, appearance and backup", Icons.Filled.Settings, onSettings)
            }
        }

        item {
            NoopSurface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onMoreTools),
                level = NoopSurfaceLevel.Standard,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NoopSpacing.md),
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NoopSpacing.xxs)) {
                        Text("Advanced tools", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Legacy and specialist screens while migration continues",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun YouRow(title: String, body: String, icon: ImageVector, onClick: () -> Unit) {
    NoopSurface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        level = NoopSurfaceLevel.Standard,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NoopSpacing.md),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NoopSpacing.xxs)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
