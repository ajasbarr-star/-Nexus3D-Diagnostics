package com.nexus3d.diagnostics

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Private-by-identity IPC endpoint for the Nexus3D Editor Test APK.
 * Every call validates the Binder caller UID, exact Editor package, and pinned signing certificate.
 */
class DiagnosticBridgeProvider : ContentProvider() {
    private val database by lazy {
        Room.databaseBuilder(
            requireNotNull(context).applicationContext,
            DiagnosticsDatabase::class.java,
            "nexus3d-diagnostics.db"
        ).addMigrations(
            DiagnosticsDatabase.MIGRATION_1_2,
            DiagnosticsDatabase.MIGRATION_2_3
        )
            .build()
    }

    override fun onCreate(): Boolean = true

    @Synchronized
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (method != "ingestBatch") throw IllegalArgumentException("Unsupported bridge method")
        val appContext = requireNotNull(context)
        val callerUid = Binder.getCallingUid()
        val callerPackages = appContext.packageManager.getPackagesForUid(callerUid).orEmpty()
        if (!callerPackages.contains(ALLOWED_EDITOR_PACKAGE) ||
            !hasTrustedEditorSignature(appContext.packageManager, ALLOWED_EDITOR_PACKAGE)
        ) {
            throw SecurityException("Only the signed Nexus3D Editor Test APK may submit diagnostic events")
        }

        val payload = extras?.getString("eventsJson")
            ?: throw IllegalArgumentException("Missing diagnostic event payload")
        if (payload.length > MAX_PAYLOAD_CHARS) {
            throw IllegalArgumentException("Diagnostic batch exceeds the payload limit")
        }

        val array = JSONArray(payload)
        if (array.length() !in 1..MAX_BATCH_SIZE) {
            throw IllegalArgumentException("Diagnostic batch must contain 1-$MAX_BATCH_SIZE events")
        }

        val accepted = ArrayList<DiagnosticEvent>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
                ?: throw IllegalArgumentException("Invalid event at index $index")
            val source = item.optString("source")
            val severity = item.optString("severity").uppercase()
            val title = item.optString("title")
            val details = item.optString("details")
            val sessionId = item.optString("sessionId").takeIf { it.isNotBlank() }
            val timestamp = item.optLong("timestampEpochMs", System.currentTimeMillis())
            val eventId = item.optString("eventId").takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Missing event ID")
            val deviceId = item.optString("deviceId").takeIf { it.isNotBlank() }
                ?: "local-bridge-unknown"
            val sequence = item.optLong("sequence", timestamp)
            val schemaVersion = item.optInt("schemaVersion", 1)

            if (!isUuid(eventId)) throw IllegalArgumentException("Invalid event ID")
            if (deviceId != "local-bridge-unknown" && !isUuid(deviceId)) {
                throw IllegalArgumentException("Invalid device ID")
            }
            if (sequence < 0L) throw IllegalArgumentException("Invalid event sequence")
            if (schemaVersion != 1) throw IllegalArgumentException("Unsupported event schema version")
            if (source !in ALLOWED_SOURCES) throw IllegalArgumentException("Unsupported event source")
            if (severity !in ALLOWED_SEVERITIES) throw IllegalArgumentException("Unsupported event severity")
            if (title.isBlank() || title.length > 240) throw IllegalArgumentException("Invalid event title")
            if (details.length > MAX_DETAILS_CHARS) throw IllegalArgumentException("Event details exceed the limit")
            if (sessionId != null && sessionId.length > 128) throw IllegalArgumentException("Invalid session ID")

            accepted += DiagnosticEvent(
                source = source,
                severity = severity,
                title = title,
                details = details,
                sessionId = sessionId,
                createdAtEpochMs = timestamp,
                eventId = eventId,
                deviceId = deviceId,
                sequence = sequence,
                schemaVersion = schemaVersion
            )
        }

        // Idempotent delivery: retries of an already-stored event are acknowledged,
        // but never inserted a second time. The synchronized provider call serializes
        // this check-and-insert sequence within the Diagnostics process.
        val acknowledgedEventIds = ArrayList<String>(accepted.size)
        var inserted = 0
        var duplicates = 0
        runBlocking {
            withContext(Dispatchers.IO) {
                accepted.forEach { event ->
                    if (database.eventDao().insertIfAbsent(event)) {
                        inserted += 1
                    } else {
                        duplicates += 1
                    }
                    acknowledgedEventIds.add(event.eventId)
                }
            }
        }
        return Bundle().apply {
            putBoolean("ok", true)
            putInt("accepted", accepted.size)
            putInt("inserted", inserted)
            putInt("duplicates", duplicates)
            putStringArrayList("acknowledgedEventIds", acknowledgedEventIds)
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? =
        throw UnsupportedOperationException("Query is not supported")

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Insert is not supported")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Delete is not supported")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Update is not supported")

    companion object {
        private const val ALLOWED_EDITOR_PACKAGE = "com.nexus3d.studio.test"
        // SHA-256 signing-certificate digest from the verified Nexus3D Editor Test APK.
        // Update only when the Editor Test APK signing key is intentionally rotated.
        private const val ALLOWED_EDITOR_CERT_SHA256 = "99096295753f31b52fb01be570ab377ea61f4a64d40cd8d32e7e36ebc285472b"
        private const val MAX_BATCH_SIZE = 20
        private const val MAX_PAYLOAD_CHARS = 128 * 1024
        private const val MAX_DETAILS_CHARS = 24000
        private val ALLOWED_SOURCES = setOf("RDS_LOGCAT", "RIT_TRACE", "RUNTIME", "WEBGL_SHADER", "SHADOW_MAPS", "MATERIALS_TEXTURES", "DFI_CORE", "MESH_DFI", "ROOT_CAUSE", "SOURCE_AUDIT", "REPORTS")

        private fun hasTrustedEditorSignature(packageManager: PackageManager, packageName: String): Boolean {
            return runCatching {
                val expectedDigest = ALLOWED_EDITOR_CERT_SHA256.chunked(2)
                    .map { it.toInt(16).toByte() }.toByteArray()
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    packageManager.hasSigningCertificate(
                        packageName,
                        expectedDigest,
                        PackageManager.CERT_INPUT_SHA256
                    )
                } else {
                    @Suppress("DEPRECATION")
                    val signatures = packageManager.getPackageInfo(
                        packageName,
                        PackageManager.GET_SIGNATURES
                    ).signatures.orEmpty()
                    signatures.any { signature ->
                        val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                        digest.contentEquals(expectedDigest)
                    }
                }
            }.getOrDefault(false)
        }

        private fun isUuid(value: String): Boolean =
            value.matches(Regex("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) &&
                runCatching { java.util.UUID.fromString(value) }.isSuccess
        private val ALLOWED_SEVERITIES = setOf("VERBOSE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL")
    }
}
