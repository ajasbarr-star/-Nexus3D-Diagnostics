package com.nexus3d.diagnostics

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import java.io.File

data class DiagnosticsAppearance(
    val themeName: String = "Dark Gray",
    val backgroundStyle: String = "Preset",
    val backgroundColor: Int = 0xFF242629.toInt(),
    val gradientStart: Int = 0xFF101923.toInt(),
    val gradientEnd: Int = 0xFF253F55.toInt(),
    val accentColor: Int = 0xFF80CBC4.toInt(),
    val textColor: Int = 0xFFE8EAED.toInt(),
    val panelColor: Int = 0xFF2D3034.toInt(),
    val buttonColor: Int = 0xFF80CBC4.toInt(),
    val opacity: Float = 1f,
    val brightness: Float = 1f,
    val blur: Float = 0f,
    val backgroundScale: Float = 1f,
    val backgroundOffsetX: Float = 0f,
    val backgroundOffsetY: Float = 0f,
    val textScale: Float = 1f,
    val panelOpacity: Float = 0.96f,
    val borderWidth: Float = 1f,
    val cornerRadius: Float = 18f,
    val iconScale: Float = 1f,
    val backgroundImagePath: String? = null,
    val applyToAllScreens: Boolean = true
) {
    companion object {
        val Default = DiagnosticsAppearance()
    }
}

class DiagnosticsAppearanceStore(context: Context) {
    private val prefs = context.getSharedPreferences("nexus3d_diagnostics_appearance_v2", Context.MODE_PRIVATE)

    fun load(): DiagnosticsAppearance = DiagnosticsAppearance(
        themeName = prefs.getString("themeName", "Dark Gray") ?: "Dark Gray",
        backgroundStyle = prefs.getString("backgroundStyle", "Preset") ?: "Preset",
        backgroundColor = prefs.getInt("backgroundColor", DiagnosticsAppearance.Default.backgroundColor),
        gradientStart = prefs.getInt("gradientStart", DiagnosticsAppearance.Default.gradientStart),
        gradientEnd = prefs.getInt("gradientEnd", DiagnosticsAppearance.Default.gradientEnd),
        accentColor = prefs.getInt("accentColor", DiagnosticsAppearance.Default.accentColor),
        textColor = prefs.getInt("textColor", DiagnosticsAppearance.Default.textColor),
        panelColor = prefs.getInt("panelColor", DiagnosticsAppearance.Default.panelColor),
        buttonColor = prefs.getInt("buttonColor", DiagnosticsAppearance.Default.buttonColor),
        opacity = prefs.getFloat("opacity", 1f),
        brightness = prefs.getFloat("brightness", 1f),
        blur = prefs.getFloat("blur", 0f),
        backgroundScale = prefs.getFloat("backgroundScale", 1f),
        backgroundOffsetX = prefs.getFloat("backgroundOffsetX", 0f),
        backgroundOffsetY = prefs.getFloat("backgroundOffsetY", 0f),
        textScale = prefs.getFloat("textScale", 1f),
        panelOpacity = prefs.getFloat("panelOpacity", 0.96f),
        borderWidth = prefs.getFloat("borderWidth", 1f),
        cornerRadius = prefs.getFloat("cornerRadius", 18f),
        iconScale = prefs.getFloat("iconScale", 1f),
        backgroundImagePath = prefs.getString("backgroundImagePath", null),
        applyToAllScreens = prefs.getBoolean("applyToAllScreens", true)
    )

    fun save(value: DiagnosticsAppearance) {
        prefs.edit()
            .putString("themeName", value.themeName)
            .putString("backgroundStyle", value.backgroundStyle)
            .putInt("backgroundColor", value.backgroundColor)
            .putInt("gradientStart", value.gradientStart)
            .putInt("gradientEnd", value.gradientEnd)
            .putInt("accentColor", value.accentColor)
            .putInt("textColor", value.textColor)
            .putInt("panelColor", value.panelColor)
            .putInt("buttonColor", value.buttonColor)
            .putFloat("opacity", value.opacity)
            .putFloat("brightness", value.brightness)
            .putFloat("blur", value.blur)
            .putFloat("backgroundScale", value.backgroundScale)
            .putFloat("backgroundOffsetX", value.backgroundOffsetX)
            .putFloat("backgroundOffsetY", value.backgroundOffsetY)
            .putFloat("textScale", value.textScale)
            .putFloat("panelOpacity", value.panelOpacity)
            .putFloat("borderWidth", value.borderWidth)
            .putFloat("cornerRadius", value.cornerRadius)
            .putFloat("iconScale", value.iconScale)
            .putString("backgroundImagePath", value.backgroundImagePath)
            .putBoolean("applyToAllScreens", value.applyToAllScreens)
            .apply()
    }
}

