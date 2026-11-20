package com.example.ui.screens

import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.engine.CompressionConfig
import com.example.core.engine.CompressionPhase
import com.example.core.engine.CompressionProfile
import com.example.core.engine.CompressionProgress
import com.example.core.engine.CompressionReport
import com.example.core.engine.InstallIdentityMode
import com.example.core.engine.InstalledAppItem
import com.example.core.engine.PackageInspectionResult
import com.example.core.engine.formatBytes
import com.example.core.shizuku.ShizukuStatus
import com.example.core.telemetry.DeviceTelemetryState
import com.example.ui.ApkInstallUiState
import com.example.ui.ForgePipelineState
import com.example.ui.InstallOptionsSheetState
import com.example.ui.components.forgeFieldColors
import com.example.ui.components.CompatibilityReportCard
import com.example.ui.components.CompressionSavingsRing
import com.example.ui.components.DrawableAppIcon
import com.example.ui.components.EntropyGauge
import com.example.ui.components.HeroForgeBanner
import com.example.ui.components.LocalAppLanguageCode
import com.example.ui.components.PackageBreakdownCanvasBar
import com.example.ui.components.ProvideLocalizedContext
import com.example.ui.components.ShizukuEngineBanner
import com.example.ui.theme.TelemetryError
import com.example.ui.theme.TelemetrySuccess
import com.example.ui.theme.TelemetryWarning
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgeScreen(
    isExpandedWidth: Boolean,
    devicePrimaryAbi: String,
    pipelineState: ForgePipelineState,
    installState: ApkInstallUiState,
    installOptionsSheet: InstallOptionsSheetState?,
    isRepackingClone: Boolean,
    canInstallPackages: Boolean,
    shizukuStatus: ShizukuStatus,
    telemetry: DeviceTelemetryState,
    config: CompressionConfig,
    installedApps: List<InstalledAppItem>,
    isLoadingApps: Boolean,
    onPickFileClick: () -> Unit,
    onOpenInstalledAppsPicker: () -> Unit,
    onSelectInstalledApp: (InstalledAppItem) -> Unit,
    onSelfTestClick: () -> Unit,
    onAutoFixCompatibility: () -> Unit,
    onSelectProfile: (CompressionProfile) -> Unit,
    onUpdateConfig: ((CompressionConfig) -> CompressionConfig) -> Unit,
    onSelectInstallMode: (InstallIdentityMode) -> Unit,
    onCloneSuffixChange: (String) -> Unit,
    onCustomCloneLabelChange: (String) -> Unit,
    onKeepAllAbis: () -> Unit,
    onDeviceAbiOnly: () -> Unit,
    onToggleAbi: (String, List<String>) -> Unit,
    onToggleLocale: (String) -> Unit,
    onGrantShizuku: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRefreshShizuku: () -> Unit,
    onCompressClick: () -> Unit,
    onInstallApkClick: () -> Unit,
    onOpenInstallOptions: () -> Unit,
    onDismissInstallOptions: () -> Unit,
    onChooseUpdateExisting: () -> Unit,
    onChooseUninstallAndInstall: () -> Unit,
    onChooseInstallClone: (String) -> Unit,
    onChooseInstallViaShizuku: () -> Unit,
    onChooseInstallCompanionLibs: () -> Unit,
    onChooseUninstallViaShizuku: (String, Boolean) -> Unit,
    onSaveOutputClick: (String) -> Unit,
    onShareOutputClick: () -> Unit,
    onJsonCopied: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAppsSheet by remember { mutableStateOf(false) }

    if (showAppsSheet) {
        InstalledAppsModalSheet(
            apps = installedApps,
            isLoading = isLoadingApps,
            onSelectApp = {
                showAppsSheet = false
                onSelectInstalledApp(it)
            },
            onDismiss = { showAppsSheet = false }
        )
    }

    if (installOptionsSheet != null) {
        InstallOptionsModalSheet(
            sheetState = installOptionsSheet,
            defaultSuffix = config.cloneSuffix.ifBlank { "pkf" },
            isRepackingClone = isRepackingClone,
            onChooseUpdateExisting = onChooseUpdateExisting,
            onChooseUninstallAndInstall = onChooseUninstallAndInstall,
            onChooseInstallClone = onChooseInstallClone,
            onChooseInstallViaShizuku = onChooseInstallViaShizuku,
            onChooseInstallCompanionLibs = onChooseInstallCompanionLibs,
            onChooseUninstallViaShizuku = onChooseUninstallViaShizuku,
            onDismiss = onDismissInstallOptions
        )
    }

    val openAppsAction = {
        onOpenInstalledAppsPicker()
        showAppsSheet = true
    }

    val activeInspection = when (pipelineState) {
        is ForgePipelineState.Ready -> pipelineState.inspection
        is ForgePipelineState.Compressing -> pipelineState.inspection
        is ForgePipelineState.Completed -> pipelineState.inspection
        else -> null
    }

    if (isExpandedWidth) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.46f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeroForgeBanner(telemetry = telemetry)
                SourcePickerCard(
                    onPickFileClick = onPickFileClick,
                    onPickInstalledClick = openAppsAction,
                    onSelfTestClick = onSelfTestClick
                )
                EngineControlsCard(
                    config = config,
                    detectedAbis = activeInspection?.detectedAbis.orEmpty(),
                    detectedLocales = activeInspection?.detectedLocales.orEmpty(),
                    hasSplits = activeInspection?.splitSourcePaths?.isNotEmpty() == true,
                    hasSharedUserId = activeInspection?.hasSharedUserId == true,
                    hasStaticLibraries = activeInspection?.staticLibraries?.isNotEmpty() == true,
                    sourcePackageId = activeInspection?.packageId,
                    devicePrimaryAbi = devicePrimaryAbi,
                    shizukuStatus = shizukuStatus,
                    canCompress = pipelineState is ForgePipelineState.Ready || pipelineState is ForgePipelineState.Completed,
                    isCompressing = pipelineState is ForgePipelineState.Compressing,
                    onSelectProfile = onSelectProfile,
                    onUpdateConfig = onUpdateConfig,
                    onSelectInstallMode = onSelectInstallMode,
                    onCloneSuffixChange = onCloneSuffixChange,
                    onCustomCloneLabelChange = onCustomCloneLabelChange,
                    onKeepAllAbis = onKeepAllAbis,
                    onDeviceAbiOnly = onDeviceAbiOnly,
                    onToggleAbi = onToggleAbi,
                    onToggleLocale = onToggleLocale,
                    onGrantShizuku = onGrantShizuku,
                    onOpenShizuku = onOpenShizuku,
                    onRefreshShizuku = onRefreshShizuku,
                    onCompressClick = onCompressClick
                )
            }

            Column(
                modifier = Modifier
                    .weight(0.54f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PipelineStagePanel(
                    pipelineState = pipelineState,
                    installState = installState,
                    canInstallPackages = canInstallPackages,
                    onAutoFixCompatibility = onAutoFixCompatibility,
                    onInstallApkClick = onInstallApkClick,
                    onOpenInstallOptions = onOpenInstallOptions,
                    onSaveOutputClick = onSaveOutputClick,
                    onShareOutputClick = onShareOutputClick,
                    onJsonCopied = onJsonCopied,
                    onDismissError = onDismissError
                )
            }
        }
    } else {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeroForgeBanner(telemetry = telemetry)
                SourcePickerCard(
                    onPickFileClick = onPickFileClick,
                    onPickInstalledClick = openAppsAction,
                    onSelfTestClick = onSelfTestClick
                )
                PipelineStagePanel(
                    pipelineState = pipelineState,
                    installState = installState,
                    canInstallPackages = canInstallPackages,
                    onAutoFixCompatibility = onAutoFixCompatibility,
                    onInstallApkClick = onInstallApkClick,
                    onOpenInstallOptions = onOpenInstallOptions,
                    onSaveOutputClick = onSaveOutputClick,
                    onShareOutputClick = onShareOutputClick,
                    onJsonCopied = onJsonCopied,
                    onDismissError = onDismissError
                )
                EngineControlsCard(
                    config = config,
                    detectedAbis = activeInspection?.detectedAbis.orEmpty(),
                    detectedLocales = activeInspection?.detectedLocales.orEmpty(),
                    hasSplits = activeInspection?.splitSourcePaths?.isNotEmpty() == true,
                    hasSharedUserId = activeInspection?.hasSharedUserId == true,
                    hasStaticLibraries = activeInspection?.staticLibraries?.isNotEmpty() == true,
                    sourcePackageId = activeInspection?.packageId,
                    devicePrimaryAbi = devicePrimaryAbi,
                    shizukuStatus = shizukuStatus,
                    canCompress = pipelineState is ForgePipelineState.Ready || pipelineState is ForgePipelineState.Completed,
                    isCompressing = pipelineState is ForgePipelineState.Compressing,
                    onSelectProfile = onSelectProfile,
                    onUpdateConfig = onUpdateConfig,
                    onSelectInstallMode = onSelectInstallMode,
                    onCloneSuffixChange = onCloneSuffixChange,
                    onCustomCloneLabelChange = onCustomCloneLabelChange,
                    onKeepAllAbis = onKeepAllAbis,
                    onDeviceAbiOnly = onDeviceAbiOnly,
                    onToggleAbi = onToggleAbi,
                    onToggleLocale = onToggleLocale,
                    onGrantShizuku = onGrantShizuku,
                    onOpenShizuku = onOpenShizuku,
                    onRefreshShizuku = onRefreshShizuku,
                    onCompressClick = onCompressClick
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SourcePickerCard(
    onPickFileClick: () -> Unit,
    onPickInstalledClick: () -> Unit,
    onSelfTestClick: () -> Unit
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPickFileClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .testTag("upload_package_button"),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileUpload,
                        contentDescription = stringResource(R.string.action_pick_file),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_pick_file),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledTonalButton(
                    onClick = onPickInstalledClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .testTag("installed_apps_button"),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Apps,
                        contentDescription = stringResource(R.string.action_pick_installed),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_pick_installed),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedButton(
                onClick = onSelfTestClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("self_test_button"),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = stringResource(R.string.action_self_test),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.action_self_test))
            }
        }
    }
}

