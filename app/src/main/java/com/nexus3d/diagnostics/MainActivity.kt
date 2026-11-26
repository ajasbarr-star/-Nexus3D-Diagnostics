
package com.nexus3d.diagnostics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var database: DiagnosticsDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = Room.databaseBuilder(
            applicationContext,
            DiagnosticsDatabase::class.java,
            "nexus3d-diagnostics.db"
        ).build()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DiagnosticsHome(database)
                }
            }
        }
    }
}

private enum class DiagnosticsScreen {
    HOME, EVENTS, RDS_LOGCAT, RIT_TRACE, EVENT_DETAILS
}

@Composable
private fun DiagnosticsHome(database: DiagnosticsDatabase) {
    val events by database.eventDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf(DiagnosticsScreen.HOME) }
    var selectedEvent by remember { mutableStateOf<DiagnosticEvent?>(null) }
    var searchText by remember { mutableStateOf("") }
    var severityFilter by remember { mutableStateOf("ALL") }
    var showClearConfirmation by remember { mutableStateOf(false) }

    BackHandler(enabled = screen != DiagnosticsScreen.HOME) {
        screen = if (screen == DiagnosticsScreen.EVENT_DETAILS) {
            DiagnosticsScreen.EVENTS
        } else {
            DiagnosticsScreen.HOME
        }
        selectedEvent = null
    }

    val visibleEvents = events.filter { event ->
        val matchesSearch = searchText.isBlank() ||
            event.title.contains(searchText, ignoreCase = true) ||
            event.details.contains(searchText, ignoreCase = true) ||
            event.source.contains(searchText, ignoreCase = true)

        val matchesSeverity = severityFilter == "ALL" ||
            event.severity.equals(severityFilter, ignoreCase = true)

        matchesSearch && matchesSeverity
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear all diagnostic events?") },
            text = {
                Text(
                    "This permanently deletes the locally stored event history on this device."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirmation = false
                    scope.launch {
                        database.eventDao().clearAll()
                    }
                }) {
                    Text("Clear all")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showClearConfirmation = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (screen) {
            DiagnosticsScreen.HOME -> {
                Text(
                    "Nexus3D Diagnostics",
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    "Offline-first diagnostic center",
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryCard(
                        "Events",
                        events.size.toString(),
                        Modifier
                            .weight(1f)
                            .clickable {
                                screen = DiagnosticsScreen.EVENTS
                            }
                    )

                    SummaryCard(
                        "RDS LogCat",
                        events.count { it.source == "RDS_LOGCAT" }.toString(),
                        Modifier
                            .weight(1f)
                            .clickable {
                                screen = DiagnosticsScreen.RDS_LOGCAT
                            }
                    )

                    SummaryCard(
                        "RIT Trace",
                        events.count { it.source == "RIT_TRACE" }.toString(),
                        Modifier
                            .weight(1f)
                            .clickable {
                                screen = DiagnosticsScreen.RIT_TRACE
                            }
                    )
                }

                Text(
                    "Diagnostic tools",
                    style = MaterialTheme.typography.titleLarge
                )

                ToolCard(
                    "All Events",
                    "Browse saved diagnostic events"
                ) {
                    screen = DiagnosticsScreen.EVENTS
                }

                ToolCard(
                    "RDS LogCat",
                    "Runtime diagnostic log reports"
                ) {
                    screen = DiagnosticsScreen.RDS_LOGCAT
                }

                ToolCard(
                    "RIT Trace",
                    "Runtime interaction and trace reports"
                ) {
                    screen = DiagnosticsScreen.RIT_TRACE
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showClearConfirmation = true
                        }
                    ) {
                        Text("Clear / Reset")
                    }
                }

                Text(
                    "Recent activity",
                    style = MaterialTheme.typography.titleLarge
                )

                if (events.isEmpty()) {
                    Text("No diagnostic events yet.")
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(events.take(10), key = { it.id }) { event ->
                            EventCard(event) {
                                selectedEvent = event
                                screen = DiagnosticsScreen.EVENT_DETAILS
                            }
                        }
                    }
                }

                Text(
                    "Cloud sync: not configured • No data is uploaded",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            DiagnosticsScreen.EVENTS -> {
                ScreenHeader("All Events") {
                    screen = DiagnosticsScreen.HOME
                }

                EventFilters(
                    searchText,
                    { searchText = it },
                    severityFilter,
                    { severityFilter = it }
                )

                Text("${visibleEvents.size} matching event(s)")

                if (visibleEvents.isEmpty()) {
                    Text("No events match the current search and filters.")
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visibleEvents, key = { it.id }) { event ->
                            EventCard(event) {
                                selectedEvent = event
                                screen = DiagnosticsScreen.EVENT_DETAILS
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        showClearConfirmation = true
                    }
                ) {
                    Text("Clear All Events")
                }
            }

            DiagnosticsScreen.RDS_LOGCAT -> {
                ScreenHeader("RDS LogCat") {
                    screen = DiagnosticsScreen.HOME
                }

                Text(
                    "Runtime Diagnostic System",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "This screen shows events tagged RDS_LOGCAT that have been received and saved by this Diagnostics app."
                )

                Text(
                    "Editor LogCat capture is not connected yet. This screen does not generate or capture Editor logs by itself."
                )

                val rdsEvents = events.filter {
                    it.source == "RDS_LOGCAT"
                }

                if (rdsEvents.isEmpty()) {
                    Text("No RDS LogCat reports received yet.")
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(rdsEvents, key = { it.id }) { event ->
                            EventCard(event) {
                                selectedEvent = event
                                screen = DiagnosticsScreen.EVENT_DETAILS
                            }
                        }
                    }
                }
            }

            DiagnosticsScreen.RIT_TRACE -> {
                ScreenHeader("RIT Trace") {
                    screen = DiagnosticsScreen.HOME
                }

                Text(
                    "Runtime Interaction Trace",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "This screen shows events tagged RIT_TRACE that have been received and saved by this Diagnostics app."
                )

                Text(
                    "Editor trace capture is not connected yet. This screen does not generate traces by itself."
                )

                val ritEvents = events.filter {
                    it.source == "RIT_TRACE"
                }

                if (ritEvents.isEmpty()) {
                    Text("No RIT Trace reports received yet.")
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ritEvents, key = { it.id }) { event ->
                            EventCard(event) {
                                selectedEvent = event
                                screen = DiagnosticsScreen.EVENT_DETAILS
                            }
                        }
                    }
                }
            }

            DiagnosticsScreen.EVENT_DETAILS -> {
                ScreenHeader("Event Details") {
                    screen = DiagnosticsScreen.EVENTS
                    selectedEvent = null
                }

                selectedEvent?.let { event ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            event.title,
                            style = MaterialTheme.typography.titleLarge
                        )

                        Text("Source: ${event.source}")
                        Text("Severity: ${event.severity}")
                        Text("Event ID: ${event.id}")
                        Text("Timestamp: ${formatTimestamp(event.createdAtEpochMs)}")

                        event.sessionId?.let {
                            Text("Session ID: $it")
                        }

                        Text(
                            "Details",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(event.details)
                    }
                } ?: Text("No event selected.")
            }
        }
    }
}

@Composable
private fun ScreenHeader(
    title: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("← Back")
        }

        Text(
            title,
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

@Composable
private fun EventFilters(
    searchText: String,
    onSearchChange: (String) -> Unit,
    severityFilter: String,
    onSeverityChange: (String) -> Unit
) {
    OutlinedTextField(
        value = searchText,
        onValueChange = onSearchChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Search events") },
        singleLine = true
    )

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("ALL", "INFO", "WARN", "ERROR").forEach { severity ->
            FilterChip(
                selected = severityFilter == severity,
                onClick = {
                    onSeverityChange(severity)
                },
                label = {
                    Text(severity)
                }
            )
        }
    }

    TextButton(
        onClick = {
            onSearchChange("")
            onSeverityChange("ALL")
        }
    ) {
        Text("Reset filters")
    }
}

@Composable
private fun ToolCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                description,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun EventCard(
    event: DiagnosticEvent,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                event.title,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                "${event.source} • ${event.severity}",
                style = MaterialTheme.typography.labelMedium
            )

            Text(
                event.details,
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                formatTimestamp(event.createdAtEpochMs),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                label,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun formatTimestamp(epochMs: Long): String {
    return SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss",
        Locale.getDefault()
    ).format(Date(epochMs))
}
