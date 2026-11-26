package com.nexus3d.diagnostics

import android.content.ContentProvider
import android.content.ContentValues
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

/**
 * Private-by-identity IPC endpoint for the Nexus3D Editor Test APK.
 * Every call validates the Binder caller UID against the Editor package.
 */
class DiagnosticBridgeProvider : ContentProvider() {
    private val database by lazy {
        Room.databaseBuilder(
            requireNotNull(context).applicationContext,
            DiagnosticsDatabase::class.java,
            "nexus3d-diagnostics.db"
        ).build()
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (method != "ingestBatch") throw IllegalArgumentException("Unsupported bridge method")
        val appContext = requireNotNull(context)
        val callerUid = Binder.getCallingUid()
        val callerPackages = appContext.packageManager.getPackagesForUid(callerUid).orEmpty()
        if (!callerPackages.contains(ALLOWED_EDITOR_PACKAGE)) {
            throw SecurityException("Only the Nexus3D Editor Test APK may submit diagnostic events")
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
                createdAtEpochMs = timestamp
            )
        }

        val ids = runBlocking {
            withContext(Dispatchers.IO) {
                database.eventDao().insertAll(accepted)
            }
        }
        return Bundle().apply {
            putBoolean("ok", true)
            putInt("accepted", ids.size)
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
        private const val MAX_BATCH_SIZE = 20
        private const val MAX_PAYLOAD_CHARS = 128 * 1024
        private const val MAX_DETAILS_CHARS = 2400
        private val ALLOWED_SOURCES = setOf("RDS_LOGCAT", "RIT_TRACE")
        private val ALLOWED_SEVERITIES = setOf("VERBOSE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL")
    }
}
