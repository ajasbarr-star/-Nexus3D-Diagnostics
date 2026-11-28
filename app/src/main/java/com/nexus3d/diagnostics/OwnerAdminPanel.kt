package com.nexus3d.diagnostics

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File

private enum class OwnerAdminDialog {
    NONE, SETUP, UNLOCK, CHANGE, RECOVERY_CODE, RECOVERY_NEW
}

@Composable
fun OwnerAdminStatusbarPanel(
    status: OwnerAdminStatus?,
    repository: SupabaseAccountRepository,
    autoLockTimeoutMillis: Long = 5 * 60 * 1000L
) {
    val owner = status ?: return
    val adminId = owner.adminId ?: return
    if (!owner.registered || owner.status != "active" || adminId.isBlank()) return

    val context = LocalContext.current
    val appearance = LocalDiagnosticsAppearance.current
    val scaledIconSize = (24f * appearance.iconScale.coerceIn(0.7f, 1.5f)).dp
    val clipboard = LocalClipboardManager.current
    val store = remember { OwnerAdminLocalStore(context) }
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(false) }
    var hasCredential by remember { mutableStateOf(store.hasCredential()) }
    var dialog by remember { mutableStateOf(OwnerAdminDialog.NONE) }
    var credentialKind by remember { mutableStateOf(store.credentialKind()) }
    var currentCredential by remember { mutableStateOf("") }
    var newCredential by remember { mutableStateOf("") }
    var confirmCredential by remember { mutableStateOf("") }
    var recoveryCode by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var profilePath by remember { mutableStateOf(store.imagePath("profile")) }
    var coverPath by remember { mutableStateOf(store.imagePath("cover")) }
    var profileScale by remember { mutableStateOf(store.imageScale("profile")) }
    var coverScale by remember { mutableStateOf(store.imageScale("cover")) }
    var coverOffsetX by remember { mutableStateOf(store.imageOffsetX("cover")) }
    var coverOffsetY by remember { mutableStateOf(store.imageOffsetY("cover")) }

    val profilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching { store.saveImage(uri, "profile") }
            .onSuccess { profilePath = it; profileScale = 1f; store.saveImageCrop("profile", 1f); message = "Profile picture saved on this device." }
            .onFailure { error = it.message ?: "Unable to save profile picture." }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching { store.saveImage(uri, "cover") }
            .onSuccess { coverPath = it; coverScale = 1f; coverOffsetX = 0f; coverOffsetY = 0f; store.saveImageCrop("cover", 1f); message = "Cover photo saved on this device." }
            .onFailure { error = it.message ?: "Unable to save cover photo." }
    }

    DisposableEffect(unlocked, autoLockTimeoutMillis) {
        if (unlocked && autoLockTimeoutMillis > 0L) {
            val handler = Handler(Looper.getMainLooper())
            val lockRunnable = Runnable {
                unlocked = false
                expanded = false
                message = "Owner Admin panel locked after 5 minutes."
            }
            handler.postDelayed(lockRunnable, autoLockTimeoutMillis)
            onDispose { handler.removeCallbacks(lockRunnable) }
        } else {
            onDispose { }
        }
    }

    fun resetDialogFields() {
        currentCredential = ""
        newCredential = ""
        confirmCredential = ""
        recoveryCode = ""
        error = ""
        message = ""
    }

    fun saveNewCredential() {
        val normalized = newCredential.trim()
        val valid = if (credentialKind == "PIN") {
            normalized.length in 6..12 && normalized.all(Char::isDigit)
        } else {
            normalized.length in 10..128
        }
        if (!valid) {
            error = if (credentialKind == "PIN") "PIN must contain 6–12 digits." else "Password must contain 10–128 characters."
            return
        }
        if (normalized != confirmCredential) {
            error = "PIN/password confirmation does not match."
            return
        }
        runCatching { store.saveCredential(normalized, credentialKind) }
            .onSuccess {
                hasCredential = true
                unlocked = true
                expanded = true
                dialog = OwnerAdminDialog.NONE
                resetDialogFields()
                message = "Owner Admin panel security saved."
            }
            .onFailure { error = it.message ?: "Unable to save the security credential." }
    }

    Card(modifier = Modifier.fillMaxWidth().border(appearance.borderWidth.coerceIn(0f, 4f).dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("OWNER ADMIN STATUSBAR", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (unlocked) "Unlocked • auto-lock in 5 minutes" else if (hasCredential) "Locked • verification required" else "Security setup required",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(onClick = {
                    when {
                        unlocked -> expanded = !expanded
                        hasCredential -> { resetDialogFields(); dialog = OwnerAdminDialog.UNLOCK }
                        else -> { resetDialogFields(); dialog = OwnerAdminDialog.SETUP }
                    }
                }) {
                    Icon(
                        modifier = Modifier.size(scaledIconSize),
                        imageVector = when {
                            unlocked && expanded -> Icons.Default.ExpandLess
                            unlocked -> Icons.Default.ExpandMore
                            hasCredential -> Icons.Default.Lock
                            else -> Icons.Default.Key
                        },
                        contentDescription = if (unlocked && expanded) "Collapse Owner Admin panel" else "Unlock Owner Admin panel"
                    )
                }
            }

            if (!unlocked && !hasCredential) {
                Text("Set a PIN or Password before opening protected Owner Admin information.")
                Button(onClick = { resetDialogFields(); dialog = OwnerAdminDialog.SETUP }) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(scaledIconSize))
                    Text("SET PIN/PASS")
                }
            }

            if (unlocked && expanded) {
                OwnerAdminMediaPreview(profilePath, coverPath, profileScale, coverScale, coverOffsetX, coverOffsetY)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("OWNER ADMIN", style = MaterialTheme.typography.titleSmall)
                    Text("ACTIVE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
                Text("Verified Owner Admin: ajasbarr@gmail.com", style = MaterialTheme.typography.bodySmall)
                Text("Permanent Admin Unique ID", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(adminId, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(adminId))
                        message = "Permanent Admin Unique ID copied."
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Permanent Admin Unique ID", modifier = Modifier.size(scaledIconSize))
                    }
                }

                Text("PROFILE PICTURE", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { profilePicker.launch("image/*") }) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(scaledIconSize))
                        Text(if (profilePath == null) "Add Picture" else "Change Picture")
                    }
                    if (profilePath != null) IconButton(onClick = { store.removeImage("profile"); profilePath = null; profileScale = 1f }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove profile picture", modifier = Modifier.size(scaledIconSize))
                    }
                }
                if (profilePath != null) {
                    Text("Profile picture crop / zoom", style = MaterialTheme.typography.bodySmall)
                    Slider(value = profileScale, onValueChange = { profileScale = it; store.saveImageCrop("profile", it) }, valueRange = 1f..2.5f)
                }
                Text("COVER PHOTO", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { coverPicker.launch("image/*") }) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(scaledIconSize))
                        Text(if (coverPath == null) "Add Cover" else "Change Cover")
                    }
                    if (coverPath != null) IconButton(onClick = { store.removeImage("cover"); coverPath = null; coverScale = 1f; coverOffsetX = 0f; coverOffsetY = 0f }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove cover photo", modifier = Modifier.size(scaledIconSize))
                    }
                }
                if (coverPath != null) {
                    Text("Cover photo crop / zoom", style = MaterialTheme.typography.bodySmall)
                    Slider(value = coverScale, onValueChange = { coverScale = it; store.saveImageCrop("cover", it, coverOffsetX, coverOffsetY) }, valueRange = 1f..2.5f)
                    Text("Cover horizontal position", style = MaterialTheme.typography.bodySmall)
                    Slider(value = coverOffsetX, onValueChange = { coverOffsetX = it; store.saveImageCrop("cover", coverScale, it, coverOffsetY) }, valueRange = -1f..1f)
                    Text("Cover vertical position", style = MaterialTheme.typography.bodySmall)
                    Slider(value = coverOffsetY, onValueChange = { coverOffsetY = it; store.saveImageCrop("cover", coverScale, coverOffsetX, it) }, valueRange = -1f..1f)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = {
                        resetDialogFields()
                        credentialKind = store.credentialKind()
                        dialog = OwnerAdminDialog.CHANGE
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(scaledIconSize))
                        Text("SET PIN/PASS")
                    }
                    OutlinedButton(onClick = {
                        resetDialogFields()
                        credentialKind = store.credentialKind()
                        dialog = OwnerAdminDialog.CHANGE
                    }) {
                        Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(scaledIconSize))
                        Text("RESET PIN/PASS")
                    }
                    IconButton(onClick = { unlocked = false; expanded = false }) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock Owner Admin panel", modifier = Modifier.size(scaledIconSize))
                    }
                }
            }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            if (error.isNotBlank() && dialog == OwnerAdminDialog.NONE) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (dialog != OwnerAdminDialog.NONE) {
        val title = when (dialog) {
            OwnerAdminDialog.SETUP -> "Set Owner Admin PIN / Password"
            OwnerAdminDialog.UNLOCK -> "Unlock Owner Admin Panel"
            OwnerAdminDialog.CHANGE -> "Change Owner Admin PIN / Password"
            OwnerAdminDialog.RECOVERY_CODE -> "Email Recovery Verification"
            OwnerAdminDialog.RECOVERY_NEW -> "Set a New PIN / Password"
            OwnerAdminDialog.NONE -> ""
        }
        AlertDialog(
            onDismissRequest = { if (!busy) { dialog = OwnerAdminDialog.NONE; resetDialogFields() } },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (dialog) {
                        OwnerAdminDialog.SETUP -> {
                            Text("Choose one security method.")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = credentialKind == "PIN", onClick = { credentialKind = "PIN" }, label = { Text("PIN") })
                                FilterChip(selected = credentialKind == "PASSWORD", onClick = { credentialKind = "PASSWORD" }, label = { Text("Password") })
                            }
                            OutlinedTextField(
                                value = newCredential,
                                onValueChange = { newCredential = it },
                                label = { Text(if (credentialKind == "PIN") "6–12 digit PIN" else "Password (10–128 characters)") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true
                            )
                            OutlinedTextField(value = confirmCredential, onValueChange = { confirmCredential = it }, label = { Text("Confirm PIN / Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                        }
                        OwnerAdminDialog.UNLOCK -> {
                            OutlinedTextField(
                                value = currentCredential,
                                onValueChange = { currentCredential = it },
                                label = { Text(if (credentialKind == "PIN") "Enter PIN" else "Enter Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true
                            )
                            TextButton(enabled = !busy, onClick = {
                                busy = true
                                error = ""
                                scope.launch {
                                    try {
                                        repository.requestOwnerAdminPinRecoveryCode()
                                        dialog = OwnerAdminDialog.RECOVERY_CODE
                                        message = "Recovery code requested for ajasbarr@gmail.com. Check the inbox."
                                    } catch (e: Exception) {
                                        error = e.message ?: "Unable to request email recovery."
                                    } finally { busy = false }
                                }
                            }) { Text("Forgot PIN / Password? Send recovery code") }
                        }
                        OwnerAdminDialog.CHANGE -> {
                            OutlinedTextField(value = currentCredential, onValueChange = { currentCredential = it }, label = { Text("Current PIN / Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                            Text("New security method")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = credentialKind == "PIN", onClick = { credentialKind = "PIN" }, label = { Text("PIN") })
                                FilterChip(selected = credentialKind == "PASSWORD", onClick = { credentialKind = "PASSWORD" }, label = { Text("Password") })
                            }
                            OutlinedTextField(value = newCredential, onValueChange = { newCredential = it }, label = { Text("New PIN / Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                            OutlinedTextField(value = confirmCredential, onValueChange = { confirmCredential = it }, label = { Text("Confirm new PIN / Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                        }
                        OwnerAdminDialog.RECOVERY_CODE -> {
                            Text("A one-time verification code was requested for ajasbarr@gmail.com.")
                            OutlinedTextField(value = recoveryCode, onValueChange = { recoveryCode = it.filter(Char::isDigit).take(8) }, label = { Text("Email verification code") }, singleLine = true)
                        }
                        OwnerAdminDialog.RECOVERY_NEW -> {
                            Text("Email verification succeeded. Choose a new PIN or Password.")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = credentialKind == "PIN", onClick = { credentialKind = "PIN" }, label = { Text("PIN") })
                                FilterChip(selected = credentialKind == "PASSWORD", onClick = { credentialKind = "PASSWORD" }, label = { Text("Password") })
                            }
                            OutlinedTextField(value = newCredential, onValueChange = { newCredential = it }, label = { Text("New PIN / Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                            OutlinedTextField(value = confirmCredential, onValueChange = { confirmCredential = it }, label = { Text("Confirm new PIN / Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                        }
                        OwnerAdminDialog.NONE -> Unit
                    }
                    if (busy) CircularProgressIndicator()
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    when (dialog) {
                        OwnerAdminDialog.SETUP -> saveNewCredential()
                        OwnerAdminDialog.UNLOCK -> {
                            if (store.verifyCredential(currentCredential)) {
                                unlocked = true
                                expanded = true
                                dialog = OwnerAdminDialog.NONE
                                resetDialogFields()
                            } else error = "Incorrect PIN / Password."
                        }
                        OwnerAdminDialog.CHANGE -> {
                            if (!store.verifyCredential(currentCredential)) {
                                error = "Current PIN / Password is incorrect."
                            } else saveNewCredential()
                        }
                        OwnerAdminDialog.RECOVERY_CODE -> {
                            busy = true
                            error = ""
                            scope.launch {
                                try {
                                    repository.verifyOwnerAdminPinRecoveryCode(recoveryCode)
                                    credentialKind = store.credentialKind()
                                    dialog = OwnerAdminDialog.RECOVERY_NEW
                                    recoveryCode = ""
                                } catch (e: Exception) {
                                    error = e.message ?: "Email verification failed."
                                } finally { busy = false }
                            }
                        }
                        OwnerAdminDialog.RECOVERY_NEW -> saveNewCredential()
                        OwnerAdminDialog.NONE -> Unit
                    }
                }) {
                    Text(
                        when (dialog) {
                            OwnerAdminDialog.SETUP -> "Save Security"
                            OwnerAdminDialog.UNLOCK -> "Unlock"
                            OwnerAdminDialog.CHANGE -> "Save Changes"
                            OwnerAdminDialog.RECOVERY_CODE -> "Verify Email"
                            OwnerAdminDialog.RECOVERY_NEW -> "Save New PIN / Password"
                            OwnerAdminDialog.NONE -> "OK"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { dialog = OwnerAdminDialog.NONE; resetDialogFields() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun OwnerAdminMediaPreview(
    profilePath: String?,
    coverPath: String?,
    profileScale: Float,
    coverScale: Float,
    coverOffsetX: Float,
    coverOffsetY: Float
) {
    val coverBitmap = remember(coverPath) { coverPath?.let { path -> if (File(path).isFile) BitmapFactory.decodeFile(path) else null } }
    val profileBitmap = remember(profilePath) { profilePath?.let { path -> if (File(path).isFile) BitmapFactory.decodeFile(path) else null } }

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        if (coverBitmap != null) {
            Box(Modifier.fillMaxWidth().height(112.dp).clip(MaterialTheme.shapes.medium)) {
                Image(
                    bitmap = coverBitmap.asImageBitmap(),
                    contentDescription = "Owner Admin cover photo",
                    modifier = Modifier.fillMaxSize().graphicsLayer(
                        scaleX = coverScale.coerceIn(1f, 2.5f),
                        scaleY = coverScale.coerceIn(1f, 2.5f),
                        translationX = coverOffsetX.coerceIn(-1f, 1f) * 100f,
                        translationY = coverOffsetY.coerceIn(-1f, 1f) * 60f
                    ),
                    contentScale = ContentScale.Crop
                )
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                    Text("ADD COVER PHOTO", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (profileBitmap != null) {
                Box(Modifier.size(72.dp).clip(CircleShape)) {
                    Image(
                        bitmap = profileBitmap.asImageBitmap(),
                        contentDescription = "Owner Admin profile picture",
                        modifier = Modifier.fillMaxSize().graphicsLayer(
                            scaleX = profileScale.coerceIn(1f, 2.5f),
                            scaleY = profileScale.coerceIn(1f, 2.5f)
                        ),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("👤", style = MaterialTheme.typography.headlineLarge) }
            }
            Text("OWNER ADMIN PROFILE", style = MaterialTheme.typography.titleSmall)
        }
    }
}
