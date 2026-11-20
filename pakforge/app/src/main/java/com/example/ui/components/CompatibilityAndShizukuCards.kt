package com.example.ui.components

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.engine.CompatibilityTier
import com.example.core.engine.PackageCompatibilityReport
import com.example.core.sdk.PluginLocalization
import com.example.core.shizuku.ShizukuStatus
import com.example.ui.theme.TelemetryError
import com.example.ui.theme.TelemetrySuccess
import com.example.ui.theme.TelemetryWarning

@Composable
fun CompatibilityReportCard(
    report: PackageCompatibilityReport,
    onAutoFixClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (tierColor, tierLabelRes, tierIcon) = when (report.tier) {
        CompatibilityTier.VERIFIED_READY -> Triple(
            TelemetrySuccess,
            R.string.compat_tier_ready,
            Icons.Rounded.CheckCircle
        )
        CompatibilityTier.AUTO_FIXABLE -> Triple(
            TelemetryWarning,
            R.string.compat_tier_fixable,
            Icons.Rounded.AutoFixHigh
        )
        CompatibilityTier.PROTECTED_SYSTEM -> Triple(
            TelemetryError,
            R.string.compat_tier_protected,
            Icons.Rounded.Shield
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("compatibility_report_card"),
        shape = RoundedCornerShape(14.dp),
        color = tierColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, tierColor.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = tierIcon,
                        contentDescription = null,
                        tint = tierColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.compat_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = tierColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "${stringResource(tierLabelRes)} • ${stringResource(R.string.compat_score, report.readinessScore)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tierColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                report.checks.forEach { check ->
                    val itemColor = when (check.severity) {
                        CompatibilityTier.VERIFIED_READY -> TelemetrySuccess
                        CompatibilityTier.AUTO_FIXABLE -> TelemetryWarning
                        CompatibilityTier.PROTECTED_SYSTEM -> TelemetryError
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = when (check.severity) {
                                CompatibilityTier.VERIFIED_READY -> Icons.Rounded.CheckCircle
                                else -> Icons.Rounded.WarningAmber
                            },
                            contentDescription = null,
                            tint = itemColor,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(14.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = check.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = check.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (report.canAutoFix) {
                FilledTonalButton(
                    onClick = onAutoFixClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("compat_autofix_button"),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.compat_autofix_action))
                }
            }
        }
    }
}

