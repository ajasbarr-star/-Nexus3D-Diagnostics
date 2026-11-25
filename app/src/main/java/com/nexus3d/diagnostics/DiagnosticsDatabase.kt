package com.nexus3d.diagnostics

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DiagnosticEvent::class], version = 1, exportSchema = true)
abstract class DiagnosticsDatabase : RoomDatabase() {
    abstract fun eventDao(): DiagnosticEventDao
}
