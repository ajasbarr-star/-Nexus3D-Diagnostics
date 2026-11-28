package com.nexus3d.diagnostics

import android.content.SharedPreferences
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private data class AppearanceDraft(
    val theme: String,
    val primary: String,
    val background: String,
    val surface: String,
    val text: String,
    val border: String,
    val button: String,
    val icon: String,
    val gradient: Boolean,
    val gradientEnd: String,
    val opacity: Float,
    val brightness: Float,
    val blur: Float,
    val imageUri: String,
    val scope: String
)

private fun loadAppearanceDraft(prefs: SharedPreferences, scope: String): AppearanceDraft {
    fun value(key: String, fallback: String) = prefs.getString("${scope}_$key", null)
        ?: prefs.getString(key, fallback) ?: fallback
    return AppearanceDraft(
        theme = value("background_theme", "Dark Gray"),
        primary = value("primary_hex", "#80CBC4"),
        background = value("background_hex", "#242629"),
        surface = value("surface_hex", "#2D3034"),
        text = value("text_hex", "#E8EAED"),
        border = value("border_hex", "#60666D"),
        button = value("button_hex", "#80CBC4"),
        icon = value("icon_hex", "#80CBC4"),
        gradient = prefs.getBoolean("${scope}_gradient_enabled", prefs.getBoolean("gradient_enabled", false)),
        gradientEnd = value("gradient_end_hex", "#101923"),
        opacity = prefs.getFloat("${scope}_panel_opacity", prefs.getFloat("panel_opacity", 1f)),
        brightness = prefs.getFloat("${scope}_brightness", prefs.getFloat("brightness", 1f)),
        blur = prefs.getFloat("${scope}_blur", prefs.getFloat("blur", 0f)),
        imageUri = value("background_image_uri", ""),
        scope = scope
    )
}

private fun parseAppearanceColor(value: String, fallback: Color): Color = runCatching {
    val raw = value.trim().removePrefix("#")
    val argb = when (raw.length) {
        6 -> (0xFF000000L or raw.toLong(16)).toInt()
        8 -> raw.toLong(16).toInt()
        else -> error("Invalid color")
    }
    Color(argb)
}.getOrDefault(fallback)

