package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CompressionHistoryDao {
    @Query("SELECT * FROM compression_history ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<CompressionHistoryEntity>>

    @Query("SELECT COALESCE(SUM(savedBytes), 0) FROM compression_history")
    fun observeTotalSavedBytes(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CompressionHistoryEntity)

    @Query("DELETE FROM compression_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM compression_history")
    suspend fun clearAll()
}
