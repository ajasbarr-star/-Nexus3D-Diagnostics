package com.nexus3d.diagnostics

import androidx.room.ColumnInfo
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
    @ColumnInfo(defaultValue = "''") val eventId: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "'legacy-local'") val deviceId: String = "local-bridge-unknown",
    @ColumnInfo(defaultValue = "0") val sequence: Long = 0,
    @ColumnInfo(defaultValue = "1") val schemaVersion: Int = 1
)