@Composable
fun AppearanceSettingsScreen(
    prefs: SharedPreferences,
    onApplied: (String, String, Color, Color, Color, Color, Color) -> Unit
) {
    val context = LocalContext.current
    var scope by remember { mutableStateOf("global") }
    var draft by remember { mutableStateOf(loadAppearanceDraft(prefs, scope)) }
    var primary by remember { mutableStateOf(draft.primary) }
    var background by remember { mutableStateOf(draft.background) }
    var surface by remember { mutableStateOf(draft.surface) }
    var textColor by remember { mutableStateOf(draft.text) }
    var border by remember { mutableStateOf(draft.border) }
    var button by remember { mutableStateOf(draft.button) }
    var icon by remember { mutableStateOf(draft.icon) }
    var gradientEnd by remember { mutableStateOf(draft.gradientEnd) }
    var gradient by remember { mutableStateOf(draft.gradient) }
    var opacity by remember { mutableFloatStateOf(draft.opacity) }
    var brightness by remember { mutableFloatStateOf(draft.brightness) }
    var blur by remember { mutableFloatStateOf(draft.blur) }
    var theme by remember { mutableStateOf(draft.theme) }
    var imageUri by remember { mutableStateOf(draft.imageUri) }
    var message by remember { mutableStateOf("Changes are a preview until you tap Apply.") }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            runCatching { prefs.edit().putString("pending_background_image_uri", uri.toString()).apply() }
            imageUri = uri.toString()
            message = "Background image selected. Tap Apply to save it."
        }
    }

    fun restoreDraft(nextScope: String) {
        val next = loadAppearanceDraft(prefs, nextScope)
        draft = next
        scope = nextScope
        theme = next.theme
        primary = next.primary
        background = next.background
        surface = next.surface
        textColor = next.text
        border = next.border
        button = next.button
        icon = next.icon
        gradient = next.gradient
        gradientEnd = next.gradientEnd
        opacity = next.opacity
        brightness = next.brightness
        blur = next.blur
        imageUri = next.imageUri
    }

    fun resetDraft() {
        theme = "Dark Gray"
        primary = "#80CBC4"
        background = "#242629"
        surface = "#2D3034"
        textColor = "#E8EAED"
        border = "#60666D"
        button = "#80CBC4"
        icon = "#80CBC4"
        gradient = false
        gradientEnd = "#101923"
        opacity = 1f
        brightness = 1f
        blur = 0f
        imageUri = ""
        message = "Default appearance restored in preview. Tap Apply to save."
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("FULL CUSTOMIZATION", style = MaterialTheme.typography.titleMedium)
        Text("Choose whether changes apply across the app or only to one screen.")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("global" to "All Screens", "login" to "Login", "home" to "Home", "drawer" to "Drawers", "reports" to "Reports", "devices" to "Devices", "admin" to "Admin").forEach { (key, label) ->
                FilterChip(selected = scope == key, onClick = { restoreDraft(key) }, label = { Text(label) })
            }
        }
        Text("THEME PRESETS", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Dark Gray", "Graphite", "Midnight", "Deep Navy").forEach { preset ->
                FilterChip(selected = theme == preset, onClick = {
                    theme = preset
                    when (preset) {
                        "Graphite" -> { primary="#B8C4D0"; background="#202326"; surface="#292D31"; textColor="#F0F1F2"; border="#596168"; button="#9FAAB5"; icon="#B8C4D0" }
                        "Midnight" -> { primary="#8AB4F8"; background="#080D16"; surface="#101827"; textColor="#E7EDF7"; border="#42516A"; button="#9AAFCB"; icon="#8AB4F8" }
                        "Deep Navy" -> { primary="#70D6E8"; background="#101923"; surface="#172635"; textColor="#E5F1F5"; border="#496274"; button="#9BB9D2"; icon="#70D6E8" }
                        else -> { primary="#80CBC4"; background="#242629"; surface="#2D3034"; textColor="#E8EAED"; border="#60666D"; button="#80CBC4"; icon="#80CBC4" }
                    }
                }, label = { Text(preset) })
            }
        }
        Text("COLOR CONTROLS — HEX COLOR", style = MaterialTheme.typography.labelLarge)
        listOf(
            "Primary / Accent" to (primary to { v: String -> primary = v }),
            "Background" to (background to { v: String -> background = v }),
            "Panels / Surface" to (surface to { v: String -> surface = v }),
            "Text" to (textColor to { v: String -> textColor = v }),
            "Borders" to (border to { v: String -> border = v }),
            "Buttons" to (button to { v: String -> button = v }),
            "Icons" to (icon to { v: String -> icon = v }),
            "Gradient End" to (gradientEnd to { v: String -> gradientEnd = v })
        ).forEach { (label, pair) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = pair.first, onValueChange = pair.second, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("#RRGGBB") })
                }
                Column(Modifier.padding(top = 24.dp)) {
                    Card(Modifier.height(42.dp).width(48.dp)) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.background(parseAppearanceColor(pair.first, MaterialTheme.colorScheme.primary)).padding(10.dp)
                        ) { Text(" ", Modifier.padding(4.dp)) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Enable background gradient")
            Switch(checked = gradient, onCheckedChange = { gradient = it })
        }
        Text("Panel opacity: ${(opacity * 100).toInt()}%")
        Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.25f..1f)
        Text("Brightness: ${(brightness * 100).toInt()}%")
        Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 0.4f..1.5f)
        Text("Blur intensity: ${blur.toInt()} dp")
        Slider(value = blur, onValueChange = { blur = it }, valueRange = 0f..24f)
        Text("BACKGROUND IMAGE", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) { Text("Choose Custom Background Image") }
        Text(if (imageUri.isBlank()) "No custom image selected." else "Custom image selected and ready to apply.", style = MaterialTheme.typography.bodySmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("LIVE PREVIEW", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().background(parseAppearanceColor(background, MaterialTheme.colorScheme.background), RoundedCornerShape(12.dp)).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Nexus3D Diagnostics", color = parseAppearanceColor(textColor, MaterialTheme.colorScheme.onSurface))
                        Text("Panel • Button • Icon", color = parseAppearanceColor(primary, MaterialTheme.colorScheme.primary))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("BUTTON", color = parseAppearanceColor(button, MaterialTheme.colorScheme.primary))
                            Text("BORDER", color = parseAppearanceColor(border, MaterialTheme.colorScheme.outline))
                            Text("ICON", color = parseAppearanceColor(icon, MaterialTheme.colorScheme.primary))
                        }
                    }
                }
            }
        }
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val editor = prefs.edit()
                fun put(key: String, value: String) { editor.putString("${scope}_$key", value); if (scope == "global") editor.putString(key, value) }
                put("background_theme", theme)
                put("primary_hex", primary)
                put("background_hex", background)
                put("surface_hex", surface)
                put("text_hex", textColor)
                put("border_hex", border)
                put("button_hex", button)
                put("icon_hex", icon)
                put("gradient_end_hex", gradientEnd)
                editor.putBoolean("${scope}_gradient_enabled", gradient)
                editor.putFloat("${scope}_panel_opacity", opacity)
                editor.putFloat("${scope}_brightness", brightness)
                editor.putFloat("${scope}_blur", blur)
                editor.putString("${scope}_background_image_uri", imageUri)
                if (scope == "global") {
                    editor.putBoolean("gradient_enabled", gradient)
                    editor.putFloat("panel_opacity", opacity)
                    editor.putFloat("brightness", brightness)
                    editor.putFloat("blur", blur)
                    editor.putString("background_image_uri", imageUri)
                }
                editor.remove("pending_background_image_uri")
                editor.apply()
                onApplied(scope, theme, parseAppearanceColor(primary, Color(0xFF80CBC4)), parseAppearanceColor(background, Color(0xFF242629)), parseAppearanceColor(surface, Color(0xFF2D3034)), parseAppearanceColor(textColor, Color(0xFFE8EAED)), parseAppearanceColor(border, Color(0xFF60666D)))
                message = "Appearance saved on this device."
            }) { Text("Apply") }
            OutlinedButton(onClick = { restoreDraft(scope); message = "Unapplied changes cancelled." }) { Text("Cancel") }
            OutlinedButton(onClick = { resetDraft() }) { Text("Reset") }
        }
        Text("Offline settings are stored locally. Screen-specific settings are saved separately from All Screens.", style = MaterialTheme.typography.bodySmall)
    }
}