@Composable
private fun PipelineStagePanel(
    pipelineState: ForgePipelineState,
    installState: ApkInstallUiState,
    canInstallPackages: Boolean,
    onAutoFixCompatibility: () -> Unit,
    onInstallApkClick: () -> Unit,
    onOpenInstallOptions: () -> Unit,
    onSaveOutputClick: (String) -> Unit,
    onShareOutputClick: () -> Unit,
    onJsonCopied: () -> Unit,
    onDismissError: () -> Unit
) {
    when (pipelineState) {
        is ForgePipelineState.Idle -> {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    com.example.ui.components.ForgeEmptyIconBadge(icon = Icons.Rounded.Shield)
                    Text(
                        text = stringResource(R.string.empty_state_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.empty_state_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        is ForgePipelineState.Inspecting -> {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.phase_inspecting),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = pipelineState.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        is ForgePipelineState.Ready -> {
            PackageBlueprintCard(
                inspection = pipelineState.inspection,
                onAutoFixCompatibility = onAutoFixCompatibility
            )
        }

        is ForgePipelineState.Compressing -> {
            CompressionProgressCard(progress = pipelineState.progress)
            PackageBlueprintCard(
                inspection = pipelineState.inspection,
                onAutoFixCompatibility = onAutoFixCompatibility
            )
        }

        is ForgePipelineState.Completed -> {
            CompressionReportCard(
                report = pipelineState.report,
                installState = installState,
                canInstallPackages = canInstallPackages,
                onInstallApkClick = onInstallApkClick,
                onOpenInstallOptions = onOpenInstallOptions,
                onSaveOutputClick = onSaveOutputClick,
                onShareOutputClick = onShareOutputClick,
                onJsonCopied = onJsonCopied
            )
            PackageBlueprintCard(
                inspection = pipelineState.inspection,
                onAutoFixCompatibility = onAutoFixCompatibility
            )
        }

        is ForgePipelineState.Error -> {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = pipelineState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    TextButton(
                        onClick = onDismissError,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(text = stringResource(R.string.action_close))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackageBlueprintCard(
    inspection: PackageInspectionResult,
    onAutoFixCompatibility: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DrawableAppIcon(
                    drawable = inspection.appIcon,
                    contentDescription = inspection.displayName,
                    size = 48.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = inspection.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = inspection.packageId ?: "${inspection.fileExtension.uppercase(Locale.US)} Archive",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                CompressionSavingsRing(
                    savedPercent = inspection.predictedSavedPercent,
                    label = stringResource(R.string.predicted_output_label),
                    accentColor = MaterialTheme.colorScheme.primary
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip(label = stringResource(R.string.result_before), value = formatBytes(inspection.totalArchiveBytes))
                MetricChip(label = stringResource(R.string.predicted_output_label), value = formatBytes(inspection.predictedOutputBytes))
                MetricChip(label = stringResource(R.string.pkg_entries), value = "${inspection.entryCount}")
                MetricChip(label = stringResource(R.string.pkg_safety_score), value = "${inspection.safetyScore}%")
                inspection.versionName?.let {
                    MetricChip(label = stringResource(R.string.pkg_version), value = it)
                }
                if (inspection.minSdk != null && inspection.targetSdk != null) {
                    MetricChip(label = stringResource(R.string.pkg_sdk), value = "${inspection.minSdk}→${inspection.targetSdk}")
                }
                if (inspection.splitSourcePaths.isNotEmpty()) {
                    MetricChip(
                        label = "Bundle",
                        value = stringResource(R.string.split_bundle_badge, inspection.splitSourcePaths.size + 1)
                    )
                }
            }

            inspection.compatibilityReport?.let { compat ->
                CompatibilityReportCard(
                    report = compat,
                    onAutoFixClick = onAutoFixCompatibility
                )
            }

            if (inspection.manifestRuntimeRisks.isNotEmpty()) {
                RuntimeRiskBanner(risks = inspection.manifestRuntimeRisks)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.pkg_entropy),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                EntropyGauge(entropy = inspection.averageEntropy)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            PackageBreakdownCanvasBar(
                breakdown = inspection.breakdown,
                totalRawBytes = inspection.totalRawBytes
            )
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CompressionProgressCard(progress: CompressionProgress) {
    val phaseText = when (progress.phase) {
        CompressionPhase.INSPECTING -> stringResource(R.string.phase_inspecting)
        CompressionPhase.COMPRESSING -> stringResource(R.string.phase_compressing)
        CompressionPhase.VERIFYING -> stringResource(R.string.phase_verifying)
    }

    com.example.ui.components.ForgeSectionCard(
        title = phaseText,
        icon = androidx.compose.material.icons.Icons.Rounded.Bolt,
        accent = MaterialTheme.colorScheme.primary,
        highlighted = true,
        trailing = {
            com.example.ui.components.ForgePill(
                text = "${progress.processedEntries}/${progress.totalEntries} • ${progress.activeBufferKb}KB",
                accent = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp)),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Text(
            text = progress.currentEntry,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompressionReportCard(
    report: CompressionReport,
    installState: ApkInstallUiState,
    canInstallPackages: Boolean,
    onInstallApkClick: () -> Unit,
    onOpenInstallOptions: () -> Unit,
    onSaveOutputClick: (String) -> Unit,
    onShareOutputClick: () -> Unit,
    onJsonCopied: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, TelemetrySuccess.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TelemetrySuccess.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = TelemetrySuccess,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.result_verified_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = TelemetrySuccess,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (report.signedV2CertSha256 != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = stringResource(R.string.install_signed_v2_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    if (report.mergedUniversalApk) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = stringResource(R.string.result_merged_universal),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    } else if (report.splitOutputPaths.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = stringResource(R.string.split_bundle_badge, report.splitOutputPaths.size + 1),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    if (report.strippedSharedUserId) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TelemetrySuccess.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = stringResource(R.string.result_stripped_shared_uid),
                                style = MaterialTheme.typography.labelSmall,
                                color = TelemetrySuccess,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                CompressionSavingsRing(
                    savedPercent = report.savedPercent,
                    label = stringResource(R.string.result_saved),
                    accentColor = TelemetrySuccess
                )
            }

            report.effectivePackageId?.let { targetPkg ->
                Text(
                    text = stringResource(R.string.install_target_pkg, targetPkg),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.result_before),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatBytes(report.originalBytes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.result_after),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatBytes(report.compressedBytes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.result_saved),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatBytes(report.savedBytes),
                            style = MaterialTheme.typography.titleMedium,
                            color = TelemetrySuccess,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip(label = stringResource(R.string.result_elapsed), value = "${report.elapsedMs} ms")
                MetricChip(label = stringResource(R.string.result_peak_ram), value = "${report.peakHeapMb} MB")
                MetricChip(label = stringResource(R.string.result_bypassed), value = "${report.bypassedMediaCount}")
                MetricChip(label = stringResource(R.string.result_pruned), value = "${report.prunedArtifactsCount}")
                if (report.strippedAbiEntriesCount > 0) {
                    MetricChip(label = stringResource(R.string.result_abi_stripped), value = "${report.strippedAbiEntriesCount}")
                }
                if (report.localesPrunedCount > 0) {
                    MetricChip(label = stringResource(R.string.result_locales_pruned), value = "${report.localesPrunedCount}")
                }
                if (report.imagesOptimizedCount > 0) {
                    MetricChip(label = stringResource(R.string.result_images_crunched), value = "${report.imagesOptimizedCount}")
                }
                if (report.resourcesArscAligned) {
                    MetricChip(label = "ZipAlign", value = stringResource(R.string.result_arsc_status))
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.result_sha256),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = report.sha256Hex,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (report.companionLibraryOutputs.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = stringResource(R.string.install_companion_and_app),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (report.manifestRuntimeRisks.isNotEmpty()) {
                RuntimeRiskBanner(risks = report.manifestRuntimeRisks)
            }

            if (report.isApkArchive) {
                Button(
                    onClick = onInstallApkClick,
                    enabled = installState !is ApkInstallUiState.PreparingSession,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("install_apk_button"),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canInstallPackages) TelemetrySuccess else MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (canInstallPackages) Icons.Rounded.InstallMobile else Icons.Rounded.Security,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (canInstallPackages) {
                            stringResource(R.string.action_install_apk)
                        } else {
                            stringResource(R.string.action_grant_install_perm)
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = onOpenInstallOptions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("install_options_button"),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_install_options),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                AnimatedVisibility(visible = installState !is ApkInstallUiState.Idle) {
                    val (bgColor, textColor, msg) = when (installState) {
                        ApkInstallUiState.PreparingSession -> Triple(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            MaterialTheme.colorScheme.primary,
                            stringResource(R.string.install_status_preparing)
                        )
                        ApkInstallUiState.WaitingUserConfirm -> Triple(
                            TelemetryWarning.copy(alpha = 0.16f),
                            TelemetryWarning,
                            stringResource(R.string.install_status_prompt)
                        )
                        is ApkInstallUiState.Installed -> Triple(
                            TelemetrySuccess.copy(alpha = 0.18f),
                            TelemetrySuccess,
                            stringResource(R.string.install_status_success)
                        )
                        is ApkInstallUiState.Failed -> Triple(
                            TelemetryError.copy(alpha = 0.16f),
                            TelemetryError,
                            stringResource(R.string.install_status_failed, installState.reason)
                        )
                        ApkInstallUiState.Idle -> Triple(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.onSurface,
                            ""
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = bgColor
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor
                            )
                            if (installState is ApkInstallUiState.Failed) {
                                OutlinedButton(
                                    onClick = onOpenInstallOptions,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("install_recovery_button")
                                ) {
                                    Text(text = stringResource(R.string.action_install_options))
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = { onSaveOutputClick(report.outputFileName) },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .testTag("save_output_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = stringResource(R.string.action_save_output),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.action_save_output))
                }

                FilledTonalButton(
                    onClick = onShareOutputClick,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .testTag("share_output_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = stringResource(R.string.action_share_output),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.action_share_output))
                }
            }

            OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(report.toAuditJson()))
                        onJsonCopied()
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("copy_json_audit_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.action_copy_json),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.action_copy_json))
                }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EngineControlsCard(
    config: CompressionConfig,
    detectedAbis: List<String>,
    detectedLocales: List<String>,
    hasSplits: Boolean,
    hasSharedUserId: Boolean,
    hasStaticLibraries: Boolean,
    sourcePackageId: String?,
    devicePrimaryAbi: String,
    shizukuStatus: ShizukuStatus,
    canCompress: Boolean,
    isCompressing: Boolean,
    onSelectProfile: (CompressionProfile) -> Unit,
    onUpdateConfig: ((CompressionConfig) -> CompressionConfig) -> Unit,
    onSelectInstallMode: (InstallIdentityMode) -> Unit,
    onCloneSuffixChange: (String) -> Unit,
    onCustomCloneLabelChange: (String) -> Unit,
    onKeepAllAbis: () -> Unit,
    onDeviceAbiOnly: () -> Unit,
    onToggleAbi: (String, List<String>) -> Unit,
    onToggleLocale: (String) -> Unit,
    onGrantShizuku: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRefreshShizuku: () -> Unit,
    onCompressClick: () -> Unit
) {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                com.example.ui.components.ForgeIconTile(
                    icon = Icons.Rounded.Tune,
                    accent = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.engine_config_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            ProfileOptionRow(
                selected = config.profile == CompressionProfile.ZERO_RISK_BALANCED,
                title = stringResource(R.string.profile_balanced),
                desc = stringResource(R.string.profile_balanced_desc),
                tag = "profile_balanced",
                onClick = { onSelectProfile(CompressionProfile.ZERO_RISK_BALANCED) }
            )
            ProfileOptionRow(
                selected = config.profile == CompressionProfile.ULTRA_SHRINK,
                title = stringResource(R.string.profile_ultra),
                desc = stringResource(R.string.profile_ultra_desc),
                tag = "profile_ultra",
                onClick = { onSelectProfile(CompressionProfile.ULTRA_SHRINK) }
            )
            ProfileOptionRow(
                selected = config.profile == CompressionProfile.LOW_RESOURCE_ECO,
                title = stringResource(R.string.profile_eco),
                desc = stringResource(R.string.profile_eco_desc),
                tag = "profile_eco",
                onClick = { onSelectProfile(CompressionProfile.LOW_RESOURCE_ECO) }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = stringResource(R.string.install_mode_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            ProfileOptionRow(
                selected = config.installIdentityMode == InstallIdentityMode.SAME_PACKAGE,
                title = stringResource(R.string.install_mode_same),
                desc = stringResource(R.string.install_mode_same_desc),
                tag = "install_mode_same",
                onClick = { onSelectInstallMode(InstallIdentityMode.SAME_PACKAGE) }
            )
            ProfileOptionRow(
                selected = config.installIdentityMode == InstallIdentityMode.CLONE_NEW_PACKAGE,
                title = stringResource(R.string.install_mode_clone),
                desc = stringResource(R.string.install_mode_clone_desc),
                tag = "install_mode_clone",
                onClick = { onSelectInstallMode(InstallIdentityMode.CLONE_NEW_PACKAGE) }
            )

            AnimatedVisibility(visible = config.installIdentityMode == InstallIdentityMode.CLONE_NEW_PACKAGE) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        colors = forgeFieldColors(),
                        value = config.cloneSuffix,
                        onValueChange = onCloneSuffixChange,
                        label = { Text(stringResource(R.string.install_clone_suffix)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clone_suffix_input")
                    )
                    OutlinedTextField(
                        colors = forgeFieldColors(),
                        value = config.customCloneLabel,
                        onValueChange = onCustomCloneLabelChange,
                        label = { Text(stringResource(R.string.clone_custom_label_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clone_custom_label_input")
                    )
                    GuardSwitchRow(
                        label = stringResource(R.string.clone_tint_icon_toggle),
                        checked = config.tintCloneIcon,
                        tag = "switch_clone_tint_icon",
                        onCheckedChange = { checked -> onUpdateConfig { it.copy(tintCloneIcon = checked) } }
                    )
                    if (sourcePackageId != null) {
                        val previewSuffix = config.cloneSuffix.ifBlank { "pkf" }
                        Text(
                            text = stringResource(R.string.install_target_pkg, "$sourcePackageId.$previewSuffix"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (hasSplits) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                GuardSwitchRow(
                    label = stringResource(R.string.opt_universal_merge),
                    checked = config.mergeSplitsIntoUniversal,
                    tag = "switch_universal_merge",
                    onCheckedChange = { checked -> onUpdateConfig { it.copy(mergeSplitsIntoUniversal = checked) } }
                )
                Text(
                    text = stringResource(R.string.opt_universal_merge_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (detectedAbis.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeveloperBoard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.abi_optimizer_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Text(
                        text = stringResource(R.string.abi_device_badge, devicePrimaryAbi),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = config.allowedAbis.isEmpty(),
                        onClick = onKeepAllAbis,
                        label = { Text(stringResource(R.string.abi_keep_all)) },
                        modifier = Modifier.testTag("abi_keep_all_chip")
                    )
                    if (devicePrimaryAbi in detectedAbis && detectedAbis.size > 1) {
                        FilterChip(
                            selected = config.allowedAbis == setOf(devicePrimaryAbi),
                            onClick = onDeviceAbiOnly,
                            label = { Text(stringResource(R.string.abi_device_only)) },
                            modifier = Modifier.testTag("abi_device_only_chip")
                        )
                    }
                    detectedAbis.forEach { abi ->
                        val isKept = config.allowedAbis.isEmpty() || abi in config.allowedAbis
                        FilterChip(
                            selected = isKept,
                            onClick = { onToggleAbi(abi, detectedAbis) },
                            label = { Text(abi) }
                        )
                    }
                }
            }

            if (detectedLocales.size > 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                GuardSwitchRow(
                    label = stringResource(R.string.opt_locale_prune),
                    checked = config.pruneUnusedLocales,
                    tag = "switch_locale_prune",
                    onCheckedChange = { checked -> onUpdateConfig { it.copy(pruneUnusedLocales = checked) } }
                )
                Text(
                    text = stringResource(R.string.opt_locale_prune_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AnimatedVisibility(visible = config.pruneUnusedLocales) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.locale_keep_chips_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            detectedLocales.take(18).forEach { loc ->
                                FilterChip(
                                    selected = loc in config.keptLocales,
                                    onClick = { onToggleLocale(loc) },
                                    label = { Text(loc.uppercase(Locale.US)) }
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            GuardSwitchRow(
                label = stringResource(R.string.opt_image_crunch),
                checked = config.optimizeLargeImages,
                tag = "switch_image_crunch",
                onCheckedChange = { checked -> onUpdateConfig { it.copy(optimizeLargeImages = checked) } }
            )
            GuardSwitchRow(
                label = stringResource(R.string.opt_strip_shared_uid) + if (hasSharedUserId) " (!)" else "",
                checked = config.stripSharedUserId,
                tag = "switch_strip_shared_uid",
                onCheckedChange = { checked -> onUpdateConfig { it.copy(stripSharedUserId = checked) } }
            )
            if (hasStaticLibraries) {
                GuardSwitchRow(
                    label = stringResource(R.string.opt_patch_static_lib),
                    checked = config.patchStaticLibCertDigest,
                    tag = "switch_patch_cert_digest",
                    onCheckedChange = { checked -> onUpdateConfig { it.copy(patchStaticLibCertDigest = checked) } }
                )
                GuardSwitchRow(
                    label = stringResource(R.string.opt_bundle_companion_libs),
                    checked = config.bundleCompanionStaticLibs,
                    tag = "switch_bundle_companion_libs",
                    onCheckedChange = { checked -> onUpdateConfig { it.copy(bundleCompanionStaticLibs = checked) } }
                )
            }
            if (shizukuStatus is ShizukuStatus.Ready) {
                GuardSwitchRow(
                    label = stringResource(R.string.opt_prefer_shizuku),
                    checked = config.preferShizukuInstaller,
                    tag = "switch_prefer_shizuku",
                    onCheckedChange = { checked -> onUpdateConfig { it.copy(preferShizukuInstaller = checked) } }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            GuardSwitchRow(
                label = stringResource(R.string.guard_arsc_align),
                checked = config.enforceResourcesArscAlign,
                tag = "switch_arsc_align",
                onCheckedChange = { checked -> onUpdateConfig { it.copy(enforceResourcesArscAlign = checked) } }
            )
            GuardSwitchRow(
                label = stringResource(R.string.guard_entropy_bypass),
                checked = config.smartEntropyBypass,
                tag = "switch_entropy_bypass",
                onCheckedChange = { checked -> onUpdateConfig { it.copy(smartEntropyBypass = checked) } }
            )
            GuardSwitchRow(
                label = stringResource(R.string.guard_prune_debug),
                checked = config.pruneDebugArtifacts,
                tag = "switch_prune_debug",
                onCheckedChange = { checked -> onUpdateConfig { it.copy(pruneDebugArtifacts = checked) } }
            )
            GuardSwitchRow(
                label = stringResource(R.string.guard_ram_throttle),
                checked = config.adaptiveRamThrottle,
                tag = "switch_ram_throttle",
                onCheckedChange = { checked -> onUpdateConfig { it.copy(adaptiveRamThrottle = checked) } }
            )

            ShizukuEngineBanner(
                status = shizukuStatus,
                onGrantClick = onGrantShizuku,
                onOpenShizukuClick = onOpenShizuku,
                onRefreshClick = onRefreshShizuku
            )

            Button(
                onClick = onCompressClick,
                enabled = canCompress && !isCompressing,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .testTag("compress_now_button"),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Compress,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCompressing) {
                        stringResource(R.string.action_compressing)
                    } else {
                        stringResource(R.string.action_compress_now)
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun ProfileOptionRow(
    selected: Boolean,
    title: String,
    desc: String,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag(tag),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun GuardSwitchRow(
    label: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstalledAppsModalSheet(
    apps: List<InstalledAppItem>,
    isLoading: Boolean,
    onSelectApp: (InstalledAppItem) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter {
            it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    val activeLang = LocalAppLanguageCode.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ProvideLocalizedContext(languageCode = activeLang) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.picker_installed_title),
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                colors = forgeFieldColors(),
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.picker_search_apps)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = stringResource(R.string.picker_loading))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(filtered, key = { it.packageName }) { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { onSelectApp(item) },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                DrawableAppIcon(
                                    drawable = item.icon,
                                    contentDescription = item.appName,
                                    size = 40.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.appName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.isSplitBundle || item.isSystemApp) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (item.isSplitBundle) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.split_bundle_badge, item.splitApkPaths.size + 1),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.tertiary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            if (item.isSystemApp) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.system_app_badge),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Text(
                                    text = formatBytes(item.apkSizeBytes),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallOptionsModalSheet(
    sheetState: InstallOptionsSheetState,
    defaultSuffix: String,
    isRepackingClone: Boolean,
    onChooseUpdateExisting: () -> Unit,
    onChooseUninstallAndInstall: () -> Unit,
    onChooseInstallClone: (String) -> Unit,
    onChooseInstallViaShizuku: () -> Unit,
    onChooseInstallCompanionLibs: () -> Unit,
    onChooseUninstallViaShizuku: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var cloneSuffix by remember(defaultSuffix) { mutableStateOf(defaultSuffix) }
    val modalState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val activeLang = LocalAppLanguageCode.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalState
    ) {
        ProvideLocalizedContext(languageCode = activeLang) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.install_sheet_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = if (sheetState.isSignatureConflict || sheetState.failureReason != null) {
                    stringResource(R.string.install_sheet_conflict_subtitle)
                } else {
                    stringResource(R.string.install_sheet_ready_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (sheetState.manifestRuntimeRisks.isNotEmpty()) {
                RuntimeRiskBanner(risks = sheetState.manifestRuntimeRisks)
            }

            if (sheetState.hasCompanionLibs) {
                Button(
                    onClick = onChooseInstallCompanionLibs,
                    enabled = !isRepackingClone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("install_companion_libs_button"),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(text = stringResource(R.string.install_companion_libs_btn))
                }
            }

            if (sheetState.shizukuReady) {
                Button(
                    onClick = onChooseInstallViaShizuku,
                    enabled = !isRepackingClone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("install_via_shizuku_button"),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TelemetrySuccess,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(text = stringResource(R.string.install_via_shizuku))
                }

                val targetForUninstall = sheetState.currentTargetPackageId ?: sheetState.originalPackageId
                if ((sheetState.isOriginalInstalled || sheetState.isTargetInstalled) && targetForUninstall != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onChooseUninstallViaShizuku(targetForUninstall, true) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("shizuku_uninstall_keep_data_button")
                        ) {
                            Text(
                                text = stringResource(R.string.shizuku_uninstall_keep_data_btn),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = { onChooseUninstallViaShizuku(targetForUninstall, false) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("shizuku_uninstall_system_button")
                        ) {
                            Text(
                                text = stringResource(R.string.shizuku_uninstall_system_btn),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Option 1: Update Existing Package Directly
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(enabled = !isRepackingClone, onClick = onChooseUpdateExisting)
                    .testTag("install_opt_update_card"),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.InstallMobile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.install_opt_update_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Text(
                        text = stringResource(R.string.install_opt_update_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sheetState.isSignatureConflict) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = TelemetryWarning.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = stringResource(R.string.install_opt_update_sig_warn),
                                style = MaterialTheme.typography.labelSmall,
                                color = TelemetryWarning,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Option 2: Uninstall Existing & Install Compressed
            val canUninstall = sheetState.isTargetInstalled || sheetState.isOriginalInstalled
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(enabled = canUninstall && !isRepackingClone, onClick = onChooseUninstallAndInstall)
                    .testTag("install_opt_uninstall_card"),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (canUninstall) 0.55f else 0.25f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(R.string.install_opt_uninstall_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Text(
                        text = stringResource(R.string.install_opt_uninstall_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sheetState.isSystemApp) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = TelemetryError.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = stringResource(R.string.install_opt_system_warn),
                                style = MaterialTheme.typography.labelSmall,
                                color = TelemetryError,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Option 3: Install Side-by-Side Clone Copy (Recommended on Signature Mismatch)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.install_opt_clone_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = stringResource(R.string.install_opt_clone_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        colors = forgeFieldColors(),
                        value = cloneSuffix,
                        onValueChange = { input ->
                            cloneSuffix = input.lowercase().filter { it.isLetterOrDigit() || it == '_' }.take(12)
                        },
                        label = { Text(stringResource(R.string.install_clone_suffix)) },
                        singleLine = true,
                        enabled = !isRepackingClone,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sheet_clone_suffix_input")
                    )

                    sheetState.originalPackageId?.let { orig ->
                        val preview = "$orig.${cloneSuffix.ifBlank { "pkf" }}"
                        Text(
                            text = stringResource(R.string.install_target_pkg, preview),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Button(
                        onClick = { onChooseInstallClone(cloneSuffix) },
                        enabled = !isRepackingClone,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("install_opt_clone_button"),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (isRepackingClone) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.install_opt_repacking))
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.install_opt_clone_action))
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun RuntimeRiskBanner(risks: List<String>) {
    val summaryLabel = remember(risks) {
        risks.joinToString(", ") { raw ->
            raw.substringAfter(':', raw)
        }
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = TelemetryWarning.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, TelemetryWarning.copy(alpha = 0.45f)),
        modifier = Modifier.testTag("runtime_risk_warning_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = TelemetryWarning,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.runtime_risk_static_lib_warn, summaryLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
