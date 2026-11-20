package com.example

import android.app.Application
import com.example.core.engine.SmartPackageEngine
import com.example.core.installer.PakForgeInstaller
import com.example.core.sdk.PluginManager
import com.example.core.shizuku.ShizukuBridgeManager
import com.example.core.telemetry.DeviceTelemetryProvider
import com.example.data.local.HistoryRepository
import com.example.data.local.PakForgeDatabase
import com.example.data.preferences.SettingsRepository

class PakForgeApp : Application() {
    val telemetryProvider: DeviceTelemetryProvider by lazy {
        DeviceTelemetryProvider(this)
    }

    val packageEngine: SmartPackageEngine by lazy {
        SmartPackageEngine(this, telemetryProvider)
    }

    val packageInstaller: PakForgeInstaller by lazy {
        PakForgeInstaller(this)
    }

    val shizukuBridge: ShizukuBridgeManager by lazy {
        ShizukuBridgeManager(this)
    }

    val database: PakForgeDatabase by lazy {
        PakForgeDatabase.getInstance(this)
    }

    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(database.historyDao())
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(this)
    }

    val pluginManager: PluginManager by lazy {
        PluginManager(
            context = this,
            telemetryProvider = telemetryProvider,
            shizukuBridge = shizukuBridge,
            historyRepository = historyRepository
        )
    }
}
