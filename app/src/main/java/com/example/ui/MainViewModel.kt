package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.PakForgeApp
import com.example.core.engine.CompatibilityAnalyzer
import com.example.core.engine.CompressionConfig
import com.example.core.engine.CompressionProfile
import com.example.core.engine.CompressionProgress
import com.example.core.engine.CompressionReport
import com.example.core.engine.InstallIdentityMode
import com.example.core.engine.InstalledAppItem
import com.example.core.engine.PackageCategory
import com.example.core.engine.PackageInspectionResult
import com.example.core.engine.SmartPackageEngine
import com.example.core.installer.InstallSessionBus
import com.example.core.installer.InstallStatusEvent
import com.example.core.installer.PakForgeInstaller
import com.example.core.privileged.PrivilegedBackendKind
import com.example.core.privileged.PrivilegedBridgeSnapshot
import com.example.core.sdk.DeveloperKeyInfo
import com.example.core.sdk.PluginCompressionRule
import com.example.core.sdk.PluginHostStateBridge
import com.example.core.sdk.PluginManager
import com.example.core.sdk.PluginManifest
import com.example.core.sdk.PluginNotificationRecord
import com.example.core.sdk.PluginPermission
import com.example.core.sdk.PluginRecord
import com.example.core.sdk.PluginSdkZipExporter
import com.example.core.sdk.PluginSecurityScanResult
import com.example.core.sdk.SmartPluginRecommendation
import com.example.core.sdk.StorePluginListing
import com.example.core.sdk.applyPluginRule
import com.example.core.shizuku.ShizukuBridgeManager
import com.example.core.shizuku.ShizukuStatus
import com.example.core.telemetry.DeviceTelemetryProvider
import com.example.core.telemetry.DeviceTelemetryState
import com.example.data.local.CompressionHistoryEntity
import com.example.data.local.HistoryRepository
import com.example.data.preferences.SettingsRepository
import com.example.data.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

sealed interface ForgePipelineState {
    data object Idle : ForgePipelineState
    data class Inspecting(val label: String) : ForgePipelineState
    data class Ready(val inspection: PackageInspectionResult) : ForgePipelineState
    data class Compressing(
        val inspection: PackageInspectionResult,
        val progress: CompressionProgress
    ) : ForgePipelineState
    data class Completed(
        val inspection: PackageInspectionResult,
        val report: CompressionReport
    ) : ForgePipelineState
    data class Error(val message: String) : ForgePipelineState
}

sealed interface ApkInstallUiState {
    data object Idle : ApkInstallUiState
    data object PreparingSession : ApkInstallUiState
    data object WaitingUserConfirm : ApkInstallUiState
    data class Installed(val packageName: String?) : ApkInstallUiState
    data class Failed(
        val reason: String,
        val isConflictOrSignatureMismatch: Boolean = false
    ) : ApkInstallUiState
}

data class InstallOptionsSheetState(
    val originalPackageId: String?,
    val currentTargetPackageId: String?,
    val isOriginalInstalled: Boolean,
    val isTargetInstalled: Boolean,
    val isSystemApp: Boolean,
    val signaturesMatch: Boolean,
    val isSignatureConflict: Boolean,
    val manifestRuntimeRisks: List<String> = emptyList(),
    val hasCompanionLibs: Boolean = false,
    val shizukuReady: Boolean = false,
    val failureReason: String? = null
)

enum class PakForgeTab {
    FORGE,
    INSPECTOR,
    HISTORY,
    SETTINGS
}

sealed interface UiEvent {
    data object SavedToDevice : UiEvent
    data object JsonCopied : UiEvent
    data class LaunchIntent(val intent: Intent) : UiEvent
    data class LaunchUninstallIntent(val intent: Intent) : UiEvent
    data class ErrorToast(val message: String) : UiEvent
}

