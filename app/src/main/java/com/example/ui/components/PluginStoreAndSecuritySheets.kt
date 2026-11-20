package com.example.ui.components

import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Category
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.core.sdk.DeveloperKeyInfo
import com.example.core.sdk.PakForgeSdkSpec
import com.example.core.sdk.PluginActionStep
import com.example.core.sdk.PluginCategory
import com.example.core.sdk.PluginCompressionRule
import com.example.core.sdk.PluginHookType
import com.example.core.sdk.PluginLocalization
import com.example.core.sdk.PluginManifest
import com.example.core.sdk.PluginManifestCodec
import com.example.core.sdk.PluginPermission
import com.example.core.sdk.PluginSecurityScanResult
import com.example.core.sdk.SecurityRiskTier
import com.example.core.sdk.SignatureTrustLevel
import com.example.core.sdk.SmartMatchCriteria
import com.example.core.sdk.SmartPluginRecommendation
import com.example.core.sdk.StorePluginListing
import com.example.ui.theme.TelemetryError
import com.example.ui.theme.TelemetrySuccess
import com.example.ui.theme.TelemetryWarning

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PluginStoreTabContent(
    storeCatalog: List<StorePluginListing>,
    installedPluginIds: Set<String>,
    smartRecommendations: List<SmartPluginRecommendation>,
    onInspectStoreListing: (StorePluginListing) -> Unit,
    onRunInstalledPlugin: (String) -> Unit,
    onImportBundleClick: () -> Unit,
    onOpenBuilderClick: () -> Unit,
    onRefreshSmartContext: () -> Unit
) {
    val activeLang = LocalAppLanguageCode.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<PluginCategory?>(null) }

    val filteredCatalog = remember(storeCatalog, searchQuery, selectedCategory) {
        storeCatalog.filter { listing ->
            val catOk = selectedCategory == null || listing.manifest.category == selectedCategory
            val q = searchQuery.trim().lowercase()
            val queryOk = q.isEmpty() ||
                listing.manifest.name.lowercase().contains(q) ||
                listing.manifest.description.lowercase().contains(q) ||
                listing.manifest.id.lowercase().contains(q)
            catOk && queryOk
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onImportBundleClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .testTag("store_import_bundle_btn"),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.store_import_bundle_btn))
                }

                FilledTonalButton(
                    onClick = onOpenBuilderClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .testTag("store_open_builder_btn"),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.store_create_builder_btn))
                }
            }
        }

        // Smart Context-Aware Recognition Card
        item {
            ForgeSectionCard(
                title = stringResource(R.string.store_smart_match_title),
                icon = Icons.Rounded.AutoAwesome,
                accent = MaterialTheme.colorScheme.tertiary,
                highlighted = true,
                modifier = Modifier.testTag("store_smart_recognition_card"),
                trailing = {
                    IconButton(
                        onClick = onRefreshSmartContext,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("store_refresh_smart_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.store_smart_refresh_btn),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            ) {
                    if (smartRecommendations.isEmpty()) {
                        Text(
                            text = stringResource(R.string.store_smart_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        smartRecommendations.take(3).forEach { rec ->
                            val isInstalled = rec.manifest.id in installedPluginIds
                            val storeListing = storeCatalog.firstOrNull { it.manifest.id == rec.manifest.id }
                            val recCopy = remember(rec.manifest, activeLang) {
                                PluginLocalization.manifestCopy(rec.manifest, activeLang)
                            }
                            val localizedReasons = remember(rec.matchReasons, activeLang) {
                                rec.matchReasons.joinToString(" • ") { PluginLocalization.localizeSmartReason(it, activeLang) }
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = recCopy.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = TelemetrySuccess.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = "${rec.matchScore}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = TelemetrySuccess,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${rec.contextSummary} • $localizedReasons",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isInstalled) {
                                            FilledTonalButton(
                                                onClick = { onRunInstalledPlugin(rec.manifest.id) },
                                                modifier = Modifier
                                                    .heightIn(min = 40.dp)
                                                    .testTag("smart_run_${rec.manifest.id}")
                                            ) {
                                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(stringResource(R.string.dev_run_sandbox_btn))
                                            }
                                        } else if (storeListing != null) {
                                            Button(
                                                onClick = { onInspectStoreListing(storeListing) },
                                                modifier = Modifier
                                                    .heightIn(min = 40.dp)
                                                    .testTag("smart_install_${rec.manifest.id}")
                                            ) {
                                                Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(stringResource(R.string.store_verify_install_btn))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
            }
        }

        // Search + Category Filters
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    colors = forgeFieldColors(),
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.store_search_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("store_search_input"),
                    shape = MaterialTheme.shapes.medium
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text(stringResource(R.string.store_cat_all)) },
                        modifier = Modifier.testTag("store_cat_all")
                    )
                    PluginCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                            label = { Text(PluginLocalization.categoryTitle(cat, activeLang)) },
                            modifier = Modifier.testTag("store_cat_${cat.name}")
                        )
                    }
                }
            }
        }

        // Signed Store Catalog Listings
        items(filteredCatalog, key = { it.manifest.id }) { listing ->
            val m = listing.manifest
            val isInstalled = m.id in installedPluginIds
            val scan = listing.scanResult
            val trustLabel = PluginLocalization.trustBadge(scan.signatureTrust, activeLang)
            val copy = remember(m, activeLang) {
                PluginLocalization.manifestCopy(m, activeLang)
            }

            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = copy.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "v${m.version} • ${m.author} • API ${m.apiVersion}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TelemetrySuccess.copy(alpha = 0.14f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VerifiedUser,
                                    contentDescription = trustLabel,
                                    tint = TelemetrySuccess,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "$trustLabel • ${scan.safetyScore}/100",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TelemetrySuccess,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Text(
                        text = copy.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tags = copy.highlights.ifEmpty {
                            if (activeLang == "ar") listing.highlightsAr else listing.highlightsEn
                        }
                        tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isInstalled) {
                            FilledTonalButton(
                                onClick = { onRunInstalledPlugin(m.id) },
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag("store_quick_run_${m.id}")
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.dev_run_sandbox_btn))
                            }
                            OutlinedButton(
                                onClick = { onInspectStoreListing(listing) },
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag("store_install_${m.id}")
                            ) {
                                Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.store_installed_badge))
                            }
                        } else {
                            Button(
                                onClick = { onInspectStoreListing(listing) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 40.dp)
                                    .testTag("store_install_${m.id}"),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.store_verify_install_btn))
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
fun PluginSecurityReviewDialog(
    scanResult: PluginSecurityScanResult,
    onConfirmInstall: (Set<PluginPermission>) -> Unit,
    onDismiss: () -> Unit
) {
    val activeLang = LocalAppLanguageCode.current
    val manifest = scanResult.manifest
    var selectedPermissions by remember(scanResult) {
        mutableStateOf(manifest?.permissions?.toSet() ?: emptySet())
    }

    val trustColor = when (scanResult.signatureTrust) {
        SignatureTrustLevel.VERIFIED_OFFICIAL,
        SignatureTrustLevel.VERIFIED_DEVELOPER,
        SignatureTrustLevel.BUILTIN -> TelemetrySuccess
        SignatureTrustLevel.UNSIGNED -> TelemetryWarning
        SignatureTrustLevel.TAMPERED_REJECTED -> TelemetryError
    }
    val trustLabel = PluginLocalization.trustBadge(scanResult.signatureTrust, activeLang)
    val riskLabel = PluginLocalization.riskTierLabel(scanResult.riskTier, activeLang)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        ProvideLocalizedContext(languageCode = activeLang) {
            BackHandler(onBack = onDismiss)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 620.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = stringResource(R.string.security_review_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("security_review_close_btn")
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
                            }
                        }

                        // Trust & Risk Banner
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = trustColor.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, trustColor.copy(alpha = 0.6f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = when (scanResult.riskTier) {
                                            SecurityRiskTier.BLOCKED -> Icons.Rounded.Cancel
                                            SecurityRiskTier.LOW -> Icons.Rounded.VerifiedUser
                                            else -> Icons.Rounded.WarningAmber
                                        },
                                        contentDescription = null,
                                        tint = trustColor
                                    )
                                    Text(
                                        text = "$trustLabel • " + stringResource(
                                            R.string.security_review_score,
                                            scanResult.safetyScore,
                                            riskLabel
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = trustColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                scanResult.signerFingerprintSha256?.let { fp ->
                                    Text(
                                        text = stringResource(R.string.security_ecdsa_key_label, fp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                scanResult.payloadSha256?.let { sha ->
                                    Text(
                                        text = stringResource(R.string.security_payload_sha_label, sha.take(28)),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (manifest != null) {
                            val localizedManifest = remember(manifest, activeLang) {
                                PluginLocalization.manifestCopy(manifest, activeLang)
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${localizedManifest.name} v${manifest.version}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${manifest.id} • ${manifest.author} • " + stringResource(R.string.security_steps_count, manifest.actions.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = localizedManifest.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Findings Checklist
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            scanResult.findings.forEach { f ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (f.passed) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                                        contentDescription = null,
                                        tint = if (f.passed) TelemetrySuccess else TelemetryError,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = PluginLocalization.findingTitle(f, activeLang),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = f.detail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (scanResult.canInstall && manifest != null && manifest.permissions.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                text = stringResource(R.string.security_review_permissions),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            manifest.permissions.forEach { perm ->
                                val isGranted = perm in selectedPermissions
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
                                            text = PluginLocalization.permissionRiskSummary(perm, activeLang),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = isGranted,
                                        onCheckedChange = { checked ->
                                            selectedPermissions = if (checked) {
                                                selectedPermissions + perm
                                            } else {
                                                selectedPermissions - perm
                                            }
                                        },
                                        modifier = Modifier.testTag("security_perm_switch_${perm.name}")
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 40.dp)
                                    .testTag("security_cancel_btn")
                            ) {
                                Text(stringResource(R.string.action_close))
                            }
                            Button(
                                onClick = { onConfirmInstall(selectedPermissions) },
                                enabled = scanResult.canInstall,
                                modifier = Modifier
                                    .weight(1.6f)
                                    .heightIn(min = 40.dp)
                                    .testTag("security_confirm_install_btn"),
                                colors = if (scanResult.canInstall) {
                                    ButtonDefaults.buttonColors()
                                } else {
                                    ButtonDefaults.buttonColors(containerColor = TelemetryError)
                                }
                            ) {
                                Text(
                                    text = stringResource(
                                        if (scanResult.canInstall) {
                                            R.string.security_review_confirm_btn
                                        } else {
                                            R.string.security_review_blocked_btn
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeveloperPluginBuilderDialog(
    developerKeyInfo: DeveloperKeyInfo,
    onSignAndInstall: (PluginManifest) -> Unit,
    onSignAndExport: (PluginManifest) -> Unit,
    onDismiss: () -> Unit
) {
    val activeLang = LocalAppLanguageCode.current
    val defaultToolName = stringResource(R.string.builder_default_name)
    val defaultAuthor = stringResource(R.string.builder_default_author)
    val defaultDesc = stringResource(R.string.builder_default_desc)
    var pluginId by remember { mutableStateOf("dev.community.smart_slimmer") }
    var pluginName by remember(defaultToolName) { mutableStateOf(defaultToolName) }
    var version by remember { mutableStateOf("1.0.0") }
    var author by remember(defaultAuthor) { mutableStateOf(defaultAuthor) }
    var description by remember(defaultDesc) { mutableStateOf(defaultDesc) }
    var category by remember { mutableStateOf(PluginCategory.DEVELOPER_TOOL) }

    var selectedSteps by remember {
        mutableStateOf(
            setOf(
                PluginActionStep.CHECK_DEVICE_HEADROOM,
                PluginActionStep.INSPECT_ACTIVE_PACKAGE,
                PluginActionStep.APPLY_COMPRESSION_RULE,
                PluginActionStep.DISPATCH_NOTIFICATION
            )
        )
    }
    var selectedPermissions by remember {
        mutableStateOf(
            setOf(
                PluginPermission.READ_DEVICE_TELEMETRY,
                PluginPermission.READ_DIAGNOSTICS,
                PluginPermission.ACCESS_HOST_TOOLS,
                PluginPermission.SEND_NOTIFICATIONS
            )
        )
    }
    var ruleProfile by remember { mutableStateOf<String?>("ZERO_RISK_BALANCED") }
    var pruneLocales by remember { mutableStateOf(true) }
    var stripSharedUid by remember { mutableStateOf(false) }
    var mergeSplits by remember { mutableStateOf(true) }
    var requireSplits by remember { mutableStateOf(false) }
    var requireSharedUid by remember { mutableStateOf(false) }
    var requireLowRam by remember { mutableStateOf(false) }

    val draftManifest = remember(
        pluginId, pluginName, version, author, description, category,
        selectedSteps, selectedPermissions, ruleProfile, pruneLocales,
        stripSharedUid, mergeSplits, requireSplits, requireSharedUid, requireLowRam
    ) {
        val rule = if (ruleProfile != null || pruneLocales || stripSharedUid || mergeSplits) {
            PluginCompressionRule(
                profile = ruleProfile,
                pruneUnusedLocales = pruneLocales,
                keptLocales = if (pruneLocales) setOf("en", "ar") else null,
                mergeSplitsIntoUniversal = mergeSplits,
                stripSharedUserId = stripSharedUid
            )
        } else null

        PluginManifest(
            id = pluginId.trim(),
            name = pluginName.trim(),
            version = version.trim(),
            apiVersion = PakForgeSdkSpec.SDK_VERSION,
            minHostApiLevel = PakForgeSdkSpec.MIN_SUPPORTED_API_LEVEL,
            author = author.trim().ifBlank { "Community Dev" },
            description = description.trim(),
            entryPoint = "${pluginId.trim()}.GeneratedToolPlugin",
            permissions = selectedPermissions.toList(),
            hooks = listOf(PluginHookType.MANUAL_RUN, PluginHookType.ON_PACKAGE_INSPECTED),
            compressionRule = rule,
            category = category,
            actions = selectedSteps.toList(),
            smartMatch = SmartMatchCriteria(
                requireSplits = requireSplits,
                requireSharedUserId = requireSharedUid,
                requireLowRamOrPowerSave = requireLowRam
            )
        )
    }

    val validation = remember(draftManifest) { PluginManifestCodec.validate(draftManifest) }

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
                    title = { Text(stringResource(R.string.builder_title), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("builder_close_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_close))
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
                        .widthIn(max = 760.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ECDSA Key Identity Card
                    ForgeSectionCard(
                        title = developerKeyInfo.algorithm,
                        subtitle = stringResource(R.string.builder_signer_sha_label, developerKeyInfo.fingerprintSha256),
                        icon = Icons.Rounded.Key,
                        accent = MaterialTheme.colorScheme.primary,
                        highlighted = true
                    ) {}

                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                    OutlinedTextField(
                        colors = forgeFieldColors(),
                        value = pluginId,
                        onValueChange = { pluginId = it },
                        label = { Text(stringResource(R.string.builder_id_label)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("builder_id_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            colors = forgeFieldColors(),
                            value = pluginName,
                            onValueChange = { pluginName = it },
                            label = { Text(stringResource(R.string.builder_name_label)) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.7f)
                                .testTag("builder_name_input")
                        )
                        OutlinedTextField(
                            colors = forgeFieldColors(),
                            value = version,
                            onValueChange = { version = it },
                            label = { Text(stringResource(R.string.builder_version_label)) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("builder_version_input")
                        )
                    }

                    OutlinedTextField(
                        colors = forgeFieldColors(),
                        value = author,
                        onValueChange = { author = it },
                        label = { Text(stringResource(R.string.builder_author_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        colors = forgeFieldColors(),
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.builder_desc_label)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                        }
                    }

                    ForgeSectionCard(
                        title = stringResource(R.string.builder_category_label),
                        icon = Icons.Rounded.Category,
                        accent = MaterialTheme.colorScheme.primary
                    ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PluginCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(PluginLocalization.categoryTitle(cat, activeLang)) }
                            )
                        }
                    }
                    }

                    ForgeSectionCard(
                        title = stringResource(R.string.builder_steps_label),
                        icon = Icons.Rounded.Build,
                        accent = MaterialTheme.colorScheme.secondary
                    ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PluginActionStep.entries.forEach { step ->
                            FilterChip(
                                selected = step in selectedSteps,
                                onClick = {
                                    selectedSteps = if (step in selectedSteps) {
                                        if (selectedSteps.size <= 1) selectedSteps else selectedSteps - step
                                    } else {
                                        selectedSteps + step
                                    }
                                },
                                label = { Text(PluginLocalization.actionStepTitle(step, activeLang)) }
                            )
                        }
                    }
                    }

                    ForgeSectionCard(
                        title = stringResource(R.string.builder_perms_label),
                        icon = Icons.Rounded.Security,
                        accent = MaterialTheme.colorScheme.tertiary
                    ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PluginPermission.entries.forEach { perm ->
                            FilterChip(
                                selected = perm in selectedPermissions,
                                onClick = {
                                    selectedPermissions = if (perm in selectedPermissions) {
                                        selectedPermissions - perm
                                    } else {
                                        selectedPermissions + perm
                                    }
                                },
                                label = { Text(PluginLocalization.permissionTitle(perm, activeLang)) }
                            )
                        }
                    }
                    }

                    ForgeSectionCard(
                        title = stringResource(R.string.builder_rules_label),
                        icon = Icons.Rounded.Tune,
                        accent = MaterialTheme.colorScheme.primary
                    ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = requireSplits,
                            onClick = { requireSplits = !requireSplits },
                            label = { Text(stringResource(R.string.builder_rule_smart_splits)) }
                        )
                        FilterChip(
                            selected = requireSharedUid,
                            onClick = { requireSharedUid = !requireSharedUid },
                            label = { Text(stringResource(R.string.builder_rule_smart_uid)) }
                        )
                        FilterChip(
                            selected = requireLowRam,
                            onClick = { requireLowRam = !requireLowRam },
                            label = { Text(stringResource(R.string.builder_rule_smart_ram)) }
                        )
                        FilterChip(
                            selected = pruneLocales,
                            onClick = { pruneLocales = !pruneLocales },
                            label = { Text(stringResource(R.string.builder_rule_prune_locales)) }
                        )
                        FilterChip(
                            selected = mergeSplits,
                            onClick = { mergeSplits = !mergeSplits },
                            label = { Text(stringResource(R.string.builder_rule_merge_splits)) }
                        )
                        FilterChip(
                            selected = stripSharedUid,
                            onClick = { stripSharedUid = !stripSharedUid },
                            label = { Text(stringResource(R.string.builder_rule_strip_uid)) }
                        )
                    }
                    }

                    if (!validation.isValid) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TelemetryError.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = validation.errors.joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall,
                                color = TelemetryError,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSignAndExport(draftManifest) },
                            enabled = validation.isValid,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                                .testTag("builder_sign_export_btn"),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.builder_sign_export_btn))
                        }

                        Button(
                            onClick = { onSignAndInstall(draftManifest) },
                            enabled = validation.isValid,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                                .testTag("builder_sign_install_btn"),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.builder_sign_install_btn))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
        }
    }
}
