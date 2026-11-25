package com.nexus3d.diagnostics

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diagnostic_events")
data class DiagnosticEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val severity: String,
    val title: String,
    val details: String,
    val sessionId: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
