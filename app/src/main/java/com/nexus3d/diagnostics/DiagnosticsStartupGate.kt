package com.nexus3d.diagnostics

import android.graphics.BitmapFactory
import android.net.Uri

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private enum class StartupStage { AUTH, CONSENT, SETUP_CODE, ADMIN_ID, DASHBOARD }

@Composable
fun DiagnosticsStartupGate(database: DiagnosticsDatabase, repository: SupabaseAccountRepository) {
    var stage by remember { mutableStateOf(StartupStage.AUTH) }
    var registerMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(repository.shouldRememberSession()) }
    var passwordResetMode by remember { mutableStateOf(false) }
    var changePasswordMode by remember { mutableStateOf(false) }
    var setupCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<OwnerAdminStatus?>(null) }
    var busy by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val appearancePrefs = remember { context.getSharedPreferences("nexus3d_diagnostics_appearance", 0) }
    val appearanceScope = if (stage == StartupStage.AUTH) "login" else "admin"
    val startupBackground = parseHexColor(appearancePrefs.getString("${appearanceScope}_background_hex", appearancePrefs.getString("background_hex", "#242629")), Color(0xFF242629))
    val startupSurface = parseHexColor(appearancePrefs.getString("${appearanceScope}_surface_hex", appearancePrefs.getString("surface_hex", "#2D3034")), Color(0xFF2D3034))
    val startupText = parseHexColor(appearancePrefs.getString("${appearanceScope}_text_hex", appearancePrefs.getString("text_hex", "#E8EAED")), Color(0xFFE8EAED))
    val startupBorder = parseHexColor(appearancePrefs.getString("${appearanceScope}_border_hex", appearancePrefs.getString("border_hex", "#60666D")), Color(0xFF60666D))
    val startupAccent = parseHexColor(appearancePrefs.getString("${appearanceScope}_primary_hex", appearancePrefs.getString("primary_hex", "#80CBC4")), Color(0xFF80CBC4))
    val startupButton = parseHexColor(appearancePrefs.getString("${appearanceScope}_button_hex", appearancePrefs.getString("button_hex", "#80CBC4")), Color(0xFF80CBC4))
    val startupIcon = parseHexColor(appearancePrefs.getString("${appearanceScope}_icon_hex", appearancePrefs.getString("icon_hex", "#80CBC4")), Color(0xFF80CBC4))
    val startupOpacity = appearancePrefs.getFloat("${appearanceScope}_panel_opacity", appearancePrefs.getFloat("panel_opacity", 1f)).coerceIn(0.25f, 1f)
    val startupBlur = appearancePrefs.getFloat("${appearanceScope}_blur", appearancePrefs.getFloat("blur", 0f)).coerceIn(0f, 24f)
    val startupBrightness = appearancePrefs.getFloat("${appearanceScope}_brightness", appearancePrefs.getFloat("brightness", 1f)).coerceIn(0.4f, 1.5f)
    val startupGradient = appearancePrefs.getBoolean("${appearanceScope}_gradient_enabled", appearancePrefs.getBoolean("gradient_enabled", false))
    val startupGradientEnd = parseHexColor(appearancePrefs.getString("${appearanceScope}_gradient_end_hex", appearancePrefs.getString("gradient_end_hex", "#101923")), Color(0xFF101923))
    val startupImageUri = appearancePrefs.getString("${appearanceScope}_background_image_uri", appearancePrefs.getString("background_image_uri", "")) ?: ""
    val startupColors = darkColorScheme(
        primary = startupButton,
        secondary = startupAccent,
        tertiary = startupIcon,
        background = startupBackground,
        surface = startupSurface.copy(alpha = startupOpacity),
        onSurface = startupText,
        onBackground = startupText,
        outline = startupBorder
    )

    fun clearSessionSafely() {
        runCatching { repository.signOut() }
    }

    fun routeFor(owner: OwnerAdminStatus) {
        status = owner
        when {
            !owner.emailVerified || !owner.eligible -> {
                clearSessionSafely()
                stage = StartupStage.AUTH
                error = if (!owner.emailVerified) "Verify your Gmail address using the email link, then sign in again."
                    else "This Gmail account is not authorized as the permanent Owner Admin."
            }
            !owner.consentAccepted -> stage = StartupStage.CONSENT
            owner.registered && owner.status == "active" && !owner.adminId.isNullOrBlank() -> stage = StartupStage.DASHBOARD
            owner.registered -> {
                clearSessionSafely()
                stage = StartupStage.AUTH
                error = "The registered Owner Admin account is locked or inactive. Dashboard access is blocked."
            }
            else -> stage = StartupStage.SETUP_CODE
        }
    }

    suspend fun verifyCurrentSession() {
        routeFor(repository.fetchOwnerAdminStatus())
    }

    LaunchedEffect(Unit) {
        try {
            when {
                repository.hasSession() && repository.isPasswordRecoveryPending() -> {
                    passwordResetMode = true
                    stage = StartupStage.AUTH
                }
                repository.hasSession() && repository.shouldRememberSession() -> verifyCurrentSession()
                else -> {
                    if (repository.hasSession()) clearSessionSafely()
                    stage = StartupStage.AUTH
                }
            }
        } catch (e: Exception) {
            clearSessionSafely()
            stage = StartupStage.AUTH
            error = "Saved session could not be opened or verified. Please sign in again. " +
                (e.message ?: "")
        } finally {
            busy = false
        }
    }

    BackHandler(enabled = stage == StartupStage.CONSENT || stage == StartupStage.SETUP_CODE || stage == StartupStage.ADMIN_ID) {
        clearSessionSafely()
        status = null
        stage = StartupStage.AUTH
        message = ""
        error = "Owner Admin verification must be completed before entering Diagnostics."
    }

    MaterialTheme(colorScheme = startupColors) {
        Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
            Box(
                Modifier.fillMaxSize().background(
                    if (startupGradient) Brush.linearGradient(listOf(startupBackground, startupGradientEnd))
                    else androidx.compose.ui.graphics.SolidColor(startupBackground)
                )
            ) {
                val startupBitmap = remember(startupImageUri) {
                    if (startupImageUri.isBlank()) null else runCatching {
                        context.contentResolver.openInputStream(Uri.parse(startupImageUri))?.use { stream ->
                            BitmapFactory.decodeStream(stream)?.asImageBitmap()
                        }
                    }.getOrNull()
                }
                if (startupBitmap != null) {
                    Image(
                        bitmap = startupBitmap,
                        contentDescription = "Custom Diagnostics background",
                        modifier = Modifier.fillMaxSize().blur(startupBlur.dp),
                        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply {
                            setToScale(startupBrightness, startupBrightness, startupBrightness, 1f)
                        }),
                        contentScale = ContentScale.Crop
                    )
                }
                when (stage) {
                StartupStage.AUTH -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Spacer(Modifier.padding(top = 12.dp))
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = "Nexus3D Diagnostics logo",
                                modifier = Modifier.size(72.dp)
                            )
                            Text("Nexus3D Diagnostics", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                if (registerMode) "OWNER ADMIN REGISTRATION" else "OWNER ADMIN LOGIN",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "SECURE CENTRAL RECEIVER",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Text("Only the verified, permanently registered Owner Admin can access the Central Receiver.")
                    if (passwordResetMode) {
                        Text("RESET PASSWORD", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Enter a new password for your verified Owner Admin account.")
                        OutlinedTextField(value = newPassword, onValueChange = { newPassword = it }, modifier = Modifier.fillMaxWidth(), label = { Text("New Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                        OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Confirm New Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                        Button(
                            enabled = !busy && newPassword.length >= 6 && newPassword == confirmPassword,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                busy = true; error = ""; message = ""
                                scope.launch {
                                    try {
                                        repository.updatePassword(newPassword)
                                        passwordResetMode = false
                                        newPassword = ""; confirmPassword = ""
                                        message = "Password changed successfully. Verifying Owner Admin access…"
                                        verifyCurrentSession()
                                    } catch (e: Exception) {
                                        error = e.message ?: "Unable to update password."
                                    } finally { busy = false }
                                }
                            }
                        ) { Text("Save New Password") }
                    } else {
                        OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Gmail address") }, singleLine = true)
                        OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                        if (registerMode) {
                            OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Confirm Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = rememberMe, onCheckedChange = { rememberMe = it })
                                Text("✓ REMEMBER ME")
                            }
                        }
                        Button(
                            enabled = !busy && email.trim().contains("@") && password.length >= 6 && (!registerMode || (confirmPassword == password && confirmPassword.isNotBlank())),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                busy = true; error = ""; message = ""
                                scope.launch {
                                    try {
                                        if (registerMode) {
                                            message = repository.signUp(email, password)
                                            if (repository.hasSession()) verifyCurrentSession()
                                            else {
                                                message = "Verification email sent. Open the link in Gmail, then return here and sign in."
                                                registerMode = false
                                                password = ""
                                                confirmPassword = ""
                                            }
                                        } else {
                                            repository.signIn(email, password, rememberMe)
                                            verifyCurrentSession()
                                        }
                                    } catch (e: Exception) {
                                        error = e.message ?: "Authentication failed."
                                    } finally { busy = false }
                                }
                            }
                        ) { Text(if (registerMode) "Create Owner Admin Account" else "Sign In") }
                        if (!registerMode) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(enabled = !busy && email.trim().contains("@"), onClick = {
                                    busy = true; error = ""; message = ""
                                    scope.launch {
                                        try { message = repository.requestPasswordReset(email) }
                                        catch (e: Exception) { error = e.message ?: "Unable to request password reset." }
                                        finally { busy = false }
                                    }
                                }) { Text("RESET PASSWORD") }
                                TextButton(enabled = !busy && email.trim().contains("@") && password.length >= 6, onClick = {
                                    changePasswordMode = !changePasswordMode
                                    newPassword = ""; confirmPassword = ""; error = ""; message = ""
                                }) { Text(if (changePasswordMode) "CANCEL CHANGE" else "CHANGE PASSWORD") }
                            }
                            if (changePasswordMode) {
                                Text("Confirm your current password above, then choose a new password.")
                                OutlinedTextField(value = newPassword, onValueChange = { newPassword = it }, modifier = Modifier.fillMaxWidth(), label = { Text("New Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                                OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Confirm New Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                                Button(enabled = !busy && newPassword.length >= 6 && newPassword == confirmPassword, modifier = Modifier.fillMaxWidth(), onClick = {
                                    busy = true; error = ""; message = ""
                                    scope.launch {
                                        try {
                                            repository.signIn(email, password, rememberMe)
                                            repository.updatePassword(newPassword)
                                            changePasswordMode = false
                                            newPassword = ""; confirmPassword = ""; password = ""
                                            message = "Password changed successfully. Please sign in with your new password."
                                            repository.signOut()
                                        } catch (e: Exception) { error = e.message ?: "Unable to change password." }
                                        finally { busy = false }
                                    }
                                }) { Text("UPDATE PASSWORD") }
                            }
                        }
                        TextButton(onClick = { registerMode = !registerMode; error = ""; message = ""; password = ""; confirmPassword = ""; changePasswordMode = false }, enabled = !busy) {
                            Text(if (registerMode) "Already registered? Sign in" else "First-time Owner Admin? Register")
                        }
                        Text("Registration is restricted by the server to the configured Owner Admin Gmail and the one-time setup code.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                    if (busy) CircularProgressIndicator()
                }
                StartupStage.CONSENT -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Nexus3D Diagnostics — Explicit Consent", style = MaterialTheme.typography.headlineSmall)
                    Text("Gmail account: " + (email.ifBlank { "verified Owner Admin account" }))
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Consent for Diagnostics access and processing", style = MaterialTheme.typography.titleMedium)
                            Text("I agree that Nexus3D Diagnostics may use my verified Gmail identity to authenticate my Owner Admin account and process diagnostic information submitted by my Nexus3D Editor installations for the Central Receiver.")
                            Text("This consent does NOT grant Nexus3D Diagnostics access to read, scan, or manage my Gmail inbox or messages.")
                            Text("Consent version: 2026-11-27-v1", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                        busy = true; error = ""
                        scope.launch {
                            try {
                                repository.submitOwnerAdminConsent()
                                val fresh = repository.fetchOwnerAdminStatus()
                                status = fresh
                                if (fresh.registered && fresh.status == "active") stage = StartupStage.DASHBOARD
                                else stage = StartupStage.SETUP_CODE
                            } catch (e: Exception) { error = e.message ?: "Unable to save consent securely." }
                            finally { busy = false }
                        }
                    }) { Text("I AGREE") }
                    OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                        clearSessionSafely(); status = null; stage = StartupStage.AUTH
                        message = "Consent declined. Diagnostics access is not enabled."
                    }) { Text("I DO NOT AGREE") }
                    if (busy) CircularProgressIndicator()
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                }
                StartupStage.SETUP_CODE -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Complete Owner Admin Registration", style = MaterialTheme.typography.headlineSmall)
                    Text("Gmail verification and consent are complete. Enter the one-time Admin Setup Code to claim the single permanent Owner Admin slot.")
                    OutlinedTextField(value = setupCode, onValueChange = { setupCode = it }, modifier = Modifier.fillMaxWidth(), label = { Text("One-Time Admin Setup Code") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    Button(enabled = !busy && setupCode.trim().length >= 20, modifier = Modifier.fillMaxWidth(), onClick = {
                        busy = true; error = ""
                        scope.launch {
                            try {
                                val registered = repository.registerOwnerAdmin(setupCode)
                                setupCode = ""
                                status = registered
                                if (registered.registered && registered.status == "active" && !registered.adminId.isNullOrBlank()) stage = StartupStage.ADMIN_ID
                                else error = "The server did not confirm an active permanent Owner Admin registration."
                            } catch (e: Exception) { error = e.message ?: "Owner Admin registration failed." }
                            finally { busy = false }
                        }
                    }) { Text("Claim Permanent Owner Admin ID") }
                    TextButton(enabled = !busy, onClick = { clearSessionSafely(); stage = StartupStage.AUTH; setupCode = "" }) { Text("Cancel and Sign Out") }
                    if (busy) CircularProgressIndicator()
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                }
                StartupStage.ADMIN_ID -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Owner Admin Registration Complete", style = MaterialTheme.typography.headlineSmall)
                    Text("Permanent Admin Unique ID", style = MaterialTheme.typography.titleMedium)
                    Text(status?.adminId ?: "Unavailable", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text("This ID belongs to the Diagnostics Owner Admin account and is separate from all Nexus3D Editor Device IDs.")
                    Button(enabled = status?.adminId?.isNotBlank() == true, onClick = { stage = StartupStage.DASHBOARD }) { Text("Continue to Main Dashboard") }
                }
                StartupStage.DASHBOARD -> Box(Modifier.fillMaxSize()) {
                    DiagnosticsHome(database, repository, onSignOut = {
                        clearSessionSafely()
                        status = null
                        stage = StartupStage.AUTH
                        message = "Signed out securely."
                        error = ""
                    })
                    TextButton(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        onClick = {
                            clearSessionSafely()
                            status = null
                            stage = StartupStage.AUTH
                            message = "Signed out securely."
                            error = ""
                        }
                    ) { Text("Sign Out") }
                }
                }
            }
        }
    }
}
