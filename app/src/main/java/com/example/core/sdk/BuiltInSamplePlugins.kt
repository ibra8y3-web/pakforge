package com.example.core.sdk

import com.example.core.engine.formatBytes

/**
 * Built-in reference plugins showcasing real unified Plugin SDK interfaces.
 * Every call passes through [PluginHostContext] and respects granted [PluginPermission]s.
 */
object BuiltInSamplePlugins {

    val deviceThermalGuardPlugin: PakForgePlugin = object : PakForgePlugin {
        override val manifest = PluginManifest(
            id = "dev.pakforge.sample.telemetry_guard",
            name = "Device & Shell Health Guard",
            version = "1.0.0",
            apiVersion = PakForgeSdkSpec.SDK_VERSION,
            minHostApiLevel = 1,
            author = "PakForge SDK Team",
            description = "Audits RAM/CPU headroom, Root/Sui/Shizuku status, and auto-tunes compression buffer & sends diagnostic alert.",
            entryPoint = "dev.pakforge.sample.DeviceThermalGuardPlugin",
            permissions = listOf(
                PluginPermission.READ_DEVICE_TELEMETRY,
                PluginPermission.READ_DIAGNOSTICS,
                PluginPermission.PRIVILEGED_SHELL_READ,
                PluginPermission.ACCESS_HOST_TOOLS,
                PluginPermission.SEND_NOTIFICATIONS
            ),
            hooks = listOf(PluginHookType.MANUAL_RUN, PluginHookType.ON_DIAGNOSTICS_CHECK)
        )

        override suspend fun execute(ctx: PluginHostContext): PluginExecutionOutput {
            val traces = ArrayList<String>()
            val hw = ctx.device.getDeviceTelemetry()
            traces.add("DeviceInterface -> ${hw.manufacturerModel} • API ${hw.androidApiLevel} • ABI=${hw.primaryAbi} • Free RAM=${hw.freeRamMb}/${hw.totalRamMb} MB (${hw.ramUsagePercent}% used)")
            traces.add("BufferInterface -> Recommended I/O buffer = ${ctx.device.getRecommendedBufferKb()} KB")

            val diag = ctx.diagnostics.getPrivilegedBackendsSummary()
            val uid = ctx.tools.queryElevatedShellUid()
            traces.add("DiagnosticsInterface -> Active backend=${diag.activeBackend} (${diag.activeDetail}), UID=${uid ?: "none"}")

            val health = ctx.diagnostics.getEngineHealthChecks()
            health.forEach { ev ->
                traces.add("HealthCheck[${ev.id}] -> ${ev.severity}: ${ev.detail}")
            }

            val targetProfile = if (hw.isLowRamDevice || hw.isPowerSaveMode || hw.freeRamMb < 450) {
                "LOW_RESOURCE_ECO"
            } else {
                "ZERO_RISK_BALANCED"
            }
            val applied = ctx.tools.applyCompressionPreset(
                PluginCompressionRule(profile = targetProfile)
            )
            traces.add("HostToolsInterface -> Applied profile=$targetProfile (success=$applied)")

            ctx.notifications.postNotification(
                title = "Device Health Verified",
                message = "${hw.freeRamMb} MB free • Backend: ${diag.activeBackend} • Profile: $targetProfile",
                severity = PluginSeverity.SUCCESS
            )
            traces.add("NotificationInterface -> Notification dispatched")

            return PluginExecutionOutput(
                ok = true,
                summary = "Profile $targetProfile active • ${hw.freeRamMb} MB free • Shell=${diag.activeBackend}",
                traces = traces
            )
        }
    }

