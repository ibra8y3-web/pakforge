package com.example.core.shizuku

import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Privileged UserService hosted inside the Shizuku daemon (shell UID 2000 or root UID 0).
 * Streams APK payloads over Binder ParcelFileDescriptor directly into `cmd package install-write`.
 */
class PakForgeShellService : IPakForgeShellService.Stub() {

    private val safeTokenRegex = Regex("^[a-zA-Z0-9._:@-]+$")

    override fun destroy() {
        exitProcess(0)
    }

    override fun getUid(): Int = Process.myUid()

    override fun createInstallSession(
        totalSizeBytes: Long,
        allowDowngrade: Boolean,
        installerPkg: String?
    ): String {
        val cmd = ArrayList<String>().apply {
            add("cmd")
            add("package")
            add("install-create")
            add("-r")
            add("-t")
            if (allowDowngrade) add("-d")
            add("-S")
            add(totalSizeBytes.coerceAtLeast(1L).toString())
            if (!installerPkg.isNullOrBlank() && safeTokenRegex.matches(installerPkg)) {
                add("-i")
                add(installerPkg)
            }
        }
        val (exitCode, out) = runCommand(cmd)
        if (exitCode != 0) {
            throw IllegalStateException("install-create failed ($exitCode): $out")
        }
        val match = Regex("\\[(\\d+)]").find(out)
            ?: throw IllegalStateException("Could not parse session ID from: $out")
        return match.groupValues[1]
    }

    override fun writeSessionFile(
        sessionId: String,
        entryName: String,
        sizeBytes: Long,
        pfd: ParcelFileDescriptor
    ): Boolean {
        require(safeTokenRegex.matches(sessionId)) { "Invalid sessionId" }
        require(safeTokenRegex.matches(entryName)) { "Invalid entryName" }

        val process = ProcessBuilder(
            "cmd", "package", "install-write",
            "-S", sizeBytes.coerceAtLeast(1L).toString(),
            sessionId,
            entryName,
            "-"
        ).redirectErrorStream(true).start()

        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
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
        val finished = process.waitFor(120, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return false
        }
        return process.exitValue() == 0
    }

    override fun commitInstallSession(sessionId: String): String {
        require(safeTokenRegex.matches(sessionId)) { "Invalid sessionId" }
        val (exitCode, out) = runCommand(listOf("cmd", "package", "install-commit", sessionId))
        return if (exitCode == 0 && out.contains("Success", ignoreCase = true)) {
            "OK"
        } else {
            out.ifBlank { "Exit code $exitCode" }
        }
    }

    override fun abandonInstallSession(sessionId: String) {
        if (!safeTokenRegex.matches(sessionId)) return
        runCommand(listOf("cmd", "package", "install-abandon", sessionId))
    }

    override fun uninstallPackageForUser(packageName: String, keepData: Boolean): String {
        require(safeTokenRegex.matches(packageName)) { "Invalid packageName" }
        val cmd = ArrayList<String>().apply {
            add("pm")
            add("uninstall")
            if (keepData) add("-k")
            add("--user")
            add("0")
            add(packageName)
        }
        val (exitCode, out) = runCommand(cmd)
        return if (exitCode == 0 && out.contains("Success", ignoreCase = true)) {
            "OK"
        } else {
            out.ifBlank { "Uninstall failed ($exitCode)" }
        }
    }

    override fun setPackageEnabledState(packageName: String, enabled: Boolean): String {
        require(safeTokenRegex.matches(packageName)) { "Invalid packageName" }
        val cmd = if (enabled) {
            listOf("pm", "enable", packageName)
        } else {
            listOf("pm", "disable-user", "--user", "0", packageName)
        }
        val (exitCode, out) = runCommand(cmd)
        return if (exitCode == 0) "OK" else out.ifBlank { "State change failed ($exitCode)" }
    }

    private fun runCommand(args: List<String>): Pair<Int, String> {
        val process = ProcessBuilder(args)
            .redirectErrorStream(true)
            .start()
        val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            reader.readText().trim()
        }
        val finished = process.waitFor(60, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return -1 to "Command timed out"
        }
        return process.exitValue() to output
    }
}
