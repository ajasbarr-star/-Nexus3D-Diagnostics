package com.nexus3d.diagnostics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Logout
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private enum class StartupStage { AUTH, CONSENT, SETUP_CODE, ADMIN_ID, RESET_PASSWORD, DASHBOARD }

@Composable
fun DiagnosticsStartupGate(database: DiagnosticsDatabase, repository: SupabaseAccountRepository) {
    var stage by remember { mutableStateOf(StartupStage.AUTH) }
    var registerMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("ajasbarr@gmail.com") }
    var rememberLogin by remember { mutableStateOf(repository.isRememberLoginEnabled()) }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var showSignOutConfirmation by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var setupCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<OwnerAdminStatus?>(null) }
    var busy by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

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
            if (repository.isPasswordRecoveryPending()) {
                if (repository.hasSession()) stage = StartupStage.RESET_PASSWORD
                else {
                    stage = StartupStage.AUTH
                    error = "The recovery session is unavailable. Request a new password recovery email."
                }
            } else if (repository.hasSession()) {
                verifyCurrentSession()
            } else {
                stage = StartupStage.AUTH
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

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                    OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Gmail address") }, singleLine = true)
                    OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    if (!registerMode) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = rememberLogin, onCheckedChange = {
                            rememberLogin = it
                            repository.setRememberLogin(it)
                        })
                        Text("✔ REMEMBER MY LOGIN")
                    }
                    if (registerMode) OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Confirm Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    Button(
                        enabled = !busy && email.trim().contains("@") && password.length >= 6 && (!registerMode || (confirmPassword == password && confirmPassword.isNotBlank())),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            busy = true; error = ""; message = ""
                            scope.launch {
                                try {
                                    if (registerMode) {
                                        message = repository.signUp(email, password, rememberLogin)
                                        if (repository.hasSession()) verifyCurrentSession()
                                        else {
                                            message = "Verification email sent. Open the link in Gmail, then return here and sign in."
                                            registerMode = false
                                            password = ""
                                            confirmPassword = ""
                                        }
                                    } else {
                                        repository.signIn(email, password, rememberLogin)
                                        verifyCurrentSession()
                                    }
                                } catch (e: Exception) {
                                    error = e.message ?: "Authentication failed."
                                } finally { busy = false }
                            }
                        }
                    ) { Text(if (registerMode) "Create Owner Admin Account" else "Sign In") }
                    if (!registerMode) TextButton(
                        enabled = !busy && email.trim().equals("ajasbarr@gmail.com", ignoreCase = true),
                        onClick = {
                            busy = true; error = ""; message = ""
                            scope.launch {
                                try {
                                    message = repository.requestPasswordReset(email)
                                } catch (e: Exception) {
                                    error = e.message ?: "Unable to request password recovery."
                                } finally { busy = false }
                            }
                        }
                    ) { Text("RESET PASSWORD") }
                    TextButton(onClick = { registerMode = !registerMode; error = ""; message = ""; password = ""; confirmPassword = "" }, enabled = !busy) {
                        Text(if (registerMode) "Already registered? Sign in" else "First-time Owner Admin? Register")
                    }
                    Text("Registration is restricted by the server to the configured Owner Admin Gmail and the one-time setup code.", style = MaterialTheme.typography.bodySmall)
                    if (!registerMode) Text("Password recovery email is sent only to the verified Owner Admin address.", style = MaterialTheme.typography.bodySmall)
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
                StartupStage.RESET_PASSWORD -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("RESET OWNER ADMIN PASSWORD", style = MaterialTheme.typography.headlineSmall)
                    Text("Verified recovery link received. Create a new password for ajasbarr@gmail.com.")
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("New password (10+ characters)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = confirmNewPassword,
                        onValueChange = { confirmNewPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Confirm new password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    Button(
                        enabled = !busy && newPassword.length >= 10 && newPassword == confirmNewPassword,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            busy = true; error = ""; message = ""
                            scope.launch {
                                try {
                                    repository.updatePassword(newPassword)
                                    repository.signOut()
                                    newPassword = ""; confirmNewPassword = ""
                                    stage = StartupStage.AUTH
                                    message = "Password updated. Sign in using your new password."
                                } catch (e: Exception) {
                                    error = e.message ?: "Unable to update password."
                                } finally { busy = false }
                            }
                        }
                    ) { Text("SAVE NEW PASSWORD") }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(status?.adminId ?: "Unavailable", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        IconButton(enabled = status?.adminId?.isNotBlank() == true, onClick = {
                            status?.adminId?.let { clipboard.setText(AnnotatedString(it)); message = "Permanent Admin Unique ID copied." }
                        }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy Permanent Admin Unique ID") }
                    }
                    Text("This ID belongs to the Diagnostics Owner Admin account and is separate from all Nexus3D Editor Device IDs.")
                    Button(enabled = status?.adminId?.isNotBlank() == true, onClick = { stage = StartupStage.DASHBOARD }) { Text("Continue to Main Dashboard") }
                }
                StartupStage.DASHBOARD -> Box(Modifier.fillMaxSize()) {
                    DiagnosticsHome(database, repository, status, onSignOut = {
                        clearSessionSafely()
                        status = null
                        stage = StartupStage.AUTH
                        message = "Signed out securely."
                        error = ""
                    })
                    if (showSignOutConfirmation) AlertDialog(
                        onDismissRequest = { showSignOutConfirmation = false },
                        title = { Text("Sign out?") },
                        text = { Text("Your current Diagnostics login session will be cleared. Owner Admin registration and Unique ID will remain unchanged.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showSignOutConfirmation = false
                                clearSessionSafely()
                                status = null
                                stage = StartupStage.AUTH
                                message = "Signed out securely."
                                error = ""
                            }) { Text("Sign Out") }
                        },
                        dismissButton = { TextButton(onClick = { showSignOutConfirmation = false }) { Text("Cancel") } }
                    )
                    IconButton(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        onClick = { showSignOutConfirmation = true }
                    ) { Icon(Icons.Default.Logout, contentDescription = "Sign Out") }
                }
            }
        }
    }
}
