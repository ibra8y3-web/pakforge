package com.example.core.sdk

import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates a complete, standalone, Gradle-buildable multi-module Kotlin SDK + Sample Project ZIP:
 * - pakforge-sdk/  (Unified Interfaces, Manifest, SemVer, Permissions, HostContext)
 * - sample-plugin/ (Complete runnable sample plugins + pakforge-plugin.json)
 * - docs/          (Full English + Arabic SDK Reference & Manifest Guide)
 */
object PluginSdkZipExporter {

    const val DEFAULT_ARCHIVE_NAME = "pakforge-plugin-sdk-${PakForgeSdkSpec.SDK_VERSION}.zip"

    fun buildSdkFilesMap(): Map<String, String> = linkedMapOf(
        "README.md" to buildReadme(),
        "settings.gradle.kts" to """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositories {
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "PakForgePluginSDK"
            include(":pakforge-sdk", ":sample-plugin")
        """.trimIndent(),
        "build.gradle.kts" to """
            plugins {
                kotlin("jvm") version "2.1.0" apply false
            }
        """.trimIndent(),
        "pakforge-sdk/build.gradle.kts" to """
            plugins {
                kotlin("jvm")
            }

            group = "dev.pakforge"
            version = "${PakForgeSdkSpec.SDK_VERSION}"

            kotlin {
                jvmToolchain(11)
            }
        """.trimIndent(),
        "pakforge-sdk/src/main/kotlin/dev/pakforge/sdk/PluginSdkContract.kt" to buildSdkSourceCode(),
        "sample-plugin/build.gradle.kts" to """
            plugins {
                kotlin("jvm")
            }

            dependencies {
                implementation(project(":pakforge-sdk"))
            }

            kotlin {
                jvmToolchain(11)
            }
        """.trimIndent(),
        "sample-plugin/src/main/resources/pakforge-plugin.json" to PluginManifestCodec.toJson(
            BuiltInSamplePlugins.deviceThermalGuardPlugin.manifest.copy(
                id = "dev.sample.smart_release_guard",
                name = "Smart Release Guard (Sample)",
                author = "Developer"
            )
        ),
        "sample-plugin/src/main/kotlin/com/example/sampleplugin/SmartAuditSamplePlugin.kt" to buildSamplePluginSource(),
        "sample-plugin/src/main/kotlin/com/example/sampleplugin/RtlSlimmerSamplePlugin.kt" to buildSecondSamplePluginSource(),
        "docs/SDK_REFERENCE.md" to buildDocsReference()
    )

