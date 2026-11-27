package com.nexus3d.diagnostics

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DiagnosticEvent::class], version = 3, exportSchema = true)
abstract class DiagnosticsDatabase : RoomDatabase() {
    abstract fun eventDao(): DiagnosticEventDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE diagnostic_events ADD COLUMN eventId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE diagnostic_events ADD COLUMN deviceId TEXT NOT NULL DEFAULT 'legacy-local'"
                )
                db.execSQL(
                    "ALTER TABLE diagnostic_events ADD COLUMN sequence INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE diagnostic_events ADD COLUMN schemaVersion INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "UPDATE diagnostic_events SET eventId = 'legacy-' || id, sequence = id"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Keep every historical row. Give duplicate historical event IDs
                // deterministic per-row aliases before enforcing uniqueness.
                db.execSQL(
                    """
                    UPDATE diagnostic_events
                    SET eventId = eventId || '-legacy-' || id
                    WHERE eventId IN (
                        SELECT eventId
                        FROM diagnostic_events
                        GROUP BY eventId
                        HAVING COUNT(*) > 1
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_diagnostic_events_eventId ON diagnostic_events(eventId)"
                )
            }
        }
    }
}
