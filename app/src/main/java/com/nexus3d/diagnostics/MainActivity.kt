
package com.nexus3d.diagnostics

import android.os.Bundle
import android.os.Build
import android.graphics.Rect
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var database: DiagnosticsDatabase
    private lateinit var cloudAccountRepository: SupabaseAccountRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyImmersiveFullscreen()

        cloudAccountRepository = SupabaseAccountRepository(applicationContext)

        database = Room.databaseBuilder(
            applicationContext,
            DiagnosticsDatabase::class.java,
            "nexus3d-diagnostics.db"
        ).addMigrations(DiagnosticsDatabase.MIGRATION_1_2, DiagnosticsDatabase.MIGRATION_2_3).build()

        setContent {
            DiagnosticsStartupGate(database, cloudAccountRepository)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveFullscreen()
    }

    private fun applyImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (Build.VERSION.SDK_INT >= 29) {
            window.decorView.post {
                val view = window.decorView
                val width = view.width
                val height = view.height
                if (width > 0 && height > 0) {
                    val density = resources.displayMetrics.density
                    val strip = (24f * density).toInt().coerceAtLeast(1)
                    val exclusionHeight = (200f * density).toInt().coerceAtMost(height)
                    val top = ((height - exclusionHeight) / 2).coerceAtLeast(0)
                    view.systemGestureExclusionRects = listOf(
                        Rect(0, top, strip.coerceAtMost(width), top + exclusionHeight),
                        Rect((width - strip).coerceAtLeast(0), top, width, top + exclusionHeight)
                    )
                }
            }
        }
    }
}

private enum class DiagnosticsScreen {
    HOME, EVENTS, RDS_LOGCAT, RIT_TRACE, EVENT_DETAILS, CLOUD_ACCOUNT, DEVICE_INVENTORY, ZIP_RECORDS
}

