package com.example.core.engine

import android.graphics.drawable.Drawable
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

enum class CompressionProfile {
    ZERO_RISK_BALANCED,
    ULTRA_SHRINK,
    LOW_RESOURCE_ECO
}

enum class PackageCategory {
    DEX_BYTECODE,
    NATIVE_LIBS,
    RESOURCES_ARSC,
    MANIFEST_XML,
    MEDIA_ASSETS,
    METADATA_SIGNING,
    OTHER
}

enum class EntryStrategy {
    DEFLATE_MAX,
    DEFLATE_BALANCED,
    PRESERVE_STORED_4ALIGN,
    PRESERVE_STORED_4KB,
    BYPASS_HIGH_ENTROPY,
    PRUNE_SAFE_DEBUG,
    STRIP_UNUSED_ABI
}

enum class InstallIdentityMode {
    SAME_PACKAGE,
    CLONE_NEW_PACKAGE
}

data class CompressionConfig(
    val profile: CompressionProfile = CompressionProfile.ZERO_RISK_BALANCED,
    val enforceResourcesArscAlign: Boolean = true,
    val smartEntropyBypass: Boolean = true,
    val pruneDebugArtifacts: Boolean = true,
    val adaptiveRamThrottle: Boolean = true,
    val allowedAbis: Set<String> = emptySet(),
    val installIdentityMode: InstallIdentityMode = InstallIdentityMode.SAME_PACKAGE,
    val cloneSuffix: String = "pkf",
    val mergeSplitsIntoUniversal: Boolean = false,
    val stripSharedUserId: Boolean = true,
    val patchStaticLibCertDigest: Boolean = true,
    val bundleCompanionStaticLibs: Boolean = true,
    val pruneUnusedLocales: Boolean = false,
    val keptLocales: Set<String> = setOf("en", "ar"),
    val optimizeLargeImages: Boolean = false,
    val customCloneLabel: String = "",
    val tintCloneIcon: Boolean = true,
    val preferShizukuInstaller: Boolean = false
)

data class EntryAnalysis(
    val path: String,
    val category: PackageCategory,
    val abiName: String?,
    val rawBytes: Long,
    val sourceCompressedBytes: Long,
    val sourceStored: Boolean,
    val crc32: Long,
    val shannonEntropy: Double,
    val strategy: EntryStrategy,
    val predictedOutputBytes: Long
)

data class CategoryBreakdown(
    val category: PackageCategory,
    val rawBytes: Long,
    val archiveBytes: Long,
    val entryCount: Int
)

data class DuplicateAssetGroup(
    val crc32: Long,
    val fileSizeBytes: Long,
    val paths: List<String>
) {
    val wastedBytes: Long get() = fileSizeBytes * (paths.size - 1).coerceAtLeast(0)
}

data class PackageSecurityAudit(
    val requestedPermissions: List<String>,
    val dangerousPermissions: List<String>,
    val isDebuggable: Boolean,
    val allowsBackup: Boolean,
    val signingCertSha256: String?
)

data class PackageInspectionResult(
    val workingFilePath: String,
    val displayName: String,
    val fileExtension: String,
    val packageId: String?,
    val versionName: String?,
    val versionCode: Long?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val appIcon: Drawable?,
    val totalArchiveBytes: Long,
    val totalRawBytes: Long,
    val predictedOutputBytes: Long,
    val entryCount: Int,
    val averageEntropy: Double,
    val hasResourcesArsc: Boolean,
    val hasUncompressedNativeLibs: Boolean,
    val detectedAbis: List<String>,
    val debugArtifactsCount: Int,
    val safetyScore: Int,
    val duplicateGroups: List<DuplicateAssetGroup>,
    val securityAudit: PackageSecurityAudit?,
    val breakdown: List<CategoryBreakdown>,
    val entries: List<EntryAnalysis>,
    val splitSourcePaths: List<String> = emptyList(),
    val manifestRuntimeRisks: List<String> = emptyList(),
    val detectedLocales: List<String> = emptyList(),
    val hasSharedUserId: Boolean = false,
    val staticLibraries: List<DiscoveredStaticLibrary> = emptyList(),
    val compatibilityReport: PackageCompatibilityReport? = null
) {
    val totalDuplicateFilesCount: Int
        get() = duplicateGroups.sumOf { (it.paths.size - 1).coerceAtLeast(0) }

    val totalDuplicateWastedBytes: Long
        get() = duplicateGroups.sumOf { it.wastedBytes }

    val predictedSavedBytes: Long
        get() = (totalArchiveBytes - predictedOutputBytes).coerceAtLeast(0L)

    val predictedSavedPercent: Double
        get() = if (totalArchiveBytes > 0L) {
            (predictedSavedBytes.toDouble() / totalArchiveBytes.toDouble()) * 100.0
        } else 0.0
}

