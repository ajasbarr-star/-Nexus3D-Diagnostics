package com.nexus3d.diagnostics

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticEventDao {
    @Insert
    suspend fun insert(event: DiagnosticEvent): Long

    @Insert
    suspend fun insertAll(events: List<DiagnosticEvent>): List<Long>

    @Transaction
    suspend fun insertIfAbsent(event: DiagnosticEvent): Boolean {
        if (countByEventId(event.eventId) > 0) return false
        insert(event)
        return true
    }

    @Transaction
    suspend fun insertAllIfAbsent(events: List<DiagnosticEvent>): Int {
        var inserted = 0
        events.forEach { event ->
            if (countByEventId(event.eventId) == 0) {
                insert(event)
                inserted += 1
            }
        }
        return inserted
    }

    @Query("SELECT * FROM diagnostic_events ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<DiagnosticEvent>>

    @Query("SELECT COUNT(*) FROM diagnostic_events")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM diagnostic_events WHERE eventId = :eventId")
    suspend fun countByEventId(eventId: String): Int

    @Query("DELETE FROM diagnostic_events")
    suspend fun clearAll()
}
