package com.example.core.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.Settings
import com.example.BuildConfig
import com.example.core.privileged.BackendAvailability
import com.example.core.privileged.DhizukuProbe
import com.example.core.privileged.PrivilegedBackendKind
import com.example.core.privileged.PrivilegedBridgeSnapshot
import com.example.core.privileged.RootFlavor
import com.example.core.privileged.RootProbeResult
import com.example.core.privileged.RootShellExecutor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.File

sealed interface ShizukuStatus {
    data object NotInstalled : ShizukuStatus
    data object ServiceNotRunning : ShizukuStatus
    data object PermissionRequired : ShizukuStatus
    data class Ready(
        val uid: Int,
        val isRoot: Boolean,
        val backendKind: PrivilegedBackendKind = PrivilegedBackendKind.SHIZUKU,
        val backendDetail: String = "Shizuku"
    ) : ShizukuStatus
}

/**
 * Multi-backend elevated execution orchestrator:
 * Accepts & automatically switches across KernelSU / APatch / Magisk Root (`su`),
 * Sui (Systemless Zygisk Shizuku API), Shizuku (`moe.shizuku.privileged.api`),
 * and probes Dhizuku (`com.rosan.dhizuku`), with graceful Standard PackageInstaller fallback.
 */
class ShizukuBridgeManager(private val context: Context) {

