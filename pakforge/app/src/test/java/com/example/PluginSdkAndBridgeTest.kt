package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.core.engine.PackageInspectionResult
import com.example.core.privileged.PrivilegedBackendKind
import com.example.core.privileged.PrivilegedBridgeSnapshot
import com.example.core.privileged.RootFlavor
import com.example.core.privileged.RootShellExecutor
import androidx.compose.ui.test.onNodeWithText
import com.example.core.sdk.BuiltInSamplePlugins
import com.example.core.sdk.PluginActionStep
import com.example.core.sdk.PluginCategory
import com.example.core.sdk.PluginLocalization
import com.example.core.sdk.PluginManifest
import com.example.core.sdk.PluginManifestCodec
import com.example.core.sdk.PluginPermission
import com.example.core.sdk.PluginSdkExamples
import com.example.core.sdk.PluginSdkZipExporter
import com.example.core.sdk.PluginSecurityEngine
import com.example.core.sdk.PluginStoreCatalog
import com.example.core.sdk.PluginVersion
import com.example.core.sdk.SecurityRiskTier
import com.example.core.sdk.SignatureTrustLevel
import com.example.core.sdk.SmartPluginRecommender
import com.example.core.telemetry.DeviceTelemetryState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class PluginSdkAndBridgeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `semver versioning validates host api compatibility accurately`() {
        val v1 = PluginVersion.parseOrNull("1.0.0")!!
        val v12 = PluginVersion.parseOrNull("1.2.3")!!
        val v20 = PluginVersion.parseOrNull("2.0.0")!!

        assertTrue(v1.isApiCompatibleWithHost(PluginVersion(1, 0, 0)))
        assertFalse(v12.isApiCompatibleWithHost(PluginVersion(1, 0, 0)))
        assertFalse(v20.isApiCompatibleWithHost(PluginVersion(1, 0, 0)))
        assertEquals(null, PluginVersion.parseOrNull("not-a-version"))
    }

    @Test
    fun `manifest codec validates schema, rejects invalid minHostApiLevel, and roundtrips JSON`() {
        val sample = BuiltInSamplePlugins.deviceThermalGuardPlugin.manifest
        val json = PluginManifestCodec.toJson(sample)
        val decoded = PluginManifestCodec.parseJson(json).getOrThrow()

        assertEquals(sample.id, decoded.id)
        assertEquals(sample.permissions.toSet(), decoded.permissions.toSet())
        assertEquals(sample.hooks, decoded.hooks)

        val invalidApiJson = json.replace("\"minHostApiLevel\": 1", "\"minHostApiLevel\": 99")
        val failed = PluginManifestCodec.parseJson(invalidApiJson)
        assertTrue(failed.isFailure)
    }

    @Test
    fun `standalone sdk zip exporter builds complete gradle sdk, sample project, and docs`() {
        val baos = ByteArrayOutputStream()
        val count = PluginSdkZipExporter.writeZip(baos)
        assertTrue(count >= 8)

        val entries = HashSet<String>()
        ZipInputStream(ByteArrayInputStream(baos.toByteArray())).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                entries.add(e.name)
                zis.closeEntry()
            }
        }

        assertTrue("README.md" in entries)
        assertTrue("settings.gradle.kts" in entries)
        assertTrue("pakforge-sdk/src/main/kotlin/dev/pakforge/sdk/PluginSdkContract.kt" in entries)
        assertTrue("sample-plugin/src/main/resources/pakforge-plugin.json" in entries)
        assertTrue("sample-plugin/src/main/kotlin/com/example/sampleplugin/SmartAuditSamplePlugin.kt" in entries)
        assertTrue("docs/SDK_REFERENCE.md" in entries)
    }

    @Test
    fun `plugin manager enforces permission guard on revoke and succeeds when granted`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>() as PakForgeApp
        val manager = app.pluginManager
        val targetId = BuiltInSamplePlugins.deviceThermalGuardPlugin.manifest.id

        val okOut = manager.runPlugin(targetId)
        assertTrue("Expected success, got: ${okOut.summary}", okOut.ok)
        assertTrue(okOut.traces.any { it.contains("DeviceInterface ->") })
        assertTrue(okOut.traces.any { it.contains("DiagnosticsInterface ->") })

        manager.togglePluginPermission(targetId, PluginPermission.READ_DEVICE_TELEMETRY)
        val deniedOut = manager.runPlugin(targetId)
        assertFalse(deniedOut.ok)
        assertTrue(deniedOut.summary.contains("READ_DEVICE_TELEMETRY"))

        manager.togglePluginPermission(targetId, PluginPermission.READ_DEVICE_TELEMETRY)
        val restoredOut = manager.runPlugin(targetId)
        assertTrue(restoredOut.ok)
    }

    @Test
    fun `ecdsa p256 pfpak signing verifies valid bundles and rejects tampered or dex bundles`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val secEngine = PluginSecurityEngine(app)
        val manifest = PluginManifest(
            id = "dev.store.signed_tester",
            name = "Signed Tester Tool",
            version = "1.0.0",
            author = "PakForge Dev",
            description = "Validates ECDSA signature",
            entryPoint = "dev.store.SignedTesterPlugin",
            permissions = listOf(PluginPermission.READ_DEVICE_TELEMETRY),
            category = PluginCategory.DEVELOPER_TOOL,
            actions = listOf(PluginActionStep.CHECK_DEVICE_HEADROOM)
        )

        val signedBytes = secEngine.signAndPackPfpak(manifest, useOfficialKey = false)
        val scanOk = secEngine.inspectAndVerifyIncomingBytes(signedBytes, "signed_tester.pfpak")
        assertTrue(scanOk.canInstall)
        assertEquals(SignatureTrustLevel.VERIFIED_DEVELOPER, scanOk.signatureTrust)
        assertEquals(SecurityRiskTier.LOW, scanOk.riskTier)

        // Tamper with pakforge-plugin.json without re-signing -> must block cleanly
        val tamperedOut = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(signedBytes)).use { zis ->
            ZipOutputStream(tamperedOut).use { zos ->
                while (true) {
                    val e = zis.nextEntry ?: break
                    val data = zis.readBytes()
                    zos.putNextEntry(ZipEntry(e.name))
                    if (e.name == "pakforge-plugin.json") {
                        val modified = String(data).replace("Signed Tester Tool", "Hacked Tester Tool")
                        zos.write(modified.toByteArray())
                    } else {
                        zos.write(data)
                    }
                    zos.closeEntry()
                    zis.closeEntry()
                }
            }
        }
        val tamperedScan = secEngine.inspectAndVerifyIncomingBytes(tamperedOut.toByteArray(), "tampered.pfpak")
        assertFalse(tamperedScan.canInstall)
        assertEquals(SignatureTrustLevel.TAMPERED_REJECTED, tamperedScan.signatureTrust)
        assertEquals(SecurityRiskTier.BLOCKED, tamperedScan.riskTier)

        // Inject classes.dex -> blocked by DCL scanner
        val dexOut = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(signedBytes)).use { zis ->
            ZipOutputStream(dexOut).use { zos ->
                while (true) {
                    val e = zis.nextEntry ?: break
                    zos.putNextEntry(ZipEntry(e.name))
                    zos.write(zis.readBytes())
                    zos.closeEntry()
                    zis.closeEntry()
                }
                zos.putNextEntry(ZipEntry("classes.dex"))
                zos.write(byteArrayOf(1, 2, 3, 4))
                zos.closeEntry()
            }
        }
        val dexScan = secEngine.inspectAndVerifyIncomingBytes(dexOut.toByteArray(), "malicious.pfpak")
        assertFalse(dexScan.canInstall)
        assertEquals(SecurityRiskTier.BLOCKED, dexScan.riskTier)
    }

    @Test
    fun `smart recommender ranks system and split tools first when matching apk context`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val catalog = PluginStoreCatalog.buildSignedCatalog(PluginSecurityEngine(app))
        val inspection = PackageInspectionResult(
            workingFilePath = "/tmp/chrome.apk",
            displayName = "Chrome.apk",
            fileExtension = "apk",
            packageId = "com.android.chrome",
            versionName = "128.0",
            versionCode = 1L,
            minSdk = 26,
            targetSdk = 35,
            appIcon = null,
            totalArchiveBytes = 64L * 1024L * 1024L,
            totalRawBytes = 90L * 1024L * 1024L,
            predictedOutputBytes = 42L * 1024L * 1024L,
            entryCount = 240,
            averageEntropy = 6.2,
            hasResourcesArsc = true,
            hasUncompressedNativeLibs = true,
            detectedAbis = listOf("arm64-v8a", "armeabi-v7a"),
            debugArtifactsCount = 2,
            safetyScore = 88,
            duplicateGroups = emptyList(),
            securityAudit = null,
            breakdown = emptyList(),
            entries = emptyList(),
            splitSourcePaths = listOf("/tmp/split_config.arm64_v8a.apk"),
            detectedLocales = listOf("en", "ar", "de", "es", "fr", "zh"),
            hasSharedUserId = true
        )
        val telemetry = DeviceTelemetryState(
            freeRamMb = 420,
            totalRamMb = 4096,
            ramUsagePercent = 90,
            cpuCores = 8,
            isPowerSaveMode = true,
            isLowRamDevice = false,
            recommendedBufferKb = 16,
            heapUsedMb = 24,
            heapMaxMb = 256
        )
        val bridge = PrivilegedBridgeSnapshot(
            preferredMode = PrivilegedBackendKind.AUTO,
            activeBackend = PrivilegedBackendKind.ROOT_SU,
            activeDetail = "KernelSU Root (UID 0)",
            activeUid = 0,
            rootFlavor = RootFlavor.KERNEL_SU
        )

        val recs = SmartPluginRecommender.evaluate(
            manifests = catalog.map { it.manifest },
            inspection = inspection,
            telemetry = telemetry,
            bridge = bridge
        )
        assertTrue(recs.isNotEmpty())
        assertTrue(recs.any { it.manifest.id == "dev.pakforge.store.system_trichrome_unlocker" && it.matchScore >= 75 })
        assertTrue(recs.any { it.manifest.id == "dev.pakforge.store.game_bundle_universalizer" && it.matchScore >= 70 })
    }

    @Test
    fun `store plugin installs after scan and executes dynamic pipeline without rebuild`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>() as PakForgeApp
        val manager = app.pluginManager
        val listing = manager.storeCatalog.value.first {
            it.manifest.id == "dev.pakforge.store.privacy_bloat_inspector"
        }

        assertEquals(SignatureTrustLevel.VERIFIED_OFFICIAL, listing.scanResult.signatureTrust)
        val installed = manager.installScannedPlugin(listing.scanResult).getOrThrow()
        assertEquals(SignatureTrustLevel.VERIFIED_OFFICIAL, installed.signatureTrust)

        val runResult = manager.runPlugin(installed.manifest.id)
        assertTrue("Expected dynamic store plugin success, got: ${runResult.summary}", runResult.ok)
        assertTrue(runResult.traces.any { it.contains("ReportsInterface ->") })
        assertTrue(runResult.traces.any { it.contains("NotificationInterface ->") })
    }

    @Test
    fun `root shell probe handles non-rooted environment without crashing`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val probe = RootShellExecutor.probe(app, verifyGrant = true)
        assertNotNull(probe.flavor)
    }

    @Test
    fun `settings tab houses multi-backend bridge, plugin store, smart recognition, builder, and sdk docs`() {
        composeTestRule.onNodeWithTag("nav_settings").performClick()
        composeTestRule.onNodeWithTag("privileged_multi_bridge_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_dev_hub_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_dev_hub_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Tab 0: Plugin Store + Smart Recognition + Builder
        composeTestRule.onNodeWithTag("dev_hub_tab_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("store_smart_recognition_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("store_open_builder_btn").performClick()
        composeTestRule.onNodeWithTag("builder_id_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("builder_close_btn").performClick()

        // Tabs 1..4: Installed Plugins, SDK & Sample, APIs & Code, Docs
        composeTestRule.onNodeWithTag("dev_hub_tab_1").performClick()
        composeTestRule.onNodeWithTag("dev_hub_tab_2").performClick()
        composeTestRule.onNodeWithTag("export_sdk_zip_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("dev_hub_tab_3").performClick()
        composeTestRule.onNodeWithTag("dev_hub_tab_4").performClick()
        composeTestRule.onNodeWithTag("dev_hub_close_btn").performClick()
    }

    @Test
    fun `plugin localization supports all 6 languages and renders arabic inside developer hub dialog`() {
        val supported = listOf("ar", "en", "es", "fr", "de", "zh")
        val app = ApplicationProvider.getApplicationContext<Context>()
        val catalog = PluginStoreCatalog.buildSignedCatalog(PluginSecurityEngine(app))

        for (lang in supported) {
            PluginPermission.entries.forEach { perm ->
                assertTrue(PluginLocalization.permissionTitle(perm, lang).isNotBlank())
                assertTrue(PluginLocalization.permissionRiskSummary(perm, lang).isNotBlank())
            }
            PluginCategory.entries.forEach { cat ->
                assertTrue(PluginLocalization.categoryTitle(cat, lang).isNotBlank())
            }
            assertEquals(5, PluginLocalization.documentationSections(lang).size)
            assertEquals(5, PluginLocalization.sdkInterfaces(lang).size)
            PluginSdkExamples.snippets.forEach { snippet ->
                val (title, subtitle) = PluginLocalization.localizeExampleMeta(snippet, lang)
                assertTrue(title.isNotBlank() && subtitle.isNotBlank())
            }
            catalog.forEach { listing ->
                val copy = PluginLocalization.manifestCopy(listing.manifest, lang)
                assertTrue(copy.name.isNotBlank() && copy.description.isNotBlank())
                if (lang != "en") {
                    assertNotEquals(listing.manifest.name, copy.name)
                }
            }
        }

        // Switch UI language to Arabic and verify Dialog contents actually render in Arabic
        composeTestRule.onNodeWithTag("nav_settings").performClick()
        composeTestRule.onNodeWithTag("lang_chip_ar").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(androidx.compose.ui.test.hasText("الإضافات والمطورون (Plugin SDK)"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("الإضافات والمطورون (Plugin SDK)").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_dev_hub_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("متجر الإضافات").assertIsDisplayed()
        composeTestRule.onNodeWithText("إنشاء وتوقيع .pfpak").assertIsDisplayed()

        // Open Builder in Arabic and close
        composeTestRule.onNodeWithTag("store_open_builder_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("استوديو بناء وتوقيع أدوات .pfpak").assertIsDisplayed()
        composeTestRule.onNodeWithText("اسم الأداة").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("builder_close_btn").performClick()
        composeTestRule.waitForIdle()

        // Verify Tabs 1..4 in Arabic
        composeTestRule.onNodeWithTag("dev_hub_tab_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("حارس صحة الجهاز ومحرك الصلاحيات").assertIsDisplayed()

        composeTestRule.onNodeWithTag("dev_hub_tab_2").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("نموذج pakforge-plugin.json المرجعي").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("dev_hub_tab_3").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("واجهات الـ SDK الموحدة").assertIsDisplayed()

        composeTestRule.onNodeWithTag("dev_hub_tab_4").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1. العقد الموحد للـ Plugin API").assertIsDisplayed()

        composeTestRule.onNodeWithTag("dev_hub_close_btn").performClick()
    }
}