enum class CompressionPhase {
    INSPECTING,
    COMPRESSING,
    VERIFYING
}

data class CompressionProgress(
    val phase: CompressionPhase,
    val currentEntry: String,
    val processedEntries: Int,
    val totalEntries: Int,
    val bytesProcessed: Long,
    val totalBytes: Long,
    val activeBufferKb: Int,
    val elapsedMs: Long
) {
    val fraction: Float
        get() = if (totalEntries <= 0) 0f else (processedEntries.toFloat() / totalEntries.toFloat()).coerceIn(0f, 1f)
}

data class CompressionReport(
    val sourceDisplayName: String,
    val packageId: String?,
    val effectivePackageId: String?,
    val originalBytes: Long,
    val compressedBytes: Long,
    val savedBytes: Long,
    val savedPercent: Double,
    val elapsedMs: Long,
    val peakHeapMb: Int,
    val verifiedEntriesCount: Int,
    val corruptEntriesCount: Int,
    val resourcesArscAligned: Boolean,
    val bypassedMediaCount: Int,
    val prunedArtifactsCount: Int,
    val strippedAbiEntriesCount: Int,
    val averageEntropy: Double,
    val sha256Hex: String,
    val signedV2CertSha256: String?,
    val outputFilePath: String,
    val outputFileName: String,
    val splitOutputPaths: List<String> = emptyList(),
    val companionLibraryOutputs: List<List<String>> = emptyList(),
    val manifestRuntimeRisks: List<String> = emptyList(),
    val mergedUniversalApk: Boolean = false,
    val localesPrunedCount: Int = 0,
    val imagesOptimizedCount: Int = 0,
    val strippedSharedUserId: Boolean = false,
    val isApkArchive: Boolean,
    val reSignRequiredWarning: Boolean
) {
    fun toAuditJson(): String {
        val safeName = sourceDisplayName.replace("\"", "\\\"")
        val safePkg = (effectivePackageId ?: packageId)?.replace("\"", "\\\"") ?: "null"
        return """
            |{
            |  "engine": "PakForge Smart Engine v3.0",
            |  "packageName": "$safeName",
            |  "packageId": ${if (safePkg == "null") "null" else "\"$safePkg\""},
            |  "originalBytes": $originalBytes,
            |  "compressedBytes": $compressedBytes,
            |  "savedBytes": $savedBytes,
            |  "savedPercent": ${String.format(Locale.US, "%.2f", savedPercent)},
            |  "elapsedMs": $elapsedMs,
            |  "peakHeapMb": $peakHeapMb,
            |  "verifiedEntries": $verifiedEntriesCount,
            |  "corruptEntries": $corruptEntriesCount,
            |  "resourcesArsc4ByteAligned": $resourcesArscAligned,
            |  "mergedUniversalApk": $mergedUniversalApk,
            |  "localesPruned": $localesPrunedCount,
            |  "imagesOptimized": $imagesOptimizedCount,
            |  "strippedSharedUserId": $strippedSharedUserId,
            |  "bypassedHighEntropyEntries": $bypassedMediaCount,
            |  "prunedDebugArtifacts": $prunedArtifactsCount,
            |  "strippedAbiEntries": $strippedAbiEntriesCount,
            |  "averageShannonEntropy": ${String.format(Locale.US, "%.3f", averageEntropy)},
            |  "outputSha256": "$sha256Hex",
            |  "signedV2CertSha256": ${if (signedV2CertSha256 == null) "null" else "\"$signedV2CertSha256\""},
            |  "reSignRequired": $reSignRequiredWarning
            |}
        """.trimMargin()
    }
}

data class InstalledAppItem(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val apkPath: String,
    val splitApkPaths: List<String> = emptyList(),
    val apkSizeBytes: Long,
    val isSystemApp: Boolean = false,
    val icon: Drawable?
) {
    val isSplitBundle: Boolean get() = splitApkPaths.isNotEmpty()
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return String.format(Locale.US, if (digitGroups == 0) "%.0f %s" else "%.2f %s", value, units[digitGroups])
}
