package com.nexus3d.diagnostics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeviceInventoryScreen(
    repository: SupabaseAccountRepository,
    onBack: () -> Unit,
    onOpenCloudAccount: () -> Unit,
    inline: Boolean = false,
    events: List<DiagnosticEvent> = emptyList(),
    onEventSelected: (DiagnosticEvent) -> Unit = {}
) {
    var devices by remember { mutableStateOf<List<RegisteredDevice>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var cloudInventoryConnected by remember { mutableStateOf(false) }
    var listExpanded by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        if (loading) return
        scope.launch {
            loading = true
            error = null
            try {
                devices = repository.fetchRegisteredDevices()
                loaded = true
                cloudInventoryConnected = true
            } catch (exception: Exception) {
                cloudInventoryConnected = false
                error = exception.message ?: "Unable to load device inventory."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            delay(15_000L)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().then(if (inline) Modifier else Modifier.fillMaxSize()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!inline) OutlinedButton(onClick = onBack) { Text("← Back") }
            Button(onClick = { refresh() }, enabled = !loading) { Text("Refresh") }
        }
        Text("Device Inventory", style = MaterialTheme.typography.titleMedium)
        Text("Registered Nexus3D Editor installations. Each device contains its own information and report records.", style = MaterialTheme.typography.bodySmall)
        InventoryCollapsePanel(
            title = "Device Inventory List",
            subtitle = "${devices.size} registered device(s)",
            expanded = listExpanded,
            onToggle = { listExpanded = !listExpanded }
        ) {
            if (loading) CircularProgressIndicator()
            error?.let {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(it, color = MaterialTheme.colorScheme.error)
                        Button(onClick = onOpenCloudAccount) { Text("Open Admin Profile") }
                    }
                }
            }
            if (loaded && devices.isEmpty() && error == null && !loading) {
                Text("No registered Editor installations found yet.")
                Text("Open Nexus3D Editor, allow cloud diagnostics registration, then refresh this inventory.", style = MaterialTheme.typography.bodySmall)
            }
            devices.forEachIndexed { index, device ->
                val deviceExpandedKey = "device_${device.id}"
                val deviceEvents = events.filter { it.deviceId == device.id }
                var registeredExpanded by remember(deviceExpandedKey) { mutableStateOf(false) }
                var nameExpanded by remember(deviceExpandedKey) { mutableStateOf(false) }
                var infoExpanded by remember(deviceExpandedKey) { mutableStateOf(false) }
                var apkExpanded by remember(deviceExpandedKey) { mutableStateOf(false) }
                var reportsExpanded by remember(deviceExpandedKey) { mutableStateOf(false) }
                val online = isDevicePresenceOnline(device.presenceState, device.lastActiveAt)
                val registered = device.status.equals("active", ignoreCase = true)
                val latestUpdate = device.versionHistory.firstOrNull()
                val previousUpdate = device.versionHistory.drop(1).firstOrNull()
                InventoryCollapsePanel(
                    title = "Registered Device [${index + 1}]",
                    subtitle = if (device.name.isBlank()) device.id else device.name,
                    expanded = registeredExpanded,
                    onToggle = { registeredExpanded = !registeredExpanded }
                ) {
                    InventoryCollapsePanel(
                        title = device.name.ifBlank { "Unnamed Device" },
                        subtitle = "Device ID: ${device.id}",
                        expanded = nameExpanded,
                        onToggle = { nameExpanded = !nameExpanded }
                    ) {
                        InventoryCollapsePanel("Device Info", "Hardware and installation identity", infoExpanded, { infoExpanded = !infoExpanded }) {
                            Text("Device ID No.: ${device.id}")
                            Text("Manufacturer / Model: ${listOf(device.manufacturer, device.model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Not reported" }}")
                            Text("Android Version: ${device.androidVersion.ifBlank { "Not reported" }}")
                            Text("CPU: ${device.cpuInfo.ifBlank { "Not reported" }}")
                            Text("GPU: ${device.gpuInfo.ifBlank { "Not reported" }}")
                            Text("RAM: ${formatHardwareBytes(device.ramBytes)}")
                            Text("ROM: ${formatHardwareBytes(device.romBytes)}")
                        }
                        InventoryCollapsePanel("Nexus3D APK INFO", "Version, status and activity", apkExpanded, { apkExpanded = !apkExpanded }) {
                            Text("User Status: ${if (online) "ONLINE" else "OFFLINE"}", color = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Last Seen: ${formatLastActive(device.lastSeenAt)}")
                            Text("Apk Name: Nexus3D")
                            Text("Nexus3D Apk Active Time: ${formatActiveTime(device.lastActiveAt)}")
                            Text("Installation Device Time: ${formatInstallationDate(device.installationDate)}")
                            Text("Apk Version: ${device.appVersion}")
                            Text("Apk Build No.: ${device.buildNumber?.toString() ?: "—"}")
                            Text("New Update Version: ${latestUpdate?.let { formatVersionEvent(it) } ?: "Not recorded"}")
                            Text("Old Version: ${formatVersionEvent(previousUpdate)}")
                            Text("Registration: ${if (registered) "ACTIVE" else device.status.uppercase()}")
                            Text("Registration Method: ${device.registrationMethod}")
                            Text("Connection: Editor APK ${if (online) "CONNECTED" else "DISCONNECTED"} • Cloud Inventory ${if (cloudInventoryConnected) "CONNECTED" else "DISCONNECTED"} • Diagnostics APK PRESENCE UNKNOWN")
                        }
                        InventoryCollapsePanel("Nexus3D User Reports", "${deviceEvents.size} report(s) from this device", reportsExpanded, { reportsExpanded = !reportsExpanded }) {
                            val reportGroups = listOf(
                                "Full Reports" to deviceEvents,
                                "Error Reports" to deviceEvents.filter { it.severity.equals("ERROR", true) || it.severity.equals("FATAL", true) },
                                "Warning Reports" to deviceEvents.filter { it.severity.equals("WARN", true) || it.severity.equals("WARNING", true) },
                                "Performance Reports" to deviceEvents.filter { it.source.contains("PERFORMANCE", true) || it.source.contains("PERF", true) },
                                "RDS LogCat Reports" to deviceEvents.filter { it.source == "RDS_LOGCAT" },
                                "RIT Trace Reports" to deviceEvents.filter { it.source == "RIT_TRACE" },
                                "Session Reports" to deviceEvents.filter { it.sessionId != null }
                            )
                            reportGroups.forEach { (label, rows) ->
                                var groupExpanded by remember(deviceExpandedKey, label) { mutableStateOf(false) }
                                InventoryCollapsePanel(label, "${rows.size} record(s)", groupExpanded, { groupExpanded = !groupExpanded }) {
                                    if (rows.isEmpty()) {
                                        Text("No ${label.lowercase()} received for this device.", style = MaterialTheme.typography.bodySmall)
                                    } else {
                                        rows.sortedByDescending { it.createdAtEpochMs }.forEach { event ->
                                            Card(Modifier.fillMaxWidth().clickable { onEventSelected(event) }) {
                                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    Text(event.title, style = MaterialTheme.typography.titleSmall)
                                                    Text("${event.source} • ${event.severity}", style = MaterialTheme.typography.labelSmall)
                                                    Text(formatDeviceEventTimestamp(event.createdAtEpochMs), style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryCollapsePanel(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (expanded) content()
        }
    }
}

private fun formatDeviceEventTimestamp(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
