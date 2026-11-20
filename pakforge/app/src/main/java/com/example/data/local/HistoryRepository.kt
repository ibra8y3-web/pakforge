package com.example.data.local

import com.example.core.engine.CompressionProfile
import com.example.core.engine.CompressionReport
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val dao: CompressionHistoryDao) {
    val historyItems: Flow<List<CompressionHistoryEntity>> = dao.observeAll()
    val totalSavedBytes: Flow<Long> = dao.observeTotalSavedBytes()

    suspend fun recordRun(report: CompressionReport, profile: CompressionProfile) {
        dao.insert(
            CompressionHistoryEntity(
                displayName = report.sourceDisplayName,
                packageId = report.packageId,
                originalBytes = report.originalBytes,
                compressedBytes = report.compressedBytes,
                savedBytes = report.savedBytes,
                savedPercent = report.savedPercent,
                elapsedMs = report.elapsedMs,
                peakHeapMb = report.peakHeapMb,
                verifiedEntriesCount = report.verifiedEntriesCount,
                corruptEntriesCount = report.corruptEntriesCount,
                resourcesArscAligned = report.resourcesArscAligned,
                profileName = profile.name,
                sha256Short = report.sha256Hex.take(16)
            )
        )
    }

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun clearAll() = dao.clearAll()
}
