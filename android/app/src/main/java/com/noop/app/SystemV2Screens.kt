package com.noop.app

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.noop.data.DeviceStatus
import com.noop.data.PairedDeviceRow
import com.noop.ingest.HealthConnectImporter
import com.noop.ui.AppViewModel
import com.noop.ui.NotifPrefs
import com.noop.ui.designsystem.NoopSpacing
import com.noop.ui.designsystem.NoopSurface
import com.noop.ui.designsystem.NoopSurfaceLevel
import com.noop.ui.designsystem.NoopType
import kotlinx.coroutines.launch

enum class RebuildThemeMode { System, Light, Dark }

private const val REBUILD_PREFS = "noop_rebuild_prefs"
private const val THEME_KEY = "theme_mode"
private const val METRIC_KEY = "metric_units"
internal fun loadThemeMode(ctx: Context): RebuildThemeMode = runCatching {
    RebuildThemeMode.valueOf(ctx.getSharedPreferences(REBUILD_PREFS, Context.MODE_PRIVATE).getString(THEME_KEY, RebuildThemeMode.System.name) ?: RebuildThemeMode.System.name)
}.getOrDefault(RebuildThemeMode.System)
internal fun saveThemeMode(ctx: Context, mode: RebuildThemeMode) = ctx.getSharedPreferences(REBUILD_PREFS, Context.MODE_PRIVATE).edit().putString(THEME_KEY, mode.name).apply()

