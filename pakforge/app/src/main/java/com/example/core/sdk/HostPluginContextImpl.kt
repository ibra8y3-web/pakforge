package com.example.core.sdk

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.engine.CompressionConfig
import com.example.core.engine.CompressionProfile
import com.example.core.engine.CompressionReport
import com.example.core.engine.PackageInspectionResult
import com.example.core.shizuku.ShizukuBridgeManager
import com.example.core.telemetry.DeviceTelemetryProvider
import com.example.data.local.HistoryRepository
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

interface PluginHostStateBridge {
    fun currentInspection(): PackageInspectionResult?
    fun latestReport(): CompressionReport?
    fun applyCompressionRule(rule: PluginCompressionRule): Boolean
    fun recordNotification(record: PluginNotificationRecord)
}

class HostPluginContextImpl(
    private val context: Context,
    override val pluginId: String,
    override val pluginName: String,
    override val grantedPermissions: Set<PluginPermission>,
    private val telemetryProvider: DeviceTelemetryProvider,
    private val shizukuBridge: ShizukuBridgeManager,
    private val historyRepository: HistoryRepository,
    private val hostStateBridge: PluginHostStateBridge
) : PluginHostContext {

    companion object {
        private const val CHANNEL_ID = "pakforge_plugin_sdk_alerts"
        private val notifIdCounter = AtomicLong(4100L)
    }

    override val sdkVersion: String = PakForgeSdkSpec.SDK_VERSION
    override val hostApiLevel: Int = PakForgeSdkSpec.HOST_API_LEVEL

    private fun requirePermission(permission: PluginPermission) {
        if (permission !in grantedPermissions) {
            throw PluginSecurityException(pluginId, permission)
        }
    }

    override val device: DeviceInterface = object : DeviceInterface {
        override fun getDeviceTelemetry(): PluginDeviceSnapshot {
            requirePermission(PluginPermission.READ_DEVICE_TELEMETRY)
            val snap = telemetryProvider.snapshot()
            return PluginDeviceSnapshot(
                manufacturerModel = telemetryProvider.deviceLabel(),
                androidApiLevel = Build.VERSION.SDK_INT,
                primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
                supportedAbis = Build.SUPPORTED_ABIS.toList(),
                cpuCores = snap.cpuCores,
                freeRamMb = snap.freeRamMb,
                totalRamMb = snap.totalRamMb,
                ramUsagePercent = snap.ramUsagePercent,
                heapUsedMb = snap.heapUsedMb,
                heapMaxMb = snap.heapMaxMb,
                isPowerSaveMode = snap.isPowerSaveMode,
                isLowRamDevice = snap.isLowRamDevice,
                recommendedBufferKb = snap.recommendedBufferKb
            )
        }

        override fun getRecommendedBufferKb(): Int {
            requirePermission(PluginPermission.READ_DEVICE_TELEMETRY)
            return telemetryProvider.snapshot().recommendedBufferKb
        }
    }

    override val diagnostics: DiagnosticsInterface = object : DiagnosticsInterface {
        override fun getPrivilegedBackendsSummary(): PluginPrivilegedSnapshot {
            requirePermission(PluginPermission.READ_DIAGNOSTICS)
            val bridge = shizukuBridge.bridgeSnapshot.value
            return PluginPrivilegedSnapshot(
                activeBackend = bridge.activeBackend.name,
                activeDetail = bridge.activeDetail,
                activeUid = bridge.activeUid,
                rootFlavor = bridge.rootFlavor.displayName,
                availableBackendNames = bridge.backends.filter { it.isReady }.map { it.title }
            )
        }

        override fun getCurrentPackageDiagnostics(): PluginPackageDiagnostics? {
            requirePermission(PluginPermission.READ_DIAGNOSTICS)
            val insp = hostStateBridge.currentInspection() ?: return null
            return PluginPackageDiagnostics(
                packageId = insp.packageId,
                displayName = insp.displayName,
                archiveBytes = insp.totalArchiveBytes,
                entryCount = insp.entryCount,
                detectedAbis = insp.detectedAbis,
                detectedLocales = insp.detectedLocales,
                hasSharedUserId = insp.hasSharedUserId,
                splitCount = insp.splitSourcePaths.size,
                compatibilityScore = insp.compatibilityReport?.readinessScore,
                compatibilityTier = insp.compatibilityReport?.tier?.name,
                manifestRisks = insp.manifestRuntimeRisks
            )
        }

        override fun getEngineHealthChecks(): List<PluginDiagnosticEvent> {
            requirePermission(PluginPermission.READ_DIAGNOSTICS)
            val events = ArrayList<PluginDiagnosticEvent>()
            val snap = telemetryProvider.snapshot()
            events.add(
                PluginDiagnosticEvent(
                    id = "ram_headroom",
                    title = "JVM & System Memory",
                    severity = if (snap.isLowRamDevice || snap.ramUsagePercent > 88) PluginSeverity.WARNING else PluginSeverity.SUCCESS,
                    detail = "${snap.freeRamMb} MB free / ${snap.totalRamMb} MB total (Heap ${snap.heapUsedMb}/${snap.heapMaxMb} MB)"
                )
            )
            val bridge = shizukuBridge.bridgeSnapshot.value
            events.add(
                PluginDiagnosticEvent(
                    id = "privileged_bridge",
                    title = "Privileged Shell Orchestrator",
                    severity = if (bridge.isElevatedReady) PluginSeverity.SUCCESS else PluginSeverity.INFO,
                    detail = "${bridge.activeBackend.name}: ${bridge.activeDetail}"
                )
            )
            val insp = hostStateBridge.currentInspection()
            if (insp != null && insp.compatibilityReport != null) {
                val compat = insp.compatibilityReport
                events.add(
                    PluginDiagnosticEvent(
                        id = "compat_probe",
                        title = "Active Package Readiness",
                        severity = if (compat.readinessScore >= 80) PluginSeverity.SUCCESS else PluginSeverity.WARNING,
                        detail = "${insp.displayName}: ${compat.readinessScore}/100 (${compat.tier.name})"
                    )
                )
            }
            return events
        }
    }

    override val reports: ReportsInterface = object : ReportsInterface {
        override suspend fun getRecentReports(limit: Int): List<PluginReportSummary> {
            requirePermission(PluginPermission.READ_REPORTS)
            val items = historyRepository.historyItems.first()
            return items.take(limit.coerceIn(1, 50)).map { entity ->
                PluginReportSummary(
                    fileName = entity.displayName,
                    profileName = entity.profileName,
                    originalBytes = entity.originalBytes,
                    compressedBytes = entity.compressedBytes,
                    savedBytes = entity.savedBytes,
                    savedPercent = entity.savedPercent,
                    timestampMillis = entity.timestampMs
                )
            }
        }

        override suspend fun getTotalSavedBytes(): Long {
            requirePermission(PluginPermission.READ_REPORTS)
            return historyRepository.totalSavedBytes.first()
        }

        override fun getLatestAuditJson(): String? {
            requirePermission(PluginPermission.READ_REPORTS)
            return hostStateBridge.latestReport()?.toAuditJson()
        }
    }

    override val notifications: NotificationInterface = object : NotificationInterface {
        override fun postNotification(title: String, message: String, severity: PluginSeverity): Boolean {
            requirePermission(PluginPermission.SEND_NOTIFICATIONS)
            var postedSystem = false
            val canPostSystem = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

            if (canPostSystem) {
                runCatching {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    if (nm != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val ch = NotificationChannel(
                                CHANNEL_ID,
                                "PakForge Plugin SDK",
                                NotificationManager.IMPORTANCE_DEFAULT
                            )
                            nm.createNotificationChannel(ch)
                        }
                        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle("[$pluginName] $title")
                            .setContentText(message)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                            .setAutoCancel(true)
                            .build()
                        nm.notify(notifIdCounter.incrementAndGet().toInt(), notif)
                        postedSystem = true
                    }
                }
            }

            hostStateBridge.recordNotification(
                PluginNotificationRecord(
                    id = notifIdCounter.incrementAndGet(),
                    pluginId = pluginId,
                    pluginName = pluginName,
                    title = title,
                    message = message,
                    severity = severity,
                    dispatchedToSystemTray = postedSystem
                )
            )
            return true
        }
    }

    override val tools: HostToolsInterface = object : HostToolsInterface {
        override fun computeSha256(bytes: ByteArray): String {
            requirePermission(PluginPermission.ACCESS_HOST_TOOLS)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }

        override fun runCompatibilitySummary(): String {
            requirePermission(PluginPermission.ACCESS_HOST_TOOLS)
            val insp = hostStateBridge.currentInspection()
                ?: return "No active package loaded in Forge. Load an APK to run compatibility diagnostics."
            val compat = insp.compatibilityReport
                ?: return "${insp.displayName}: compatibility report unavailable."
            return "${insp.displayName} • ${compat.tier.name} (${compat.readinessScore}/100) • " +
                compat.checks.joinToString(" | ") { it.title }
        }

        override fun applyCompressionPreset(rule: PluginCompressionRule): Boolean {
            requirePermission(PluginPermission.ACCESS_HOST_TOOLS)
            return hostStateBridge.applyCompressionRule(rule)
        }

        override fun queryElevatedShellUid(): Int? {
            requirePermission(PluginPermission.PRIVILEGED_SHELL_READ)
            return shizukuBridge.bridgeSnapshot.value.activeUid
        }
    }
}

fun CompressionConfig.applyPluginRule(rule: PluginCompressionRule): CompressionConfig {
    val nextProfile = rule.profile?.let {
        runCatching { CompressionProfile.valueOf(it) }.getOrNull()
    } ?: profile
    return copy(
        profile = nextProfile,
        pruneUnusedLocales = rule.pruneUnusedLocales ?: pruneUnusedLocales,
        keptLocales = rule.keptLocales?.takeIf { it.isNotEmpty() } ?: keptLocales,
        optimizeLargeImages = rule.optimizeLargeImages ?: optimizeLargeImages,
        mergeSplitsIntoUniversal = rule.mergeSplitsIntoUniversal ?: mergeSplitsIntoUniversal,
        stripSharedUserId = rule.stripSharedUserId ?: stripSharedUserId,
        patchStaticLibCertDigest = rule.patchStaticLibCertDigest ?: patchStaticLibCertDigest,
        bundleCompanionStaticLibs = rule.bundleCompanionStaticLibs ?: bundleCompanionStaticLibs
    )
}
