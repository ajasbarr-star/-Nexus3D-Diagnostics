
package com.nexus3d.diagnostics

import android.os.Bundle
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var database: DiagnosticsDatabase
    private lateinit var cloudAccountRepository: SupabaseAccountRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
}

private enum class DiagnosticsScreen {
    HOME, EVENTS, RDS_LOGCAT, RIT_TRACE, EVENT_DETAILS, CLOUD_ACCOUNT, DEVICE_INVENTORY, ZIP_RECORDS
}

@Composable
internal fun DiagnosticsHome(database: DiagnosticsDatabase, cloudAccountRepository: SupabaseAccountRepository, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val events by database.eventDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("nexus3d_diagnostics_appearance", 0) }
    var screen by remember { mutableStateOf(DiagnosticsScreen.HOME) }
    var leftOpen by remember { mutableStateOf(false) }
    var rightOpen by remember { mutableStateOf(false) }
    var leftExpanded by remember { mutableStateOf("Device Inventory") }
    val appearanceScope = when {
        leftOpen && leftExpanded == "Device Inventory" -> "devices"
        leftOpen && leftExpanded == "Admin Profile" -> "admin"
        leftOpen && leftExpanded == "GLOBAL REPORTS" -> "reports"
        rightOpen -> "drawer"
        screen == DiagnosticsScreen.EVENTS || screen == DiagnosticsScreen.RDS_LOGCAT || screen == DiagnosticsScreen.RIT_TRACE || screen == DiagnosticsScreen.EVENT_DETAILS -> "reports"
        screen == DiagnosticsScreen.DEVICE_INVENTORY -> "devices"
        screen == DiagnosticsScreen.CLOUD_ACCOUNT -> "admin"
        else -> "home"
    }
    var appearanceRefresh by remember { mutableStateOf(0) }
    val themeName = prefs.getString("${appearanceScope}_background_theme", prefs.getString("background_theme", "Dark Gray")) ?: "Dark Gray"
    val primaryColor = parseHexColor(prefs.getString("${appearanceScope}_primary_hex", prefs.getString("primary_hex", "#80CBC4")), Color(0xFF80CBC4))
    val buttonColor = parseHexColor(prefs.getString("${appearanceScope}_button_hex", prefs.getString("button_hex", "#80CBC4")), Color(0xFF80CBC4))
    val iconColor = parseHexColor(prefs.getString("${appearanceScope}_icon_hex", prefs.getString("icon_hex", "#80CBC4")), Color(0xFF80CBC4))
    val backgroundColor = parseHexColor(prefs.getString("${appearanceScope}_background_hex", prefs.getString("background_hex", "#242629")), Color(0xFF242629))
    val surfaceColor = parseHexColor(prefs.getString("${appearanceScope}_surface_hex", prefs.getString("surface_hex", "#2D3034")), Color(0xFF2D3034))
    val textColor = parseHexColor(prefs.getString("${appearanceScope}_text_hex", prefs.getString("text_hex", "#E8EAED")), Color(0xFFE8EAED))
    val borderColor = parseHexColor(prefs.getString("${appearanceScope}_border_hex", prefs.getString("border_hex", "#60666D")), Color(0xFF60666D))
    val panelOpacity = prefs.getFloat("${appearanceScope}_panel_opacity", prefs.getFloat("panel_opacity", 1f)).coerceIn(0.25f, 1f)
    val backgroundBlur = prefs.getFloat("${appearanceScope}_blur", prefs.getFloat("blur", 0f)).coerceIn(0f, 24f)
    val backgroundBrightness = prefs.getFloat("${appearanceScope}_brightness", prefs.getFloat("brightness", 1f)).coerceIn(0.4f, 1.5f)
    val gradientEnabled = prefs.getBoolean("${appearanceScope}_gradient_enabled", prefs.getBoolean("gradient_enabled", false))
    val gradientEnd = parseHexColor(prefs.getString("${appearanceScope}_gradient_end_hex", prefs.getString("gradient_end_hex", "#101923")), Color(0xFF101923))
    val backgroundImageUri = prefs.getString("${appearanceScope}_background_image_uri", prefs.getString("background_image_uri", "")) ?: ""
        var selectedEvent by remember { mutableStateOf<DiagnosticEvent?>(null) }
    var searchText by remember { mutableStateOf("") }
    var severityFilter by remember { mutableStateOf("ALL") }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var rightExpanded by remember { mutableStateOf("Appearance & Settings") }

    val colors = darkColorScheme(
        primary = buttonColor,
        secondary = primaryColor,
        tertiary = iconColor,
        background = backgroundColor,
        surface = surfaceColor.copy(alpha = panelOpacity),
        surfaceVariant = surfaceColor.copy(alpha = 0.92f),
        onSurface = textColor,
        onBackground = textColor,
        outline = borderColor
    )
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

    MaterialTheme(colorScheme=colors) {
        Surface(Modifier.fillMaxSize(), color=Color.Transparent) {
            if (showClearConfirmation) AlertDialog(
                onDismissRequest={showClearConfirmation=false},
                title={Text("Clear all diagnostic events?")},
                text={Text("This permanently deletes the locally stored event history on this device.")},
                confirmButton={TextButton(onClick={showClearConfirmation=false; scope.launch { database.eventDao().clearAll() }}) { Text("Clear all") }},
                dismissButton={TextButton(onClick={showClearConfirmation=false}) { Text("Cancel") }}
            )
            Box(
                Modifier.fillMaxSize().background(
                    if (gradientEnabled) Brush.linearGradient(listOf(backgroundColor, gradientEnd))
                    else androidx.compose.ui.graphics.SolidColor(backgroundColor)
                )
            ) {
                val customBackgroundBitmap = remember(backgroundImageUri, appearanceRefresh) {
                    if (backgroundImageUri.isBlank()) null else runCatching {
                        context.contentResolver.openInputStream(Uri.parse(backgroundImageUri))?.use { stream ->
                            BitmapFactory.decodeStream(stream)?.asImageBitmap()
                        }
                    }.getOrNull()
                }
                if (customBackgroundBitmap != null) {
                    Image(
                        bitmap = customBackgroundBitmap,
                        contentDescription = "Custom Diagnostics background",
                        modifier = Modifier.fillMaxSize().blur(backgroundBlur.dp).alpha(backgroundBrightness.coerceIn(0f, 1f)),
                        contentScale = ContentScale.Crop
                    )
                }
                if (screen == DiagnosticsScreen.HOME) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
                            OutlinedButton(onClick={rightOpen=false; leftOpen=true}) { Text("☰ Tools") }
                            Text("Nexus3D Diagnostics", style=MaterialTheme.typography.titleLarge)
                            OutlinedButton(onClick={leftOpen=false; rightOpen=true}) { Text("Manage ⋮") }
                        }
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
                        Text("Background style: $themeName",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)
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
                        DrawerSection("Admin Profile","Permanent Owner Admin account and enrollment",leftExpanded=="Admin Profile",{leftExpanded=if(leftExpanded=="Admin Profile")"" else "Admin Profile"}) {
                            CloudAccountScreen(cloudAccountRepository,onBack={},inline=true,onSignOut=onSignOut)
                        }
                        DrawerSection("Device Inventory","Registered Nexus3D Editor installations",leftExpanded=="Device Inventory",{leftExpanded=if(leftExpanded=="Device Inventory")"" else "Device Inventory"}) {
                            DeviceInventoryScreen(
                                cloudAccountRepository,
                                onBack={},
                                onOpenCloudAccount={leftExpanded="Admin Profile"},
                                inline=true,
                                events=events,
                                onEventSelected={e->selectedEvent=e;screen=DiagnosticsScreen.EVENT_DETAILS;leftOpen=false}
                            )
                        }
                        DrawerSection("GLOBAL REPORTS","Consolidated reports across all registered devices • ${events.size} record(s)",leftExpanded=="GLOBAL REPORTS",{leftExpanded=if(leftExpanded=="GLOBAL REPORTS")"" else "GLOBAL REPORTS"}) {
                            DrawerSection("Full Reports","${events.size} record(s)",leftExpanded=="Full Reports",{leftExpanded=if(leftExpanded=="Full Reports")"" else "Full Reports"}) {
                                DrawerEventList(events){e->selectedEvent=e;screen=DiagnosticsScreen.EVENT_DETAILS;leftOpen=false}
                            }
                            DrawerSection("Error Reports","${events.count{it.severity.equals("ERROR",true)||it.severity.equals("FATAL",true)}} record(s)",leftExpanded=="Error Reports",{leftExpanded=if(leftExpanded=="Error Reports")"" else "Error Reports"}) {
                                DrawerEventList(events.filter{it.severity.equals("ERROR",true)||it.severity.equals("FATAL",true)}){e->selectedEvent=e;screen=DiagnosticsScreen.EVENT_DETAILS;leftOpen=false}
                            }
                            DrawerSection("Warning Reports","${events.count{it.severity.equals("WARN",true)||it.severity.equals("WARNING",true)}} record(s)",leftExpanded=="Warning Reports",{leftExpanded=if(leftExpanded=="Warning Reports")"" else "Warning Reports"}) {
                                DrawerEventList(events.filter{it.severity.equals("WARN",true)||it.severity.equals("WARNING",true)}){e->selectedEvent=e;screen=DiagnosticsScreen.EVENT_DETAILS;leftOpen=false}
                            }
                            DrawerSection("Performance Reports","${events.count{it.source.contains("PERFORMANCE",true)||it.source.contains("PERF",true)}} record(s)",leftExpanded=="Performance Reports",{leftExpanded=if(leftExpanded=="Performance Reports")"" else "Performance Reports"}) {
                                DrawerEventList(events.filter{it.source.contains("PERFORMANCE",true)||it.source.contains("PERF",true)}){e->selectedEvent=e;screen=DiagnosticsScreen.EVENT_DETAILS;leftOpen=false}
                            }
                            DrawerSection("RDS LogCat Reports","${events.count{it.source=="RDS_LOGCAT"}} record(s)",leftExpanded=="RDS LogCat Reports",{leftExpanded=if(leftExpanded=="RDS LogCat Reports")"" else "RDS LogCat Reports"}) {
                                DrawerEventList(events.filter{it.source=="RDS_LOGCAT"}){e->selectedEvent=e;screen=DiagnosticsScreen.EVENT_DETAILS;leftOpen=false}
                            }
                            DrawerSection("RIT Trace Reports","${events.count{it.source=="RIT_TRACE"}} record(s)",leftExpanded=="RIT Trace Reports",{leftExpanded=if(leftExpanded=="RIT Trace Reports")"" else "RIT Trace Reports"}) {
                                DrawerEventList(events.filter{it.source=="RIT_TRACE"}){e->selectedEvent=e;screen=DiagnosticsScreen.EVENT_DETAILS;leftOpen=false}
                            }
                            DrawerSection("Session Reports","${events.count{it.sessionId!=null}} record(s)",leftExpanded=="Session Reports",{leftExpanded=if(leftExpanded=="Session Reports")"" else "Session Reports"}) {
                                DrawerEventList(events.filter{it.sessionId!=null}){e->selectedEvent=e;screen=DiagnosticsScreen.EVENT_DETAILS;leftOpen=false}
                            }
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
                        DrawerSection("Appearance & Settings","Full interface customization",rightExpanded=="Appearance & Settings",{rightExpanded=if(rightExpanded=="Appearance & Settings")"" else "Appearance & Settings"}) {
                            AppearanceSettingsScreen(prefs) { scope, _, _, _, _, _, _ ->
                                appearanceRefresh = !appearanceRefresh
                                if (scope == "global" || scope == appearanceScope) {
                                    // Theme values are read from local preferences on recomposition.
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
    else events.forEach { e ->
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

private fun parseHexColor(value: String?, fallback: Color): Color = runCatching {
    val raw = value?.trim()?.removePrefix("#") ?: error("Missing color")
    val argb = when (raw.length) {
        6 -> (0xFF000000L or raw.toLong(16)).toInt()
        8 -> raw.toLong(16).toInt()
        else -> error("Invalid color")
    }
    Color(argb)
}.getOrDefault(fallback)
