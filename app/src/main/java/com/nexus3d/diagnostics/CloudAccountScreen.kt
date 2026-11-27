package com.nexus3d.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

@Composable
fun CloudAccountScreen(
    repository: SupabaseAccountRepository,
    onBack: () -> Unit,
    inline: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signedIn by remember { mutableStateOf(repository.hasSession()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var enrollment by remember { mutableStateOf<EnrollmentCode?>(null) }
    var secondsLeft by remember { mutableIntStateOf(0) }

    LaunchedEffect(enrollment?.expiresAt) {
        val expiry = enrollment?.expiresAt ?: return@LaunchedEffect
        while (true) {
            secondsLeft = runCatching {
                Duration.between(Instant.now(), Instant.parse(expiry)).seconds
                    .coerceAtLeast(0).toInt()
            }.getOrDefault(0)
            if (secondsLeft <= 0) break
            delay(1000)
        }
    }

    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (inline) Modifier else Modifier.fillMaxSize())
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!inline) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onBack) { Text("← Back") }
                Text("CLOUD ACCOUNT", style = MaterialTheme.typography.labelLarge)
            }
            Text("Nexus3D Cloud Diagnostics", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Sign in to your Diagnostics workspace to pair Nexus3D installations. Enrollment codes expire after 10 minutes.",
                style = MaterialTheme.typography.bodyMedium
            )

            if (!signedIn) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email address") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = !busy && email.contains("@") && password.length >= 6,
                        onClick = {
                            busy = true
                            errorMessage = ""
                            message = ""
                            scope.launch {
                                try {
                                    repository.signIn(email, password)
                                    signedIn = true
                                    message = "Signed in successfully."
                                } catch (error: Exception) {
                                    errorMessage = error.message ?: "Unable to sign in."
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) { Text("Sign In") }
                    OutlinedButton(
                        enabled = !busy && email.contains("@") && password.length >= 6,
                        onClick = {
                            busy = true
                            errorMessage = ""
                            message = ""
                            scope.launch {
                                try {
                                    message = repository.signUp(email, password)
                                    signedIn = repository.hasSession()
                                } catch (error: Exception) {
                                    errorMessage = error.message ?: "Unable to create account."
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) { Text("Create Account") }
                }
                Text(
                    "Use an email address you can access. Password must be at least 6 characters.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Workspace connected", style = MaterialTheme.typography.titleMedium)
                        Text("This Diagnostics app is signed in. Generate a one-time code to pair your Nexus3D APK.")
                        Button(
                            enabled = !busy,
                            onClick = {
                                busy = true
                                errorMessage = ""
                                message = ""
                                scope.launch {
                                    try {
                                        enrollment = repository.createEnrollmentCode()
                                        secondsLeft = enrollment?.expiresInSeconds ?: 600
                                        message = "Enrollment code generated. Enter it in Nexus3D APK."
                                    } catch (error: Exception) {
                                        errorMessage = error.message ?: "Unable to generate enrollment code."
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        ) { Text("＋  Generate Enrollment Code") }
                    }
                }

                enrollment?.let { code ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("ADD DEVICE", style = MaterialTheme.typography.titleLarge)
                            Text(
                                if (secondsLeft > 0) "Expires in %d:%02d".format(secondsLeft / 60, secondsLeft % 60)
                                else "This code has expired. Generate a new one.",
                                color = if (secondsLeft > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Text(
                                code.code,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Button(
                                enabled = secondsLeft > 0,
                                onClick = { clipboard.setText(AnnotatedString(code.code)); message = "Enrollment code copied." }
                            ) { Text("Copy Code") }
                        }
                    }
                }

                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        repository.signOut()
                        signedIn = false
                        enrollment = null
                        message = "Signed out."
                    }
                ) { Text("Sign Out") }
            }

            if (busy) CircularProgressIndicator()
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
            if (errorMessage.isNotBlank()) Text(errorMessage, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }
    }
}
