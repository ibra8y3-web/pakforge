package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "compression_history")
data class CompressionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val displayName: String,
    val packageId: String?,
    val originalBytes: Long,
    val compressedBytes: Long,
    val savedBytes: Long,
    val savedPercent: Double,
    val elapsedMs: Long,
    val peakHeapMb: Int,
    val verifiedEntriesCount: Int,
    val corruptEntriesCount: Int,
    val resourcesArscAligned: Boolean,
    val profileName: String,
    val sha256Short: String,
    val timestampMs: Long = System.currentTimeMillis()
)
