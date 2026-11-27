package com.nexus3d.diagnostics

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private data class ZipRecordFile(val file: File, val modified: Long)

@Composable
fun ZipRecordsScreen(database: DiagnosticsDatabase, onBack: () -> Unit, inline: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<ZipRecordFile>>(emptyList()) }
    var status by remember { mutableStateOf("ZIP archives are stored locally in this Diagnostics installation.") }
    var inspectFile by remember { mutableStateOf<File?>(null) }

    fun refresh() {
        val directory = File(context.filesDir, "zip_records")
        directory.mkdirs()
        records = directory.listFiles()?.filter { it.isFile && it.extension.equals("zip", true) }
            ?.map { ZipRecordFile(it, it.lastModified()) }?.sortedByDescending { it.modified }.orEmpty()
    }

    LaunchedEffect(Unit) { refresh() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            status = runCatching {
                withContext(Dispatchers.IO) { importArchive(context, database, uri) }
            }.fold({ "Imported archive: $it" }, { "Import failed: ${it.message ?: "Invalid ZIP archive"}" })
            refresh()
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            status = runCatching {
                withContext(Dispatchers.IO) { exportArchive(context, database, uri) }
            }.fold({ "Export complete: $it" }, { "Export failed: ${it.message ?: "Unable to create ZIP"}" })
            refresh()
        }
    }

    Column(Modifier.fillMaxWidth().then(if (inline) Modifier else Modifier.fillMaxSize()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!inline) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Overview") }
            Text("ARCHIVE MANAGEMENT", style = MaterialTheme.typography.titleLarge)
        }
        Text("A dedicated records workspace for diagnostic archives. This interface is independent from the Nexus3D Editor design.", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArchiveAction("Import ZIP", "Add an existing report archive", Icons.Outlined.UploadFile) {
                importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
            }
            ArchiveAction("Export ZIP", "Package saved diagnostic events", Icons.Outlined.Archive) {
                exportLauncher.launch("Nexus3D_Diagnostics_Report_${System.currentTimeMillis()}.zip")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArchiveAction("Inspect", "Read archive manifest", Icons.Outlined.Insights) {
                status = if (records.isEmpty()) "No ZIP records are available to inspect." else "Select an archive below to inspect its manifest."
            }
            ArchiveAction("Backup", "Refresh local archive index", Icons.Outlined.Backup) {
                refresh()
                status = "Local ZIP archive index refreshed. No records were deleted."
            }
        }
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Text("LOCAL ZIP RECORDS · ${records.size}", style = MaterialTheme.typography.titleMedium)
        if (records.isEmpty()) {
            Text("No ZIP archives stored yet. Import an existing report or export the current diagnostic events.")
        } else {
            LazyColumn(if (inline) Modifier.heightIn(max = 360.dp) else Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(records, key = { it.file.absolutePath }) { record ->
                    Card(Modifier.fillMaxWidth().clickable { inspectFile = record.file }) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(record.file.name, style = MaterialTheme.typography.titleSmall)
                            Text("${record.file.length()} bytes · ${formatArchiveDate(record.modified)}", style = MaterialTheme.typography.bodySmall)
                            Text("Tap to inspect archive contents", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    inspectFile?.let { file ->
        AlertDialog(
            onDismissRequest = { inspectFile = null },
            title = { Text("Archive inspection") },
            text = { Text(inspectArchive(file)) },
            confirmButton = { TextButton(onClick = { inspectFile = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun ArchiveAction(label: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(0.5f).clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(description, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private suspend fun exportArchive(context: Context, database: DiagnosticsDatabase, destination: Uri): String {
    val events = database.eventDao().observeAll()
    val snapshot = events.first()
    val json = JSONArray()
    snapshot.forEach { event ->
        json.put(JSONObject().apply {
            put("eventId", event.eventId); put("deviceId", event.deviceId)
            put("source", event.source); put("severity", event.severity)
            put("title", event.title); put("details", event.details)
            put("sessionId", event.sessionId ?: JSONObject.NULL)
            put("timestampEpochMs", event.createdAtEpochMs)
            put("sequence", event.sequence); put("schemaVersion", event.schemaVersion)
        })
    }
    val report = buildString {
        appendLine("Nexus3D Diagnostics — Full Report")
        appendLine("Exported: ${Date()}")
        appendLine("Event count: ${snapshot.size}")
        snapshot.forEach { e ->
            appendLine(); appendLine("[${e.severity}] ${e.title}")
            appendLine("Tool: ${e.source} | Device ID: ${e.deviceId}")
            appendLine("Timestamp: ${e.createdAtEpochMs} | Session: ${e.sessionId ?: "not reported"}")
            appendLine(e.details)
        }
    }
    context.contentResolver.openOutputStream(destination, "w")?.use { stream ->
        ZipOutputStream(stream).use { zip ->
            zip.putNextEntry(ZipEntry("events.json")); zip.write(json.toString(2).toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("Full_Report.txt")); zip.write(report.toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(JSONObject().put("format", "nexus3d-diagnostics-zip-v1").put("createdAt", System.currentTimeMillis()).put("eventCount", snapshot.size).toString(2).toByteArray())
            zip.closeEntry()
        }
    } ?: error("Unable to open destination")
    val copy = File(context.filesDir, "zip_records/Nexus3D_Diagnostics_${System.currentTimeMillis()}.zip")
    copy.parentFile?.mkdirs()
    context.contentResolver.openInputStream(destination)?.use { input -> copy.outputStream().use(input::copyTo) }
    return "${snapshot.size} diagnostic events"
}

private const val MAX_IMPORT_ARCHIVE_BYTES = 50L * 1024L * 1024L
private const val MAX_IMPORT_TOTAL_UNCOMPRESSED_BYTES = 25L * 1024L * 1024L
private const val MAX_IMPORT_ENTRY_BYTES = 16L * 1024L * 1024L
private const val MAX_IMPORT_MANIFEST_BYTES = 1L * 1024L * 1024L

private fun readEntryBounded(zip: ZipInputStream, maxBytes: Long): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0L
    while (true) {
        val read = zip.read(buffer)
        if (read < 0) break
        total += read
        require(total <= maxBytes) { "ZIP entry exceeds the import size limit" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun copyUriBounded(context: Context, source: Uri, target: File, maxBytes: Long) {
    val input = context.contentResolver.openInputStream(source) ?: error("Unable to read selected ZIP")
    try {
        input.use { sourceStream ->
            target.outputStream().buffered().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val read = sourceStream.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maxBytes) { "ZIP archive exceeds the compressed file size limit" }
                    output.write(buffer, 0, read)
                }
            }
        }
    } catch (error: Exception) {
        target.delete()
        throw error
    }
}

private suspend fun importArchive(context: Context, database: DiagnosticsDatabase, source: Uri): String {
    val directory = File(context.filesDir, "zip_records").apply { mkdirs() }
    val target = File(directory, "Imported_${System.currentTimeMillis()}.zip")
    copyUriBounded(context, source, target, MAX_IMPORT_ARCHIVE_BYTES)

    var sawManifest = false
    var sawEvents = false
    var totalUncompressedBytes = 0L
    val seenEntries = mutableSetOf<String>()
    val stagedEvents = mutableListOf<DiagnosticEvent>()
    val seenEventIds = mutableSetOf<String>()
    val dao = database.eventDao()

    try {
        ZipInputStream(target.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            var count = 0
            while (entry != null) {
                count++
                require(count <= 500) { "Archive contains too many entries" }
                val name = entry.name
                require(
                    !entry.isDirectory &&
                    !name.startsWith("/") &&
                    !name.startsWith("\\\\") &&
                    !name.contains('\\\\') &&
                    !Regex("^[A-Za-z]:").containsMatchIn(name) &&
                    name.split('/').none { it == ".." || it.isBlank() }
                ) { "Unsafe archive entry" }
                require(seenEntries.add(name)) { "Archive contains duplicate entry names" }

                val entryLimit = when (name) {
                    "manifest.json" -> MAX_IMPORT_MANIFEST_BYTES
                    "events.json" -> MAX_IMPORT_ENTRY_BYTES
                    "Full_Report.txt" -> 8L * 1024L * 1024L
                    else -> error("Unexpected ZIP entry: $name")
                }
                val bytes = readEntryBounded(zip, entryLimit)
                totalUncompressedBytes += bytes.size
                require(totalUncompressedBytes <= MAX_IMPORT_TOTAL_UNCOMPRESSED_BYTES) {
                    "Archive exceeds the total uncompressed size limit"
                }

                when (name) {
                    "manifest.json" -> {
                        val manifest = JSONObject(bytes.toString(Charsets.UTF_8))
                        require(manifest.optString("format") == "nexus3d-diagnostics-zip-v1") {
                            "Unsupported ZIP format"
                        }
                        sawManifest = true
                    }
                    "events.json" -> {
                        val array = JSONArray(bytes.toString(Charsets.UTF_8))
                        require(array.length() <= 10000) { "Event count exceeds import limit" }
                        val importedAt = System.currentTimeMillis()
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            val eventId = item.optString("eventId").trim()
                            require(eventId.isNotBlank() && eventId.length <= 128) { "Invalid imported event ID" }
                            if (!seenEventIds.add(eventId) || dao.countByEventId(eventId) > 0) continue

                            val sourceName = item.optString("source", "IMPORTED").trim().ifBlank { "IMPORTED" }
                            val severity = item.optString("severity", "INFO").uppercase(Locale.ROOT)
                            require(severity in setOf("VERBOSE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL")) {
                                "Invalid imported event severity"
                            }
                            val title = item.optString("title", "Imported diagnostic event")
                            val details = item.optString("details", "")
                            val deviceId = item.optString("deviceId", "imported-unknown")
                            val sessionId = item.optString("sessionId").takeIf { it.isNotBlank() && it != "null" }
                            require(sourceName.length <= 80 && title.length <= 240 && details.length <= 24000) {
                                "Imported event field exceeds the allowed length"
                            }
                            require(deviceId.isNotBlank() && deviceId.length <= 128) { "Invalid imported device ID" }
                            require(sessionId == null || sessionId.length <= 128) { "Invalid imported session ID" }

                            stagedEvents += DiagnosticEvent(
                                source = sourceName,
                                severity = severity,
                                title = title,
                                details = "[IMPORTED ARCHIVE: ${target.name}; importedAtEpochMs=$importedAt]\\n$details",
                                sessionId = sessionId,
                                createdAtEpochMs = item.optLong("timestampEpochMs", importedAt).takeIf { it > 0 } ?: importedAt,
                                eventId = eventId,
                                deviceId = deviceId,
                                sequence = item.optLong("sequence", 0).coerceAtLeast(0),
                                schemaVersion = item.optInt("schemaVersion", 1).coerceAtLeast(1)
                            )
                        }
                        sawEvents = true
                    }
                    "Full_Report.txt" -> Unit
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        require(sawManifest && sawEvents) { "ZIP must contain a valid manifest.json and events.json" }
        if (stagedEvents.isNotEmpty()) dao.insertAll(stagedEvents)
        return "${stagedEvents.size} new event(s) added; imported provenance marked; original archive retained"
    } catch (error: Exception) {
        // Keep the copied source archive for inspection; no live rows are inserted until validation completes.
        throw error
    }
}

private fun inspectArchive(file: File): String = runCatching {
    val names = mutableListOf<String>()
    ZipInputStream(file.inputStream().buffered()).use { zip ->
        var entry = zip.nextEntry
        while (entry != null && names.size < 500) {
            names += entry.name
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    "File: ${file.name}\nSize: ${file.length()} bytes\n\n" + names.joinToString("\n").ifBlank { "Empty archive" }
}.getOrElse { "Unable to inspect archive: ${it.message}" }

private fun formatArchiveDate(epoch: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epoch))
