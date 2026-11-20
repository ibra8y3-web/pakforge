package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.rounded.VerifiedUser
import com.example.R
import com.example.core.sdk.DeveloperKeyInfo
import com.example.core.sdk.PakForgeSdkSpec
import com.example.core.sdk.PluginLocalization
import com.example.core.sdk.PluginManifest
import com.example.core.sdk.PluginManifestCodec
import com.example.core.sdk.PluginNotificationRecord
import com.example.core.sdk.PluginPermission
import com.example.core.sdk.PluginRecord
import com.example.core.sdk.PluginSdkExamples
import com.example.core.sdk.PluginSdkZipExporter
import com.example.core.sdk.PluginSecurityScanResult
import com.example.core.sdk.PluginSeverity
import com.example.core.sdk.SignatureTrustLevel
import com.example.core.sdk.SmartPluginRecommendation
import com.example.core.sdk.StorePluginListing
import com.example.ui.theme.TelemetryError
import com.example.ui.theme.TelemetrySuccess
import com.example.ui.theme.TelemetryWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperPluginSdkDialog(
    plugins: List<PluginRecord>,
    storeCatalog: List<StorePluginListing>,
    smartRecommendations: List<SmartPluginRecommendation>,
    pendingSecurityReview: PluginSecurityScanResult?,
    developerKeyInfo: DeveloperKeyInfo,
    sandboxTraces: List<String>,
    pluginNotifications: List<PluginNotificationRecord>,
    onTogglePluginEnabled: (String) -> Unit,
    onTogglePermission: (String, PluginPermission) -> Unit,
    onRunPlugin: (String) -> Unit,
    onRemoveCustomPlugin: (String) -> Unit,
    onInspectStoreListing: (StorePluginListing) -> Unit,
    onConfirmInstallScannedPlugin: (Set<PluginPermission>) -> Unit,
    onDismissSecurityReview: () -> Unit,
    onInstallDeveloperPlugin: (PluginManifest) -> Unit,
    onExportDeveloperPfpak: (PluginManifest) -> Unit,
    onRefreshSmartContext: () -> Unit,
    onImportManifestClick: () -> Unit,
    onExportSdkZipClick: () -> Unit,
    onClearNotifications: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showBuilderDialog by rememberSaveable { mutableStateOf(false) }
    var pendingPluginRunId by remember { mutableStateOf<String?>(null) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        pendingPluginRunId?.let { id ->
            pendingPluginRunId = null
            onRunPlugin(id)
        }
    }

    val runWithNotificationCheck: (PluginRecord) -> Unit = { rec ->
        val needsNotifPerm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            PluginPermission.SEND_NOTIFICATIONS in rec.grantedPermissions &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotifPerm) {
            pendingPluginRunId = rec.manifest.id
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onRunPlugin(rec.manifest.id)
        }
    }

    val activeLang = LocalAppLanguageCode.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        ProvideLocalizedContext(languageCode = activeLang) {
            BackHandler(onBack = onDismiss)

            Scaffold(
                modifier = Modifier.forgeBackdrop(),
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.dev_hub_card_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "SDK v${PakForgeSdkSpec.SDK_VERSION} • Host API ${PakForgeSdkSpec.HOST_API_LEVEL}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("dev_hub_close_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.action_close)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 820.dp)
                        .fillMaxSize()
                ) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        edgePadding = 12.dp,
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        val tabs = listOf(
                            R.string.dev_hub_tab_store,
                            R.string.dev_hub_tab_plugins,
                            R.string.dev_hub_tab_sdk,
                            R.string.dev_hub_tab_examples,
                            R.string.dev_hub_tab_docs
                        )
                        tabs.forEachIndexed { index, titleRes ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(stringResource(titleRes)) },
                                modifier = Modifier.testTag("dev_hub_tab_$index")
                            )
                        }
                    }

                    when (selectedTab) {
                        0 -> PluginStoreTabContent(
                            storeCatalog = storeCatalog,
                            installedPluginIds = plugins.mapTo(HashSet()) { it.manifest.id },
                            smartRecommendations = smartRecommendations,
                            onInspectStoreListing = onInspectStoreListing,
                            onRunInstalledPlugin = { id ->
                                val rec = plugins.firstOrNull { it.manifest.id == id }
                                if (rec != null) {
                                    runWithNotificationCheck(rec)
                                } else {
                                    onRunPlugin(id)
                                }
                            },
                            onImportBundleClick = onImportManifestClick,
                            onOpenBuilderClick = { showBuilderDialog = true },
                            onRefreshSmartContext = onRefreshSmartContext
                        )
                        1 -> PluginsAndPermissionsTab(
                            plugins = plugins,
                            sandboxTraces = sandboxTraces,
                            pluginNotifications = pluginNotifications,
                            onTogglePluginEnabled = onTogglePluginEnabled,
                            onTogglePermission = onTogglePermission,
                            onRunPlugin = runWithNotificationCheck,
                            onRemoveCustomPlugin = onRemoveCustomPlugin,
                            onImportManifestClick = onImportManifestClick,
                            onClearNotifications = onClearNotifications
                        )
                        2 -> SdkAndSampleProjectTab(
                            onExportSdkZipClick = onExportSdkZipClick
                        )
                        3 -> UnifiedApisAndExamplesTab()
                        else -> FullSdkDocumentationTab()
                    }
                }
            }
        }
        }
    }

    if (showBuilderDialog) {
        DeveloperPluginBuilderDialog(
            developerKeyInfo = developerKeyInfo,
            onSignAndInstall = { manifest ->
                showBuilderDialog = false
                onInstallDeveloperPlugin(manifest)
            },
            onSignAndExport = { manifest ->
                onExportDeveloperPfpak(manifest)
            },
            onDismiss = { showBuilderDialog = false }
        )
    }

    pendingSecurityReview?.let { scanResult ->
        PluginSecurityReviewDialog(
            scanResult = scanResult,
            onConfirmInstall = { chosenPerms ->
                onConfirmInstallScannedPlugin(chosenPerms)
                selectedTab = 1
            },
            onDismiss = onDismissSecurityReview
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginsAndPermissionsTab(
    plugins: List<PluginRecord>,
    sandboxTraces: List<String>,
    pluginNotifications: List<PluginNotificationRecord>,
    onTogglePluginEnabled: (String) -> Unit,
    onTogglePermission: (String, PluginPermission) -> Unit,
    onRunPlugin: (PluginRecord) -> Unit,
    onRemoveCustomPlugin: (String) -> Unit,
    onImportManifestClick: () -> Unit,
    onClearNotifications: () -> Unit
) {
    val activeLang = LocalAppLanguageCode.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            OutlinedButton(
                onClick = onImportManifestClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("dev_import_manifest_btn"),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(imageVector = Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.dev_import_manifest_btn))
            }
        }

        items(plugins, key = { it.manifest.id }) { rec ->
            var showPermissions by remember(rec.manifest.id) { mutableStateOf(false) }

            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val localizedCopy = remember(rec.manifest, activeLang) {
                        PluginLocalization.manifestCopy(rec.manifest, activeLang)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ForgeIconTile(
                            icon = Icons.Rounded.Extension,
                            accent = if (rec.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = localizedCopy.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${rec.manifest.id} • v${rec.manifest.version} • API ${rec.manifest.apiVersion}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val trustBadge = PluginLocalization.trustBadge(rec.signatureTrust, activeLang)
                            val trustTint = when (rec.signatureTrust) {
                                SignatureTrustLevel.UNSIGNED -> TelemetryWarning
                                SignatureTrustLevel.TAMPERED_REJECTED -> TelemetryError
                                else -> TelemetrySuccess
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VerifiedUser,
                                    contentDescription = null,
                                    tint = trustTint,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = buildString {
                                        append(trustBadge)
                                        rec.signerFingerprint?.let { append(" • ").append(it.take(12)) }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = trustTint
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!rec.isBuiltInSample) {
                                IconButton(
                                    onClick = { onRemoveCustomPlugin(rec.manifest.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = stringResource(R.string.dev_remove_plugin_cd),
                                        tint = TelemetryError
                                    )
                                }
                            }
                            Switch(
                                checked = rec.enabled,
                                onCheckedChange = { onTogglePluginEnabled(rec.manifest.id) },
                                modifier = Modifier.testTag("plugin_toggle_${rec.manifest.id}")
                            )
                        }
                    }

                    Text(
                        text = localizedCopy.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onRunPlugin(rec) },
                            enabled = rec.enabled,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("run_plugin_${rec.manifest.id}")
                        ) {
                            Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.dev_run_sandbox_btn))
                        }

                        OutlinedButton(
                            onClick = { showPermissions = !showPermissions },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("perms_toggle_${rec.manifest.id}")
                        ) {
                            Icon(imageVector = Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                stringResource(
                                    R.string.dev_permissions_label,
                                    rec.grantedPermissions.size,
                                    rec.manifest.permissions.size
                                )
                            )
                        }
                    }

                    if (showPermissions) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            rec.manifest.permissions.forEach { perm ->
                                val granted = perm in rec.grantedPermissions
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = PluginLocalization.permissionTitle(perm, activeLang),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${perm.key} — ${PluginLocalization.permissionRiskSummary(perm, activeLang)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = granted,
                                        onCheckedChange = { onTogglePermission(rec.manifest.id, perm) },
                                        modifier = Modifier.testTag("perm_switch_${rec.manifest.id}_${perm.name}")
                                    )
                                }
                            }
                        }
                    }

                    rec.lastExecutionSummary?.let { summary ->
                        val ok = rec.lastExecutionOk == true
                        val tint = if (ok) TelemetrySuccess else TelemetryError
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = tint.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.labelSmall,
                                color = tint,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        if (sandboxTraces.isNotEmpty()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.dev_sandbox_output_title), style = MaterialTheme.typography.titleSmall)
                        }
                        sandboxTraces.forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        if (pluginNotifications.isNotEmpty()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                                Text(stringResource(R.string.dev_notifications_feed_title), style = MaterialTheme.typography.titleSmall)
                            }
                            OutlinedButton(onClick = onClearNotifications, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.dev_clear_btn))
                            }
                        }
                        val sentTrayLabel = stringResource(R.string.dev_sent_system_tray)
                        pluginNotifications.take(8).forEach { notif ->
                            val badgeColor = when (notif.severity) {
                                PluginSeverity.SUCCESS -> TelemetrySuccess
                                PluginSeverity.WARNING -> TelemetryWarning
                                PluginSeverity.ERROR -> TelemetryError
                                PluginSeverity.INFO -> MaterialTheme.colorScheme.primary
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = badgeColor.copy(alpha = 0.08f)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                    Text(
                                        text = "[${notif.pluginName}] ${notif.title}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = badgeColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = notif.message + if (notif.dispatchedToSystemTray) " • $sentTrayLabel" else "",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun SdkAndSampleProjectTab(
    onExportSdkZipClick: () -> Unit
) {
    val sdkFiles = remember { PluginSdkZipExporter.buildSdkFilesMap() }
    val sampleManifestJson = remember {
        PluginManifestCodec.toJson(com.example.core.sdk.BuiltInSamplePlugins.deviceThermalGuardPlugin.manifest)
    }
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(R.string.dev_sdk_header_title, PakForgeSdkSpec.SDK_VERSION),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = stringResource(R.string.dev_sdk_header_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onExportSdkZipClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("export_sdk_zip_button"),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.dev_export_sdk_btn))
                }
            }
        }

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(stringResource(R.string.dev_sdk_tree_title, sdkFiles.size), style = MaterialTheme.typography.titleSmall)
                sdkFiles.keys.forEach { path ->
                    Text(
                        text = "• $path",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.dev_sdk_ref_manifest), style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(sampleManifestJson)) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.dev_copy_code_btn))
                    }
                }
                Text(
                    text = sampleManifestJson,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            }
        }
    }
}

@Composable
private fun UnifiedApisAndExamplesTab() {
    val activeLang = LocalAppLanguageCode.current
    val clipboard = LocalClipboardManager.current
    val interfaces = remember(activeLang) {
        PluginLocalization.sdkInterfaces(activeLang)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.dev_apis_unified_title), style = MaterialTheme.typography.titleSmall)
                }
                interfaces.forEach { info ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(info.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(
                                text = stringResource(R.string.dev_apis_perm_label, info.permissionKey),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(info.methods, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        PluginSdkExamples.snippets.forEach { example ->
            val (exTitle, exSubtitle) = remember(example, activeLang) {
                PluginLocalization.localizeExampleMeta(example, activeLang)
            }
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(exTitle, style = MaterialTheme.typography.titleSmall)
                            Text(exSubtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(example.code)) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.dev_copy_code_btn))
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = example.code,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                                .horizontalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullSdkDocumentationTab() {
    val activeLang = LocalAppLanguageCode.current
    val sections = remember(activeLang) {
        PluginLocalization.documentationSections(activeLang)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        sections.forEach { section ->
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(section.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