fun DiagnosticsAppearance.toColorScheme(): ColorScheme = darkColorScheme(
    primary = Color(accentColor),
    secondary = Color(buttonColor),
    background = Color(backgroundColor),
    surface = Color(panelColor).copy(alpha = panelOpacity.coerceIn(0.2f, 1f)),
    surfaceVariant = Color(panelColor).copy(alpha = panelOpacity.coerceIn(0.2f, 1f)),
    onSurface = Color(textColor),
    onBackground = Color(textColor),
    outline = Color(accentColor).copy(alpha = (borderWidth / 4f).coerceIn(0.12f, 1f))
)

fun DiagnosticsAppearance.toShapes(): Shapes {
    val r = cornerRadius.coerceIn(4f, 32f)
    return Shapes(
        extraSmall = RoundedCornerShape((r * 0.35f).dp),
        small = RoundedCornerShape((r * 0.55f).dp),
        medium = RoundedCornerShape((r * 0.75f).dp),
        large = RoundedCornerShape(r.dp),
        extraLarge = RoundedCornerShape((r * 1.2f).dp)
    )
}

fun DiagnosticsAppearance.toTypography(base: Typography): Typography {
    val scale = textScale.coerceIn(0.75f, 1.5f)
    return base.copy(
        displayLarge = base.displayLarge.copy(fontSize = base.displayLarge.fontSize * scale),
        headlineMedium = base.headlineMedium.copy(fontSize = base.headlineMedium.fontSize * scale),
        headlineSmall = base.headlineSmall.copy(fontSize = base.headlineSmall.fontSize * scale),
        titleLarge = base.titleLarge.copy(fontSize = base.titleLarge.fontSize * scale),
        titleMedium = base.titleMedium.copy(fontSize = base.titleMedium.fontSize * scale),
        titleSmall = base.titleSmall.copy(fontSize = base.titleSmall.fontSize * scale),
        bodyLarge = base.bodyLarge.copy(fontSize = base.bodyLarge.fontSize * scale),
        bodyMedium = base.bodyMedium.copy(fontSize = base.bodyMedium.fontSize * scale),
        bodySmall = base.bodySmall.copy(fontSize = base.bodySmall.fontSize * scale),
        labelLarge = base.labelLarge.copy(fontSize = base.labelLarge.fontSize * scale),
        labelMedium = base.labelMedium.copy(fontSize = base.labelMedium.fontSize * scale),
        labelSmall = base.labelSmall.copy(fontSize = base.labelSmall.fontSize * scale)
    )
}

@Composable
fun DiagnosticsAppearanceBackdrop(
    settings: DiagnosticsAppearance,
    content: @Composable () -> Unit
) {
    val bitmap = remember(settings.backgroundImagePath) {
        settings.backgroundImagePath?.let { path ->
            if (File(path).isFile) BitmapFactory.decodeFile(path)?.asImageBitmap() else null
        }
    }
    Box(Modifier.fillMaxSize()) {
        when (settings.backgroundStyle) {
            "Gradient" -> Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(Color(settings.gradientStart), Color(settings.gradientEnd)))
                )
            )
            "Image" -> if (bitmap != null) {
                val matrix = remember(settings.brightness) {
                    ColorMatrix().apply {
                        setToScale(settings.brightness, settings.brightness, settings.brightness, 1f)
                    }
                }
                Image(
                    bitmap = bitmap,
                    contentDescription = "Custom Diagnostics background",
                    modifier = Modifier.fillMaxSize()
                        .blur(settings.blur.coerceIn(0f, 24f).dp)
                        .graphicsLayer(
                            alpha = settings.opacity.coerceIn(0.15f, 1f),
                            scaleX = settings.backgroundScale.coerceIn(1f, 2.5f),
                            scaleY = settings.backgroundScale.coerceIn(1f, 2.5f),
                            translationX = settings.backgroundOffsetX.coerceIn(-1f, 1f) * 120f,
                            translationY = settings.backgroundOffsetY.coerceIn(-1f, 1f) * 120f
                        ),
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(matrix)
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(settings.backgroundColor)))
            }
            else -> Box(Modifier.fillMaxSize().background(Color(settings.backgroundColor)))
        }
        content()
    }
}