@Composable
internal fun DiagnosticsHome(
    database: DiagnosticsDatabase,
    cloudAccountRepository: SupabaseAccountRepository,
    ownerAdminStatus: OwnerAdminStatus?,
    appearance: DiagnosticsAppearance,
    onAppearanceChange: (DiagnosticsAppearance) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val events by database.eventDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(DiagnosticsScreen.HOME) }
    var selectedEvent by remember { mutableStateOf<DiagnosticEvent?>(null) }
    var searchText by remember { mutableStateOf("") }
    var severityFilter by remember { mutableStateOf("ALL") }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var leftOpen by remember { mutableStateOf(false) }
    var rightOpen by remember { mutableStateOf(false) }
    var leftExpanded by remember { mutableStateOf("Device Inventory List") }
    var rightExpanded by remember { mutableStateOf("Appearance & Settings") }

    val filteredEvents = events.filter { e ->
        (searchText.isBlank() || e.title.contains(searchText,true) || e.details.contains(searchText,true) || e.source.contains(searchText,true)) &&
        when (severityFilter) {
            "ALL" -> true
            "ERROR" -> e.severity.equals("ERROR",true) || e.severity.equals("FATAL",true)
            "WARN" -> e.severity.equals("WARN",true) || e.severity.equals("WARNING",true)
            else -> e.severity.equals(severityFilter,true)
        }
    }

    BackHandler(enabled=leftOpen || rightOpen || screen != DiagnosticsScreen.HOME) {
        when {
            rightOpen -> rightOpen=false
            leftOpen -> leftOpen=false
            screen == DiagnosticsScreen.EVENT_DETAILS -> { screen=DiagnosticsScreen.EVENTS; selectedEvent=null }
            else -> screen=DiagnosticsScreen.HOME
        }
    }

    Surface(Modifier.fillMaxSize(), color=Color.Transparent) {
            if (showClearConfirmation) AlertDialog(
                onDismissRequest={showClearConfirmation=false},
                title={Text("Clear all diagnostic events?")},
                text={Text("This permanently deletes the locally stored event history on this device.")},
                confirmButton={TextButton(onClick={showClearConfirmation=false; scope.launch { database.eventDao().clearAll() }}) { Text("Clear all") }},
                dismissButton={TextButton(onClick={showClearConfirmation=false}) { Text("Cancel") }}
            )
            Box(Modifier.fillMaxSize()) {
                if (screen == DiagnosticsScreen.HOME) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.Center, verticalAlignment=Alignment.CenterVertically) {
                            Text("Nexus3D Diagnostics", style=MaterialTheme.typography.titleLarge)
                        }
                        OwnerAdminStatusbarPanel(ownerAdminStatus, cloudAccountRepository)
                        Text("Swipe inward from either screen edge to open the drawers.", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.secondary)
                        Text("OFFLINE-FIRST DIAGNOSTIC WORKSPACE", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.secondary)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            SummaryCard("Events",events.size.toString(),Modifier.weight(1f))
                            SummaryCard("RDS LogCat",events.count{it.source=="RDS_LOGCAT"}.toString(),Modifier.weight(1f))
                            SummaryCard("RIT Trace",events.count{it.source=="RIT_TRACE"}.toString(),Modifier.weight(1f))
                        }
                        Card(Modifier.fillMaxWidth(), shape=RoundedCornerShape(18.dp)) {
                            Column(Modifier.padding(18.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("MAIN VIEW",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                                Text("Workspace ready",style=MaterialTheme.typography.headlineSmall)
                                Text("This Main Panel is reserved for upcoming Nexus3D Diagnostics features. Open either side drawer for monitoring, reports and management tools.")
                            }
                        }
                        Text("Quick status",style=MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            SummaryCard("Errors",events.count{it.severity.equals("ERROR",true)||it.severity.equals("FATAL",true)}.toString(),Modifier.weight(1f))
                            SummaryCard("Warnings",events.count{it.severity.equals("WARN",true)||it.severity.equals("WARNING",true)}.toString(),Modifier.weight(1f))
                        }
                        Text("Recent activity",style=MaterialTheme.typography.titleMedium)
                        if(events.isEmpty()) Text("No diagnostic events yet.") else events.take(5).forEach { e ->
                            EventCard(e) { selectedEvent=e; screen=DiagnosticsScreen.EVENT_DETAILS }
                        }
                        Text("Background style: ${appearance.backgroundStyle}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        when(screen) {
                            DiagnosticsScreen.CLOUD_ACCOUNT -> CloudAccountScreen(cloudAccountRepository,onBack={screen=DiagnosticsScreen.HOME},onSignOut=onSignOut)
                            DiagnosticsScreen.ZIP_RECORDS -> ZipRecordsScreen(database,onBack={screen=DiagnosticsScreen.HOME})
                            DiagnosticsScreen.DEVICE_INVENTORY -> DeviceInventoryScreen(cloudAccountRepository,onBack={screen=DiagnosticsScreen.HOME},onOpenCloudAccount={screen=DiagnosticsScreen.CLOUD_ACCOUNT})
                            DiagnosticsScreen.EVENTS -> {
                                ScreenHeader("REPORT"){screen=DiagnosticsScreen.HOME}
                                EventFilters(searchText,{searchText=it},severityFilter,{severityFilter=it})
                                Text("${filteredEvents.size} matching event(s)")
                                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    items(filteredEvents,key={it.id}) { e -> EventCard(e){selectedEvent=e;screen=DiagnosticsScreen.EVENT_DETAILS} }
                                }
                                OutlinedButton(onClick={showClearConfirmation=true}){Text("Clear All Events")}
                            }
                            DiagnosticsScreen.RDS_LOGCAT, DiagnosticsScreen.RIT_TRACE -> {
                                val logcat=screen==DiagnosticsScreen.RDS_LOGCAT
                                ScreenHeader(if(logcat)"RDS LogCat" else "RIT Trace"){screen=DiagnosticsScreen.HOME}
                                Text(if(logcat)"Runtime Diagnostic System" else "Runtime Interaction Trace",style=MaterialTheme.typography.titleMedium)
                                val rows=events.filter{it.source==if(logcat)"RDS_LOGCAT" else "RIT_TRACE"}
                                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    items(rows,key={it.id}){e->EventCard(e){selectedEvent=e;screen=DiagnosticsScreen.EVENT_DETAILS}}
                                }
                            }
                            DiagnosticsScreen.EVENT_DETAILS -> {
                                ScreenHeader("Event Details"){screen=DiagnosticsScreen.EVENTS;selectedEvent=null}
                                selectedEvent?.let { e ->
                                    Text(e.title,style=MaterialTheme.typography.titleLarge)
                                    Text("Source: ${e.source}")
                                    Text("Severity: ${e.severity}")
                                    Text("Local Record ID: ${e.id}")
                                    Text("Event ID: ${e.eventId}")
                                    Text("Device ID: ${e.deviceId}")
                                    Text("Sequence: ${e.sequence}")
                                    Text("Schema Version: ${e.schemaVersion}")
                                    Text("Timestamp: ${formatTimestamp(e.createdAtEpochMs)}")
                                    e.sessionId?.let{Text("Session ID: ${it}")}
                                    Text("Details",style=MaterialTheme.typography.titleMedium)
                                    Text(e.details)
                                } ?: Text("No event selected.")
                            }
                            DiagnosticsScreen.HOME -> Unit
                        }
                    }
                }

                if(leftOpen || rightOpen) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.62f)).clickable{leftOpen=false;rightOpen=false})

                AnimatedVisibility(leftOpen,Modifier.align(Alignment.CenterStart),enter=slideInHorizontally(initialOffsetX={-it})+fadeIn(),exit=slideOutHorizontally(targetOffsetX={-it})+fadeOut()) {
                    Column(Modifier.widthIn(max=340.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface,RoundedCornerShape(topEnd=20.dp,bottomEnd=20.dp)).border(1.dp,MaterialTheme.colorScheme.outline,RoundedCornerShape(topEnd=20.dp,bottomEnd=20.dp)).verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                            Text("NAVIGATION & REPORTS",style=MaterialTheme.typography.titleMedium)
                            TextButton(onClick={leftOpen=false}){Text("Close ✕")}
                        }
                        DrawerSection("Device Inventory List","Registered Editor installations",leftExpanded=="Device Inventory List",{leftExpanded=if(leftExpanded=="Device Inventory List")"" else "Device Inventory List"}) {
                            DeviceInventoryScreen(cloudAccountRepository,onBack={},onOpenCloudAccount={leftExpanded="";rightOpen=true;leftOpen=false},inline=true)
                        }
                        DrawerSection("All Events / Reports","${events.size} saved event(s)",leftExpanded=="All Events / Reports",{leftExpanded=if(leftExpanded=="All Events / Reports")"" else "All Events / Reports"}) {
                            DrawerEventList(events){e->selectedEvent=e;screen=DiagnosticsScreen.EVENT_DETAILS;leftOpen=false}
                            TextButton(onClick={screen=DiagnosticsScreen.EVENTS;leftOpen=false}){Text("Open full report view")}
                        }
                        DrawerSection("RDS LogCat","${events.count{it.source=="RDS_LOGCAT"}} report(s)",leftExpanded=="RDS LogCat",{leftExpanded=if(leftExpanded=="RDS LogCat")"" else "RDS LogCat"}) {
                            DrawerEventList(events.filter{it.source=="RDS_LOGCAT"}){e->selectedEvent=e;screen=DiagnosticsScreen.EVENT_DETAILS;leftOpen=false}
                        }
                        DrawerSection("RIT Trace","${events.count{it.source=="RIT_TRACE"}} report(s)",leftExpanded=="RIT Trace",{leftExpanded=if(leftExpanded=="RIT Trace")"" else "RIT Trace"}) {
                            DrawerEventList(events.filter{it.source=="RIT_TRACE"}){e->selectedEvent=e;screen=DiagnosticsScreen.EVENT_DETAILS;leftOpen=false}
                        }
                        DrawerSection("Errors","ERROR / FATAL reports",leftExpanded=="Errors",{leftExpanded=if(leftExpanded=="Errors")"" else "Errors"}) {
                            DrawerEventList(events.filter{it.severity.equals("ERROR",true)||it.severity.equals("FATAL",true)}){e->selectedEvent=e;screen=DiagnosticsScreen.EVENT_DETAILS;leftOpen=false}
                        }
                        DrawerSection("Warnings","WARN / WARNING reports",leftExpanded=="Warnings",{leftExpanded=if(leftExpanded=="Warnings")"" else "Warnings"}) {
                            DrawerEventList(events.filter{it.severity.equals("WARN",true)||it.severity.equals("WARNING",true)}){e->selectedEvent=e;screen=DiagnosticsScreen.EVENT_DETAILS;leftOpen=false}
                        }
                    }
                }

                AnimatedVisibility(rightOpen,Modifier.align(Alignment.CenterEnd),enter=slideInHorizontally(initialOffsetX={it})+fadeIn(),exit=slideOutHorizontally(targetOffsetX={it})+fadeOut()) {
                    Column(Modifier.widthIn(max=340.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface,RoundedCornerShape(topStart=20.dp,bottomStart=20.dp)).border(1.dp,MaterialTheme.colorScheme.outline,RoundedCornerShape(topStart=20.dp,bottomStart=20.dp)).verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                            Text("SYSTEM & MANAGEMENT",style=MaterialTheme.typography.titleMedium)
                            TextButton(onClick={rightOpen=false}){Text("Close ✕")}
                        }
                        DrawerSection("Cloud Account","Pair Editor installations",rightExpanded=="Cloud Account",{rightExpanded=if(rightExpanded=="Cloud Account")"" else "Cloud Account"}) {
                            CloudAccountScreen(cloudAccountRepository,onBack={},inline=true,onSignOut=onSignOut)
                        }
                        DrawerSection("ZIP Records","Import, export and inspect archives",rightExpanded=="ZIP Records",{rightExpanded=if(rightExpanded=="ZIP Records")"" else "ZIP Records"}) {
                            ZipRecordsScreen(database,onBack={},inline=true)
                        }
                        DrawerSection("Storage & Data","Read-only local storage overview",rightExpanded=="Storage & Data",{rightExpanded=if(rightExpanded=="Storage & Data")"" else "Storage & Data"}) {
                            val bytes=context.filesDir.walkTopDown().filter{it.isFile}.sumOf{it.length()}
                            val dir=java.io.File(context.filesDir,"zip_records")
                            val count=dir.listFiles()?.count{it.isFile&&it.extension.equals("zip",true)}?:0
                            Text("Local event records: ${events.size}")
                            Text("ZIP archives: ${count}")
                            Text("App files size: ${formatHardwareBytes(bytes)}")
                            Text("Read-only overview. No records are changed or deleted here.",style=MaterialTheme.typography.bodySmall)
                        }
                        DrawerSection("Appearance & Settings","Choose Diagnostics background style",rightExpanded=="Appearance & Settings",{rightExpanded=if(rightExpanded=="Appearance & Settings")"" else "Appearance & Settings"}) {
                            AppearanceSettingsPanel(appearance, onAppearanceChange)
                            Text("Settings are saved on this device and restored after restarting the APK.",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Narrow edge-only gesture zones avoid stealing ordinary viewport and scroll touches.
                if (!leftOpen && !rightOpen) {
                    Box(
                        Modifier.align(Alignment.CenterStart).fillMaxHeight().width(28.dp)
                            .pointerInput(leftOpen, rightOpen) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    if (dragAmount > 7f && !leftOpen && !rightOpen) {
                                        leftOpen = true
                                        rightOpen = false
                                    }
                                }
                            }
                    )
                    Box(
                        Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(28.dp)
                            .pointerInput(leftOpen, rightOpen) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    if (dragAmount < -7f && !leftOpen && !rightOpen) {
                                        rightOpen = true
                                        leftOpen = false
                                    }
                                }
                            }
                    )
                }
                if (leftOpen) {
                    Box(
                        Modifier.align(Alignment.CenterStart).fillMaxHeight().width(28.dp)
                            .pointerInput(leftOpen) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    if (dragAmount < -7f) leftOpen = false
                                }
                            }
                    )
                }
                if (rightOpen) {
                    Box(
                        Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(28.dp)
                            .pointerInput(rightOpen) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    if (dragAmount > 7f) rightOpen = false
                                }
                            }
                    )
                }
            }
        }
}

@Composable
private fun DrawerSection(title:String,subtitle:String,expanded:Boolean,onToggle:()->Unit,content:@Composable ()->Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick=onToggle),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(title,style=MaterialTheme.typography.titleSmall)
                    Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)
                }
                Text(if(expanded)"−" else "+",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)
            }
            if(expanded) content()
        }
    }
}

@Composable
private fun DrawerEventList(events:List<DiagnosticEvent>,onSelect:(DiagnosticEvent)->Unit) {
    if(events.isEmpty()) Text("No matching reports received yet.",style=MaterialTheme.typography.bodySmall)
    else events.take(10).forEach { e ->
        Card(Modifier.fillMaxWidth().clickable{onSelect(e)}) {
            Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(e.title,style=MaterialTheme.typography.titleSmall)
                Text("${e.source} • ${e.severity}",style=MaterialTheme.typography.labelSmall)
                Text(formatTimestamp(e.createdAtEpochMs),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)
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
