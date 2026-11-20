package com.example.core.privileged

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class RootProbeResult(
    val suBinaryPath: String?,
    val flavor: RootFlavor,
    val versionOutput: String?,
    val managerInstalled: Boolean,
    val rootAuthorized: Boolean,
    val uid: Int?
)

/**
 * Real non-mock Superuser (`su`) bridge supporting KernelSU, APatch, Magisk, and generic su.
 * Probes binaries safely without blocking the main thread and streams APK payloads directly
 * into `cmd package install-write` via stdin without creating world-readable temp copies.
 */
object RootShellExecutor {

    private val candidateSuPaths = listOf(
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su",
        "/debug_ramdisk/su",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/vendor/bin/su",
        "/su/bin/su"
    )

    private val managerPackages = mapOf(
        "me.weishu.kernelsu" to RootFlavor.KERNEL_SU,
        "me.bmax.apatch" to RootFlavor.APATCH,
        "com.topjohnwu.magisk" to RootFlavor.MAGISK
    )

    private val safeTokenRegex = Regex("^[a-zA-Z0-9._:@-]+$")

    fun detectManagerFlavor(context: Context): Pair<RootFlavor, Boolean> {
        val pm = context.packageManager
        for ((pkg, flavor) in managerPackages) {
            val installed = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
            }.isSuccess
            if (installed) return flavor to true
        }
        return RootFlavor.NONE to false
    }

    fun findSuBinary(): String? {
        for (path in candidateSuPaths) {
            val file = File(path)
            if (file.exists() && file.canExecute()) return path
        }
        val pathEnv = System.getenv("PATH")?.split(':') ?: emptyList()
        for (dir in pathEnv) {
            val f = File(dir, "su")
            if (f.exists() && f.canExecute()) return f.absolutePath
        }
        return null
    }

    /**
     * Inspects local root binary presence and verifies active root grant (`su -c id`)
     * when [verifyGrant] is true. Safe to invoke on background IO dispatcher.
     */
    fun probe(context: Context, verifyGrant: Boolean = true): RootProbeResult {
        val (mgrFlavor, mgrInstalled) = detectManagerFlavor(context)
        val suPath = findSuBinary()
        if (suPath == null) {
            return RootProbeResult(
                suBinaryPath = null,
                flavor = mgrFlavor,
                versionOutput = null,
                managerInstalled = mgrInstalled,
                rootAuthorized = false,
                uid = null
            )
        }

        val versionOut = runCatching {
            val (code, out) = runRawCommand(listOf(suPath, "-v"), timeoutSec = 3L)
            if (code == 0 && out.isNotBlank()) out.lineSequence().firstOrNull()?.trim() else null
        }.getOrNull()

        val inferredFlavor = when {
            suPath.contains("/ksu/") || versionOut?.contains("KernelSU", ignoreCase = true) == true ->
                RootFlavor.KERNEL_SU
            suPath.contains("/ap/") || versionOut?.contains("APatch", ignoreCase = true) == true ->
                RootFlavor.APATCH
            versionOut?.contains("MAGISKSU", ignoreCase = true) == true ->
                RootFlavor.MAGISK
            mgrFlavor != RootFlavor.NONE -> mgrFlavor
            else -> RootFlavor.GENERIC_SU
        }

        if (!verifyGrant) {
            return RootProbeResult(
                suBinaryPath = suPath,
                flavor = inferredFlavor,
                versionOutput = versionOut,
                managerInstalled = mgrInstalled,
                rootAuthorized = false,
                uid = null
            )
        }

        val idCheck = runCatching {
            val (code, out) = runRawCommand(listOf(suPath, "-c", "id"), timeoutSec = 5L)
            if (code == 0 && out.contains("uid=0")) 0 else null
        }.getOrNull()

        return RootProbeResult(
            suBinaryPath = suPath,
            flavor = inferredFlavor,
            versionOutput = versionOut,
            managerInstalled = mgrInstalled,
            rootAuthorized = idCheck == 0,
            uid = idCheck
        )
    }

    suspend fun installApkBundle(
        suPath: String,
        apkFiles: List<File>,
        allowDowngrade: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(apkFiles.isNotEmpty()) { "No APK files to install" }
            val totalSize = apkFiles.sumOf { it.length() }.coerceAtLeast(1L)
            val dFlag = if (allowDowngrade) "-d " else ""
            val createCmd = "cmd package install-create -r -t $dFlag-S $totalSize -i com.android.shell"
            val (createCode, createOut) = runRawCommand(listOf(suPath, "-c", createCmd), timeoutSec = 20L)
            if (createCode != 0) {
                throw IllegalStateException("Root install-create failed ($createCode): $createOut")
            }
            val sessionId = Regex("\\[(\\d+)]").find(createOut)?.groupValues?.getOrNull(1)
                ?: throw IllegalStateException("Could not parse root install session ID: $createOut")

            try {
                apkFiles.forEachIndexed { idx, file ->
                    val entryName = if (idx == 0) "base.apk" else "split_${idx}.apk"
                    require(safeTokenRegex.matches(entryName)) { "Unsafe entry name" }
                    val writeCmd = "cmd package install-write -S ${file.length().coerceAtLeast(1L)} $sessionId $entryName -"
                    val process = ProcessBuilder(suPath, "-c", writeCmd)
                        .redirectErrorStream(true)
                        .start()
                    FileInputStream(file).use { input ->
                        process.outputStream.use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buf)
                                if (read <= 0) break
                                out.write(buf, 0, read)
                            }
                            out.flush()
                        }
                    }
                    val ok = process.waitFor(120, TimeUnit.SECONDS)
                    if (!ok || process.exitValue() != 0) {
                        val errOut = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText().trim() }
                        if (!ok) process.destroyForcibly()
                        throw IllegalStateException("Root install-write failed for $entryName: $errOut")
                    }
                }

                val (commitCode, commitOut) = runRawCommand(
                    listOf(suPath, "-c", "cmd package install-commit $sessionId"),
                    timeoutSec = 60L
                )
                if (commitCode != 0 || !commitOut.contains("Success", ignoreCase = true)) {
                    throw IllegalStateException(commitOut.ifBlank { "Root commit failed ($commitCode)" })
                }
                "Installed via Root su (UID 0)"
            } catch (t: Throwable) {
                runCatching {
                    runRawCommand(listOf(suPath, "-c", "cmd package install-abandon $sessionId"), timeoutSec = 10L)
                }
                throw t
            }
        }
    }

    suspend fun uninstallPackageForUser(
        suPath: String,
        packageName: String,
        keepData: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(safeTokenRegex.matches(packageName)) { "Invalid packageName" }
            val kFlag = if (keepData) "-k " else ""
            val (code, out) = runRawCommand(
                listOf(suPath, "-c", "pm uninstall ${kFlag}--user 0 $packageName"),
                timeoutSec = 30L
            )
            if (code == 0 && out.contains("Success", ignoreCase = true)) {
                "Removed $packageName via Root (UID 0)"
            } else {
                throw IllegalStateException(out.ifBlank { "Root uninstall failed ($code)" })
            }
        }
    }

    suspend fun setPackageEnabled(
        suPath: String,
        packageName: String,
        enabled: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(safeTokenRegex.matches(packageName)) { "Invalid packageName" }
            val subCmd = if (enabled) "pm enable $packageName" else "pm disable-user --user 0 $packageName"
            val (code, out) = runRawCommand(listOf(suPath, "-c", subCmd), timeoutSec = 20L)
            if (code == 0) "OK" else throw IllegalStateException(out.ifBlank { "Root state change failed ($code)" })
        }
    }

    private fun runRawCommand(cmd: List<String>, timeoutSec: Long): Pair<Int, String> {
        val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText().trim() }
        val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return -1 to "Timed out"
        }
        return process.exitValue() to output
    }
}
