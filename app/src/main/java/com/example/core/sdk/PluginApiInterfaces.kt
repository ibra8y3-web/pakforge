package com.example.core.sdk

enum class PluginSeverity {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

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

data class PluginNotificationRecord(
    val id: Long,
    val pluginId: String,
    val pluginName: String,
    val title: String,
    val message: String,
    val severity: PluginSeverity,
    val dispatchedToSystemTray: Boolean,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class PluginExecutionOutput(
    val ok: Boolean,
    val summary: String,
    val traces: List<String> = emptyList()
)

/**
 * Unified Hardware & Device Telemetry Interface.
 * Permission required: [PluginPermission.READ_DEVICE_TELEMETRY]
 */
interface DeviceInterface {
    fun getDeviceTelemetry(): PluginDeviceSnapshot
    fun getRecommendedBufferKb(): Int
}

/**
 * Unified Engine & System Diagnostics Interface.
 * Permission required: [PluginPermission.READ_DIAGNOSTICS]
 */
interface DiagnosticsInterface {
    fun getPrivilegedBackendsSummary(): PluginPrivilegedSnapshot
    fun getCurrentPackageDiagnostics(): PluginPackageDiagnostics?
    fun getEngineHealthChecks(): List<PluginDiagnosticEvent>
}

/**
 * Unified Compression Reports & Audit History Interface.
 * Permission required: [PluginPermission.READ_REPORTS]
 */
interface ReportsInterface {
    suspend fun getRecentReports(limit: Int = 10): List<PluginReportSummary>
    suspend fun getTotalSavedBytes(): Long
    fun getLatestAuditJson(): String?
}

/**
 * Unified Notification Dispatch Interface.
 * Permission required: [PluginPermission.SEND_NOTIFICATIONS]
 */
interface NotificationInterface {
    fun postNotification(
        title: String,
        message: String,
        severity: PluginSeverity = PluginSeverity.INFO
    ): Boolean
}

/**
 * Unified Permission-Gated Host Tools Interface.
 * Permissions required: [PluginPermission.ACCESS_HOST_TOOLS] / [PluginPermission.PRIVILEGED_SHELL_READ]
 */
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

/**
 * Unified entry contract implemented by every PakForge plugin.
 */
interface PakForgePlugin {
    val manifest: PluginManifest
    suspend fun onInitialize(ctx: PluginHostContext) {}
    suspend fun execute(ctx: PluginHostContext): PluginExecutionOutput
}
