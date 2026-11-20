package com.example.core.privileged

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/**
 * Real detector & permission probe for Dhizuku (Device-Owner Binder bridge: com.rosan.dhizuku).
 * Detects installation state and queries Dhizuku's server ContentProvider when available.
 */
object DhizukuProbe {

    const val DHIZUKU_PACKAGE = "com.rosan.dhizuku"
    private const val DHIZUKU_AUTHORITY = "com.rosan.dhizuku.server.provider"

    fun isInstalled(context: Context): Boolean {
        return runCatching {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(DHIZUKU_PACKAGE, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(DHIZUKU_PACKAGE, 0)
            }
        }.isSuccess
    }

    fun probe(context: Context): BackendAvailability {
        val installed = isInstalled(context)
        if (!installed) {
            return BackendAvailability(
                kind = PrivilegedBackendKind.DHIZUKU,
                title = "Dhizuku (Device Owner)",
                isInstalled = false,
                isRunning = false,
                isAuthorized = false,
                uid = null,
                detail = "Not installed"
            )
        }

        val serverProbe = runCatching {
            val uri = Uri.parse("content://$DHIZUKU_AUTHORITY")
            context.contentResolver.call(uri, "get_version", null, null)
        }.getOrNull()

        val hasBinder = serverProbe != null
        val detail = if (hasBinder) {
            "Device Owner server reachable"
        } else {
            "Installed • Open Dhizuku to activate server"
        }

        return BackendAvailability(
            kind = PrivilegedBackendKind.DHIZUKU,
            title = "Dhizuku (Device Owner)",
            isInstalled = true,
            isRunning = hasBinder,
            isAuthorized = false,
            uid = null,
            detail = detail
        )
    }

    fun buildLaunchIntent(context: Context): Intent? {
        return context.packageManager.getLaunchIntentForPackage(DHIZUKU_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