@Composable
fun ShizukuEngineBanner(
    status: ShizukuStatus,
    onGrantClick: () -> Unit,
    onOpenShizukuClick: () -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (accent, statusText) = when (status) {
        is ShizukuStatus.Ready -> {
            TelemetrySuccess to if (status.backendKind != com.example.core.privileged.PrivilegedBackendKind.SHIZUKU) {
                "${status.backendDetail} • UID ${status.uid}"
            } else if (status.isRoot) {
                stringResource(R.string.shizuku_ready_root)
            } else {
                stringResource(R.string.shizuku_ready_shell, status.uid)
            }
        }
        ShizukuStatus.PermissionRequired -> {
            TelemetryWarning to stringResource(R.string.shizuku_perm_required)
        }
        ShizukuStatus.ServiceNotRunning -> {
            TelemetryWarning to stringResource(R.string.shizuku_not_running)
        }
        ShizukuStatus.NotInstalled -> {
            MaterialTheme.colorScheme.onSurfaceVariant to stringResource(R.string.shizuku_not_installed)
        }
    }

    ForgeSectionCard(
        title = stringResource(R.string.shizuku_title),
        icon = if (status is ShizukuStatus.Ready) Icons.Rounded.Bolt else Icons.Rounded.Terminal,
        accent = accent,
        highlighted = status is ShizukuStatus.Ready,
        modifier = modifier.testTag("shizuku_engine_banner"),
        trailing = {
            IconButton(
                onClick = onRefreshClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("shizuku_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.shizuku_refresh_btn),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) {
        ForgePill(text = statusText, accent = accent)

        when (status) {
            ShizukuStatus.PermissionRequired -> {
                Button(
                    onClick = onGrantClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("shizuku_grant_button"),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(text = stringResource(R.string.shizuku_grant_btn), fontWeight = FontWeight.Bold)
                }
            }
            ShizukuStatus.ServiceNotRunning,
            ShizukuStatus.NotInstalled -> {
                OutlinedButton(
                    onClick = onOpenShizukuClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("shizuku_open_app_button"),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(text = stringResource(R.string.shizuku_open_app_btn))
                }
            }
            is ShizukuStatus.Ready -> Unit
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PrivilegedBridgeMultiCard(
    status: ShizukuStatus,
    snapshot: com.example.core.privileged.PrivilegedBridgeSnapshot,
    onSelectBackend: (com.example.core.privileged.PrivilegedBackendKind) -> Unit,
    onGrantClick: () -> Unit,
    onOpenAppClick: () -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeLang = LocalAppLanguageCode.current
    val activeAccent = if (snapshot.isElevatedReady || status is ShizukuStatus.Ready) {
        TelemetrySuccess
    } else if (snapshot.backends.any { it.isInstalled }) {
        TelemetryWarning
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    ForgeSectionCard(
        title = stringResource(R.string.bridge_multi_title),
        subtitle = stringResource(R.string.bridge_multi_desc),
        icon = if (snapshot.isElevatedReady) Icons.Rounded.Bolt else Icons.Rounded.Terminal,
        accent = activeAccent,
        highlighted = snapshot.isElevatedReady,
        modifier = modifier.testTag("privileged_multi_bridge_card"),
        trailing = {
            IconButton(
                onClick = onRefreshClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("multi_bridge_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.shizuku_refresh_btn),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) {
        ForgePill(
            text = stringResource(
                R.string.bridge_active_label,
                PluginLocalization.localizeBackendDetail(snapshot.activeDetail, activeLang)
            ),
            accent = activeAccent
        )

        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val modes = listOf(
                com.example.core.privileged.PrivilegedBackendKind.AUTO to R.string.bridge_mode_auto,
                com.example.core.privileged.PrivilegedBackendKind.ROOT_SU to R.string.bridge_mode_root,
                com.example.core.privileged.PrivilegedBackendKind.SUI to R.string.bridge_mode_sui,
                com.example.core.privileged.PrivilegedBackendKind.SHIZUKU to R.string.bridge_mode_shizuku
            )
            modes.forEach { (kind, labelRes) ->
                androidx.compose.material3.FilterChip(
                    selected = snapshot.preferredMode == kind,
                    onClick = { onSelectBackend(kind) },
                    label = { Text(stringResource(labelRes)) },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.testTag("bridge_mode_${kind.name.lowercase()}")
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            snapshot.backends.forEach { item ->
                val itemAccent = when {
                    item.isReady -> TelemetrySuccess
                    item.isInstalled -> TelemetryWarning
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = itemAccent.copy(alpha = 0.09f),
                    border = BorderStroke(1.dp, itemAccent.copy(alpha = 0.28f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(itemAccent)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = PluginLocalization.localizeBackendTitle(item, activeLang),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = PluginLocalization.localizeBackendDetail(item.detail, activeLang),
                                style = MaterialTheme.typography.labelSmall,
                                color = itemAccent
                            )
                        }
                        if (item.uid != null) {
                            ForgePill(text = "UID ${item.uid}", accent = itemAccent)
                        }
                    }
                }
            }
        }

        if (status is ShizukuStatus.PermissionRequired) {
            Button(
                onClick = onGrantClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("shizuku_grant_button"),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(text = stringResource(R.string.shizuku_grant_btn), fontWeight = FontWeight.Bold)
            }
        } else if (!snapshot.isElevatedReady) {
            OutlinedButton(
                onClick = onOpenAppClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("shizuku_open_app_button"),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(text = stringResource(R.string.shizuku_open_app_btn))
            }
        }
    }
}
