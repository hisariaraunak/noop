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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
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
    onWorkouts: () -> Unit,
    onDevices: () -> Unit,
    onDataSources: () -> Unit,
    onNotifications: () -> Unit,
    onSettings: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NoopSpacing.screenHorizontal, NoopSpacing.lg, NoopSpacing.screenHorizontal, NoopSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(NoopSpacing.lg),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
                Text("YOU", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Your NOOP", style = NoopType.editorialHeadline)
                Text("Activity, devices, data and preferences — all in one place.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            NoopSurface(level = NoopSurfaceLevel.Glass, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
                    Text("PRIVATE BY DEFAULT", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Your health data stays yours", style = MaterialTheme.typography.bodyLarge)
                    Text("Stored locally and connected directly to your devices.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                Text("ACTIVITY", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                YouRow("Workouts", "Recent exercise and strain", Icons.Filled.FitnessCenter, onWorkouts)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                Text("DEVICE & DATA", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                YouRow("Devices", "Manage paired sources", Icons.Filled.Sensors, onDevices)
                YouRow("Data sources", "Health Connect and imports", Icons.Filled.Storage, onDataSources)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                Text("PREFERENCES", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                YouRow("Notifications", "Choose what NOOP can surface", Icons.Filled.Notifications, onNotifications)
                YouRow("Settings", "Units, appearance and privacy", Icons.Filled.Settings, onSettings)
            }
        }
    }
}

@Composable
private fun YouRow(title: String, body: String, icon: ImageVector, onClick: () -> Unit) {
    NoopSurface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), level = NoopSurfaceLevel.Standard) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NoopSpacing.md)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NoopSpacing.xxs)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
