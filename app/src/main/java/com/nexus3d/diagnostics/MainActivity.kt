package com.nexus3d.diagnostics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
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

        lifecycleScope.launch {
            if (database.eventDao().count() == 0) {
                database.eventDao().insert(
                    DiagnosticEvent(
                        source = "SYSTEM",
                        severity = "INFO",
                        title = "Diagnostics is ready",
                        details = "Local offline storage initialized. Waiting for diagnostic events."
                    )
                )
            }
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DiagnosticsHome(database)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsHome(database: DiagnosticsDatabase) {
    val events by database.eventDao().observeAll().collectAsState(initial = emptyList())
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Nexus3D Diagnostics", style = MaterialTheme.typography.headlineMedium)
        Text("Offline-first diagnostic center", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard("Events", events.size.toString(), Modifier.weight(1f))
            SummaryCard("RDS LogCat", events.count { it.source == "RDS_LOGCAT" }.toString(), Modifier.weight(1f))
            SummaryCard("RIT Trace", events.count { it.source == "RIT_TRACE" }.toString(), Modifier.weight(1f))
        }
        Text("Recent activity", style = MaterialTheme.typography.titleLarge)
        if (events.isEmpty()) {
            Text("No diagnostic events yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(events, key = { it.id }) { event ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(event.title, style = MaterialTheme.typography.titleMedium)
                            Text("${event.source} • ${event.severity}", style = MaterialTheme.typography.labelMedium)
                            Text(event.details, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Cloud sync: not configured • No data is uploaded", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