class MainViewModel(
    application: Application,
    private val engine: SmartPackageEngine,
    private val installer: PakForgeInstaller,
    private val shizukuBridge: ShizukuBridgeManager,
    private val pluginManager: PluginManager,
    private val telemetryProvider: DeviceTelemetryProvider,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    val devicePrimaryAbi: String = engine.devicePrimaryAbi()

    val shizukuStatus: StateFlow<ShizukuStatus> = shizukuBridge.status
    val bridgeSnapshot: StateFlow<PrivilegedBridgeSnapshot> = shizukuBridge.bridgeSnapshot
    val plugins: StateFlow<List<PluginRecord>> = pluginManager.plugins
    val storeCatalog: StateFlow<List<StorePluginListing>> = pluginManager.storeCatalog
    val smartRecommendations: StateFlow<List<SmartPluginRecommendation>> = pluginManager.smartRecommendations
    val pluginNotifications: StateFlow<List<PluginNotificationRecord>> = pluginManager.notifications
    val sandboxTraces: StateFlow<List<String>> = pluginManager.sandboxTraces
    val developerKeyInfo: DeveloperKeyInfo get() = pluginManager.securityEngine.developerKeyInfo()

    private val _pendingSecurityReview = MutableStateFlow<PluginSecurityScanResult?>(null)
    val pendingSecurityReview: StateFlow<PluginSecurityScanResult?> = _pendingSecurityReview.asStateFlow()

    @Volatile
    private var pendingExportPfpakBytes: ByteArray? = null

    private val _pipelineState = MutableStateFlow<ForgePipelineState>(ForgePipelineState.Idle)
    val pipelineState: StateFlow<ForgePipelineState> = _pipelineState.asStateFlow()

    private val _installState = MutableStateFlow<ApkInstallUiState>(ApkInstallUiState.Idle)
    val installState: StateFlow<ApkInstallUiState> = _installState.asStateFlow()

    private val _installOptionsSheet = MutableStateFlow<InstallOptionsSheetState?>(null)
    val installOptionsSheet: StateFlow<InstallOptionsSheetState?> = _installOptionsSheet.asStateFlow()

    private val _isRepackingClone = MutableStateFlow(false)
    val isRepackingClone: StateFlow<Boolean> = _isRepackingClone.asStateFlow()

    private var pendingUninstallPkg: String? = null

    private val _canInstallPackages = MutableStateFlow(installer.canRequestPackageInstalls())
    val canInstallPackages: StateFlow<Boolean> = _canInstallPackages.asStateFlow()

    private val _telemetry = MutableStateFlow(telemetryProvider.snapshot())
    val telemetry: StateFlow<DeviceTelemetryState> = _telemetry.asStateFlow()

    private val _activeConfig = MutableStateFlow(CompressionConfig())
    val activeConfig: StateFlow<CompressionConfig> = _activeConfig.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _inspectorSearch = MutableStateFlow("")
    val inspectorSearch: StateFlow<String> = _inspectorSearch.asStateFlow()

    private val _inspectorCategory = MutableStateFlow<PackageCategory?>(null)
    val inspectorCategory: StateFlow<PackageCategory?> = _inspectorCategory.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>()
    val events = _events.asSharedFlow()

    val userPreferences: StateFlow<UserPreferences> = settingsRepository.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val historyItems: StateFlow<List<CompressionHistoryEntity>> = historyRepository.historyItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalSavedBytes: StateFlow<Long> = historyRepository.totalSavedBytes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private var configInitialized = false

    init {
        pluginManager.attachHostStateBridge(object : PluginHostStateBridge {
            override fun currentInspection(): PackageInspectionResult? {
                return when (val st = _pipelineState.value) {
                    is ForgePipelineState.Ready -> st.inspection
                    is ForgePipelineState.Compressing -> st.inspection
                    is ForgePipelineState.Completed -> st.inspection
                    else -> null
                }
            }

            override fun latestReport(): CompressionReport? {
                return (_pipelineState.value as? ForgePipelineState.Completed)?.report
            }

            override fun applyCompressionRule(rule: PluginCompressionRule): Boolean {
                updateConfig { it.applyPluginRule(rule) }
                return true
            }

            override fun recordNotification(record: PluginNotificationRecord) {
                pluginManager.recordNotification(record)
            }
        })

        viewModelScope.launch {
            settingsRepository.preferences.collect { prefs ->
                if (!configInitialized) {
                    _activeConfig.value = prefs.defaultConfig
                    configInitialized = true
                }
            }
        }

        viewModelScope.launch {
            InstallSessionBus.events.collect { event ->
                when (event) {
                    is InstallStatusEvent.UserConfirmationRequired -> {
                        _installState.value = ApkInstallUiState.WaitingUserConfirm
                        _events.emit(UiEvent.LaunchIntent(event.confirmIntent))
                    }
                    is InstallStatusEvent.Success -> {
                        _installState.value = ApkInstallUiState.Installed(event.packageName)
                        _installOptionsSheet.value = null
                    }
                    is InstallStatusEvent.Failure -> {
                        _installState.value = ApkInstallUiState.Failed(
                            reason = event.reason,
                            isConflictOrSignatureMismatch = event.isConflictOrSignatureMismatch
                        )
                        if (event.isConflictOrSignatureMismatch) {
                            openInstallOptionsSheet(failureReason = event.reason)
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            while (isActive) {
                _telemetry.value = telemetryProvider.snapshot()
                delay(2500L)
            }
        }
    }

    fun refreshInstallPermission() {
        _canInstallPackages.value = installer.canRequestPackageInstalls()
        refreshShizukuStatus()
    }

    fun refreshShizukuStatus() {
        val next = shizukuBridge.refreshStatus()
        val isReady = next is ShizukuStatus.Ready
        val cur = _pipelineState.value
        val curInspection = when (cur) {
            is ForgePipelineState.Ready -> cur.inspection
            is ForgePipelineState.Completed -> cur.inspection
            else -> null
        }
        if (curInspection != null && curInspection.compatibilityReport != null) {
            val status = installer.inspectTargetStatus(curInspection.packageId, curInspection.packageId)
            val updatedCompat = CompatibilityAnalyzer.analyze(
                inspection = curInspection,
                isInstalledWithDifferentSig = status.isSignatureConflict,
                isSystemApp = status.isSystemApp,
                shizukuReady = isReady
            )
            val updatedInsp = curInspection.copy(compatibilityReport = updatedCompat)
            _pipelineState.value = when (cur) {
                is ForgePipelineState.Ready -> ForgePipelineState.Ready(updatedInsp)
                is ForgePipelineState.Completed -> ForgePipelineState.Completed(updatedInsp, cur.report)
                else -> cur
            }
        }
    }

    fun requestShizukuPermission() {
        val launched = shizukuBridge.requestShizukuPermission()
        if (!launched) {
            openShizukuOrDevSettings()
        }
    }

    fun selectPrivilegedBackend(mode: PrivilegedBackendKind) {
        shizukuBridge.setPreferredBackend(mode)
        pluginManager.refreshSmartRecommendations()
    }

    fun togglePluginEnabled(pluginId: String) {
        pluginManager.togglePluginEnabled(pluginId)
    }

    fun togglePluginPermission(pluginId: String, permission: PluginPermission) {
        pluginManager.togglePluginPermission(pluginId, permission)
    }

    fun runPluginInSandbox(pluginId: String) {
        viewModelScope.launch {
            val out = pluginManager.runPlugin(pluginId)
            _events.emit(UiEvent.ErrorToast(out.summary))
        }
    }

    fun refreshSmartPluginRecommendations() {
        pluginManager.refreshSmartRecommendations()
    }

    fun inspectStoreListingForInstall(listing: StorePluginListing) {
        val verified = pluginManager.inspectIncomingBundle(
            listing.signedPfpakBytes,
            "${listing.manifest.id}.pfpak"
        )
        _pendingSecurityReview.value = verified
    }

    fun importPluginManifestFromUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                        it.readBytes()
                    } ?: throw IllegalStateException("Cannot open plugin bundle or manifest file")
                }
                val name = uri.lastPathSegment ?: "imported.pfpak"
                pluginManager.inspectIncomingBundle(bytes, name)
            }.onSuccess { scan ->
                _pendingSecurityReview.value = scan
            }.onFailure { err ->
                _events.emit(UiEvent.ErrorToast(err.localizedMessage ?: "Failed scanning plugin file"))
            }
        }
    }

    fun confirmInstallScannedPlugin(selectedPermissions: Set<PluginPermission>) {
        val scan = _pendingSecurityReview.value ?: return
        pluginManager.installScannedPlugin(scan, selectedPermissions)
            .onSuccess { rec ->
                _pendingSecurityReview.value = null
                viewModelScope.launch {
                    _events.emit(UiEvent.ErrorToast("Installed ${rec.manifest.name} v${rec.manifest.version} (${rec.signatureTrust.badgeEn})"))
                }
            }
            .onFailure { err ->
                viewModelScope.launch {
                    _events.emit(UiEvent.ErrorToast(err.localizedMessage ?: "Security check blocked installation"))
                }
            }
    }

    fun dismissSecurityReview() {
        _pendingSecurityReview.value = null
    }

    fun installDeveloperManifestImmediately(manifest: PluginManifest) {
        pluginManager.createAndSignDeveloperBundle(manifest)
            .onSuccess { (_, scan) ->
                _pendingSecurityReview.value = scan
            }
            .onFailure { err ->
                viewModelScope.launch {
                    _events.emit(UiEvent.ErrorToast(err.localizedMessage ?: "Invalid plugin definition"))
                }
            }
    }

    fun prepareDeveloperPfpakForExport(manifest: PluginManifest): String? {
        return pluginManager.createAndSignDeveloperBundle(manifest)
            .onSuccess { (bytes, _) ->
                pendingExportPfpakBytes = bytes
            }
            .onFailure { err ->
                viewModelScope.launch {
                    _events.emit(UiEvent.ErrorToast(err.localizedMessage ?: "Cannot pack signed .pfpak bundle"))
                }
            }
            .getOrNull()?.let { "${manifest.id}-${manifest.version}.pfpak" }
    }

    fun savePendingPfpakToUri(destUri: Uri) {
        val bytes = pendingExportPfpakBytes ?: return
        pendingExportPfpakBytes = null
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(destUri)?.use { out ->
                        out.write(bytes)
                        out.flush()
                    } ?: throw IllegalStateException("Cannot write .pfpak destination")
                }
            }.onSuccess {
                _events.emit(UiEvent.SavedToDevice)
            }.onFailure { err ->
                _events.emit(UiEvent.ErrorToast(err.localizedMessage ?: "Export .pfpak failed"))
            }
        }
    }

    fun removeCustomPlugin(pluginId: String) {
        pluginManager.removeCustomPlugin(pluginId)
    }

    fun clearPluginNotifications() {
        pluginManager.clearNotifications()
    }

    fun exportPluginSdkZipToUri(destUri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(destUri)?.use { out ->
                        PluginSdkZipExporter.writeZip(out)
                    } ?: throw IllegalStateException("Cannot open destination ZIP")
                }
            }.onSuccess {
                _events.emit(UiEvent.SavedToDevice)
            }.onFailure { err ->
                _events.emit(UiEvent.ErrorToast(err.localizedMessage ?: "SDK export failed"))
            }
        }
    }

    fun openShizukuOrDevSettings() {
        val intent = shizukuBridge.buildOpenShizukuOrDevSettingsIntent() ?: return
        viewModelScope.launch {
            _events.emit(UiEvent.LaunchIntent(intent))
        }
    }

    fun openUnknownSourcesSettings() {
        viewModelScope.launch {
            _events.emit(UiEvent.LaunchIntent(installer.buildUnknownSourcesSettingsIntent()))
        }
    }

    fun openInstallOptionsSheet(failureReason: String? = null) {
        val completed = _pipelineState.value as? ForgePipelineState.Completed ?: return
        if (!completed.report.isApkArchive) return
        val status = installer.inspectTargetStatus(
            originalPkg = completed.report.packageId,
            targetPkg = completed.report.effectivePackageId ?: completed.report.packageId
        )
        _installOptionsSheet.value = InstallOptionsSheetState(
            originalPackageId = completed.report.packageId,
            currentTargetPackageId = completed.report.effectivePackageId ?: completed.report.packageId,
            isOriginalInstalled = status.isOriginalInstalled,
            isTargetInstalled = status.isTargetInstalled,
            isSystemApp = status.isSystemApp,
            signaturesMatch = status.signaturesMatch,
            isSignatureConflict = status.isSignatureConflict,
            manifestRuntimeRisks = completed.report.manifestRuntimeRisks,
            hasCompanionLibs = completed.report.companionLibraryOutputs.isNotEmpty(),
            shizukuReady = shizukuStatus.value is ShizukuStatus.Ready,
            failureReason = failureReason
        )
    }

    fun dismissInstallOptionsSheet() {
        _installOptionsSheet.value = null
    }

    fun installCompressedApk() {
        val completed = _pipelineState.value as? ForgePipelineState.Completed ?: return
        if (!completed.report.isApkArchive) return

        val shizukuIsReady = shizukuStatus.value is ShizukuStatus.Ready
        if (_activeConfig.value.preferShizukuInstaller && shizukuIsReady) {
            chooseInstallViaShizuku(allowDowngrade = true)
            return
        }

        if (!installer.canRequestPackageInstalls()) {
            _canInstallPackages.value = false
            openUnknownSourcesSettings()
            return
        }

        val status = installer.inspectTargetStatus(
            originalPkg = completed.report.packageId,
            targetPkg = completed.report.effectivePackageId ?: completed.report.packageId
        )
        if (status.isSignatureConflict || completed.report.companionLibraryOutputs.isNotEmpty()) {
            openInstallOptionsSheet()
        } else {
            startDirectInstallSession(buildInstallFilesList(completed.report))
        }
    }

    fun chooseUpdateExistingDirect() {
        val completed = _pipelineState.value as? ForgePipelineState.Completed ?: return
        _installOptionsSheet.value = null
        if (!installer.canRequestPackageInstalls()) {
            _canInstallPackages.value = false
            openUnknownSourcesSettings()
            return
        }
        startDirectInstallSession(buildInstallFilesList(completed.report))
    }

    fun chooseUninstallAndInstall() {
        val completed = _pipelineState.value as? ForgePipelineState.Completed ?: return
        val targetPkg = completed.report.effectivePackageId ?: completed.report.packageId ?: return
        _installOptionsSheet.value = null
        if (!installer.canRequestPackageInstalls()) {
            _canInstallPackages.value = false
            openUnknownSourcesSettings()
            return
        }
        pendingUninstallPkg = targetPkg
        viewModelScope.launch {
            _events.emit(UiEvent.LaunchUninstallIntent(installer.buildUninstallIntent(targetPkg)))
        }
    }

    fun onUninstallReturned() {
        val pkg = pendingUninstallPkg ?: return
        pendingUninstallPkg = null
        val completed = _pipelineState.value as? ForgePipelineState.Completed ?: return

        if (!installer.isPackageInstalled(pkg)) {
            startDirectInstallSession(buildInstallFilesList(completed.report))
        } else {
            val status = installer.inspectTargetStatus(completed.report.packageId, pkg)
            val msg = if (status.isSystemApp) {
                "System package $pkg could not be removed without Shizuku/ADB. Use Shizuku or Side-by-Side Clone instead."
            } else {
                "Uninstall was cancelled for $pkg."
            }
            _installState.value = ApkInstallUiState.Failed(msg, isConflictOrSignatureMismatch = true)
            openInstallOptionsSheet(failureReason = msg)
        }
    }

    fun chooseInstallSideBySideClone(suffix: String) {
        val completed = _pipelineState.value as? ForgePipelineState.Completed ?: return
        if (!completed.report.isApkArchive) return
        val shizukuIsReady = shizukuStatus.value is ShizukuStatus.Ready
        val useShizuku = _activeConfig.value.preferShizukuInstaller && shizukuIsReady

        if (!useShizuku && !installer.canRequestPackageInstalls()) {
            _canInstallPackages.value = false
            openUnknownSourcesSettings()
            return
        }

        val cleanSuffix = suffix.lowercase().filter { it.isLetterOrDigit() || it == '_' }
            .ifBlank { "pkf" }
            .let { if (it.first().isDigit()) "p$it" else it }

        viewModelScope.launch {
            val desiredPkg = completed.report.packageId?.let { "$it.$cleanSuffix" }
            val needsRepack = completed.report.effectivePackageId == completed.report.packageId ||
                (desiredPkg != null && completed.report.effectivePackageId != desiredPkg)

            val updatedReport = if (needsRepack) {
                _isRepackingClone.value = true
                _installState.value = ApkInstallUiState.PreparingSession
                val updated = runCatching {
                    engine.repackApkAsClone(
                        report = completed.report,
                        cloneSuffix = cleanSuffix,
                        customCloneLabel = _activeConfig.value.customCloneLabel,
                        stripSharedUserId = _activeConfig.value.stripSharedUserId,
                        tintCloneIcon = _activeConfig.value.tintCloneIcon
                    )
                }.onFailure { err ->
                    _isRepackingClone.value = false
                    _installState.value = ApkInstallUiState.Failed(err.localizedMessage ?: "Clone repack failed")
                }.getOrNull() ?: return@launch

                _isRepackingClone.value = false
                _pipelineState.value = ForgePipelineState.Completed(completed.inspection, updated)
                _activeConfig.value = _activeConfig.value.copy(
                    installIdentityMode = InstallIdentityMode.CLONE_NEW_PACKAGE,
                    cloneSuffix = cleanSuffix
                )
                updated
            } else {
                completed.report
            }

            _installOptionsSheet.value = null
            if (useShizuku) {
                installBundleWithShizukuInternal(updatedReport, allowDowngrade = true)
            } else {
                startDirectInstallSession(buildInstallFilesList(updatedReport))
            }
        }
    }

    fun chooseInstallViaShizuku(allowDowngrade: Boolean = true) {
        val completed = _pipelineState.value as? ForgePipelineState.Completed ?: return
        if (!completed.report.isApkArchive) return
        _installOptionsSheet.value = null
        viewModelScope.launch {
            installBundleWithShizukuInternal(completed.report, allowDowngrade = allowDowngrade)
        }
    }

    fun chooseInstallCompanionLibrariesStandard() {
        val completed = _pipelineState.value as? ForgePipelineState.Completed ?: return
        val firstCompanion = completed.report.companionLibraryOutputs.firstOrNull() ?: return
        _installOptionsSheet.value = null
        if (!installer.canRequestPackageInstalls()) {
            _canInstallPackages.value = false
            openUnknownSourcesSettings()
            return
        }
        startDirectInstallSession(firstCompanion.map { File(it) })
    }

    fun uninstallViaShizuku(packageName: String, keepData: Boolean = false) {
        _installOptionsSheet.value = null
        viewModelScope.launch {
            _installState.value = ApkInstallUiState.PreparingSession
            shizukuBridge.uninstallPackageForUser(packageName, keepData = keepData)
                .onSuccess { msg ->
                    _events.emit(UiEvent.ErrorToast(msg))
                    _installState.value = ApkInstallUiState.Idle
                    refreshShizukuStatus()
                }
                .onFailure { err ->
                    _installState.value = ApkInstallUiState.Failed(
                        reason = err.localizedMessage ?: "Shizuku uninstall failed",
                        isConflictOrSignatureMismatch = false
                    )
                }
        }
    }

    fun toggleSystemPackageEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            shizukuBridge.setPackageEnabled(packageName, enabled)
                .onSuccess {
                    _events.emit(UiEvent.ErrorToast(if (enabled) "Enabled $packageName" else "Disabled $packageName"))
                }
                .onFailure { err ->
                    _events.emit(UiEvent.ErrorToast(err.localizedMessage ?: "Failed changing package state"))
                }
        }
    }

    private suspend fun installBundleWithShizukuInternal(
        report: CompressionReport,
        allowDowngrade: Boolean
    ) {
        _installState.value = ApkInstallUiState.PreparingSession
        for (libPaths in report.companionLibraryOutputs) {
            val libFiles = libPaths.map { File(it) }.filter { it.exists() && it.canRead() }
            if (libFiles.isNotEmpty()) {
                val libResult = shizukuBridge.installApkBundle(libFiles, allowDowngrade = allowDowngrade)
                if (libResult.isFailure) {
                    val msg = libResult.exceptionOrNull()?.localizedMessage ?: "Companion static library install failed"
                    _installState.value = ApkInstallUiState.Failed(msg, isConflictOrSignatureMismatch = true)
                    openInstallOptionsSheet(failureReason = msg)
                    return
                }
            }
        }
        val mainFiles = buildInstallFilesList(report)
        shizukuBridge.installApkBundle(mainFiles, allowDowngrade = allowDowngrade)
            .onSuccess {
                _installState.value = ApkInstallUiState.Installed(report.effectivePackageId ?: report.packageId)
            }
            .onFailure { err ->
                val reason = err.localizedMessage ?: "Shizuku install failed"
                val isConflict = reason.contains("INCOMPATIBLE", ignoreCase = true) ||
                    reason.contains("DOWNGRADE", ignoreCase = true) ||
                    reason.contains("ALREADY_EXISTS", ignoreCase = true) ||
                    reason.contains("SHARED_USER", ignoreCase = true)
                _installState.value = ApkInstallUiState.Failed(reason, isConflictOrSignatureMismatch = isConflict)
                if (isConflict) {
                    openInstallOptionsSheet(failureReason = reason)
                }
            }
    }

    private fun buildInstallFilesList(report: CompressionReport): List<File> {
        return buildList {
            add(File(report.outputFilePath))
            report.splitOutputPaths.forEach { add(File(it)) }
        }
    }

    private fun startDirectInstallSession(apkFiles: List<File>) {
        viewModelScope.launch {
            _installState.value = ApkInstallUiState.PreparingSession
            runCatching {
                installer.startInstallSession(apkFiles)
            }.onFailure { err ->
                _installState.value = ApkInstallUiState.Failed(err.localizedMessage ?: "Failed to start installer session")
            }
        }
    }

    fun inspectUri(uri: Uri) {
        viewModelScope.launch {
            _installState.value = ApkInstallUiState.Idle
            _pipelineState.value = ForgePipelineState.Inspecting(uri.lastPathSegment ?: "package")
            val resetAbiConfig = _activeConfig.value.copy(allowedAbis = emptySet())
            _activeConfig.value = resetAbiConfig
            runCatching {
                val (stagedFile, displayName) = engine.stageUriToCache(uri)
                engine.inspectPackage(
                    file = stagedFile,
                    displayName = displayName,
                    config = resetAbiConfig,
                    shizukuReady = shizukuStatus.value is ShizukuStatus.Ready
                )
            }.onSuccess { result ->
                _pipelineState.value = ForgePipelineState.Ready(result)
                pluginManager.refreshSmartRecommendations()
            }.onFailure { err ->
                _pipelineState.value = ForgePipelineState.Error(err.localizedMessage ?: "Failed to read package")
            }
        }
    }

    fun inspectInstalledApp(item: InstalledAppItem) {
        viewModelScope.launch {
            _installState.value = ApkInstallUiState.Idle
            _pipelineState.value = ForgePipelineState.Inspecting(item.appName)
            val resetAbiConfig = _activeConfig.value.copy(allowedAbis = emptySet())
            _activeConfig.value = resetAbiConfig
            runCatching {
                val apkFile = File(item.apkPath)
                engine.inspectPackage(
                    file = apkFile,
                    displayName = "${item.appName}.apk",
                    config = resetAbiConfig,
                    splitPaths = item.splitApkPaths,
                    shizukuReady = shizukuStatus.value is ShizukuStatus.Ready
                )
            }.onSuccess { result ->
                _pipelineState.value = ForgePipelineState.Ready(result)
                pluginManager.refreshSmartRecommendations()
            }.onFailure { err ->
                _pipelineState.value = ForgePipelineState.Error(err.localizedMessage ?: "Failed to inspect APK")
            }
        }
    }

    fun inspectSelfPackage() {
        val app = getApplication<Application>()
        val sourceDir = app.applicationInfo.sourceDir
        val apkFile = File(sourceDir)
        val resetAbiConfig = _activeConfig.value.copy(allowedAbis = emptySet())
        _activeConfig.value = resetAbiConfig
        viewModelScope.launch {
            _installState.value = ApkInstallUiState.Idle
            _pipelineState.value = ForgePipelineState.Inspecting("PakForge.apk")
            runCatching {
                engine.inspectPackage(
                    file = apkFile,
                    displayName = "PakForge.apk",
                    config = resetAbiConfig,
                    shizukuReady = shizukuStatus.value is ShizukuStatus.Ready
                )
            }.onSuccess { result ->
                _pipelineState.value = ForgePipelineState.Ready(result)
                pluginManager.refreshSmartRecommendations()
            }.onFailure { err ->
                _pipelineState.value = ForgePipelineState.Error(err.localizedMessage ?: "Cannot inspect self APK")
            }
        }
    }

    fun loadInstalledAppsList() {
        if (_isLoadingApps.value) return
        viewModelScope.launch {
            _isLoadingApps.value = true
            runCatching { engine.loadInstalledApps() }
                .onSuccess { _installedApps.value = it }
            _isLoadingApps.value = false
        }
    }

    fun updateConfig(transform: (CompressionConfig) -> CompressionConfig) {
        val updated = transform(_activeConfig.value)
        _activeConfig.value = updated
        viewModelScope.launch {
            settingsRepository.updateConfig(updated)
        }
        val current = _pipelineState.value
        val inspection = when (current) {
            is ForgePipelineState.Ready -> current.inspection
            is ForgePipelineState.Completed -> current.inspection
            else -> null
        }
        if (inspection != null) {
            val recalculated = engine.recalculatePrediction(inspection, updated)
            _pipelineState.value = ForgePipelineState.Ready(recalculated)
        }
    }

    fun applyCompatibilityAutoFix() {
        val cur = _pipelineState.value
        val inspection = when (cur) {
            is ForgePipelineState.Ready -> cur.inspection
            is ForgePipelineState.Completed -> cur.inspection
            else -> null
        } ?: return

        val status = installer.inspectTargetStatus(inspection.packageId, inspection.packageId)
        val shizukuIsReady = shizukuStatus.value is ShizukuStatus.Ready
        updateConfig { current ->
            CompatibilityAnalyzer.buildAutoFixConfig(
                current = current,
                inspection = inspection,
                isInstalledWithDifferentSig = status.isSignatureConflict,
                isSystemApp = status.isSystemApp,
                shizukuReady = shizukuIsReady,
                devicePrimaryAbi = devicePrimaryAbi
            )
        }
    }

    fun setInstallIdentityMode(mode: InstallIdentityMode) {
        updateConfig { it.copy(installIdentityMode = mode) }
    }

    fun setCloneSuffix(suffix: String) {
        val clean = suffix.lowercase().filter { it.isLetterOrDigit() || it == '_' }.take(12)
        updateConfig { it.copy(cloneSuffix = clean) }
    }

    fun updateCustomCloneLabel(label: String) {
        updateConfig { it.copy(customCloneLabel = label.take(36)) }
    }

    fun setKeepAllAbis() {
        updateConfig { it.copy(allowedAbis = emptySet()) }
    }

    fun setDeviceAbiOnly() {
        val inspection = when (val st = _pipelineState.value) {
            is ForgePipelineState.Ready -> st.inspection
            is ForgePipelineState.Completed -> st.inspection
            else -> null
        } ?: return
        if (devicePrimaryAbi in inspection.detectedAbis) {
            updateConfig { it.copy(allowedAbis = setOf(devicePrimaryAbi)) }
        }
    }

    fun toggleAbiSelection(abi: String, allDetectedAbis: List<String>) {
        updateConfig { current ->
            val activeSet = if (current.allowedAbis.isEmpty()) {
                allDetectedAbis.toSet()
            } else {
                current.allowedAbis
            }
            val nextSet = if (abi in activeSet) {
                if (activeSet.size <= 1) activeSet else activeSet - abi
            } else {
                activeSet + abi
            }
            if (nextSet.size >= allDetectedAbis.size) {
                current.copy(allowedAbis = emptySet())
            } else {
                current.copy(allowedAbis = nextSet)
            }
        }
    }

    fun toggleKeepLocale(locale: String) {
        updateConfig { current ->
            val next = if (locale in current.keptLocales) {
                if (current.keptLocales.size <= 1) current.keptLocales else current.keptLocales - locale
            } else {
                current.keptLocales + locale
            }
            current.copy(keptLocales = next)
        }
    }

    fun selectProfile(profile: CompressionProfile) {
        updateConfig { current ->
            when (profile) {
                CompressionProfile.ZERO_RISK_BALANCED -> current.copy(
                    profile = profile,
                    enforceResourcesArscAlign = true,
                    smartEntropyBypass = true,
                    pruneDebugArtifacts = false
                )
                CompressionProfile.ULTRA_SHRINK -> current.copy(
                    profile = profile,
                    enforceResourcesArscAlign = true,
                    smartEntropyBypass = true,
                    pruneDebugArtifacts = true
                )
                CompressionProfile.LOW_RESOURCE_ECO -> current.copy(
                    profile = profile,
                    enforceResourcesArscAlign = true,
                    smartEntropyBypass = true,
                    adaptiveRamThrottle = true
                )
            }
        }
    }

    fun startCompression() {
        val inspection = when (val st = _pipelineState.value) {
            is ForgePipelineState.Ready -> st.inspection
            is ForgePipelineState.Completed -> st.inspection
            else -> return
        }

        val config = _activeConfig.value
        _installState.value = ApkInstallUiState.Idle
        viewModelScope.launch {
            runCatching {
                engine.compressPackage(inspection, config) { progress ->
                    _pipelineState.value = ForgePipelineState.Compressing(inspection, progress)
                }
            }.onSuccess { report ->
                _pipelineState.value = ForgePipelineState.Completed(inspection, report)
                historyRepository.recordRun(report, config.profile)
            }.onFailure { err ->
                _pipelineState.value = ForgePipelineState.Error(err.localizedMessage ?: "Compression failed")
            }
        }
    }

    fun saveOutputToUri(destUri: Uri) {
        val completed = _pipelineState.value as? ForgePipelineState.Completed ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val srcFile = File(completed.report.outputFilePath)
                    require(srcFile.exists()) { "Output file expired from cache" }
                    getApplication<Application>().contentResolver.openOutputStream(destUri)?.use { out ->
                        FileInputStream(srcFile).use { input ->
                            input.copyTo(out, bufferSize = 32 * 1024)
                        }
                    } ?: throw IllegalStateException("Unable to open destination URI")
                }
            }.onSuccess {
                _events.emit(UiEvent.SavedToDevice)
            }.onFailure { err ->
                _events.emit(UiEvent.ErrorToast(err.localizedMessage ?: "Save failed"))
            }
        }
    }

    fun notifyJsonCopied() {
        viewModelScope.launch {
            _events.emit(UiEvent.JsonCopied)
        }
    }

    fun buildShareIntent(): Intent? {
        val completed = _pipelineState.value as? ForgePipelineState.Completed ?: return null
        val app = getApplication<Application>()
        val file = File(completed.report.outputFilePath)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, completed.report.outputFileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun updateInspectorSearch(query: String) {
        _inspectorSearch.value = query
    }

    fun updateInspectorCategory(category: PackageCategory?) {
        _inspectorCategory.value = category
    }

    fun setLanguage(code: String) {
        viewModelScope.launch { settingsRepository.setLanguage(code) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch { historyRepository.deleteById(id) }
    }

    fun clearAllHistory() {
        viewModelScope.launch { historyRepository.clearAll() }
    }

    fun resetErrorToIdle() {
        _pipelineState.value = ForgePipelineState.Idle
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PakForgeApp
                MainViewModel(
                    application = app,
                    engine = app.packageEngine,
                    installer = app.packageInstaller,
                    shizukuBridge = app.shizukuBridge,
                    pluginManager = app.pluginManager,
                    telemetryProvider = app.telemetryProvider,
                    historyRepository = app.historyRepository,
                    settingsRepository = app.settingsRepository
                )
            }
        }
    }
}
