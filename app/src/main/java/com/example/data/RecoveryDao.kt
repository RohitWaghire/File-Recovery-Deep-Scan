package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecoveryDao {

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAllScanHistory(): Flow<List<ScanHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanHistory(history: ScanHistory): Long

    @Update
    suspend fun updateScanHistory(history: ScanHistory)

    @Query("SELECT * FROM recovered_files ORDER BY timestamp DESC")
    fun getAllRecoveredFiles(): Flow<List<RecoveredFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecoveredFile(file: RecoveredFile): Long

    @Query("DELETE FROM recovered_files WHERE id = :id")
    suspend fun deleteRecoveredFile(id: Long)

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestScanHistory(): ScanHistory?

    @Query("DELETE FROM scan_history")
    suspend fun clearScanHistory()
}
