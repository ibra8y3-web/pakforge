package com.example.core.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.core.signing.ApkV2Signer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class InstallTargetStatus(
    val isOriginalInstalled: Boolean,
    val isTargetInstalled: Boolean,
    val isSystemApp: Boolean,
    val signaturesMatch: Boolean,
    val isSignatureConflict: Boolean
)

class PakForgeInstaller(
    private val context: Context,
    private val apkV2Signer: ApkV2Signer = ApkV2Signer(context)
) {

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun buildUnknownSourcesSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    fun buildUninstallIntent(packageName: String): Intent {
        return Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
        }
    }

    fun isPackageInstalled(packageName: String): Boolean {
        return getInstalledPkgInfo(packageName) != null
    }

    fun inspectTargetStatus(originalPkg: String?, targetPkg: String?): InstallTargetStatus {
        val origInfo = originalPkg?.let { getInstalledPkgInfo(it) }
        val targetInfo = targetPkg?.let {
            if (it == originalPkg) origInfo else getInstalledPkgInfo(it)
        }
        val isSystem = (origInfo?.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) ?: 0) != 0

        val sigMatch = if (targetInfo != null) {
            val localCert = runCatching { apkV2Signer.getLocalCertSha256() }.getOrNull()
            val installedCert = extractInstalledCertSha256(targetInfo)
            localCert != null && installedCert != null && localCert == installedCert
        } else {
            true
        }

        val targetInstalled = targetInfo != null
        return InstallTargetStatus(
            isOriginalInstalled = origInfo != null,
            isTargetInstalled = targetInstalled,
            isSystemApp = isSystem,
            signaturesMatch = sigMatch,
            isSignatureConflict = targetInstalled && !sigMatch
        )
    }

    private fun getInstalledPkgInfo(packageName: String): PackageInfo? {
        val pm = context.packageManager
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
        }.getOrNull()
    }

    private fun extractInstalledCertSha256(info: PackageInfo): String? {
        val signerBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
                ?: info.signingInfo?.signingCertificateHistory?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()?.toByteArray()
        }
        return signerBytes?.let { bytes ->
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(":") { "%02X".format(it) }
        }
    }

    suspend fun startInstallSession(apkFile: File) = startInstallSession(listOf(apkFile))

    suspend fun startInstallSession(apkFiles: List<File>) = withContext(Dispatchers.IO) {
        require(apkFiles.isNotEmpty()) { "No APK files to install" }
        apkFiles.forEach { file ->
            require(file.exists() && file.canRead()) { "Signed APK file not found: ${file.name}" }
        }

        val totalSize = apkFiles.sumOf { it.length() }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(totalSize)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }

        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)

        try {
            val buf = ByteArray(32 * 1024)
            apkFiles.forEachIndexed { index, apkFile ->
                val writeName = if (index == 0) "pakforge_base.apk" else "pakforge_split_${index}.apk"
                session.openWrite(writeName, 0L, apkFile.length()).use { out ->
                    FileInputStream(apkFile).use { input ->
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                        }
                    }
                    session.fsync(out)
                }
            }

            val callbackIntent = Intent(context, InstallResultReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                callbackIntent,
                flags
            )
            session.commit(pendingIntent.intentSender)
        } catch (t: Throwable) {
            runCatching { session.abandon() }
            throw t
        } finally {
            session.close()
        }
    }
}