    companion object {
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val REQUEST_CODE_SHIZUKU = 9104
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow<ShizukuStatus>(ShizukuStatus.NotInstalled)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val _bridgeSnapshot = MutableStateFlow(PrivilegedBridgeSnapshot())
    val bridgeSnapshot: StateFlow<PrivilegedBridgeSnapshot> = _bridgeSnapshot.asStateFlow()

    @Volatile
    private var preferredMode: PrivilegedBackendKind = PrivilegedBackendKind.AUTO

    @Volatile
    private var cachedRootProbe: RootProbeResult? = null

    @Volatile
    private var boundService: IPakForgeShellService? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        boundService = null
        refreshStatus()
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == REQUEST_CODE_SHIZUKU) {
                refreshStatus()
            }
        }

    init {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        }
        refreshStatus(triggerRootProbe = true)
    }

    fun isShizukuAppInstalled(): Boolean {
        val pm = context.packageManager
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(SHIZUKU_PACKAGE, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(SHIZUKU_PACKAGE, 0)
            }
        }.isSuccess
    }

    fun setPreferredBackend(mode: PrivilegedBackendKind) {
        preferredMode = mode
        refreshStatus(triggerRootProbe = mode == PrivilegedBackendKind.ROOT_SU || mode == PrivilegedBackendKind.AUTO)
    }

    fun refreshStatus(triggerRootProbe: Boolean = true): ShizukuStatus {
        val fastResult = computeSnapshot(cachedRootProbe)
        _status.value = fastResult.first
        _bridgeSnapshot.value = fastResult.second

        if (triggerRootProbe) {
            scope.launch {
                val rootProbe = RootShellExecutor.probe(context, verifyGrant = true)
                cachedRootProbe = rootProbe
                val updated = computeSnapshot(rootProbe)
                _status.value = updated.first
                _bridgeSnapshot.value = updated.second
            }
        }
        return fastResult.first
    }

    private fun computeSnapshot(rootProbe: RootProbeResult?): Pair<ShizukuStatus, PrivilegedBridgeSnapshot> {
        val shizukuPkgInstalled = isShizukuAppInstalled()
        val binderAlive = runCatching { Shizuku.pingBinder() && !Shizuku.isPreV11() }.getOrDefault(false)
        val binderPermGranted = binderAlive && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val binderUid = if (binderPermGranted) runCatching { Shizuku.getUid() }.getOrNull() else null

        // Sui runs Systemless inside system_server exposing Shizuku binder without requiring moe.shizuku.privileged.api
        val isSuiBinder = binderAlive && !shizukuPkgInstalled

        val effectiveRootProbe = rootProbe ?: RootProbeResult(
            suBinaryPath = RootShellExecutor.findSuBinary(),
            flavor = RootShellExecutor.detectManagerFlavor(context).first,
            versionOutput = null,
            managerInstalled = RootShellExecutor.detectManagerFlavor(context).second,
            rootAuthorized = false,
            uid = null
        )

        val rootAvail = BackendAvailability(
            kind = PrivilegedBackendKind.ROOT_SU,
            title = if (effectiveRootProbe.flavor != RootFlavor.NONE) {
                "${effectiveRootProbe.flavor.displayName} (Root su)"
            } else {
                "Root Superuser (su)"
            },
            isInstalled = effectiveRootProbe.suBinaryPath != null || effectiveRootProbe.managerInstalled,
            isRunning = effectiveRootProbe.suBinaryPath != null,
            isAuthorized = effectiveRootProbe.rootAuthorized,
            uid = effectiveRootProbe.uid,
            detail = when {
                effectiveRootProbe.rootAuthorized ->
                    "${effectiveRootProbe.flavor.displayName} • UID 0 ${effectiveRootProbe.versionOutput ?: ""}".trim()
                effectiveRootProbe.suBinaryPath != null ->
                    "su binary found (${effectiveRootProbe.flavor.displayName}) — grant root when prompted"
                effectiveRootProbe.managerInstalled ->
                    "${effectiveRootProbe.flavor.displayName} manager detected"
                else -> "Not rooted"
            }
        )

        val suiAvail = BackendAvailability(
            kind = PrivilegedBackendKind.SUI,
            title = "Sui (Systemless Zygisk Bridge)",
            isInstalled = isSuiBinder,
            isRunning = isSuiBinder,
            isAuthorized = isSuiBinder && binderPermGranted,
            uid = if (isSuiBinder) binderUid else null,
            detail = when {
                isSuiBinder && binderPermGranted -> "Sui Active • UID ${binderUid ?: 0}"
                isSuiBinder -> "Sui Active • Grant permission"
                else -> "Sui module not active"
            }
        )

        val shizukuAvail = BackendAvailability(
            kind = PrivilegedBackendKind.SHIZUKU,
            title = "Shizuku (ADB / Root Binder)",
            isInstalled = shizukuPkgInstalled,
            isRunning = binderAlive && !isSuiBinder,
            isAuthorized = binderAlive && !isSuiBinder && binderPermGranted,
            uid = if (!isSuiBinder) binderUid else null,
            detail = when {
                binderAlive && !isSuiBinder && binderPermGranted -> "Shizuku Active • UID ${binderUid ?: 2000}"
                binderAlive && !isSuiBinder -> "Service running • Authorization required"
                shizukuPkgInstalled -> "Installed • Start via Wireless Debugging"
                else -> "Not installed"
            }
        )

        val dhizukuAvail = DhizukuProbe.probe(context)

        val readyOrder = listOf(rootAvail, suiAvail, shizukuAvail)
        val selectedActive = when (preferredMode) {
            PrivilegedBackendKind.ROOT_SU -> rootAvail.takeIf { it.isReady }
            PrivilegedBackendKind.SUI -> suiAvail.takeIf { it.isReady }
            PrivilegedBackendKind.SHIZUKU -> shizukuAvail.takeIf { it.isReady }
            PrivilegedBackendKind.STANDARD -> null
            PrivilegedBackendKind.AUTO,
            PrivilegedBackendKind.DHIZUKU -> readyOrder.firstOrNull { it.isReady }
        }

        val activeKind = selectedActive?.kind ?: PrivilegedBackendKind.STANDARD
        val activeUid = selectedActive?.uid
        val activeDetail = selectedActive?.detail ?: "Standard Android PackageInstaller"

        val legacyStatus: ShizukuStatus = when {
            selectedActive != null -> ShizukuStatus.Ready(
                uid = selectedActive.uid ?: 2000,
                isRoot = selectedActive.uid == 0 || selectedActive.kind == PrivilegedBackendKind.ROOT_SU,
                backendKind = selectedActive.kind,
                backendDetail = selectedActive.title
            )
            binderAlive && !binderPermGranted -> ShizukuStatus.PermissionRequired
            shizukuPkgInstalled || dhizukuAvail.isInstalled || rootAvail.isInstalled ->
                ShizukuStatus.ServiceNotRunning
            else -> ShizukuStatus.NotInstalled
        }

        val snapshot = PrivilegedBridgeSnapshot(
            preferredMode = preferredMode,
            activeBackend = activeKind,
            activeDetail = activeDetail,
            activeUid = activeUid,
            rootFlavor = effectiveRootProbe.flavor,
            backends = listOf(rootAvail, suiAvail, shizukuAvail, dhizukuAvail)
        )
        return legacyStatus to snapshot
    }

    fun requestShizukuPermission(): Boolean {
        refreshStatus(triggerRootProbe = true)
        return runCatching {
            if (!Shizuku.pingBinder()) return false
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                refreshStatus(triggerRootProbe = false)
                true
            } else {
                Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
                true
            }
        }.getOrDefault(false)
    }

    fun buildOpenShizukuOrDevSettingsIntent(): Intent? {
        val pm = context.packageManager
        pm.getLaunchIntentForPackage(SHIZUKU_PACKAGE)?.let {
            return it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        DhizukuProbe.buildLaunchIntent(context)?.let { return it }
        return Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private suspend fun getOrBindService(): IPakForgeShellService = withContext(Dispatchers.IO) {
        boundService?.let { existing ->
            if (runCatching { existing.asBinder().pingBinder() }.getOrDefault(false)) {
                return@withContext existing
            }
        }
        check(runCatching { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)) {
            "Shizuku / Sui permission is required before binding UserService"
        }

        val deferred = CompletableDeferred<IPakForgeShellService>()
        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, PakForgeShellService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("pakforge_shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val iface = IPakForgeShellService.Stub.asInterface(service)
                boundService = iface
                if (!deferred.isCompleted) deferred.complete(iface)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                boundService = null
            }
        }

        Shizuku.bindUserService(args, conn)
        withTimeoutOrNull(8000L) { deferred.await() }
            ?: throw IllegalStateException("Timed out connecting to Shizuku/Sui UserService")
    }

    suspend fun installApkBundle(
        apkFiles: List<File>,
        allowDowngrade: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        val snap = _bridgeSnapshot.value
        val suPath = cachedRootProbe?.takeIf { it.rootAuthorized }?.suBinaryPath
            ?: if (snap.preferredMode == PrivilegedBackendKind.ROOT_SU) RootShellExecutor.findSuBinary() else null

        if (snap.activeBackend == PrivilegedBackendKind.ROOT_SU && suPath != null) {
            return@withContext RootShellExecutor.installApkBundle(suPath, apkFiles, allowDowngrade)
        }

        runCatching {
            require(apkFiles.isNotEmpty()) { "No APK files to install" }
            val service = getOrBindService()
            val totalSize = apkFiles.sumOf { it.length() }
            val sessionId = service.createInstallSession(totalSize, allowDowngrade, "com.android.shell")
            try {
                apkFiles.forEachIndexed { idx, file ->
                    val entryName = if (idx == 0) "base.apk" else "split_${idx}.apk"
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val ok = pfd.use {
                        service.writeSessionFile(sessionId, entryName, file.length(), it)
                    }
                    if (!ok) {
                        throw IllegalStateException("Failed streaming ${file.name} into privileged session $sessionId")
                    }
                }
                val result = service.commitInstallSession(sessionId)
                if (result != "OK") {
                    throw IllegalStateException(result)
                }
                "Installed via ${snap.activeBackend.name} (UID ${service.uid})"
            } catch (t: Throwable) {
                runCatching { service.abandonInstallSession(sessionId) }
                throw t
            }
        }
    }

    suspend fun uninstallPackageForUser(
        packageName: String,
        keepData: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        val snap = _bridgeSnapshot.value
        val suPath = cachedRootProbe?.takeIf { it.rootAuthorized }?.suBinaryPath
        if (snap.activeBackend == PrivilegedBackendKind.ROOT_SU && suPath != null) {
            return@withContext RootShellExecutor.uninstallPackageForUser(suPath, packageName, keepData)
        }

        runCatching {
            val service = getOrBindService()
            val res = service.uninstallPackageForUser(packageName, keepData)
            if (res != "OK") throw IllegalStateException(res)
            "Removed $packageName via ${snap.activeBackend.name}"
        }
    }

    suspend fun setPackageEnabled(
        packageName: String,
        enabled: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        val snap = _bridgeSnapshot.value
        val suPath = cachedRootProbe?.takeIf { it.rootAuthorized }?.suBinaryPath
        if (snap.activeBackend == PrivilegedBackendKind.ROOT_SU && suPath != null) {
            return@withContext RootShellExecutor.setPackageEnabled(suPath, packageName, enabled)
        }

        runCatching {
            val service = getOrBindService()
            val res = service.setPackageEnabledState(packageName, enabled)
            if (res != "OK") throw IllegalStateException(res)
            "OK"
        }
    }
}
