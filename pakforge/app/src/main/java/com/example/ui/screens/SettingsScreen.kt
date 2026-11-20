package com.example.ui.screens

import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.components.ForgeSectionCard
import com.example.ui.components.ForgePill
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.privileged.PrivilegedBackendKind
import com.example.core.privileged.PrivilegedBridgeSnapshot
import com.example.core.sdk.DeveloperKeyInfo
import com.example.core.sdk.PakForgeSdkSpec
import com.example.core.sdk.PluginManifest
import com.example.core.sdk.PluginNotificationRecord
import com.example.core.sdk.PluginPermission
import com.example.core.sdk.PluginRecord
import com.example.core.sdk.PluginSecurityScanResult
import com.example.core.sdk.SmartPluginRecommendation
import com.example.core.sdk.StorePluginListing
import com.example.core.shizuku.ShizukuStatus
import com.example.data.preferences.UserPreferences
import com.example.ui.components.DeveloperPluginSdkDialog
import com.example.ui.components.PrivilegedBridgeMultiCard
import com.example.ui.components.SupportedLanguages

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    shizukuStatus: ShizukuStatus,
    bridgeSnapshot: PrivilegedBridgeSnapshot,
    plugins: List<PluginRecord>,
    storeCatalog: List<StorePluginListing>,
    smartRecommendations: List<SmartPluginRecommendation>,
    pendingSecurityReview: PluginSecurityScanResult?,
    developerKeyInfo: DeveloperKeyInfo,
    sandboxTraces: List<String>,
    pluginNotifications: List<PluginNotificationRecord>,
    deviceLabel: String,
    onSelectLanguage: (String) -> Unit,
    onSelectTheme: (String) -> Unit,
    onSelectPrivilegedBackend: (PrivilegedBackendKind) -> Unit,
    onGrantShizuku: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRefreshShizuku: () -> Unit,
    onTogglePluginEnabled: (String) -> Unit,
    onTogglePluginPermission: (String, PluginPermission) -> Unit,
    onRunPlugin: (String) -> Unit,
    onRemoveCustomPlugin: (String) -> Unit,
    onInspectStoreListing: (StorePluginListing) -> Unit,
    onConfirmInstallScannedPlugin: (Set<PluginPermission>) -> Unit,
    onDismissSecurityReview: () -> Unit,
    onInstallDeveloperPlugin: (PluginManifest) -> Unit,
    onExportDeveloperPfpak: (PluginManifest) -> Unit,
    onRefreshSmartRecommendations: () -> Unit,
    onImportManifestClick: () -> Unit,
    onExportSdkZipClick: () -> Unit,
    onClearPluginNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeveloperHub by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PrivilegedBridgeMultiCard(
                status = shizukuStatus,
                snapshot = bridgeSnapshot,
                onSelectBackend = onSelectPrivilegedBackend,
                onGrantClick = onGrantShizuku,
                onOpenAppClick = onOpenShizuku,
                onRefreshClick = onRefreshShizuku
            )

            // Plugins & Developers hub
            ForgeSectionCard(
                title = stringResource(R.string.dev_hub_card_title),
                subtitle = stringResource(R.string.dev_hub_card_subtitle, PakForgeSdkSpec.SDK_VERSION),
                icon = Icons.Rounded.Extension,
                accent = MaterialTheme.colorScheme.tertiary,
                highlighted = true,
                modifier = Modifier.testTag("settings_dev_hub_card")
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ForgePill(
                        text = stringResource(R.string.dev_hub_badge_plugins_api, plugins.size, PakForgeSdkSpec.HOST_API_LEVEL),
                        accent = MaterialTheme.colorScheme.primary
                    )
                    ForgePill(
                        text = stringResource(R.string.dev_hub_badge_perms_sdk),
                        accent = MaterialTheme.colorScheme.secondary
                    )
                }

                Button(
                    onClick = {
                        onRefreshSmartRecommendations()
                        showDeveloperHub = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("open_dev_hub_button"),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.dev_hub_open_btn), fontWeight = FontWeight.Bold)
                }
            }

            // Language
            ForgeSectionCard(
                title = stringResource(R.string.settings_language),
                icon = Icons.Rounded.Language,
                accent = MaterialTheme.colorScheme.primary
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SupportedLanguages.forEach { lang ->
                        val isSelected = preferences.languageCode == lang.code
                        Surface(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { onSelectLanguage(lang.code) }
                                .testTag("lang_chip_${lang.code}"),
                            shape = MaterialTheme.shapes.medium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = if (lang.code == "system") stringResource(R.string.lang_system_title) else lang.nativeLabel,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = if (lang.code == "system") stringResource(R.string.lang_system_subtitle) else lang.englishSubtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Theme
            ForgeSectionCard(
                title = stringResource(R.string.settings_theme),
                icon = Icons.Rounded.Palette,
                accent = MaterialTheme.colorScheme.secondary
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ThemeModeTile(
                        label = stringResource(R.string.theme_dark),
                        icon = Icons.Rounded.DarkMode,
                        selected = preferences.themeMode == "dark",
                        onClick = { onSelectTheme("dark") },
                        modifier = Modifier.weight(1f).testTag("theme_dark_chip")
                    )
                    ThemeModeTile(
                        label = stringResource(R.string.theme_light),
                        icon = Icons.Rounded.LightMode,
                        selected = preferences.themeMode == "light",
                        onClick = { onSelectTheme("light") },
                        modifier = Modifier.weight(1f).testTag("theme_light_chip")
                    )
                    ThemeModeTile(
                        label = stringResource(R.string.theme_system),
                        icon = Icons.Rounded.BrightnessAuto,
                        selected = preferences.themeMode == "system",
                        onClick = { onSelectTheme("system") },
                        modifier = Modifier.weight(1f).testTag("theme_system_chip")
                    )
                }
            }

            // About / open source
            ForgeSectionCard(
                title = stringResource(R.string.settings_opensource_title),
                icon = Icons.Rounded.Code,
                accent = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = stringResource(R.string.settings_opensource_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ForgePill(text = deviceLabel, accent = MaterialTheme.colorScheme.tertiary)
            }
        }
    }

    if (showDeveloperHub) {
        DeveloperPluginSdkDialog(
            plugins = plugins,
            storeCatalog = storeCatalog,
            smartRecommendations = smartRecommendations,
            pendingSecurityReview = pendingSecurityReview,
            developerKeyInfo = developerKeyInfo,
            sandboxTraces = sandboxTraces,
            pluginNotifications = pluginNotifications,
            onTogglePluginEnabled = onTogglePluginEnabled,
            onTogglePermission = onTogglePluginPermission,
            onRunPlugin = onRunPlugin,
            onRemoveCustomPlugin = onRemoveCustomPlugin,
            onInspectStoreListing = onInspectStoreListing,
            onConfirmInstallScannedPlugin = onConfirmInstallScannedPlugin,
            onDismissSecurityReview = onDismissSecurityReview,
            onInstallDeveloperPlugin = onInstallDeveloperPlugin,
            onExportDeveloperPfpak = onExportDeveloperPfpak,
            onRefreshSmartContext = onRefreshSmartRecommendations,
            onImportManifestClick = onImportManifestClick,
            onExportSdkZipClick = onExportSdkZipClick,
            onClearNotifications = onClearPluginNotifications,
            onDismiss = { showDeveloperHub = false }
        )
    }
}


@Composable
private fun ThemeModeTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val accent = if (selected) cs.secondary else cs.onSurfaceVariant
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) cs.secondary.copy(alpha = 0.16f) else cs.surfaceVariant,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) cs.secondary else cs.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) cs.secondary else cs.onSurface,
                maxLines = 1
            )
        }
    }
}
