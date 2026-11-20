package com.example.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import com.example.ui.components.forgeBackdrop
import com.example.ui.components.forgeBrandBrush
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.PakForgeApp
import com.example.R
import com.example.core.sdk.PluginSdkZipExporter
import com.example.ui.screens.ForgeScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.InspectorScreen
import com.example.ui.screens.SettingsScreen

const val ROUTE_FORGE = "forge"
const val ROUTE_INSPECTOR = "inspector"
const val ROUTE_HISTORY = "history"
const val ROUTE_SETTINGS = "settings"

private data class NavItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopNavItems = listOf(
    NavItem(ROUTE_FORGE, R.string.nav_forge, Icons.Rounded.Compress, Icons.Outlined.Compress, "nav_forge"),
    NavItem(ROUTE_INSPECTOR, R.string.nav_analyzer, Icons.Rounded.Code, Icons.Outlined.Code, "nav_inspector"),
    NavItem(ROUTE_HISTORY, R.string.nav_history, Icons.Rounded.History, Icons.Outlined.History, "nav_history"),
    NavItem(ROUTE_SETTINGS, R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings, "nav_settings")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PakForgeRoot(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val app = context.applicationContext as PakForgeApp
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_FORGE

    val pipelineState by viewModel.pipelineState.collectAsStateWithLifecycle()
    val installState by viewModel.installState.collectAsStateWithLifecycle()
    val installOptionsSheet by viewModel.installOptionsSheet.collectAsStateWithLifecycle()
    val isRepackingClone by viewModel.isRepackingClone.collectAsStateWithLifecycle()
    val canInstallPackages by viewModel.canInstallPackages.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val config by viewModel.activeConfig.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isLoadingApps by viewModel.isLoadingApps.collectAsStateWithLifecycle()
    val inspectorSearch by viewModel.inspectorSearch.collectAsStateWithLifecycle()
    val inspectorCategory by viewModel.inspectorCategory.collectAsStateWithLifecycle()
    val historyItems by viewModel.historyItems.collectAsStateWithLifecycle()
    val totalSavedBytes by viewModel.totalSavedBytes.collectAsStateWithLifecycle()
    val preferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val shizukuStatus by viewModel.shizukuStatus.collectAsStateWithLifecycle()
    val bridgeSnapshot by viewModel.bridgeSnapshot.collectAsStateWithLifecycle()
    val plugins by viewModel.plugins.collectAsStateWithLifecycle()
    val storeCatalog by viewModel.storeCatalog.collectAsStateWithLifecycle()
    val smartRecommendations by viewModel.smartRecommendations.collectAsStateWithLifecycle()
    val pendingSecurityReview by viewModel.pendingSecurityReview.collectAsStateWithLifecycle()
    val developerKeyInfo = remember { viewModel.developerKeyInfo }
    val sandboxTraces by viewModel.sandboxTraces.collectAsStateWithLifecycle()
    val pluginNotifications by viewModel.pluginNotifications.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val savedMsg = stringResource(R.string.msg_saved_success)
    val jsonCopiedMsg = stringResource(R.string.msg_json_copied)

    val uninstallLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onUninstallReturned()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshInstallPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                UiEvent.SavedToDevice -> snackbarHostState.showSnackbar(savedMsg)
                UiEvent.JsonCopied -> snackbarHostState.showSnackbar(jsonCopiedMsg)
                is UiEvent.LaunchIntent -> {
                    runCatching { context.startActivity(event.intent) }
                }
                is UiEvent.LaunchUninstallIntent -> {
                    runCatching { uninstallLauncher.launch(event.intent) }
                }
                is UiEvent.ErrorToast -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.inspectUri(it) }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
        uri?.let { viewModel.saveOutputToUri(it) }
    }

    val importPluginManifestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importPluginManifestFromUri(it) }
    }

    val exportSdkZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { viewModel.exportPluginSdkZipToUri(it) }
    }

    val exportPfpakLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { viewModel.savePendingPfpakToUri(it) }
    }

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val activeInspection = when (val st = pipelineState) {
        is ForgePipelineState.Ready -> st.inspection
        is ForgePipelineState.Compressing -> st.inspection
        is ForgePipelineState.Completed -> st.inspection
        else -> null
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().forgeBackdrop()) {
        val useNavRail = maxWidth >= 600.dp
        val isExpandedWidth = maxWidth >= 840.dp

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = { ForgeTopBar() },
            bottomBar = {
                if (!useNavRail) {
                    ForgeBottomBar(currentRoute = currentRoute, onNavigate = navigateTo)
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = Color.Transparent
                    ) {
                        Spacer(modifier = Modifier.weight(0.2f))
                        TopNavItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_FORGE,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(ROUTE_FORGE) {
                            ForgeScreen(
                                isExpandedWidth = isExpandedWidth,
                                devicePrimaryAbi = viewModel.devicePrimaryAbi,
                                pipelineState = pipelineState,
                                installState = installState,
                                installOptionsSheet = installOptionsSheet,
                                isRepackingClone = isRepackingClone,
                                canInstallPackages = canInstallPackages,
                                shizukuStatus = shizukuStatus,
                                telemetry = telemetry,
                                config = config,
                                installedApps = installedApps,
                                isLoadingApps = isLoadingApps,
                                onPickFileClick = {
                                    openDocumentLauncher.launch(
                                        arrayOf(
                                            "application/vnd.android.package-archive",
                                            "application/zip",
                                            "application/octet-stream",
                                            "*/*"
                                        )
                                    )
                                },
                                onOpenInstalledAppsPicker = { viewModel.loadInstalledAppsList() },
                                onSelectInstalledApp = { viewModel.inspectInstalledApp(it) },
                                onSelfTestClick = { viewModel.inspectSelfPackage() },
                                onAutoFixCompatibility = { viewModel.applyCompatibilityAutoFix() },
                                onSelectProfile = { viewModel.selectProfile(it) },
                                onUpdateConfig = { viewModel.updateConfig(it) },
                                onSelectInstallMode = { viewModel.setInstallIdentityMode(it) },
                                onCloneSuffixChange = { viewModel.setCloneSuffix(it) },
                                onCustomCloneLabelChange = { viewModel.updateCustomCloneLabel(it) },
                                onKeepAllAbis = { viewModel.setKeepAllAbis() },
                                onDeviceAbiOnly = { viewModel.setDeviceAbiOnly() },
                                onToggleAbi = { abi, all -> viewModel.toggleAbiSelection(abi, all) },
                                onToggleLocale = { loc -> viewModel.toggleKeepLocale(loc) },
                                onGrantShizuku = { viewModel.requestShizukuPermission() },
                                onOpenShizuku = { viewModel.openShizukuOrDevSettings() },
                                onRefreshShizuku = { viewModel.refreshShizukuStatus() },
                                onCompressClick = { viewModel.startCompression() },
                                onInstallApkClick = { viewModel.installCompressedApk() },
                                onOpenInstallOptions = { viewModel.openInstallOptionsSheet() },
                                onDismissInstallOptions = { viewModel.dismissInstallOptionsSheet() },
                                onChooseUpdateExisting = { viewModel.chooseUpdateExistingDirect() },
                                onChooseUninstallAndInstall = { viewModel.chooseUninstallAndInstall() },
                                onChooseInstallClone = { suffix -> viewModel.chooseInstallSideBySideClone(suffix) },
                                onChooseInstallViaShizuku = { viewModel.chooseInstallViaShizuku(allowDowngrade = true) },
                                onChooseInstallCompanionLibs = { viewModel.chooseInstallCompanionLibrariesStandard() },
                                onChooseUninstallViaShizuku = { pkg, keepData -> viewModel.uninstallViaShizuku(pkg, keepData) },
                                onSaveOutputClick = { suggestedName ->
                                    createDocumentLauncher.launch(suggestedName)
                                },
                                onShareOutputClick = {
                                    viewModel.buildShareIntent()?.let { shareIntent ->
                                        context.startActivity(Intent.createChooser(shareIntent, null))
                                    }
                                },
                                onJsonCopied = { viewModel.notifyJsonCopied() },
                                onDismissError = { viewModel.resetErrorToIdle() }
                            )
                        }

                        composable(ROUTE_INSPECTOR) {
                            InspectorScreen(
                                inspection = activeInspection,
                                searchQuery = inspectorSearch,
                                selectedCategory = inspectorCategory,
                                onSearchChange = { viewModel.updateInspectorSearch(it) },
                                onCategoryChange = { viewModel.updateInspectorCategory(it) }
                            )
                        }

                        composable(ROUTE_HISTORY) {
                            HistoryScreen(
                                items = historyItems,
                                totalSavedBytes = totalSavedBytes,
                                onDeleteItem = { viewModel.deleteHistoryItem(it) },
                                onClearAll = { viewModel.clearAllHistory() }
                            )
                        }

                        composable(ROUTE_SETTINGS) {
                            SettingsScreen(
                                preferences = preferences,
                                shizukuStatus = shizukuStatus,
                                bridgeSnapshot = bridgeSnapshot,
                                plugins = plugins,
                                storeCatalog = storeCatalog,
                                smartRecommendations = smartRecommendations,
                                pendingSecurityReview = pendingSecurityReview,
                                developerKeyInfo = developerKeyInfo,
                                sandboxTraces = sandboxTraces,
                                pluginNotifications = pluginNotifications,
                                deviceLabel = app.telemetryProvider.deviceLabel(),
                                onSelectLanguage = { viewModel.setLanguage(it) },
                                onSelectTheme = { viewModel.setThemeMode(it) },
                                onSelectPrivilegedBackend = { viewModel.selectPrivilegedBackend(it) },
                                onGrantShizuku = { viewModel.requestShizukuPermission() },
                                onOpenShizuku = { viewModel.openShizukuOrDevSettings() },
                                onRefreshShizuku = { viewModel.refreshShizukuStatus() },
                                onTogglePluginEnabled = { viewModel.togglePluginEnabled(it) },
                                onTogglePluginPermission = { id, perm -> viewModel.togglePluginPermission(id, perm) },
                                onRunPlugin = { viewModel.runPluginInSandbox(it) },
                                onRemoveCustomPlugin = { viewModel.removeCustomPlugin(it) },
                                onInspectStoreListing = { viewModel.inspectStoreListingForInstall(it) },
                                onConfirmInstallScannedPlugin = { perms -> viewModel.confirmInstallScannedPlugin(perms) },
                                onDismissSecurityReview = { viewModel.dismissSecurityReview() },
                                onInstallDeveloperPlugin = { manifest -> viewModel.installDeveloperManifestImmediately(manifest) },
                                onExportDeveloperPfpak = { manifest ->
                                    viewModel.prepareDeveloperPfpakForExport(manifest)?.let { filename ->
                                        exportPfpakLauncher.launch(filename)
                                    }
                                },
                                onRefreshSmartRecommendations = { viewModel.refreshSmartPluginRecommendations() },
                                onImportManifestClick = {
                                    importPluginManifestLauncher.launch(
                                        arrayOf(
                                            "application/octet-stream",
                                            "application/zip",
                                            "application/json",
                                            "text/plain",
                                            "*/*"
                                        )
                                    )
                                },
                                onExportSdkZipClick = {
                                    exportSdkZipLauncher.launch(PluginSdkZipExporter.DEFAULT_ARCHIVE_NAME)
                                },
                                onClearPluginNotifications = { viewModel.clearPluginNotifications() }
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ForgeTopBar() {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(forgeBrandBrush()),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Compress,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = cs.onBackground
        )
    }
}

@Composable
private fun ForgeBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = cs.surface,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, cs.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TopNavItems.forEach { item ->
                    val selected = currentRoute == item.route
                    val bg by animateColorAsState(
                        targetValue = if (selected) cs.primary.copy(alpha = 0.18f) else Color.Transparent,
                        label = "nav_bg"
                    )
                    val tint by animateColorAsState(
                        targetValue = if (selected) cs.primary else cs.onSurfaceVariant,
                        label = "nav_tint"
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(bg)
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = { onNavigate(item.route) }
                            )
                            .testTag(item.testTag)
                            .padding(vertical = 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = stringResource(item.labelRes),
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(item.labelRes),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = tint,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