@Composable
fun AppearanceSettingsPanel(
    settings: DiagnosticsAppearance,
    onApply: (DiagnosticsAppearance) -> Unit
) {
    val context = LocalContext.current
    var draft by remember(settings) { mutableStateOf(settings) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching { saveAppearanceImage(context, uri) }
                .onSuccess { draft = draft.copy(backgroundImagePath = it, backgroundStyle = "Image") }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("BACKGROUND STYLE", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Preset", "Solid", "Gradient", "Image").forEach { style ->
                FilterChip(
                    selected = draft.backgroundStyle == style,
                    onClick = { draft = draft.copy(backgroundStyle = style) },
                    label = { Text(style) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Dark Gray", "Graphite", "Midnight", "Deep Navy").forEach { preset ->
                FilterChip(
                    selected = draft.themeName == preset,
                    onClick = {
                        draft = when (preset) {
                            "Graphite" -> draft.copy(themeName = preset, backgroundStyle = "Preset", backgroundColor = 0xFF202326.toInt(), panelColor = 0xFF292D31.toInt(), accentColor = 0xFFB8C4D0.toInt(), textColor = 0xFFF0F1F2.toInt(), buttonColor = 0xFF9FAAB5.toInt())
                            "Midnight" -> draft.copy(themeName = preset, backgroundStyle = "Preset", backgroundColor = 0xFF080D16.toInt(), panelColor = 0xFF101827.toInt(), accentColor = 0xFF8AB4F8.toInt(), textColor = 0xFFE7EDF7.toInt(), buttonColor = 0xFF9AAFCB.toInt())
                            "Deep Navy" -> draft.copy(themeName = preset, backgroundStyle = "Preset", backgroundColor = 0xFF101923.toInt(), panelColor = 0xFF172635.toInt(), accentColor = 0xFF70D6E8.toInt(), textColor = 0xFFE5F1F5.toInt(), buttonColor = 0xFF9BB9D2.toInt())
                            else -> draft.copy(themeName = preset, backgroundStyle = "Preset", backgroundColor = 0xFF242629.toInt(), panelColor = 0xFF2D3034.toInt(), accentColor = 0xFF80CBC4.toInt(), textColor = 0xFFE8EAED.toInt(), buttonColor = 0xFF9EADB5.toInt())
                        }
                    },
                    label = { Text(preset) }
                )
            }
        }

        if (draft.backgroundStyle == "Image") {
            OutlinedButton(onClick = { imagePicker.launch("image/*") }) { Text("ADD / CHANGE CUSTOM BACKGROUND") }
            if (draft.backgroundImagePath != null) {
                OutlinedButton(onClick = { draft = draft.copy(backgroundImagePath = null, backgroundStyle = "Solid") }) { Text("REMOVE BACKGROUND IMAGE") }
                AppearanceSlider("Image Scale", draft.backgroundScale, 1f..2.5f) { draft = draft.copy(backgroundScale = it) }
                AppearanceSlider("Image Horizontal Position", draft.backgroundOffsetX, -1f..1f) { draft = draft.copy(backgroundOffsetX = it) }
                AppearanceSlider("Image Vertical Position", draft.backgroundOffsetY, -1f..1f) { draft = draft.copy(backgroundOffsetY = it) }
            }
        } else if (draft.backgroundStyle == "Solid") {
            OutlinedButton(onClick = { draft = draft.copy(backgroundStyle = "Solid") }) { Text("ADD CUSTOM BACKGROUND COLOR") }
            AppearanceColorEditor("Background Color", draft.backgroundColor) { draft = draft.copy(backgroundColor = it) }
        } else if (draft.backgroundStyle == "Gradient") {
            AppearanceColorEditor("Gradient Start", draft.gradientStart) { draft = draft.copy(gradientStart = it) }
            AppearanceColorEditor("Gradient End", draft.gradientEnd) { draft = draft.copy(gradientEnd = it) }
        }

        Text("UI COLORS", style = MaterialTheme.typography.labelLarge)
        AppearanceColorEditor("Accent / Active State", draft.accentColor) { draft = draft.copy(accentColor = it) }
        AppearanceColorEditor("Button Color", draft.buttonColor) { draft = draft.copy(buttonColor = it) }
        AppearanceColorEditor("Text & Labels Color", draft.textColor) { draft = draft.copy(textColor = it) }
        AppearanceColorEditor("Panel Background", draft.panelColor) { draft = draft.copy(panelColor = it) }

        Text("LAYOUT & EFFECTS", style = MaterialTheme.typography.labelLarge)
        AppearanceSlider("Background Opacity", draft.opacity, 0.15f..1f) { draft = draft.copy(opacity = it) }
        AppearanceSlider("Background Brightness", draft.brightness, 0.3f..1.7f) { draft = draft.copy(brightness = it) }
        AppearanceSlider("Background Blur", draft.blur, 0f..24f) { draft = draft.copy(blur = it) }
        AppearanceSlider("Panel Opacity", draft.panelOpacity, 0.2f..1f) { draft = draft.copy(panelOpacity = it) }
        AppearanceSlider("Text Size", draft.textScale, 0.75f..1.5f) { draft = draft.copy(textScale = it) }
        AppearanceSlider("Icon Scale", draft.iconScale, 0.7f..1.5f) { draft = draft.copy(iconScale = it) }
        AppearanceSlider("Border Thickness", draft.borderWidth, 0f..4f) { draft = draft.copy(borderWidth = it) }
        AppearanceSlider("Panel / Button Corner Radius", draft.cornerRadius, 4f..32f) { draft = draft.copy(cornerRadius = it) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = draft.applyToAllScreens, onCheckedChange = { draft = draft.copy(applyToAllScreens = it) })
            Text("APPLY TO ALL SCREENS")
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("LIVE PREVIEW", style = MaterialTheme.typography.labelLarge)
                Box(
                    Modifier.fillMaxWidth().height(74.dp)
                        .background(Color(draft.panelColor).copy(alpha = draft.panelOpacity), MaterialTheme.shapes.medium)
                        .padding(10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("Nexus3D Diagnostics • Preview", color = Color(draft.textColor))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onApply(draft) }) { Text("APPLY") }
            OutlinedButton(onClick = { draft = settings }) { Text("CANCEL") }
            OutlinedButton(onClick = {
                draft = DiagnosticsAppearance.Default
                onApply(DiagnosticsAppearance.Default)
            }) { Text("RESET TO DEFAULT") }
        }
    }
}

