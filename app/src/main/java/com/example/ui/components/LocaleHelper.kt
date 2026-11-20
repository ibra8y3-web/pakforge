package com.example.ui.components

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

data class AppLanguageOption(
    val code: String,
    val nativeLabel: String,
    val englishSubtitle: String
)

val SupportedLanguages = listOf(
    AppLanguageOption("system", "System Default", "Device Locale"),
    AppLanguageOption("en", "English", "English"),
    AppLanguageOption("ar", "العربية", "Arabic (RTL)"),
    AppLanguageOption("es", "Español", "Spanish"),
    AppLanguageOption("fr", "Français", "French"),
    AppLanguageOption("de", "Deutsch", "German"),
    AppLanguageOption("zh", "简体中文", "Chinese (Simplified)")
)

private val initialSystemLocale: Locale = Locale.getDefault()

val LocalAppLanguageCode = compositionLocalOf { initialSystemLocale.language }

fun normalizeSupportedLangCode(raw: String): String {
    val effective = if (raw == "system") initialSystemLocale.language else raw
    val clean = effective.lowercase().substringBefore('-').substringBefore('_')
    return when (clean) {
        "ar", "es", "fr", "de", "zh", "en" -> clean
        else -> "en"
    }
}

private class LocalizedContextWrapper(
    private val activityBase: Context,
    private val configContext: Context
) : ContextWrapper(configContext) {
    private val localizedRes: Resources = configContext.resources
    private val localizedAssets: AssetManager = configContext.assets

    override fun getResources(): Resources = localizedRes
    override fun getAssets(): AssetManager = localizedAssets
    override fun createConfigurationContext(overrideConfiguration: Configuration): Context {
        return configContext.createConfigurationContext(overrideConfiguration)
    }

    override fun startActivity(intent: android.content.Intent?) {
        activityBase.startActivity(intent)
    }
}

@Composable
fun ProvideLocalizedContext(
    languageCode: String,
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current
    val onBackPressedDispatcherOwner = LocalOnBackPressedDispatcherOwner.current

    val targetLocale = remember(languageCode, baseConfig) {
        when (languageCode) {
            "system" -> initialSystemLocale
            "zh" -> Locale.SIMPLIFIED_CHINESE
            else -> Locale.forLanguageTag(languageCode)
        }
    }

    Locale.setDefault(targetLocale)

    val localizedConfig = remember(baseConfig, targetLocale) {
        Configuration(baseConfig).apply {
            setLocale(targetLocale)
            setLocales(LocaleList(targetLocale))
            setLayoutDirection(targetLocale)
        }
    }

    val localizedContext = remember(baseContext, localizedConfig) {
        val rawActivityContext = (baseContext as? ContextWrapper)?.baseContext ?: baseContext
        val cfgCtx = rawActivityContext.createConfigurationContext(localizedConfig)
        LocalizedContextWrapper(rawActivityContext, cfgCtx)
    }

    val layoutDirection = if (targetLocale.language == "ar") {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    val normalizedLang = remember(languageCode, targetLocale) {
        normalizeSupportedLangCode(targetLocale.language)
    }

    val providedValues = buildList<ProvidedValue<*>> {
        add(LocalContext provides localizedContext)
        add(LocalConfiguration provides localizedConfig)
        add(LocalLayoutDirection provides layoutDirection)
        add(LocalAppLanguageCode provides normalizedLang)
        activityResultRegistryOwner?.let { add(LocalActivityResultRegistryOwner provides it) }
        onBackPressedDispatcherOwner?.let { add(LocalOnBackPressedDispatcherOwner provides it) }
    }.toTypedArray()

    CompositionLocalProvider(
        values = providedValues,
        content = content
    )
}
