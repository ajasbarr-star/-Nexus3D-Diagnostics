package com.nexus3d.diagnostics

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "diagnostic_events")
data class DiagnosticEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val severity: String,
    val title: String,
    val details: String,
    val sessionId: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val eventId: String = UUID.randomUUID().toString(),
    val deviceId: String = "local-bridge-unknown",
    val sequence: Long = 0,
    val schemaVersion: Int = 1
)
