package com.nexus3d.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun DeviceInventoryScreen(
    repository: SupabaseAccountRepository,
    onBack: () -> Unit,
    onOpenCloudAccount: () -> Unit
) {
    var devices by remember { mutableStateOf<List<RegisteredDevice>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        if (loading) return
        scope.launch {
            loading = true
            error = null
            try {
                devices = repository.fetchRegisteredDevices()
                loaded = true
            } catch (exception: Exception) {
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
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onBack) { Text("← Back") }
            Button(onClick = { refresh() }, enabled = !loading) { Text("Refresh") }
        }
        Text("Device Inventory", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Registered Nexus3D Editor installations in your Diagnostics workspace.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text("Registered devices: ${devices.size}", style = MaterialTheme.typography.titleMedium)

        if (loading) {
            CircularProgressIndicator()
        }
        error?.let {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onOpenCloudAccount) { Text("Open Cloud Account") }
                }
            }
        }
        if (loaded && devices.isEmpty() && error == null && !loading) {
            Text("No registered Editor installations found yet.")
            Text(
                "Open Nexus3D Editor, allow cloud diagnostics registration, then refresh this inventory.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(devices, key = { it.id }) { device ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(device.name, style = MaterialTheme.typography.titleMedium)
                        Text("Device ID: ${device.id}", style = MaterialTheme.typography.bodySmall)
                        Text("Manufacturer / Model: ${listOf(device.manufacturer, device.model).filter { value -> value.isNotBlank() }.joinToString(" ").ifBlank { "Not reported" }}")
                        Text("Android: ${device.androidVersion.ifBlank { "Not reported" }}")
                        Text("Nexus3D: ${device.appVersion} • Build ${device.buildNumber?.toString() ?: "—"}")
                        Text("Version Code: ${device.versionCode?.toString() ?: "—"}")
                        val online = isDevicePresenceOnline(device.presenceState, device.lastActiveAt)
                        Text(
                            if (online) "Presence: ONLINE" else "Presence: OFFLINE",
                            color = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text("Registration: ${device.registrationMethod}")
                        Text("Status: ${device.status}")
                        Text("Last Seen: ${formatLastActive(device.lastActiveAt)}")
                    }
                }
            }
        }
    }
}