    fun writeZip(outputStream: OutputStream): Int {
        val files = buildSdkFilesMap()
        ZipOutputStream(outputStream).use { zos ->
            files.forEach { (path, content) ->
                zos.putNextEntry(ZipEntry(path))
                zos.write(content.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }
        }
        return files.size
    }

    private fun buildReadme(): String = """
        # PakForge Plugin SDK v${PakForgeSdkSpec.SDK_VERSION} (Host API Level ${PakForgeSdkSpec.HOST_API_LEVEL})

        Standalone developer kit for building permission-scoped plugins for **PakForge** using unified Kotlin interfaces.

        ## Modules
        - `:pakforge-sdk` — Pure Kotlin unified contracts (`PakForgePlugin`, `PluginManifest`, `PluginVersion`, `PluginPermission`, `DeviceInterface`, `DiagnosticsInterface`, `ReportsInterface`, `NotificationInterface`, `HostToolsInterface`).
        - `:sample-plugin` — Complete working reference plugins with `pakforge-plugin.json`.
        - `docs/SDK_REFERENCE.md` — Full bilingual (EN / AR) API documentation.

        ## Quickstart
        1. Implement `PakForgePlugin` and inspect the required `PluginPermission` set.
        2. Author `pakforge-plugin.json` matching `minHostApiLevel = ${PakForgeSdkSpec.HOST_API_LEVEL}` and SemVer `apiVersion = "${PakForgeSdkSpec.SDK_VERSION}"`.
        3. Import `pakforge-plugin.json` inside **PakForge → Settings → Plugins & Developers Hub**, grant permissions, and run in the live Sandbox!
    """.trimIndent()

    private fun buildSdkSourceCode(): String = """
        package dev.pakforge.sdk

        object PakForgeSdkSpec {
            const val SDK_VERSION = "${PakForgeSdkSpec.SDK_VERSION}"
            const val HOST_API_LEVEL = ${PakForgeSdkSpec.HOST_API_LEVEL}
        }

        enum class PluginPermission(val key: String) {
            READ_DEVICE_TELEMETRY("pakforge.permission.READ_DEVICE_TELEMETRY"),
            READ_DIAGNOSTICS("pakforge.permission.READ_DIAGNOSTICS"),
            READ_REPORTS("pakforge.permission.READ_REPORTS"),
            SEND_NOTIFICATIONS("pakforge.permission.SEND_NOTIFICATIONS"),
            ACCESS_HOST_TOOLS("pakforge.permission.ACCESS_HOST_TOOLS"),
            PRIVILEGED_SHELL_READ("pakforge.permission.PRIVILEGED_SHELL_READ")
        }

        enum class PluginHookType {
            MANUAL_RUN, ON_PACKAGE_INSPECTED, ON_COMPRESSION_COMPLETE, ON_DIAGNOSTICS_CHECK
        }

        enum class PluginSeverity { INFO, SUCCESS, WARNING, ERROR }

        data class PluginVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<PluginVersion> {
            override fun compareTo(other: PluginVersion): Int =
                compareValuesBy(this, other, PluginVersion::major, PluginVersion::minor, PluginVersion::patch)
            override fun toString(): String = "${'$'}major.${'$'}minor.${'$'}patch"
        }

        data class PluginCompressionRule(
            val profile: String? = null,
            val pruneUnusedLocales: Boolean? = null,
            val keptLocales: Set<String>? = null,
            val optimizeLargeImages: Boolean? = null,
            val mergeSplitsIntoUniversal: Boolean? = null,
            val stripSharedUserId: Boolean? = null
        )

        data class PluginManifest(
            val id: String,
            val name: String,
            val version: String,
            val apiVersion: String = PakForgeSdkSpec.SDK_VERSION,
            val minHostApiLevel: Int = PakForgeSdkSpec.HOST_API_LEVEL,
            val author: String,
            val description: String,
            val entryPoint: String,
            val permissions: List<PluginPermission>,
            val hooks: List<PluginHookType> = listOf(PluginHookType.MANUAL_RUN),
            val compressionRule: PluginCompressionRule? = null
        )

        data class PluginDeviceSnapshot(
            val manufacturerModel: String,
            val androidApiLevel: Int,
            val primaryAbi: String,
            val supportedAbis: List<String>,
            val cpuCores: Int,
            val freeRamMb: Int,
            val totalRamMb: Int,
            val ramUsagePercent: Int,
            val heapUsedMb: Int,
            val heapMaxMb: Int,
            val isPowerSaveMode: Boolean,
            val isLowRamDevice: Boolean,
            val recommendedBufferKb: Int
        )

        data class PluginPrivilegedSnapshot(
            val activeBackend: String,
            val activeDetail: String,
            val activeUid: Int?,
            val rootFlavor: String,
            val availableBackendNames: List<String>
        )

        data class PluginPackageDiagnostics(
            val packageId: String?,
            val displayName: String,
            val archiveBytes: Long,
            val entryCount: Int,
            val detectedAbis: List<String>,
            val detectedLocales: List<String>,
            val hasSharedUserId: Boolean,
            val splitCount: Int,
            val compatibilityScore: Int?,
            val compatibilityTier: String?,
            val manifestRisks: List<String>
        )

        data class PluginDiagnosticEvent(
            val id: String,
            val title: String,
            val severity: PluginSeverity,
            val detail: String
        )

        data class PluginReportSummary(
            val fileName: String,
            val profileName: String,
            val originalBytes: Long,
            val compressedBytes: Long,
            val savedBytes: Long,
            val savedPercent: Double,
            val timestampMillis: Long
        )

        data class PluginExecutionOutput(
            val ok: Boolean,
            val summary: String,
            val traces: List<String> = emptyList()
        )

        interface DeviceInterface {
            fun getDeviceTelemetry(): PluginDeviceSnapshot
            fun getRecommendedBufferKb(): Int
        }

        interface DiagnosticsInterface {
            fun getPrivilegedBackendsSummary(): PluginPrivilegedSnapshot
            fun getCurrentPackageDiagnostics(): PluginPackageDiagnostics?
            fun getEngineHealthChecks(): List<PluginDiagnosticEvent>
        }

        interface ReportsInterface {
            suspend fun getRecentReports(limit: Int = 10): List<PluginReportSummary>
            suspend fun getTotalSavedBytes(): Long
            fun getLatestAuditJson(): String?
        }

        interface NotificationInterface {
            fun postNotification(title: String, message: String, severity: PluginSeverity = PluginSeverity.INFO): Boolean
        }

        interface HostToolsInterface {
            fun computeSha256(bytes: ByteArray): String
            fun runCompatibilitySummary(): String
            fun applyCompressionPreset(rule: PluginCompressionRule): Boolean
            fun queryElevatedShellUid(): Int?
        }

        interface PluginHostContext {
            val pluginId: String
            val pluginName: String
            val sdkVersion: String
            val hostApiLevel: Int
            val grantedPermissions: Set<PluginPermission>
            val device: DeviceInterface
            val diagnostics: DiagnosticsInterface
            val reports: ReportsInterface
            val notifications: NotificationInterface
            val tools: HostToolsInterface
            fun hasPermission(permission: PluginPermission): Boolean = permission in grantedPermissions
        }

        interface PakForgePlugin {
            val manifest: PluginManifest
            suspend fun onInitialize(ctx: PluginHostContext) {}
            suspend fun execute(ctx: PluginHostContext): PluginExecutionOutput
        }
    """.trimIndent()

    private fun buildSamplePluginSource(): String = """
        package com.example.sampleplugin

        import dev.pakforge.sdk.*

        class SmartAuditSamplePlugin : PakForgePlugin {
            override val manifest = PluginManifest(
                id = "dev.sample.smart_release_guard",
                name = "Smart Release Guard (Sample)",
                version = "1.0.0",
                author = "Developer",
                description = "Audits RAM, elevated Root/Sui/Shizuku UID, reports & compatibility.",
                entryPoint = "com.example.sampleplugin.SmartAuditSamplePlugin",
                permissions = listOf(
                    PluginPermission.READ_DEVICE_TELEMETRY,
                    PluginPermission.READ_DIAGNOSTICS,
                    PluginPermission.READ_REPORTS,
                    PluginPermission.SEND_NOTIFICATIONS,
                    PluginPermission.ACCESS_HOST_TOOLS,
                    PluginPermission.PRIVILEGED_SHELL_READ
                )
            )

            override suspend fun execute(ctx: PluginHostContext): PluginExecutionOutput {
                val traces = mutableListOf<String>()
                if (ctx.hasPermission(PluginPermission.READ_DEVICE_TELEMETRY)) {
                    val hw = ctx.device.getDeviceTelemetry()
                    traces += "RAM: ${'$'}{hw.freeRamMb}/${'$'}{hw.totalRamMb} MB • ABI=${'$'}{hw.primaryAbi}"
                }
                if (ctx.hasPermission(PluginPermission.READ_DIAGNOSTICS)) {
                    val shell = ctx.diagnostics.getPrivilegedBackendsSummary()
                    traces += "Backend: ${'$'}{shell.activeBackend} (${'$'}{shell.activeDetail})"
                }
                if (ctx.hasPermission(PluginPermission.READ_REPORTS)) {
                    val saved = ctx.reports.getTotalSavedBytes()
                    traces += "Total Saved: ${'$'}saved bytes"
                }
                if (ctx.hasPermission(PluginPermission.SEND_NOTIFICATIONS)) {
                    ctx.notifications.postNotification("Sample Plugin", traces.joinToString(" | "), PluginSeverity.SUCCESS)
                }
                return PluginExecutionOutput(ok = true, summary = "Audit complete", traces = traces)
            }
        }
    """.trimIndent()

    private fun buildSecondSamplePluginSource(): String = """
        package com.example.sampleplugin

        import dev.pakforge.sdk.*

        class RtlSlimmerSamplePlugin : PakForgePlugin {
            override val manifest = PluginManifest(
                id = "dev.sample.ar_en_slimmer",
                name = "AR/EN Slimmer Preset",
                version = "1.0.0",
                author = "Developer",
                description = "Applies Ultra Shrink + Arabic/English Locale Pruning.",
                entryPoint = "com.example.sampleplugin.RtlSlimmerSamplePlugin",
                permissions = listOf(PluginPermission.ACCESS_HOST_TOOLS, PluginPermission.SEND_NOTIFICATIONS),
                compressionRule = PluginCompressionRule(
                    profile = "ULTRA_SHRINK",
                    pruneUnusedLocales = true,
                    keptLocales = setOf("en", "ar"),
                    optimizeLargeImages = true,
                    mergeSplitsIntoUniversal = true,
                    stripSharedUserId = true
                )
            )

            override suspend fun execute(ctx: PluginHostContext): PluginExecutionOutput {
                val applied = ctx.tools.applyCompressionPreset(manifest.compressionRule!!)
                ctx.notifications.postNotification("Preset Applied", "AR/EN Slimmer active=${'$'}applied", PluginSeverity.INFO)
                return PluginExecutionOutput(ok = applied, summary = "Preset applied=${'$'}applied")
            }
        }
    """.trimIndent()

    private fun buildDocsReference(): String = """
        # PakForge Plugin SDK — Full Reference Documentation

        ## 1. Versioning & Compatibility
        - `apiVersion`: SemVer string matching host major version (`${PakForgeSdkSpec.SDK_VERSION}`).
        - `minHostApiLevel`: Minimum integer host API level (`${PakForgeSdkSpec.HOST_API_LEVEL}`).
        - Plugins exceeding host API level or mismatched major SemVer are rejected at import time.

        ## 2. Permission System
        | Permission Key | Interface Unlocked |
        |---|---|
        | `pakforge.permission.READ_DEVICE_TELEMETRY` | `DeviceInterface` |
        | `pakforge.permission.READ_DIAGNOSTICS` | `DiagnosticsInterface` |
        | `pakforge.permission.READ_REPORTS` | `ReportsInterface` |
        | `pakforge.permission.SEND_NOTIFICATIONS` | `NotificationInterface` |
        | `pakforge.permission.ACCESS_HOST_TOOLS` | `HostToolsInterface` (tools) |
        | `pakforge.permission.PRIVILEGED_SHELL_READ` | `HostToolsInterface.queryElevatedShellUid()` |

        Calling any interface method without the granted permission immediately throws `PluginSecurityException`.

        ## 3. Multi-Backend Privileged Architecture
        PakForge automatically discovers and prioritizes:
        1. **KernelSU / APatch / Magisk Root (`su` UID 0)**
        2. **Sui** (Systemless Zygisk Binder Bridge)
        3. **Shizuku** (Wireless Debugging ADB / Root Binder UserService)
        4. **Dhizuku** (Device Owner Probe)
        5. **Standard Android PackageInstaller**
    """.trimIndent()
}