    val releaseBloatAuditorPlugin: PakForgePlugin = object : PakForgePlugin {
        override val manifest = PluginManifest(
            id = "dev.pakforge.sample.release_auditor",
            name = "Release Report & Bloat Auditor",
            version = "1.0.0",
            apiVersion = PakForgeSdkSpec.SDK_VERSION,
            minHostApiLevel = 1,
            author = "PakForge SDK Team",
            description = "Reads ReportsInterface savings history + HostTools compatibility summary + computes SHA-256 fingerprint.",
            entryPoint = "dev.pakforge.sample.ReleaseBloatAuditorPlugin",
            permissions = listOf(
                PluginPermission.READ_REPORTS,
                PluginPermission.READ_DIAGNOSTICS,
                PluginPermission.ACCESS_HOST_TOOLS,
                PluginPermission.SEND_NOTIFICATIONS
            ),
            hooks = listOf(PluginHookType.MANUAL_RUN, PluginHookType.ON_COMPRESSION_COMPLETE)
        )

        override suspend fun execute(ctx: PluginHostContext): PluginExecutionOutput {
            val traces = ArrayList<String>()
            val totalSaved = ctx.reports.getTotalSavedBytes()
            val recent = ctx.reports.getRecentReports(5)
            traces.add("ReportsInterface -> Total reclaimed = ${formatBytes(totalSaved)} across ${recent.size} recent runs")
            recent.forEach { item ->
                traces.add("  • ${item.fileName}: saved ${formatBytes(item.savedBytes)} (${String.format("%.1f", item.savedPercent)}%) [${item.profileName}]")
            }

            val compatSummary = ctx.tools.runCompatibilitySummary()
            traces.add("HostToolsInterface.runCompatibilitySummary -> $compatSummary")

            val pkgDiag = ctx.diagnostics.getCurrentPackageDiagnostics()
            if (pkgDiag != null) {
                val digest = ctx.tools.computeSha256((pkgDiag.packageId ?: pkgDiag.displayName).toByteArray())
                traces.add("HostToolsInterface.computeSha256 -> ${digest.take(16)}… (${pkgDiag.splitCount} splits, locales=${pkgDiag.detectedLocales.take(5)})")
            }

            ctx.notifications.postNotification(
                title = "Audit Summary",
                message = "Total saved: ${formatBytes(totalSaved)} • Recent runs: ${recent.size}",
                severity = PluginSeverity.INFO
            )
            traces.add("NotificationInterface -> Audit alert sent")

            return PluginExecutionOutput(
                ok = true,
                summary = "Audited ${recent.size} runs • Total saved ${formatBytes(totalSaved)}",
                traces = traces
            )
        }
    }

    val rtlSlimmerPresetPlugin: PakForgePlugin = object : PakForgePlugin {
        override val manifest = PluginManifest(
            id = "dev.pakforge.sample.ar_en_slimmer",
            name = "AR/EN Universal Slimmer Preset",
            version = "1.0.0",
            apiVersion = PakForgeSdkSpec.SDK_VERSION,
            minHostApiLevel = 1,
            author = "PakForge SDK Team",
            description = "Applies Ultra Shrink preset keeping only Arabic & English locales, crunching heavy PNGs/WebPs, merging splits, and stripping sharedUserId.",
            entryPoint = "dev.pakforge.sample.RtlSlimmerPresetPlugin",
            permissions = listOf(
                PluginPermission.ACCESS_HOST_TOOLS,
                PluginPermission.SEND_NOTIFICATIONS
            ),
            hooks = listOf(PluginHookType.MANUAL_RUN, PluginHookType.ON_PACKAGE_INSPECTED),
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
            val traces = ArrayList<String>()
            val rule = manifest.compressionRule!!
            val applied = ctx.tools.applyCompressionPreset(rule)
            traces.add("HostToolsInterface -> Applied AR/EN Ultra Universal Slimmer preset (applied=$applied)")
            ctx.notifications.postNotification(
                title = "AR/EN Preset Active",
                message = "Kept locales: ar, en • Universal Merge + Image Crunch enabled",
                severity = PluginSeverity.SUCCESS
            )
            traces.add("NotificationInterface -> Notification dispatched")
            return PluginExecutionOutput(
                ok = applied,
                summary = "Applied AR/EN Ultra Shrink + Universal Merge + Image Crunch",
                traces = traces
            )
        }
    }

