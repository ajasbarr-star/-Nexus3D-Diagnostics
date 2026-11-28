package com.nexus3d.diagnostics

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream

@Composable
fun OwnerAdminProfileMediaScreen() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("nexus3d_owner_admin_profile", 0) }
    val mediaDirectory = remember { File(context.filesDir, "owner_admin_media").apply { mkdirs() } }

    var profileSource by remember {
        mutableStateOf(preferences.getString("profile_source_path", null)?.let(::decodeOwnerPhoto))
    }
    var profilePhoto by remember {
        mutableStateOf(preferences.getString("profile_photo_path", null)?.let(::decodeOwnerPhoto))
    }
    var coverSource by remember {
        mutableStateOf(preferences.getString("cover_source_path", null)?.let(::decodeOwnerPhoto))
    }
    var coverPhoto by remember {
        mutableStateOf(preferences.getString("cover_photo_path", null)?.let(::decodeOwnerPhoto))
    }
    var profileZoom by remember { mutableStateOf(1f) }
    var profileX by remember { mutableStateOf(0f) }
    var profileY by remember { mutableStateOf(0f) }
    var coverZoom by remember { mutableStateOf(1f) }
    var coverX by remember { mutableStateOf(0f) }
    var coverY by remember { mutableStateOf(0f) }
    var target by remember { mutableStateOf("profile") }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val decoded = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: error("Unable to read the selected image.")
                val scaled = scaleOwnerPhoto(decoded)
                val sourceFile = File(mediaDirectory, "${target}_source.jpg")
                FileOutputStream(sourceFile).use { scaled.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                preferences.edit().putString("${target}_source_path", sourceFile.absolutePath).apply()
                if (target == "profile") {
                    profileSource = scaled
                    profileZoom = 1f; profileX = 0f; profileY = 0f
                } else {
                    coverSource = scaled
                    coverZoom = 1f; coverX = 0f; coverY = 0f
                }
            }.onFailure { error ->
                android.widget.Toast.makeText(context, error.message ?: "Image upload failed.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val profilePreview = remember(profileSource, profileZoom, profileX, profileY) {
        profileSource?.let { cropOwnerPhoto(it, 1f, profileZoom, profileX, profileY) }
    }
    val coverPreview = remember(coverSource, coverZoom, coverX, coverY) {
        coverSource?.let { cropOwnerPhoto(it, 16f / 9f, coverZoom, coverX, coverY) }
    }

    fun saveCrop(kind: String, bitmap: Bitmap?) {
        if (bitmap == null) return
        runCatching {
            val output = File(mediaDirectory, "${kind}_photo.jpg")
            FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            preferences.edit().putString("${kind}_photo_path", output.absolutePath).apply()
            if (kind == "profile") profilePhoto = decodeOwnerPhoto(output.absolutePath)
            else coverPhoto = decodeOwnerPhoto(output.absolutePath)
            android.widget.Toast.makeText(context, "${if (kind == "profile") "Profile Photo" else "Cover Photo"} saved offline.", android.widget.Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            android.widget.Toast.makeText(context, error.message ?: "Unable to save image.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun removePhoto(kind: String) {
        listOf("${kind}_source_path", "${kind}_photo_path").forEach { key ->
            preferences.getString(key, null)?.let { File(it).delete() }
        }
        preferences.edit().remove("${kind}_source_path").remove("${kind}_photo_path").apply()
        if (kind == "profile") { profileSource = null; profilePhoto = null; profileZoom = 1f; profileX = 0f; profileY = 0f }
        else { coverSource = null; coverPhoto = null; coverZoom = 1f; coverX = 0f; coverY = 0f }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text("OWNER ADMIN PROFILE", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PROFILE PHOTO", style = MaterialTheme.typography.titleSmall)
                val displayProfile = profilePreview ?: profilePhoto
                if (displayProfile != null) {
                    Image(displayProfile.asImageBitmap(), contentDescription = "Owner Admin profile photo", modifier = Modifier.size(112.dp), contentScale = ContentScale.Crop)
                } else {
                    Text("No Profile Photo selected.")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { target = "profile"; galleryPicker.launch(arrayOf("image/*")) }) {
                        Text(if (profileSource == null) "Add Photo" else "Change Photo")
                    }
                    OutlinedButton(onClick = { removePhoto("profile") }, enabled = profileSource != null || profilePhoto != null) { Text("Remove") }
                }
                if (profileSource != null) {
                    Text("Crop / Resize")
                    Text("Zoom")
                    Slider(value = profileZoom, onValueChange = { profileZoom = it }, valueRange = 1f..3f)
                    Text("Horizontal Position")
                    Slider(value = profileX, onValueChange = { profileX = it }, valueRange = -1f..1f)
                    Text("Vertical Position")
                    Slider(value = profileY, onValueChange = { profileY = it }, valueRange = -1f..1f)
                    Button(onClick = { saveCrop("profile", profilePreview) }) { Text("Save Profile Photo") }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("COVER PHOTO", style = MaterialTheme.typography.titleSmall)
                val displayCover = coverPreview ?: coverPhoto
                if (displayCover != null) {
                    Image(displayCover.asImageBitmap(), contentDescription = "Owner Admin cover photo", modifier = Modifier.fillMaxWidth().height(118.dp), contentScale = ContentScale.Crop)
                } else {
                    Text("No Cover Photo selected.")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { target = "cover"; galleryPicker.launch(arrayOf("image/*")) }) {
                        Text(if (coverSource == null) "Add Cover" else "Change Cover")
                    }
                    OutlinedButton(onClick = { removePhoto("cover") }, enabled = coverSource != null || coverPhoto != null) { Text("Remove") }
                }
                if (coverSource != null) {
                    Text("Crop / Resize / Reposition")
                    Text("Zoom")
                    Slider(value = coverZoom, onValueChange = { coverZoom = it }, valueRange = 1f..3f)
                    Text("Horizontal Position")
                    Slider(value = coverX, onValueChange = { coverX = it }, valueRange = -1f..1f)
                    Text("Vertical Position")
                    Slider(value = coverY, onValueChange = { coverY = it }, valueRange = -1f..1f)
                    Button(onClick = { saveCrop("cover", coverPreview) }) { Text("Save Cover Photo") }
                }
            }
        }
        Text("Photos are stored inside this Diagnostics installation for offline viewing. Profile and Cover images are managed independently.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun decodeOwnerPhoto(path: String): Bitmap? = runCatching { BitmapFactory.decodeFile(path) }.getOrNull()

private fun scaleOwnerPhoto(source: Bitmap): Bitmap {
    val maxDimension = 2048
    val largest = maxOf(source.width, source.height)
    if (largest <= maxDimension) return source
    val ratio = maxDimension.toFloat() / largest
    return Bitmap.createScaledBitmap(source, (source.width * ratio).toInt().coerceAtLeast(1), (source.height * ratio).toInt().coerceAtLeast(1), true)
}

private fun cropOwnerPhoto(source: Bitmap, aspectRatio: Float, zoom: Float, offsetX: Float, offsetY: Float): Bitmap {
    var cropWidth = source.width.toFloat()
    var cropHeight = source.height.toFloat()
    if (cropWidth / cropHeight > aspectRatio) cropWidth = cropHeight * aspectRatio
    else cropHeight = cropWidth / aspectRatio
    cropWidth = (cropWidth / zoom.coerceIn(1f, 3f)).coerceAtLeast(1f)
    cropHeight = (cropHeight / zoom.coerceIn(1f, 3f)).coerceAtLeast(1f)
    val maxLeft = (source.width - cropWidth).coerceAtLeast(0f)
    val maxTop = (source.height - cropHeight).coerceAtLeast(0f)
    val left = (maxLeft / 2f + offsetX.coerceIn(-1f, 1f) * maxLeft / 2f).toInt().coerceIn(0, (source.width - cropWidth.toInt()).coerceAtLeast(0))
    val top = (maxTop / 2f + offsetY.coerceIn(-1f, 1f) * maxTop / 2f).toInt().coerceIn(0, (source.height - cropHeight.toInt()).coerceAtLeast(0))
    val width = cropWidth.toInt().coerceIn(1, source.width - left)
    val height = cropHeight.toInt().coerceIn(1, source.height - top)
    return Bitmap.createBitmap(source, left, top, width, height)
}
