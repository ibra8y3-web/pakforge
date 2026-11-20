package com.example.core.sdk

data class StorePluginListing(
    val manifest: PluginManifest,
    val signedPfpakBytes: ByteArray,
    val scanResult: PluginSecurityScanResult,
    val verifiedPublisherLabel: String,
    val highlightsEn: List<String>,
    val highlightsAr: List<String>
)

/**
 * Cryptographically signed Plugin Store catalog built with PakForge Official ECDSA P-256 keys.
 * All store items are packed into genuine `.pfpak` ZIP bundles and verified through [PluginSecurityEngine]
 * before installation so developers can add or customize tools without rebuilding PakForge.
 */
object PluginStoreCatalog {

    private val storeManifests: List<PluginManifest> = listOf(
        BuiltInSamplePlugins.deviceThermalGuardPlugin.manifest.copy(
            category = PluginCategory.PERFORMANCE,
            actions = listOf(
                PluginActionStep.CHECK_DEVICE_HEADROOM,
                PluginActionStep.AUDIT_SHELL_BACKEND,
                PluginActionStep.APPLY_COMPRESSION_RULE,
                PluginActionStep.DISPATCH_NOTIFICATION
            ),
            smartMatch = SmartMatchCriteria(
                requireLowRamOrPowerSave = true,
                requireElevatedShell = true
            )
        ),
        BuiltInSamplePlugins.rtlSlimmerPresetPlugin.manifest.copy(
            category = PluginCategory.COMPRESSION,
            actions = listOf(
                PluginActionStep.INSPECT_ACTIVE_PACKAGE,
                PluginActionStep.APPLY_COMPRESSION_RULE,
                PluginActionStep.DISPATCH_NOTIFICATION
            ),
            smartMatch = SmartMatchCriteria(
                requireSplits = true,
                minLocaleCount = 5,
                minArchiveMb = 15
            )
        ),
        PluginManifest(
            id = "dev.pakforge.store.system_trichrome_unlocker",
            name = "System & Trichrome Unlocker",
            version = "1.2.0",
            apiVersion = PakForgeSdkSpec.SDK_VERSION,
            minHostApiLevel = 1,
            author = "PakForge Core Store",
            description = "Auto-detects android:sharedUserId & static library certDigest conflicts, configures System Bypass + companion library re-signing, and verifies Root/Sui/Shizuku UID.",
            entryPoint = "dev.pakforge.store.SystemTrichromeUnlockerPlugin",
            permissions = listOf(
                PluginPermission.READ_DIAGNOSTICS,
                PluginPermission.PRIVILEGED_SHELL_READ,
                PluginPermission.ACCESS_HOST_TOOLS,
                PluginPermission.SEND_NOTIFICATIONS
            ),
            hooks = listOf(PluginHookType.ON_PACKAGE_INSPECTED, PluginHookType.MANUAL_RUN),
            compressionRule = PluginCompressionRule(
                profile = "ZERO_RISK_BALANCED",
                stripSharedUserId = true,
                patchStaticLibCertDigest = true,
                bundleCompanionStaticLibs = true
            ),
            category = PluginCategory.SYSTEM_SHELL,
            actions = listOf(
                PluginActionStep.AUDIT_SHELL_BACKEND,
                PluginActionStep.INSPECT_ACTIVE_PACKAGE,
                PluginActionStep.APPLY_COMPRESSION_RULE,
                PluginActionStep.COMPUTE_PACKAGE_DIGEST,
                PluginActionStep.DISPATCH_NOTIFICATION
            ),
            smartMatch = SmartMatchCriteria(
                requireSharedUserId = true,
                requireStaticLibs = true,
                requireElevatedShell = true
            )
        ),
        PluginManifest(
            id = "dev.pakforge.store.game_bundle_universalizer",
            name = "Heavy Split & Game Universalizer",
            version = "1.1.0",
            apiVersion = PakForgeSdkSpec.SDK_VERSION,
            minHostApiLevel = 1,
            author = "PakForge Core Store",
            description = "Merges multi-split game/app bundles into a single standalone signed Universal APK while optimizing heavy PNG/WebP assets and multi-ABI native libs.",
            entryPoint = "dev.pakforge.store.GameBundleUniversalizerPlugin",
            permissions = listOf(
                PluginPermission.READ_DEVICE_TELEMETRY,
                PluginPermission.READ_DIAGNOSTICS,
                PluginPermission.ACCESS_HOST_TOOLS,
                PluginPermission.SEND_NOTIFICATIONS
            ),
            hooks = listOf(PluginHookType.ON_PACKAGE_INSPECTED, PluginHookType.MANUAL_RUN),
            compressionRule = PluginCompressionRule(
                profile = "ULTRA_SHRINK",
                mergeSplitsIntoUniversal = true,
                optimizeLargeImages = true,
                pruneUnusedLocales = true,
                keptLocales = setOf("en", "ar")
            ),
            category = PluginCategory.COMPRESSION,
            actions = listOf(
                PluginActionStep.CHECK_DEVICE_HEADROOM,
                PluginActionStep.INSPECT_ACTIVE_PACKAGE,
                PluginActionStep.APPLY_COMPRESSION_RULE,
                PluginActionStep.DISPATCH_NOTIFICATION
            ),
            smartMatch = SmartMatchCriteria(
                requireSplits = true,
                minArchiveMb = 30,
                minAbiCount = 2
            )
        ),
        PluginManifest(
            id = "dev.pakforge.store.privacy_bloat_inspector",
            name = "Security, Bloat & Savings Auditor",
            version = "1.0.5",
            apiVersion = PakForgeSdkSpec.SDK_VERSION,
            minHostApiLevel = 1,
            author = "PakForge Core Store",
            description = "Deep-scans loaded package compatibility risks, computes SHA-256 fingerprints, summarizes reclaimed storage across all runs, and alerts on debuggable/duplicate flags.",
            entryPoint = "dev.pakforge.store.PrivacyBloatAuditorPlugin",
            permissions = listOf(
                PluginPermission.READ_DIAGNOSTICS,
                PluginPermission.READ_REPORTS,
                PluginPermission.ACCESS_HOST_TOOLS,
                PluginPermission.SEND_NOTIFICATIONS
            ),
            hooks = listOf(PluginHookType.ON_COMPRESSION_COMPLETE, PluginHookType.MANUAL_RUN),
            category = PluginCategory.SECURITY_AUDIT,
            actions = listOf(
                PluginActionStep.INSPECT_ACTIVE_PACKAGE,
                PluginActionStep.COMPUTE_PACKAGE_DIGEST,
                PluginActionStep.READ_SAVINGS_HISTORY,
                PluginActionStep.DISPATCH_NOTIFICATION
            ),
            smartMatch = SmartMatchCriteria(
                minLocaleCount = 1,
                minArchiveMb = 5
            )
        ),
        PluginManifest(
            id = "dev.pakforge.store.eco_battery_safeguard",
            name = "Eco RAM & Battery Safeguard",
            version = "1.0.2",
            apiVersion = PakForgeSdkSpec.SDK_VERSION,
            minHostApiLevel = 1,
            author = "PakForge Core Store",
            description = "Monitors free RAM & power-save mode automatically to lock I/O buffers to Low-RAM Eco throttle and prevent OOM during huge APK repacking.",
            entryPoint = "dev.pakforge.store.EcoBatterySafeguardPlugin",
            permissions = listOf(
                PluginPermission.READ_DEVICE_TELEMETRY,
                PluginPermission.ACCESS_HOST_TOOLS,
                PluginPermission.SEND_NOTIFICATIONS
            ),
            hooks = listOf(PluginHookType.ON_DIAGNOSTICS_CHECK, PluginHookType.MANUAL_RUN),
            compressionRule = PluginCompressionRule(
                profile = "LOW_RESOURCE_ECO",
                optimizeLargeImages = false
            ),
            category = PluginCategory.PERFORMANCE,
            actions = listOf(
                PluginActionStep.CHECK_DEVICE_HEADROOM,
                PluginActionStep.APPLY_COMPRESSION_RULE,
                PluginActionStep.DISPATCH_NOTIFICATION
            ),
            smartMatch = SmartMatchCriteria(
                requireLowRamOrPowerSave = true
            )
        )
    )

    fun buildSignedCatalog(securityEngine: PluginSecurityEngine): List<StorePluginListing> {
        return storeManifests.map { manifest ->
            val pfpakBytes = securityEngine.signAndPackPfpak(manifest, useOfficialKey = true)
            val scanResult = securityEngine.inspectAndVerifyIncomingBytes(
                rawBytes = pfpakBytes,
                displayName = "${manifest.id}.pfpak"
            )
            StorePluginListing(
                manifest = manifest,
                signedPfpakBytes = pfpakBytes,
                scanResult = scanResult,
                verifiedPublisherLabel = "PakForge Official ECDSA",
                highlightsEn = listOf(
                    manifest.category.titleEn,
                    "${manifest.permissions.size} scoped permissions",
                    "${manifest.actions.size} pipeline steps"
                ),
                highlightsAr = listOf(
                    manifest.category.titleAr,
                    "${manifest.permissions.size} صلاحيات",
                    "${manifest.actions.size} خطوات ذكية"
                )
            )
        }
    }
}
