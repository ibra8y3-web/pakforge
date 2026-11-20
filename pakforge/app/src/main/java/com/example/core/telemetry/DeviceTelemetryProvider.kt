package com.example.core.telemetry

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlin.math.roundToInt

data class DeviceTelemetryState(
    val freeRamMb: Int,
    val totalRamMb: Int,
    val ramUsagePercent: Int,
    val cpuCores: Int,
    val isPowerSaveMode: Boolean,
    val isLowRamDevice: Boolean,
    val recommendedBufferKb: Int,
    val heapUsedMb: Int,
    val heapMaxMb: Int
)

class DeviceTelemetryProvider(private val context: Context) {

    fun snapshot(): DeviceTelemetryState {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)

        val totalMb = (memInfo.totalMem / (1024L * 1024L)).toInt().coerceAtLeast(512)
        val freeMb = (memInfo.availMem / (1024L * 1024L)).toInt().coerceAtLeast(0)
        val usedPercent = (((totalMb - freeMb).toDouble() / totalMb.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = powerManager?.isPowerSaveMode == true
        val isLowRam = activityManager?.isLowRamDevice == true || memInfo.lowMemory

        val runtime = Runtime.getRuntime()
        val heapUsedMb = ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)).toInt()
        val heapMaxMb = (runtime.maxMemory() / (1024L * 1024L)).toInt().coerceAtLeast(64)
        val cores = runtime.availableProcessors().coerceAtLeast(1)

        val bufferKb = when {
            isLowRam || isPowerSave || freeMb < 350 -> 8
            freeMb < 900 -> 32
            else -> 64
        }

        return DeviceTelemetryState(
            freeRamMb = freeMb,
            totalRamMb = totalMb,
            ramUsagePercent = usedPercent,
            cpuCores = cores,
            isPowerSaveMode = isPowerSave,
            isLowRamDevice = isLowRam,
            recommendedBufferKb = bufferKb,
            heapUsedMb = heapUsedMb,
            heapMaxMb = heapMaxMb
        )
    }

    fun deviceLabel(): String = "${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})"
}
