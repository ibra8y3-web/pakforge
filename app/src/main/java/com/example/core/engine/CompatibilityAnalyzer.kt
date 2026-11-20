package com.example.core.engine

enum class CompatibilityTier {
    VERIFIED_READY,
    AUTO_FIXABLE,
    PROTECTED_SYSTEM
}

data class CompatibilityCheckItem(
    val id: String,
    val title: String,
    val detail: String,
    val severity: CompatibilityTier
)

data class PackageCompatibilityReport(
    val tier: CompatibilityTier,
    val readinessScore: Int,
    val checks: List<CompatibilityCheckItem>,
    val canAutoFix: Boolean
)

/**
 * Deterministic pre-flight compatibility analyzer that inspects real manifest flags,
 * Split APKs, sharedUserId, static shared libraries, and device install status.
 */
object CompatibilityAnalyzer {

    fun analyze(
        inspection: PackageInspectionResult,
        isInstalledWithDifferentSig: Boolean,
        isSystemApp: Boolean,
        shizukuReady: Boolean
    ): PackageCompatibilityReport {
        val checks = ArrayList<CompatibilityCheckItem>()
        var score = 98
        var worstTier = CompatibilityTier.VERIFIED_READY
        var autoFixUseful = false

        if (inspection.splitSourcePaths.isNotEmpty()) {
            checks.add(
                CompatibilityCheckItem(
                    id = "splits",
                    title = "Split Bundle (${inspection.splitSourcePaths.size + 1} APKs)",
                    detail = "Can be installed atomically as splits or merged into a single Universal APK.",
                    severity = CompatibilityTier.AUTO_FIXABLE
                )
            )
            score -= 6
            if (worstTier == CompatibilityTier.VERIFIED_READY) worstTier = CompatibilityTier.AUTO_FIXABLE
            autoFixUseful = true
        }

        if (inspection.hasSharedUserId) {
            checks.add(
                CompatibilityCheckItem(
                    id = "shared_uid",
                    title = "android:sharedUserId Detected",
                    detail = "Blocks re-signed installs unless stripped cleanly from AndroidManifest.xml.",
                    severity = CompatibilityTier.AUTO_FIXABLE
                )
            )
            score -= 18
            if (worstTier == CompatibilityTier.VERIFIED_READY) worstTier = CompatibilityTier.AUTO_FIXABLE
            autoFixUseful = true
        }

        if (inspection.staticLibraries.isNotEmpty()) {
            val allFound = inspection.staticLibraries.all { it.apkPaths.isNotEmpty() }
            val names = inspection.staticLibraries.joinToString(", ") { it.libraryName }
            if (allFound) {
                checks.add(
                    CompatibilityCheckItem(
                        id = "static_libs_found",
                        title = "Static Library Companion Found ($names)",
                        detail = "PakForge can re-sign the companion library and patch android:certDigest. Note: Google/Play apps with internal C++ signature checks may still self-close.",
                        severity = if (isSystemApp) CompatibilityTier.PROTECTED_SYSTEM else CompatibilityTier.AUTO_FIXABLE
                    )
                )
                score -= if (isSystemApp) 32 else 15
                worstTier = if (isSystemApp) CompatibilityTier.PROTECTED_SYSTEM else CompatibilityTier.AUTO_FIXABLE
                autoFixUseful = true
            } else {
                checks.add(
                    CompatibilityCheckItem(
                        id = "static_libs_missing",
                        title = "Protected Static Library Missing ($names)",
                        detail = "Library APK not readable on device; re-signing without it may crash at launch.",
                        severity = CompatibilityTier.PROTECTED_SYSTEM
                    )
                )
                score -= 40
                worstTier = CompatibilityTier.PROTECTED_SYSTEM
            }
        }

        if (isInstalledWithDifferentSig) {
            checks.add(
                CompatibilityCheckItem(
                    id = "sig_conflict",
                    title = if (isSystemApp) "Pre-installed System Signature Conflict" else "Existing Signature Conflict",
                    detail = if (shizukuReady) {
                        "Use Side-by-Side Clone or Shizuku elevated install engine."
                    } else {
                        "Use Side-by-Side Clone (new Package ID) or Uninstall Original first."
                    },
                    severity = CompatibilityTier.AUTO_FIXABLE
                )
            )
            score -= 10
            if (worstTier == CompatibilityTier.VERIFIED_READY) worstTier = CompatibilityTier.AUTO_FIXABLE
            autoFixUseful = true
        }

        if (isSystemApp && shizukuReady) {
            checks.add(
                CompatibilityCheckItem(
                    id = "shizuku_ready",
                    title = "Shizuku Elevated Shell Active",
                    detail = "Privileged install/downgrade & bloatware management enabled without root.",
                    severity = CompatibilityTier.VERIFIED_READY
                )
            )
        }

        if (checks.isEmpty()) {
            checks.add(
                CompatibilityCheckItem(
                    id = "clean",
                    title = "Standard Standalone Package",
                    detail = "Zero manifest blockers detected. Ready for compression & direct install.",
                    severity = CompatibilityTier.VERIFIED_READY
                )
            )
        }

        return PackageCompatibilityReport(
            tier = worstTier,
            readinessScore = score.coerceIn(15, 100),
            checks = checks,
            canAutoFix = autoFixUseful
        )
    }

    fun buildAutoFixConfig(
        current: CompressionConfig,
        inspection: PackageInspectionResult,
        isInstalledWithDifferentSig: Boolean,
        isSystemApp: Boolean,
        shizukuReady: Boolean,
        devicePrimaryAbi: String
    ): CompressionConfig {
        var next = current.copy(
            enforceResourcesArscAlign = true,
            smartEntropyBypass = true,
            stripSharedUserId = inspection.hasSharedUserId || current.stripSharedUserId,
            patchStaticLibCertDigest = inspection.staticLibraries.isNotEmpty() || current.patchStaticLibCertDigest,
            bundleCompanionStaticLibs = inspection.staticLibraries.any { it.apkPaths.isNotEmpty() } || current.bundleCompanionStaticLibs
        )
        if (inspection.splitSourcePaths.isNotEmpty()) {
            next = next.copy(mergeSplitsIntoUniversal = true)
        }
        if (devicePrimaryAbi in inspection.detectedAbis && inspection.detectedAbis.size > 1) {
            next = next.copy(allowedAbis = setOf(devicePrimaryAbi))
        }
        if (isInstalledWithDifferentSig) {
            next = next.copy(
                installIdentityMode = InstallIdentityMode.CLONE_NEW_PACKAGE,
                tintCloneIcon = true
            )
        }
        if (isSystemApp && shizukuReady) {
            next = next.copy(preferShizukuInstaller = true)
        }
        return next
    }
}
