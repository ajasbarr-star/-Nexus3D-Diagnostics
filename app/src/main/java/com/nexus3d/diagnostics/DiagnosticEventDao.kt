package com.nexus3d.diagnostics

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticEventDao {
    @Insert
    suspend fun insert(event: DiagnosticEvent): Long

    @Query("SELECT * FROM diagnostic_events ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<DiagnosticEvent>>

    @Query("SELECT COUNT(*) FROM diagnostic_events")
    suspend fun count(): Int

    @Query("DELETE FROM diagnostic_events")
    suspend fun clearAll()
}
