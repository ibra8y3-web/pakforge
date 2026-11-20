package com.example.core.sdk

import com.example.core.engine.PackageInspectionResult
import com.example.core.privileged.PrivilegedBridgeSnapshot
import com.example.core.telemetry.DeviceTelemetryState

data class SmartPluginRecommendation(
    val manifest: PluginManifest,
    val matchScore: Int,
    val matchReasons: List<String>,
    val contextSummary: String
)

/**
 * Smart Context-Aware Plugin Recognition Engine.
 * Evaluates live APK blueprint signals, device RAM/power telemetry, and active Root/Sui/Shizuku
 * capabilities to recommend high-impact Store or Installed plugins automatically.
 */
object SmartPluginRecommender {

    fun evaluate(
        manifests: List<PluginManifest>,
        inspection: PackageInspectionResult?,
        telemetry: DeviceTelemetryState,
        bridge: PrivilegedBridgeSnapshot
    ): List<SmartPluginRecommendation> {
        val archiveMb = ((inspection?.totalArchiveBytes ?: 0L) / (1024L * 1024L)).toInt()
        val splitCount = inspection?.splitSourcePaths?.size ?: 0
        val localeCount = inspection?.detectedLocales?.size ?: 0
        val abiCount = inspection?.detectedAbis?.size ?: 0
        val hasSharedUid = inspection?.hasSharedUserId == true
        val hasStaticLibs = inspection?.staticLibraries?.isNotEmpty() == true
        val lowRamOrSave = telemetry.isLowRamDevice || telemetry.isPowerSaveMode || telemetry.freeRamMb < 550
        val hasDupesOrRisks = (inspection?.totalDuplicateFilesCount ?: 0) > 0 ||
            (inspection?.manifestRuntimeRisks?.isNotEmpty() == true) ||
            (inspection?.securityAudit?.isDebuggable == true)

        return manifests.mapNotNull { manifest ->
            val criteria = manifest.smartMatch ?: SmartMatchCriteria()
            var score = 20
            val reasons = ArrayList<String>()

            if (criteria.requireSplits) {
                if (splitCount > 0) {
                    score += 34
                    reasons.add("${splitCount + 1} split APKs detected")
                } else {
                    score -= 12
                }
            }
            if (criteria.requireSharedUserId && hasSharedUid) {
                score += 35
                reasons.add("sharedUserId detected in manifest")
            }
            if (criteria.requireStaticLibs && hasStaticLibs) {
                score += 35
                reasons.add("${inspection.staticLibraries.size} static library refs")
            }
            if (criteria.minArchiveMb > 0 && archiveMb >= criteria.minArchiveMb) {
                score += 22
                reasons.add("Large archive (${archiveMb} MB >= ${criteria.minArchiveMb} MB)")
            }
            if (criteria.minLocaleCount > 0 && localeCount >= criteria.minLocaleCount) {
                score += 24
                reasons.add("$localeCount language tables in resources.arsc")
            }
            if (criteria.minAbiCount > 0 && abiCount >= criteria.minAbiCount) {
                score += 18
                reasons.add("$abiCount native ABIs can be trimmed")
            }
            if (criteria.requireLowRamOrPowerSave && lowRamOrSave) {
                score += 30
                reasons.add("Low RAM / Power-Save (${telemetry.freeRamMb} MB free)")
            }
            if (criteria.requireElevatedShell && bridge.isElevatedReady) {
                score += 24
                reasons.add("${bridge.activeBackend.name} active (UID ${bridge.activeUid ?: 0})")
            }
            val pkgRegex = criteria.targetPackageRegex
            if (!pkgRegex.isNullOrBlank() && inspection?.packageId != null) {
                val matched = runCatching { Regex(pkgRegex).containsMatchIn(inspection.packageId) }.getOrDefault(false)
                if (matched) {
                    score += 35
                    reasons.add("Matches package ${inspection.packageId}")
                }
            }
            if (manifest.category == PluginCategory.SECURITY_AUDIT && hasDupesOrRisks) {
                score += 28
                reasons.add("Security / duplicate audit opportunity")
            }

            if (reasons.isEmpty()) {
                if (inspection == null && manifest.category == PluginCategory.PERFORMANCE) {
                    reasons.add("Device monitor (${telemetry.freeRamMb} MB free RAM)")
                    score += 20
                } else if (inspection == null && manifest.category == PluginCategory.DEVELOPER_TOOL) {
                    reasons.add("Ready for immediate sandbox diagnostics")
                    score += 18
                } else if (inspection != null) {
                    reasons.add("Compatible with ${inspection.displayName}")
                }
            }

            val bounded = score.coerceIn(0, 100)
            if (bounded < 35) return@mapNotNull null

            val contextSummary = inspection?.displayName ?: "${telemetry.freeRamMb} MB free • ${bridge.activeBackend.name}"
            SmartPluginRecommendation(
                manifest = manifest,
                matchScore = bounded,
                matchReasons = reasons.distinct().take(3),
                contextSummary = contextSummary
            )
        }.sortedByDescending { it.matchScore }
    }
}
