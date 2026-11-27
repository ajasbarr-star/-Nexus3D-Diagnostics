package com.nexus3d.diagnostics

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: DiagnosticEvent): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<DiagnosticEvent>): List<Long>

    @Query("SELECT * FROM diagnostic_events ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<DiagnosticEvent>>

    @Query("SELECT COUNT(*) FROM diagnostic_events")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM diagnostic_events WHERE eventId = :eventId")
    suspend fun countByEventId(eventId: String): Int

    @Query("DELETE FROM diagnostic_events")
    suspend fun clearAll()
}
