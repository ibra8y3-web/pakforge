package com.example.core.engine

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.SharedLibraryInfo
import android.os.Build
import java.io.File

data class DiscoveredStaticLibrary(
    val libraryName: String,
    val declaringPackageName: String?,
    val apkPaths: List<String>
)

/**
 * Discovers installed Android static shared libraries (e.g. com.google.android.trichromelibrary)
 * directly from PackageManager so PakForge can re-sign/bundle companion libraries when needed.
 */
object StaticLibraryInspector {

    fun findInstalledStaticLibraries(
        context: Context,
        requiredLibNames: List<String>
    ): List<DiscoveredStaticLibrary> {
        if (requiredLibNames.isEmpty()) return emptyList()
        val pm = context.packageManager
        val discovered = ArrayList<DiscoveredStaticLibrary>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sharedLibs = runCatching { pm.getSharedLibraries(0) }.getOrNull().orEmpty()
            for (reqName in requiredLibNames) {
                val match = sharedLibs.firstOrNull { info ->
                    info.name == reqName &&
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || info.type == SharedLibraryInfo.TYPE_STATIC)
                }
                if (match != null) {
                    val pkgName = match.declaringPackage.packageName
                    val appInfo = runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getApplicationInfo(pkgName, PackageManager.ApplicationInfoFlags.of(0L))
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getApplicationInfo(pkgName, 0)
                        }
                    }.getOrNull()
                    val paths = buildList {
                        appInfo?.sourceDir?.takeIf { File(it).canRead() }?.let { add(it) }
                        appInfo?.splitSourceDirs?.filter { File(it).canRead() }?.forEach { add(it) }
                    }
                    discovered.add(
                        DiscoveredStaticLibrary(
                            libraryName = reqName,
                            declaringPackageName = pkgName,
                            apkPaths = paths
                        )
                    )
                } else {
                    discovered.add(
                        DiscoveredStaticLibrary(
                            libraryName = reqName,
                            declaringPackageName = null,
                            apkPaths = emptyList()
                        )
                    )
                }
            }
        } else {
            requiredLibNames.forEach { reqName ->
                discovered.add(DiscoveredStaticLibrary(reqName, null, emptyList()))
            }
        }
        return discovered
    }
}