    val allBuiltIns: List<PakForgePlugin> = listOf(
        deviceThermalGuardPlugin,
        releaseBloatAuditorPlugin,
        rtlSlimmerPresetPlugin
    )

    fun findById(id: String): PakForgePlugin? = allBuiltIns.firstOrNull { it.manifest.id == id }
}

data class SdkCodeExample(
    val id: String,
    val title: String,
    val subtitle: String,
    val code: String
)

object PluginSdkExamples {
    val snippets: List<SdkCodeExample> = listOf(
        SdkCodeExample(
            id = "manifest_json",
            title = "1. pakforge-plugin.json (Manifest & Versioning)",
            subtitle = "SemVer + minHostApiLevel + Scoped Permissions + Preset Rule",
            code = """
{
  "id": "dev.yourname.smart_guard",
  "name": "Smart Release Guard",
  "version": "1.0.0",
  "apiVersion": "1.0.0",
  "minHostApiLevel": 1,
  "author": "Your Studio",
  "description": "Validates device RAM headroom, Shell UID, and reports.",
  "entryPoint": "dev.yourname.SmartGuardPlugin",
  "permissions": [
    "pakforge.permission.READ_DEVICE_TELEMETRY",
    "pakforge.permission.READ_DIAGNOSTICS",
    "pakforge.permission.READ_REPORTS",
    "pakforge.permission.SEND_NOTIFICATIONS",
    "pakforge.permission.ACCESS_HOST_TOOLS",
    "pakforge.permission.PRIVILEGED_SHELL_READ"
  ],
  "hooks": ["MANUAL_RUN", "ON_PACKAGE_INSPECTED"],
  "compressionRule": {
    "profile": "ULTRA_SHRINK",
    "pruneUnusedLocales": true,
    "keptLocales": ["en", "ar"],
    "optimizeLargeImages": true,
    "mergeSplitsIntoUniversal": true,
    "stripSharedUserId": true
  }
}
            """.trimIndent()
        ),
        SdkCodeExample(
            id = "device_diag_example",
            title = "2. Device + Diagnostics + Shell UID Interfaces",
            subtitle = "Reading real SoC/RAM telemetry and KernelSU/Sui/Shizuku status",
            code = """
class DeviceGuardPlugin : PakForgePlugin {
    override suspend fun execute(ctx: PluginHostContext): PluginExecutionOutput {
        val hw = ctx.device.getDeviceTelemetry()
        val diag = ctx.diagnostics.getPrivilegedBackendsSummary()
        val shellUid = ctx.tools.queryElevatedShellUid()

        val msg = "${'$'}{hw.manufacturerModel}: ${'$'}{hw.freeRamMb}MB free, backend=${'$'}{diag.activeBackend} (UID=${'$'}shellUid)"
        ctx.notifications.postNotification("Hardware Ready", msg, PluginSeverity.INFO)
        return PluginExecutionOutput(ok = true, summary = msg)
    }
}
            """.trimIndent()
        ),
        SdkCodeExample(
            id = "reports_tools_example",
            title = "3. Reports + Host Tools (Permission-Guarded)",
            subtitle = "Enforcing hasPermission() before querying reports & SHA-256 tools",
            code = """
suspend fun runAuditor(ctx: PluginHostContext): PluginExecutionOutput {
    if (!ctx.hasPermission(PluginPermission.READ_REPORTS)) {
        return PluginExecutionOutput(false, "Missing READ_REPORTS permission")
    }
    val saved = ctx.reports.getTotalSavedBytes()
    val recent = ctx.reports.getRecentReports(limit = 5)
    val compat = ctx.tools.runCompatibilitySummary()
    val hash = ctx.tools.computeSha256(compat.toByteArray())
    return PluginExecutionOutput(
        ok = true,
        summary = "Saved=${'$'}saved bytes across ${'$'}{recent.size} runs • hash=${'$'}{hash.take(12)}"
    )
}
            """.trimIndent()
        )
    )
}