@Composable
private fun AppearanceSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$label: ${"%.2f".format(value)}", style = MaterialTheme.typography.bodySmall)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun AppearanceColorEditor(
    label: String,
    colorValue: Int,
    onChange: (Int) -> Unit
) {
    val color = Color(colorValue)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text("R ${(color.red * 255).toInt()}  G ${(color.green * 255).toInt()}  B ${(color.blue * 255).toInt()}", style = MaterialTheme.typography.labelSmall)
        AppearanceSlider("Red", color.red * 255f, 0f..255f) { onChange(Color(it / 255f, color.green, color.blue, 1f).toArgb()) }
        AppearanceSlider("Green", color.green * 255f, 0f..255f) { onChange(Color(color.red, it / 255f, color.blue, 1f).toArgb()) }
        AppearanceSlider("Blue", color.blue * 255f, 0f..255f) { onChange(Color(color.red, color.green, it / 255f, 1f).toArgb()) }
    }
}

private fun saveAppearanceImage(context: Context, uri: Uri): String {
    val mime = context.contentResolver.getType(uri).orEmpty()
    require(mime.startsWith("image/")) { "Choose an image file." }
    val folder = File(context.filesDir, "appearance").apply { mkdirs() }
    val extension = when (mime.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
    val target = File(folder, "custom_background.$extension")
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Unable to read selected image." }
        target.outputStream().use { output ->
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= 12L * 1024L * 1024L) { "Image must be 12 MB or smaller." }
                output.write(buffer, 0, count)
            }
        }
    }
    return target.absolutePath
}