@Composable
internal fun RebuildStateCard(title: String, body: String) {
    NoopSurface(level = NoopSurfaceLevel.Standard, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun DevicesV2Screen(viewModel: AppViewModel) {
    var devices by remember { mutableStateOf<List<PairedDeviceRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refresh) {
        loading = true; error = null
        runCatching { viewModel.pairedDevices() }.onSuccess { devices = it }.onFailure { error = it.message ?: "Devices could not be loaded." }
        loading = false
    }

    StandardPage("DEVICES", "Your connected ecosystem", "Choose the active source without losing any history.") {
        when {
            loading -> item { RebuildStateCard("Loading devices", "Reading paired sources from the local registry.") }
            error != null -> item { RebuildStateCard("Devices unavailable", error ?: "Unknown error") }
            devices.isEmpty() -> item { RebuildStateCard("No paired devices", "Pair a supported source from onboarding or the source setup flow.") }
            else -> items(devices.size) { i ->
                val d = devices[i]
                NoopSurface(level = if (d.status == DeviceStatus.active.name) NoopSurfaceLevel.Elevated else NoopSurfaceLevel.Standard, modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text(d.nickname ?: "${d.brand} ${d.model}", style = MaterialTheme.typography.bodyLarge); Text(d.sourceKind, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Text(d.status.uppercase(), style = NoopType.labelCaps, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(d.capabilities.split(',').filter(String::isNotBlank).joinToString(" · ").ifBlank { "Capabilities not reported" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                            if (d.status != DeviceStatus.active.name && d.status != DeviceStatus.archived.name) Button(onClick = { scope.launch { runCatching { viewModel.setActiveDevice(d.id) }.onFailure { error = it.message }; refresh++ } }) { Text("Make active") }
                            if (d.status != DeviceStatus.archived.name) Button(onClick = { scope.launch { runCatching { viewModel.archivePairedDevice(d.id) }.onFailure { error = it.message }; refresh++ } }) { Text("Archive") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DataSourcesV2Screen(viewModel: AppViewModel, onDevices: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<PairedDeviceRow>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var hcMessage by remember { mutableStateOf<String?>(null) }
    var hcBusy by remember { mutableStateOf(false) }
    val hcStatus = remember { HealthConnectImporter.sdkStatus(context) }

    fun runHealthConnectImport() {
        hcBusy = true; hcMessage = null
        scope.launch {
            val result = runCatching { HealthConnectImporter.import(context, viewModel.repo, 0.0) }
            result.onSuccess { hcMessage = it.message }.onFailure { hcMessage = it.message ?: "Health Connect import failed." }
            hcBusy = false
        }
    }

    val hcPermissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        HealthConnectImporter.markPermissionsAsked(context)
        if (granted.any { it in HealthConnectImporter.PERMISSIONS }) runHealthConnectImport()
        else hcMessage = "Health Connect access was not granted."
    }

    fun startHealthConnect() {
        if (hcStatus != HealthConnectClient.SDK_AVAILABLE) { hcMessage = "Health Connect is not available on this device."; return }
        scope.launch {
            val granted = runCatching { HealthConnectImporter.client(context).permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
            if (granted.any { it in HealthConnectImporter.PERMISSIONS } && !HealthConnectImporter.hasUnaskedPermissions(context)) runHealthConnectImport()
            else hcPermissionLauncher.launch(HealthConnectImporter.PERMISSIONS)
        }
    }

    LaunchedEffect(Unit) { runCatching { viewModel.pairedDevices() }.onSuccess { devices = it }.onFailure { error = it.message } }

    StandardPage("DATA", "Know where every signal comes from", "NOOP keeps sources explicit so imported, wearable and computed data are never silently blended.") {
        item {
            NoopSurface(level = NoopSurfaceLevel.Elevated, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
                    Text("HEALTH CONNECT", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (hcStatus == HealthConnectClient.SDK_AVAILABLE) "Available on this device" else "Not currently available", style = MaterialTheme.typography.bodyLarge)
                    Text("Import the Health Connect record types you choose to share. Partial permissions are supported.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(enabled = !hcBusy && hcStatus == HealthConnectClient.SDK_AVAILABLE, onClick = ::startHealthConnect) { Text(if (hcBusy) "Importing…" else "Import from Health Connect") }
                    hcMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        if (error != null) item { RebuildStateCard("Sources unavailable", error ?: "Unknown error") }
        else if (devices.isEmpty()) item { RebuildStateCard("No data sources", "Add a wearable or import source to start building history.") }
        else items(devices.size) { i ->
            val d = devices[i]
            NoopSurface(level = NoopSurfaceLevel.Standard, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
                    Text(d.nickname ?: "${d.brand} ${d.model}", style = MaterialTheme.typography.bodyLarge)
                    Text("${d.sourceKind} · ${d.status}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(d.capabilities.split(',').filter(String::isNotBlank).joinToString(" · "), style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Button(onClick = onDevices) { Text("Manage devices") } }
    }
}

@Composable
internal fun SettingsV2Screen(themeMode: RebuildThemeMode, onThemeMode: (RebuildThemeMode) -> Unit) {
    val context = LocalContext.current
    var metric by remember { mutableStateOf(context.getSharedPreferences(REBUILD_PREFS, Context.MODE_PRIVATE).getBoolean(METRIC_KEY, true)) }
    StandardPage("SETTINGS", "Make NOOP yours", "Appearance and unit preferences are stored locally on this device.") {
        item {
            NoopSurface(level = NoopSurfaceLevel.Standard, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.md)) {
                    Text("APPEARANCE", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    RebuildThemeMode.entries.forEach { mode -> Row(modifier = Modifier.fillMaxWidth().clickable { onThemeMode(mode) }, horizontalArrangement = Arrangement.SpaceBetween) { Text(mode.name, style = MaterialTheme.typography.bodyLarge); Text(if (themeMode == mode) "Selected" else "", color = MaterialTheme.colorScheme.primary) } }
                }
            }
        }
        item {
            NoopSurface(level = NoopSurfaceLevel.Standard, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("Metric units", style = MaterialTheme.typography.bodyLarge); Text("Use metric units where applicable", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Switch(checked = metric, onCheckedChange = { metric = it; context.getSharedPreferences(REBUILD_PREFS, Context.MODE_PRIVATE).edit().putBoolean(METRIC_KEY, it).apply() })
                }
            }
        }
        item { RebuildStateCard("Privacy by architecture", "NOOP remains account-free and local-first. Settings here do not create a cloud profile.") }
    }
}

@Composable
internal fun NotificationsV2Screen() {
    val context = LocalContext.current
    var master by remember { mutableStateOf(NotifPrefs.getBool(context, NotifPrefs.MASTER, false)) }
    var worn by remember { mutableStateOf(NotifPrefs.getBool(context, NotifPrefs.WORN, true)) }
    var quiet by remember { mutableStateOf(NotifPrefs.getBool(context, NotifPrefs.QUIET, false)) }
    StandardPage("NOTIFICATIONS", "Only the alerts you choose", "Wrist mirroring uses Android notification access and the same persisted preferences as the background bridge.") {
        item { ToggleCard("Wrist alerts", "Master switch for mirrored alerts", master) { master = it; NotifPrefs.setBool(context, NotifPrefs.MASTER, it) } }
        item { ToggleCard("Only when worn", "Suppress strap alerts when the band is not being worn", worn) { worn = it; NotifPrefs.setBool(context, NotifPrefs.WORN, it) } }
        item { ToggleCard("Quiet hours", "Respect the configured overnight quiet window", quiet) { quiet = it; NotifPrefs.setBool(context, NotifPrefs.QUIET, it) } }
        item { Button(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) { Text("Open notification access") } }
    }
}

@Composable
private fun ToggleCard(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    NoopSurface(level = NoopSurfaceLevel.Standard, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NoopSpacing.xxs)) { Text(title, style = MaterialTheme.typography.bodyLarge); Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun StandardPage(overline: String, headline: String, subtitle: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(NoopSpacing.screenHorizontal, NoopSpacing.lg, NoopSpacing.screenHorizontal, NoopSpacing.xxxl), verticalArrangement = Arrangement.spacedBy(NoopSpacing.lg)) {
        item { Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) { Text(overline, style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(headline, style = NoopType.editorialHeadline); Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        content()
    }
}
