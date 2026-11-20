package com.example.core.sdk

import android.content.Context
import com.example.core.engine.formatBytes
import com.example.core.shizuku.ShizukuBridgeManager
import com.example.core.telemetry.DeviceTelemetryProvider
import com.example.data.local.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class PluginManager(
    private val context: Context,
    private val telemetryProvider: DeviceTelemetryProvider,
    private val shizukuBridge: ShizukuBridgeManager,
    private val historyRepository: HistoryRepository
) {
    private val registryFile: File
        get() = File(context.filesDir, "pakforge_plugins_registry.json")

    val securityEngine: PluginSecurityEngine = PluginSecurityEngine(context)

    @Volatile
    private var hostStateBridge: PluginHostStateBridge? = null

    private val _plugins = MutableStateFlow<List<PluginRecord>>(emptyList())
    val plugins: StateFlow<List<PluginRecord>> = _plugins.asStateFlow()

    private val _storeCatalog = MutableStateFlow<List<StorePluginListing>>(emptyList())
    val storeCatalog: StateFlow<List<StorePluginListing>> = _storeCatalog.asStateFlow()

    private val _smartRecommendations = MutableStateFlow<List<SmartPluginRecommendation>>(emptyList())
    val smartRecommendations: StateFlow<List<SmartPluginRecommendation>> = _smartRecommendations.asStateFlow()

    private val _notifications = MutableStateFlow<List<PluginNotificationRecord>>(emptyList())
    val notifications: StateFlow<List<PluginNotificationRecord>> = _notifications.asStateFlow()

    private val _sandboxTraces = MutableStateFlow<List<String>>(emptyList())
    val sandboxTraces: StateFlow<List<String>> = _sandboxTraces.asStateFlow()

    init {
        loadFromDisk()
        _storeCatalog.value = PluginStoreCatalog.buildSignedCatalog(securityEngine)
        refreshSmartRecommendations()
    }

    fun attachHostStateBridge(bridge: PluginHostStateBridge) {
        hostStateBridge = bridge
        refreshSmartRecommendations()
    }

    fun refreshSmartRecommendations() {
        val manifests = _storeCatalog.value.map { it.manifest } +
            _plugins.value.filterNot { p -> _storeCatalog.value.any { it.manifest.id == p.manifest.id } }
                .map { it.manifest }
        _smartRecommendations.value = SmartPluginRecommender.evaluate(
            manifests = manifests,
            inspection = hostStateBridge?.currentInspection(),
            telemetry = telemetryProvider.snapshot(),
            bridge = shizukuBridge.bridgeSnapshot.value
        )
    }

    fun recordNotification(record: PluginNotificationRecord) {
        _notifications.value = (listOf(record) + _notifications.value).take(30)
    }

    fun clearNotifications() {
        _notifications.value = emptyList()
    }

    private fun loadFromDisk() {
        val persistedMap = LinkedHashMap<String, PluginRecord>()
        if (registryFile.exists()) {
            runCatching {
                val root = JSONArray(registryFile.readText())
                for (i in 0 until root.length()) {
                    val item = root.optJSONObject(i) ?: continue
                    val manifestJson = item.optJSONObject("manifest")?.toString() ?: continue
                    val manifest = PluginManifestCodec.parseJson(manifestJson).getOrNull() ?: continue
                    val enabled = item.optBoolean("enabled", true)
                    val builtIn = item.optBoolean("isBuiltInSample", false)
                    val trust = runCatching {
                        SignatureTrustLevel.valueOf(item.optString("signatureTrust", SignatureTrustLevel.BUILTIN.name))
                    }.getOrDefault(SignatureTrustLevel.BUILTIN)
                    val fingerprint = item.optString("signerFingerprint").takeIf { it.isNotBlank() }
                    val grantedArr = item.optJSONArray("grantedPermissions")
                    val granted = if (grantedArr != null) {
                        buildSet<PluginPermission> {
                            for (j in 0 until grantedArr.length()) {
                                PluginPermission.fromKeyOrName(grantedArr.optString(j))?.let { add(it) }
                            }
                        }
                    } else {
                        manifest.permissions.toSet()
                    }
                    persistedMap[manifest.id] = PluginRecord(
                        manifest = manifest,
                        enabled = enabled,
                        grantedPermissions = granted.intersect(manifest.permissions.toSet()),
                        isBuiltInSample = builtIn,
                        signatureTrust = trust,
                        signerFingerprint = fingerprint,
                        installedAtEpochMs = item.optLong("installedAtEpochMs", System.currentTimeMillis()),
                        lastExecutionSummary = item.optString("lastExecutionSummary").takeIf { it.isNotBlank() },
                        lastExecutionOk = if (item.has("lastExecutionOk")) item.optBoolean("lastExecutionOk") else null,
                        lastExecutionEpochMs = if (item.has("lastExecutionEpochMs")) item.optLong("lastExecutionEpochMs") else null
                    )
                }
            }
        }

        val merged = LinkedHashMap<String, PluginRecord>()
        for (sample in BuiltInSamplePlugins.allBuiltIns) {
            val existing = persistedMap[sample.manifest.id]
            merged[sample.manifest.id] = if (existing != null) {
                existing.copy(manifest = sample.manifest, isBuiltInSample = true, signatureTrust = SignatureTrustLevel.BUILTIN)
            } else {
                PluginRecord(
                    manifest = sample.manifest,
                    enabled = true,
                    grantedPermissions = sample.manifest.permissions.toSet(),
                    isBuiltInSample = true,
                    signatureTrust = SignatureTrustLevel.BUILTIN
                )
            }
        }
        persistedMap.values.filter { !it.isBuiltInSample }.forEach { custom ->
            merged[custom.manifest.id] = custom
        }

        _plugins.value = merged.values.toList()
        persistToDisk()
    }

    private fun persistToDisk() {
        runCatching {
            val arr = JSONArray()
            _plugins.value.forEach { rec ->
                val obj = JSONObject().apply {
                    put("manifest", JSONObject(PluginManifestCodec.toJson(rec.manifest, indent = 0)))
                    put("enabled", rec.enabled)
                    put("isBuiltInSample", rec.isBuiltInSample)
                    put("signatureTrust", rec.signatureTrust.name)
                    rec.signerFingerprint?.let { put("signerFingerprint", it) }
                    put("installedAtEpochMs", rec.installedAtEpochMs)
                    rec.lastExecutionSummary?.let { put("lastExecutionSummary", it) }
                    rec.lastExecutionOk?.let { put("lastExecutionOk", it) }
                    rec.lastExecutionEpochMs?.let { put("lastExecutionEpochMs", it) }
                    put("grantedPermissions", JSONArray().apply {
                        rec.grantedPermissions.forEach { put(it.key) }
                    })
                }
                arr.put(obj)
            }
            registryFile.writeText(arr.toString(2))
        }
    }

    fun togglePluginEnabled(pluginId: String) {
        _plugins.value = _plugins.value.map { rec ->
            if (rec.manifest.id == pluginId) rec.copy(enabled = !rec.enabled) else rec
        }
        persistToDisk()
    }

    fun togglePluginPermission(pluginId: String, permission: PluginPermission) {
        _plugins.value = _plugins.value.map { rec ->
            if (rec.manifest.id != pluginId) {
                rec
            } else {
                val nextPerms = if (permission in rec.grantedPermissions) {
                    rec.grantedPermissions - permission
                } else {
                    rec.grantedPermissions + permission
                }
                rec.copy(grantedPermissions = nextPerms)
            }
        }
        persistToDisk()
    }

    fun inspectIncomingBundle(rawBytes: ByteArray, displayName: String): PluginSecurityScanResult {
        return securityEngine.inspectAndVerifyIncomingBytes(rawBytes, displayName)
    }

    fun installScannedPlugin(
        scan: PluginSecurityScanResult,
        selectedPermissions: Set<PluginPermission>? = null
    ): Result<PluginRecord> {
        if (!scan.canInstall || scan.manifest == null) {
            val err = scan.findings.firstOrNull { !it.passed }?.detail ?: "Security scan rejected this plugin"
            return Result.failure(SecurityException(err))
        }
        val manifest = scan.manifest
        val granted = (selectedPermissions ?: manifest.permissions.toSet())
            .intersect(manifest.permissions.toSet())

        val newRec = PluginRecord(
            manifest = manifest,
            enabled = true,
            grantedPermissions = granted,
            isBuiltInSample = false,
            signatureTrust = scan.signatureTrust,
            signerFingerprint = scan.signerFingerprintSha256
        )
        _plugins.value = _plugins.value.filterNot { it.manifest.id == manifest.id } + newRec
        persistToDisk()
        refreshSmartRecommendations()
        _sandboxTraces.value = listOf(
            "✔ Installed '${manifest.name}' v${manifest.version} [${scan.signatureTrust.badgeEn}] • Score=${scan.safetyScore}/100"
        )
        return Result.success(newRec)
    }

    fun createAndSignDeveloperBundle(manifest: PluginManifest): Result<Pair<ByteArray, PluginSecurityScanResult>> {
        val validation = PluginManifestCodec.validate(manifest)
        if (!validation.isValid) {
            return Result.failure(IllegalArgumentException(validation.errors.joinToString("; ")))
        }
        val pfpakBytes = securityEngine.signAndPackPfpak(manifest, useOfficialKey = false)
        val scan = securityEngine.inspectAndVerifyIncomingBytes(pfpakBytes, "${manifest.id}.pfpak")
        return Result.success(pfpakBytes to scan)
    }

    fun importManifestJson(rawJson: String): Result<PluginManifest> {
        val scan = securityEngine.inspectAndVerifyIncomingBytes(
            rawJson.toByteArray(Charsets.UTF_8),
            "pakforge-plugin.json"
        )
        val rec = installScannedPlugin(scan).getOrElse { return Result.failure(it) }
        return Result.success(rec.manifest)
    }

    fun removeCustomPlugin(pluginId: String) {
        _plugins.value = _plugins.value.filterNot { it.manifest.id == pluginId && !it.isBuiltInSample }
        persistToDisk()
        refreshSmartRecommendations()
    }

    suspend fun runPlugin(pluginId: String): PluginExecutionOutput = withContext(Dispatchers.IO) {
        val record = _plugins.value.firstOrNull { it.manifest.id == pluginId }
            ?: return@withContext PluginExecutionOutput(false, "Plugin not found: $pluginId")

        if (!record.enabled) {
            val out = PluginExecutionOutput(false, "Plugin '${record.manifest.name}' is disabled")
            _sandboxTraces.value = listOf(out.summary)
            return@withContext out
        }

        val validation = PluginManifestCodec.validate(record.manifest)
        if (!validation.isValid) {
            val out = PluginExecutionOutput(false, "Manifest validation failed: ${validation.errors.joinToString()}")
            _sandboxTraces.value = listOf(out.summary)
            return@withContext out
        }

        val bridge = hostStateBridge ?: object : PluginHostStateBridge {
            override fun currentInspection() = null
            override fun latestReport() = null
            override fun applyCompressionRule(rule: PluginCompressionRule) = false
            override fun recordNotification(record: PluginNotificationRecord) {
                this@PluginManager.recordNotification(record)
            }
        }

        val ctx = HostPluginContextImpl(
            context = context,
            pluginId = record.manifest.id,
            pluginName = record.manifest.name,
            grantedPermissions = record.grantedPermissions,
            telemetryProvider = telemetryProvider,
            shizukuBridge = shizukuBridge,
            historyRepository = historyRepository,
            hostStateBridge = bridge
        )

        val traces = ArrayList<String>()
        traces.add("▶ Running ${record.manifest.id} v${record.manifest.version} [${record.signatureTrust.badgeEn}]")
        traces.add("Granted permissions: ${record.grantedPermissions.joinToString { it.name }.ifEmpty { "NONE" }}")

        val output = try {
            val builtIn = BuiltInSamplePlugins.findById(record.manifest.id)
            if (builtIn != null && record.manifest.actions.isEmpty()) {
                builtIn.onInitialize(ctx)
                builtIn.execute(ctx)
            } else {
                executeManifestActionPipeline(record.manifest, ctx)
            }
        } catch (sec: PluginSecurityException) {
            PluginExecutionOutput(
                ok = false,
                summary = "Blocked by Permission Guard: ${sec.permission.key}",
                traces = listOf("✖ SECURITY DENIAL: ${sec.message}")
            )
        } catch (t: Throwable) {
            PluginExecutionOutput(
                ok = false,
                summary = "Execution error: ${t.localizedMessage ?: t::class.java.simpleName}",
                traces = listOf("✖ ERROR: ${t.stackTraceToString().lineSequence().take(3).joinToString(" ")}")
            )
        }

        val combinedTraces = traces + output.traces
        _sandboxTraces.value = combinedTraces
        _plugins.value = _plugins.value.map { rec ->
            if (rec.manifest.id == pluginId) {
                rec.copy(
                    lastExecutionSummary = output.summary,
                    lastExecutionOk = output.ok,
                    lastExecutionEpochMs = System.currentTimeMillis()
                )
            } else rec
        }
        persistToDisk()
        output.copy(traces = combinedTraces)
    }

    private suspend fun executeManifestActionPipeline(
        manifest: PluginManifest,
        ctx: PluginHostContext
    ): PluginExecutionOutput {
        val traces = ArrayList<String>()
        val steps = if (manifest.actions.isNotEmpty()) {
            manifest.actions
        } else {
            // Fallback inference from declared permissions
            buildList {
                if (PluginPermission.READ_DEVICE_TELEMETRY in manifest.permissions) add(PluginActionStep.CHECK_DEVICE_HEADROOM)
                if (PluginPermission.READ_DIAGNOSTICS in manifest.permissions) add(PluginActionStep.AUDIT_SHELL_BACKEND)
                if (PluginPermission.READ_DIAGNOSTICS in manifest.permissions) add(PluginActionStep.INSPECT_ACTIVE_PACKAGE)
                if (manifest.compressionRule != null && PluginPermission.ACCESS_HOST_TOOLS in manifest.permissions) {
                    add(PluginActionStep.APPLY_COMPRESSION_RULE)
                }
                if (PluginPermission.READ_REPORTS in manifest.permissions) add(PluginActionStep.READ_SAVINGS_HISTORY)
                if (PluginPermission.SEND_NOTIFICATIONS in manifest.permissions) add(PluginActionStep.DISPATCH_NOTIFICATION)
            }
        }

        var lastNotifLine = "Executed ${manifest.name} (${steps.size} pipeline steps)"
        for (step in steps) {
            when (step) {
                PluginActionStep.CHECK_DEVICE_HEADROOM -> {
                    val hw = ctx.device.getDeviceTelemetry()
                    val line = "DeviceInterface -> ${hw.manufacturerModel}: ${hw.freeRamMb}/${hw.totalRamMb} MB free, Buffer=${hw.recommendedBufferKb}KB"
                    traces.add(line)
                    lastNotifLine = "${hw.freeRamMb} MB free • ABI ${hw.primaryAbi}"
                }
                PluginActionStep.AUDIT_SHELL_BACKEND -> {
                    val diag = ctx.diagnostics.getPrivilegedBackendsSummary()
                    val uid = if (PluginPermission.PRIVILEGED_SHELL_READ in manifest.permissions) {
                        ctx.tools.queryElevatedShellUid()
                    } else null
                    val line = "DiagnosticsInterface -> Backend=${diag.activeBackend} (${diag.activeDetail}), UID=${uid ?: "standard"}"
                    traces.add(line)
                    lastNotifLine = "Backend=${diag.activeBackend} (UID=${uid ?: "std"})"
                }
                PluginActionStep.INSPECT_ACTIVE_PACKAGE -> {
                    val pkg = ctx.diagnostics.getCurrentPackageDiagnostics()
                    if (pkg != null) {
                        val line = "PackageBlueprint -> ${pkg.displayName} (${formatBytes(pkg.archiveBytes)}), splits=${pkg.splitCount}, locales=${pkg.detectedLocales.size}, sharedUid=${pkg.hasSharedUserId}, score=${pkg.compatibilityScore ?: "N/A"}"
                        traces.add(line)
                        lastNotifLine = "${pkg.displayName} • Score=${pkg.compatibilityScore ?: "N/A"}"
                    } else {
                        traces.add("PackageBlueprint -> No active APK loaded in Forge")
                    }
                }
                PluginActionStep.APPLY_COMPRESSION_RULE -> {
                    val rule = manifest.compressionRule
                    if (rule != null) {
                        val applied = ctx.tools.applyCompressionPreset(rule)
                        traces.add("HostToolsInterface.applyCompressionPreset -> applied=$applied (profile=${rule.profile ?: "keep"}, locales=${rule.keptLocales ?: "all"}, merge=${rule.mergeSplitsIntoUniversal ?: false}, stripUid=${rule.stripSharedUserId ?: false})")
                    } else {
                        traces.add("HostToolsInterface -> No compressionRule specified")
                    }
                }
                PluginActionStep.COMPUTE_PACKAGE_DIGEST -> {
                    val summary = ctx.tools.runCompatibilitySummary()
                    val sha = ctx.tools.computeSha256(summary.toByteArray()).take(16)
                    traces.add("HostToolsInterface -> Compatibility='$summary' • SHA256=$sha…")
                }
                PluginActionStep.READ_SAVINGS_HISTORY -> {
                    val totalSaved = ctx.reports.getTotalSavedBytes()
                    val recent = ctx.reports.getRecentReports(3)
                    traces.add("ReportsInterface -> Total reclaimed=${formatBytes(totalSaved)} (${recent.size} recent runs)")
                }
                PluginActionStep.DISPATCH_NOTIFICATION -> {
                    ctx.notifications.postNotification(
                        title = manifest.name,
                        message = lastNotifLine,
                        severity = PluginSeverity.SUCCESS
                    )
                    traces.add("NotificationInterface -> Dispatched '$lastNotifLine'")
                }
            }
        }

        return PluginExecutionOutput(
            ok = true,
            summary = "${manifest.name} v${manifest.version} completed (${steps.size} steps)",
            traces = traces
        )
    }
}
