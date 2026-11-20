package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.engine.CompressionConfig
import com.example.core.engine.CompressionProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pakforge_settings")

data class UserPreferences(
    val languageCode: String = "system",
    val themeMode: String = "dark",
    val defaultConfig: CompressionConfig = CompressionConfig()
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language_code")
        val THEME = stringPreferencesKey("theme_mode")
        val PROFILE = stringPreferencesKey("compression_profile")
        val ARSC_ALIGN = booleanPreferencesKey("arsc_align")
        val ENTROPY_BYPASS = booleanPreferencesKey("entropy_bypass")
        val PRUNE_DEBUG = booleanPreferencesKey("prune_debug")
        val RAM_THROTTLE = booleanPreferencesKey("ram_throttle")
        val MERGE_SPLITS = booleanPreferencesKey("merge_splits_universal")
        val STRIP_SHARED_UID = booleanPreferencesKey("strip_shared_user_id")
        val PATCH_CERT_DIGEST = booleanPreferencesKey("patch_static_lib_cert_digest")
        val BUNDLE_COMPANION_LIBS = booleanPreferencesKey("bundle_companion_static_libs")
        val PRUNE_LOCALES = booleanPreferencesKey("prune_unused_locales")
        val OPTIMIZE_IMAGES = booleanPreferencesKey("optimize_large_images")
        val TINT_CLONE_ICON = booleanPreferencesKey("tint_clone_icon")
        val PREFER_SHIZUKU = booleanPreferencesKey("prefer_shizuku_installer")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val profile = prefs[Keys.PROFILE]?.let { name ->
            runCatching { CompressionProfile.valueOf(name) }.getOrNull()
        } ?: CompressionProfile.ZERO_RISK_BALANCED

        UserPreferences(
            languageCode = prefs[Keys.LANGUAGE] ?: "system",
            themeMode = prefs[Keys.THEME] ?: "dark",
            defaultConfig = CompressionConfig(
                profile = profile,
                enforceResourcesArscAlign = prefs[Keys.ARSC_ALIGN] ?: true,
                smartEntropyBypass = prefs[Keys.ENTROPY_BYPASS] ?: true,
                pruneDebugArtifacts = prefs[Keys.PRUNE_DEBUG] ?: true,
                adaptiveRamThrottle = prefs[Keys.RAM_THROTTLE] ?: true,
                mergeSplitsIntoUniversal = prefs[Keys.MERGE_SPLITS] ?: false,
                stripSharedUserId = prefs[Keys.STRIP_SHARED_UID] ?: true,
                patchStaticLibCertDigest = prefs[Keys.PATCH_CERT_DIGEST] ?: true,
                bundleCompanionStaticLibs = prefs[Keys.BUNDLE_COMPANION_LIBS] ?: true,
                pruneUnusedLocales = prefs[Keys.PRUNE_LOCALES] ?: false,
                optimizeLargeImages = prefs[Keys.OPTIMIZE_IMAGES] ?: false,
                tintCloneIcon = prefs[Keys.TINT_CLONE_ICON] ?: true,
                preferShizukuInstaller = prefs[Keys.PREFER_SHIZUKU] ?: false
            )
        )
    }

    suspend fun setLanguage(code: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = code }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME] = mode }
    }

    suspend fun updateConfig(config: CompressionConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PROFILE] = config.profile.name
            prefs[Keys.ARSC_ALIGN] = config.enforceResourcesArscAlign
            prefs[Keys.ENTROPY_BYPASS] = config.smartEntropyBypass
            prefs[Keys.PRUNE_DEBUG] = config.pruneDebugArtifacts
            prefs[Keys.RAM_THROTTLE] = config.adaptiveRamThrottle
            prefs[Keys.MERGE_SPLITS] = config.mergeSplitsIntoUniversal
            prefs[Keys.STRIP_SHARED_UID] = config.stripSharedUserId
            prefs[Keys.PATCH_CERT_DIGEST] = config.patchStaticLibCertDigest
            prefs[Keys.BUNDLE_COMPANION_LIBS] = config.bundleCompanionStaticLibs
            prefs[Keys.PRUNE_LOCALES] = config.pruneUnusedLocales
            prefs[Keys.OPTIMIZE_IMAGES] = config.optimizeLargeImages
            prefs[Keys.TINT_CLONE_ICON] = config.tintCloneIcon
            prefs[Keys.PREFER_SHIZUKU] = config.preferShizukuInstaller
        }
    }
}
