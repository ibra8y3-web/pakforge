package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.engine.EntryAnalysis
import com.example.core.engine.EntryStrategy
import com.example.core.engine.PackageCategory
import com.example.core.engine.PackageInspectionResult
import com.example.core.engine.formatBytes
import com.example.ui.components.forgeFieldColors
import com.example.ui.components.EntropyGauge
import com.example.ui.components.ForgeEmptyIconBadge
import com.example.ui.components.categoryColor
import com.example.ui.components.categoryLabel
import com.example.ui.theme.TelemetryError
import com.example.ui.theme.TelemetrySuccess
import com.example.ui.theme.TelemetryWarning

@Composable
fun InspectorScreen(
    inspection: PackageInspectionResult?,
    searchQuery: String,
    selectedCategory: PackageCategory?,
    onSearchChange: (String) -> Unit,
    onCategoryChange: (PackageCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (inspection == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.widthIn(max = 520.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ForgeEmptyIconBadge(icon = Icons.Rounded.Code)
                    Text(
                        text = stringResource(R.string.inspector_no_package),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
        return
    }

    val filteredEntries = remember(inspection.entries, searchQuery, selectedCategory) {
        inspection.entries.filter { entry ->
            val matchesCat = selectedCategory == null || entry.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() || entry.path.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }.take(250)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 880.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                colors = forgeFieldColors(),
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text(stringResource(R.string.inspector_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inspector_search_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onCategoryChange(null) },
                    label = { Text(stringResource(R.string.inspector_filter_all)) }
                )
                listOf(
                    PackageCategory.DEX_BYTECODE,
                    PackageCategory.NATIVE_LIBS,
                    PackageCategory.RESOURCES_ARSC,
                    PackageCategory.MANIFEST_XML,
                    PackageCategory.MEDIA_ASSETS,
                    PackageCategory.METADATA_SIGNING
                ).forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { onCategoryChange(if (selectedCategory == cat) null else cat) },
                        label = { Text(categoryLabel(cat)) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                item(key = "security_audit_header") {
                    SecurityAndBloatAuditCard(inspection = inspection)
                }
                items(filteredEntries, key = { it.path }) { entry ->
                    EntryInspectorCard(entry = entry)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SecurityAndBloatAuditCard(inspection: PackageInspectionResult) {
    val audit = inspection.securityAudit
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.testTag("security_audit_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.security_audit_title),
                    style = MaterialTheme.typography.titleSmall
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val dupCount = inspection.totalDuplicateFilesCount
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (dupCount > 0) TelemetryWarning.copy(alpha = 0.15f) else TelemetrySuccess.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            tint = if (dupCount > 0) TelemetryWarning else TelemetrySuccess,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (dupCount > 0) {
                                stringResource(
                                    R.string.audit_duplicates_found,
                                    dupCount,
                                    formatBytes(inspection.totalDuplicateWastedBytes)
                                )
                            } else {
                                stringResource(R.string.audit_no_duplicates)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (dupCount > 0) TelemetryWarning else TelemetrySuccess
                        )
                    }
                }

                if (audit != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (audit.isDebuggable) TelemetryError.copy(alpha = 0.15f) else TelemetrySuccess.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (audit.isDebuggable) Icons.Rounded.GppMaybe else Icons.Rounded.GppGood,
                                contentDescription = null,
                                tint = if (audit.isDebuggable) TelemetryError else TelemetrySuccess,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (audit.isDebuggable) {
                                    stringResource(R.string.audit_debuggable_warn)
                                } else {
                                    stringResource(R.string.audit_release_ok)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (audit.isDebuggable) TelemetryError else TelemetrySuccess
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = stringResource(
                                R.string.audit_permissions_total,
                                audit.requestedPermissions.size,
                                audit.dangerousPermissions.size
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (audit.dangerousPermissions.isNotEmpty()) TelemetryWarning else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    if (audit.allowsBackup) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = stringResource(R.string.audit_backup_flag),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            if (audit?.signingCertSha256 != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.audit_cert_sha256),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = audit.signingCertSha256,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryInspectorCard(entry: EntryAnalysis) {
    val strategyText = when (entry.strategy) {
        EntryStrategy.DEFLATE_MAX -> stringResource(R.string.action_strategy_deflate9)
        EntryStrategy.DEFLATE_BALANCED -> stringResource(R.string.action_strategy_deflate6)
        EntryStrategy.PRESERVE_STORED_4ALIGN -> stringResource(R.string.action_strategy_stored_align)
        EntryStrategy.PRESERVE_STORED_4KB -> stringResource(R.string.action_strategy_stored_page)
        EntryStrategy.BYPASS_HIGH_ENTROPY -> stringResource(R.string.action_strategy_bypass)
        EntryStrategy.PRUNE_SAFE_DEBUG -> stringResource(R.string.action_strategy_prune)
        EntryStrategy.STRIP_UNUSED_ABI -> stringResource(R.string.result_abi_stripped)
    }

    val strategyColor = when (entry.strategy) {
        EntryStrategy.DEFLATE_MAX, EntryStrategy.DEFLATE_BALANCED -> MaterialTheme.colorScheme.primary
        EntryStrategy.PRESERVE_STORED_4ALIGN, EntryStrategy.PRESERVE_STORED_4KB -> TelemetryWarning
        EntryStrategy.BYPASS_HIGH_ENTROPY -> TelemetrySuccess
        EntryStrategy.PRUNE_SAFE_DEBUG, EntryStrategy.STRIP_UNUSED_ABI -> TelemetryError
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.path,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = strategyColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = strategyText,
                        style = MaterialTheme.typography.labelSmall,
                        color = strategyColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${categoryLabel(entry.category)} • ${formatBytes(entry.rawBytes)} → ${formatBytes(entry.predictedOutputBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = categoryColor(entry.category)
                )
            }

            EntropyGauge(entropy = entry.shannonEntropy)
        }
    }
}
