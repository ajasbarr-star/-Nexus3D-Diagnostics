package com.nexus3d.diagnostics

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class EnrollmentCode(
    val code: String,
    val expiresAt: String,
    val expiresInSeconds: Int
)

class SupabaseAccountRepository(context: Context) {
    companion object {
        private const val SUPABASE_URL = "https://rupftghvjmalntjoesnv.supabase.co"
        private const val SUPABASE_KEY = "sb_publishable_t3-BF5smZ21bzc59TVrYIw_4MHGG3Dq"
        private const val PREFS = "nexus3d_cloud_auth"
        private const val ACCESS = "access_token"
        private const val REFRESH = "refresh_token"
        private const val EXPIRES = "expires_at"
    }

    private val preferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun hasSession(): Boolean = !preferences.getString(ACCESS, null).isNullOrBlank() &&
        !preferences.getString(REFRESH, null).isNullOrBlank()

    suspend fun signUp(email: String, password: String): String = withContext(Dispatchers.IO) {
        val result = request(
            path = "/auth/v1/signup",
            method = "POST",
            body = JSONObject().put("email", email.trim()).put("password", password)
        )
        saveSessionIfPresent(result)
        if (result.optString("access_token").isBlank()) {
            "Account created. Check your email to confirm it, then sign in here."
        } else {
            "Account created and signed in."
        }
    }

    suspend fun signIn(email: String, password: String) = withContext(Dispatchers.IO) {
        val result = request(
            path = "/auth/v1/token?grant_type=password",
            method = "POST",
            body = JSONObject().put("email", email.trim()).put("password", password)
        )
        saveSessionIfPresent(result)
        if (!hasSession()) error("Sign-in did not return a valid session. Confirm your email, then try again.")
    }

    suspend fun createEnrollmentCode(): EnrollmentCode = withContext(Dispatchers.IO) {
        var accessToken = preferences.getString(ACCESS, null)
            ?: error("Please sign in first.")
        val expiresAtMillis = preferences.getLong(EXPIRES, 0L)
        if (System.currentTimeMillis() >= expiresAtMillis - 60_000L) {
            val refreshToken = preferences.getString(REFRESH, null)
                ?: error("Your session expired. Please sign in again.")
            val refreshed = request(
                path = "/auth/v1/token?grant_type=refresh_token",
                method = "POST",
                body = JSONObject().put("refresh_token", refreshToken)
            )
            saveSessionIfPresent(refreshed)
            accessToken = preferences.getString(ACCESS, null)
                ?: error("Session refresh failed. Please sign in again.")
        }

        val result = request(
            path = "/functions/v1/create-enrollment-code",
            method = "POST",
            body = JSONObject(),
            bearer = accessToken
        )
        val code = result.optString("code")
        val expiresAt = result.optString("expiresAt")
        if (code.length != 48 || expiresAt.isBlank()) {
            error("The server returned an invalid enrollment code response.")
        }
        EnrollmentCode(code, expiresAt, result.optInt("expiresInSeconds", 600))
    }


    suspend fun fetchRegisteredDevices(): List<RegisteredDevice> = withContext(Dispatchers.IO) {
        var accessToken = preferences.getString(ACCESS, null)
            ?: error("Please sign in to view registered devices.")
        val expiresAtMillis = preferences.getLong(EXPIRES, 0L)
        if (System.currentTimeMillis() >= expiresAtMillis - 60_000L) {
            val refreshToken = preferences.getString(REFRESH, null)
                ?: error("Your session expired. Please sign in again.")
            val refreshed = request(
                path = "/auth/v1/token?grant_type=refresh_token",
                method = "POST",
                body = JSONObject().put("refresh_token", refreshToken)
            )
            saveSessionIfPresent(refreshed)
            accessToken = preferences.getString(ACCESS, null)
                ?: error("Session refresh failed. Please sign in again.")
        }

        val connection = (URL(SUPABASE_URL + "/rest/v1/devices?select=id,installation_id,device_name,manufacturer,model,android_version,app_version,app_build_number,app_version_code,status,registration_method,created_at,last_seen_at,presence_state,last_active_at&order=last_active_at.desc.nullslast,last_seen_at.desc")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("apikey", SUPABASE_KEY)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) error("Unable to load device inventory (HTTP $status).")
            val rows = JSONArray(responseText)
            (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                RegisteredDevice(
                    id = row.optString("installation_id"),
                    name = row.optString("device_name", "Nexus3D device"),
                    manufacturer = row.optString("manufacturer").takeUnless { it == "null" }.orEmpty(),
                    model = row.optString("model").takeUnless { it == "null" }.orEmpty(),
                    androidVersion = row.optString("android_version").takeUnless { it == "null" }.orEmpty(),
                    appVersion = row.optString("app_version", "unknown"),
                    buildNumber = if (row.isNull("app_build_number")) null else row.optLong("app_build_number"),
                    versionCode = if (row.isNull("app_version_code")) null else row.optLong("app_version_code"),
                    status = row.optString("status", "unknown"),
                    registrationMethod = row.optString("registration_method", "enrollment"),
                    lastSeenAt = row.optString("last_seen_at"),
                    presenceState = row.optString("presence_state", "offline"),
                    lastActiveAt = row.optString("last_active_at").takeUnless { it.isBlank() || it == "null" }
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    fun signOut() {
        preferences.edit().clear().apply()
    }

    private fun saveSessionIfPresent(json: JSONObject) {
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        if (access.isBlank() || refresh.isBlank()) return
        val expiresIn = json.optLong("expires_in", 3600L).coerceAtLeast(60L)
        preferences.edit()
            .putString(ACCESS, access)
            .putString(REFRESH, refresh)
            .putLong(EXPIRES, System.currentTimeMillis() + expiresIn * 1000L)
            .apply()
    }

    private fun request(
        path: String,
        method: String,
        body: JSONObject,
        bearer: String? = null
    ): JSONObject {
        val connection = (URL(SUPABASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("apikey", SUPABASE_KEY)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
            doOutput = true
        }

        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
            if (status !in 200..299) {
                val message = json.optString("msg").ifBlank {
                    json.optString("message").ifBlank {
                        json.optString("error_description").ifBlank {
                            json.optString("error").ifBlank { "Request failed (HTTP $status)." }
                        }
                    }
                }
                error(message)
            }
            return json
        } finally {
            connection.disconnect()
        }
    }
}

 
data class RegisteredDevice(
    val id: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val appVersion: String,
    val buildNumber: Long?,
    val versionCode: Long?,
    val status: String,
    val registrationMethod: String,
    val lastSeenAt: String,
    val presenceState: String,
    val lastActiveAt: String?
)
