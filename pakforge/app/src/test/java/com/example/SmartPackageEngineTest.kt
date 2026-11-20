package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.engine.ArscLocalePruner
import com.example.core.engine.ArscMerger
import com.example.core.engine.ArscPackagePatcher
import com.example.core.engine.AxmlManifestPatcher
import com.example.core.engine.CompatibilityAnalyzer
import com.example.core.engine.CompatibilityTier
import com.example.core.engine.CompressionConfig
import com.example.core.engine.CompressionProfile
import com.example.core.engine.InstallIdentityMode
import com.example.core.engine.ManifestPatchOptions
import com.example.core.engine.SmartPackageEngine
import com.example.core.engine.TunedZipOutputStream
import com.example.core.signing.ApkV2Signer
import com.example.core.telemetry.DeviceTelemetryProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Random
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class SmartPackageEngineTest {

    @Test
    fun inspectAndCompress_preservesArscStoredAligned_stripsUnusedAbis_andDetectsDuplicates() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val telemetry = DeviceTelemetryProvider(context)
        val engine = SmartPackageEngine(context, telemetry)

        val tempApk = File(context.cacheDir, "synthetic_pro_test.apk")
        ZipOutputStream(FileOutputStream(tempApk)).use { zos ->
            // 1. Compressible DEX-like repetitive bytecode
            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write(ByteArray(16 * 1024) { (it % 19).toByte() })
            zos.closeEntry()

            // 2. resources.arsc
            zos.putNextEntry(ZipEntry("resources.arsc"))
            zos.write(ByteArray(4096) { (it % 7).toByte() })
            zos.closeEntry()

            // 3. High-entropy random media bytes (should be bypassed as STORED)
            zos.putNextEntry(ZipEntry("res/raw/audio_clip.ogg"))
            val randomBytes = ByteArray(8192)
            Random(42L).nextBytes(randomBytes)
            zos.write(randomBytes)
            zos.closeEntry()

            // 4. Duplicate assets (exact same bytes & CRC)
            val dupPayload = "DUPLICATE_LICENSE_OR_FONT_PAYLOAD_1234567890".repeat(10).toByteArray()
            zos.putNextEntry(ZipEntry("assets/license_a.txt"))
            zos.write(dupPayload)
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("assets/license_b.txt"))
            zos.write(dupPayload)
            zos.closeEntry()

            // 5. Multiple ABI native libraries (distinct per architecture)
            zos.putNextEntry(ZipEntry("lib/arm64-v8a/libengine.so"))
            zos.write(ByteArray(2048) { (it % 5).toByte() })
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("lib/armeabi-v7a/libengine.so"))
            zos.write(ByteArray(2048) { (it % 7).toByte() })
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("lib/x86_64/libengine.so"))
            zos.write(ByteArray(2048) { (it % 11).toByte() })
            zos.closeEntry()

            // 6. Non-runtime debug probe
            zos.putNextEntry(ZipEntry("DebugProbesKt.bin"))
            zos.write("DEBUG_PROBE_DATA_123456".toByteArray())
            zos.closeEntry()
        }

        val config = CompressionConfig(
            profile = CompressionProfile.ULTRA_SHRINK,
            enforceResourcesArscAlign = true,
            smartEntropyBypass = true,
            pruneDebugArtifacts = true,
            adaptiveRamThrottle = true,
            allowedAbis = setOf("arm64-v8a")
        )

        val inspection = engine.inspectPackage(tempApk, "synthetic_pro_test.apk", config)
        assertTrue(inspection.hasResourcesArsc)
        assertEquals(9, inspection.entryCount)
        assertEquals(listOf("arm64-v8a", "armeabi-v7a", "x86_64"), inspection.detectedAbis)
        assertEquals(1, inspection.totalDuplicateFilesCount)
        assertTrue(inspection.totalDuplicateWastedBytes > 0L)

        val report = engine.compressPackage(inspection, config) { }
        assertEquals(0, report.corruptEntriesCount)
        assertEquals(6, report.verifiedEntriesCount) // 9 - 1 pruned - 2 stripped ABIs = 6
        assertTrue(report.resourcesArscAligned)
        assertEquals(2, report.strippedAbiEntriesCount)
        assertTrue(report.prunedArtifactsCount >= 1)
        assertTrue(report.bypassedMediaCount >= 1)
        assertNotNull(report.signedV2CertSha256)
        assertFalse(report.reSignRequiredWarning)
        assertTrue(report.toAuditJson().contains("\"strippedAbiEntries\": 2"))

        val outFile = File(report.outputFilePath)
        assertTrue(outFile.exists())

        ZipFile(outFile).use { outZip ->
            assertEquals(ZipEntry.STORED, outZip.getEntry("resources.arsc").method)
            assertEquals(ZipEntry.STORED, outZip.getEntry("res/raw/audio_clip.ogg").method)
            assertEquals(ZipEntry.DEFLATED, outZip.getEntry("classes.dex").method)
            assertNotNull(outZip.getEntry("lib/arm64-v8a/libengine.so"))
            assertNull(outZip.getEntry("lib/armeabi-v7a/libengine.so"))
            assertNull(outZip.getEntry("lib/x86_64/libengine.so"))
            assertFalse(outZip.entries().toList().any { it.name == "DebugProbesKt.bin" })
        }

        tempApk.delete()
        outFile.delete()
    }

    @Test
    fun apkV2Signer_generatesValidX509Certificate_andWritesV2SigningBlock() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val signer = ApkV2Signer(context)
        val identity = signer.getOrCreateIdentity()

        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(identity.certDer)) as X509Certificate
        assertEquals("SHA256withRSA", cert.sigAlgName)
        assertTrue(cert.subjectX500Principal.name.contains("PakForge Local Signer"))

        val unsignedApk = File(context.cacheDir, "v2_sign_input.apk")
        val signedApk = File(context.cacheDir, "v2_sign_output.apk")
        ZipOutputStream(FileOutputStream(unsignedApk)).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(byteArrayOf(1, 2, 3, 4))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write(ByteArray(4096) { (it % 13).toByte() })
            zos.closeEntry()
        }

        val fingerprint = signer.signApkV2(unsignedApk, signedApk)
        assertTrue(fingerprint.contains(":"))
        assertTrue(signedApk.exists() && signedApk.length() > unsignedApk.length())

        // Verify APK Signing Block magic immediately precedes Central Directory
        val bytes = signedApk.readBytes()
        val eocdOffset = bytes.size - 22
        assertEquals(0x50.toByte(), bytes[eocdOffset])
        assertEquals(0x4B.toByte(), bytes[eocdOffset + 1])
        assertEquals(0x05.toByte(), bytes[eocdOffset + 2])
        assertEquals(0x06.toByte(), bytes[eocdOffset + 3])
        val cdOffset = ByteBuffer.wrap(bytes, eocdOffset + 16, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .getInt()
        val magic = String(bytes, cdOffset - 16, 16, StandardCharsets.US_ASCII)
        assertEquals("APK Sig Block 42", magic)

        // Verify ZipFile still opens cleanly and all entry streams match original content
        ZipFile(signedApk).use { zf ->
            val manifestEntry = zf.getEntry("AndroidManifest.xml")
            val dexEntry = zf.getEntry("classes.dex")
            assertNotNull(manifestEntry)
            assertNotNull(dexEntry)
            assertTrue(zf.getInputStream(manifestEntry).readBytes().contentEquals(byteArrayOf(1, 2, 3, 4)))
            assertTrue(zf.getInputStream(dexEntry).readBytes().contentEquals(ByteArray(4096) { (it % 13).toByte() }))
        }

        unsignedApk.delete()
        signedApk.delete()
    }

    @Test
    fun axmlManifestPatcher_rewritesPackageExpandRelativeComponentsAndUpdatesAuthorities() {
        val origStrings = listOf(
            "manifest",
            "package",
            "com.orig.sample",
            "application",
            "label",
            "SampleApp",
            "activity",
            "name",
            ".MainActivity",
            "provider",
            "authorities",
            "com.orig.sample.fileprovider",
            "permission",
            "com.orig.sample.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        )

        val rawAxml = buildSyntheticAxml(
            strings = origStrings,
            elements = listOf(
                "manifest" to listOf("package" to "com.orig.sample"),
                "application" to listOf("label" to "SampleApp"),
                "activity" to listOf("name" to ".MainActivity"),
                "provider" to listOf("authorities" to "com.orig.sample.fileprovider"),
                "permission" to listOf("name" to "com.orig.sample.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
            )
        )

        val patched = AxmlManifestPatcher.patchManifestPackage(
            rawAxml = rawAxml,
            newPackageName = "com.orig.sample.pkf",
            appendAppLabelSuffix = " (PF)"
        )

        val buf = ByteBuffer.wrap(patched).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x0003, buf.getShort(0).toInt() and 0xFFFF)
        assertEquals(patched.size, buf.getInt(4))

        val spOffset = buf.getShort(2).toInt() and 0xFFFF
        val spChunkSize = buf.getInt(spOffset + 4)
        val strCount = buf.getInt(spOffset + 8)
        val flags = buf.getInt(spOffset + 16)
        val stringsStart = buf.getInt(spOffset + 20)
        assertEquals(0, flags and 0x0101) // UTF-8 and SORTED flags cleared

        val parsedStrings = ArrayList<String>(strCount)
        val offsetsStart = spOffset + 28
        val strDataBase = spOffset + stringsStart
        for (i in 0 until strCount) {
            val relOff = buf.getInt(offsetsStart + i * 4)
            val abs = strDataBase + relOff
            val charLen = buf.getShort(abs).toInt() and 0xFFFF
            parsedStrings.add(String(patched, abs + 2, charLen * 2, StandardCharsets.UTF_16LE))
        }

        // Read updated attribute strings from START_ELEMENT chunks
        val resolvedAttrs = LinkedHashMap<String, MutableMap<String, String>>()
        var pos = spOffset + spChunkSize
        while (pos + 8 <= patched.size) {
            val cType = buf.getShort(pos).toInt() and 0xFFFF
            val cSize = buf.getInt(pos + 4)
            if (cType == 0x0102) {
                val elemName = parsedStrings[buf.getInt(pos + 20)]
                val attrStart = buf.getShort(pos + 24).toInt() and 0xFFFF
                val attrSize = buf.getShort(pos + 26).toInt() and 0xFFFF
                val attrCount = buf.getShort(pos + 28).toInt() and 0xFFFF
                val attrMap = resolvedAttrs.getOrPut(elemName) { LinkedHashMap() }
                for (a in 0 until attrCount) {
                    val aPos = pos + 16 + attrStart + a * attrSize
                    val aName = parsedStrings[buf.getInt(aPos + 4)]
                    val aVal = parsedStrings[buf.getInt(aPos + 16)]
                    attrMap[aName] = aVal
                }
            }
            pos += cSize
        }

        assertEquals("com.orig.sample.pkf", resolvedAttrs["manifest"]?.get("package"))
        assertEquals("SampleApp (PF)", resolvedAttrs["application"]?.get("label"))
        assertEquals("com.orig.sample.MainActivity", resolvedAttrs["activity"]?.get("name"))
        assertEquals("com.orig.sample.pkf.fileprovider", resolvedAttrs["provider"]?.get("authorities"))
        assertEquals(
            "com.orig.sample.pkf.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            resolvedAttrs["permission"]?.get("name")
        )
        assertEquals("com.orig.sample", AxmlManifestPatcher.readManifestPackage(rawAxml))
        assertEquals("com.orig.sample.pkf", AxmlManifestPatcher.readManifestPackage(patched))
    }

    @Test
    fun repackApkAsClone_patchesManifestPackageOnTheFly_preservesStoredEntries_andResignsV2() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val telemetry = DeviceTelemetryProvider(context)
        val engine = SmartPackageEngine(context, telemetry)

        val rawAxml = buildSyntheticAxml(
            strings = listOf("manifest", "package", "com.demo.photos", "application", "label", "DemoPhotos"),
            elements = listOf(
                "manifest" to listOf("package" to "com.demo.photos"),
                "application" to listOf("label" to "DemoPhotos")
            )
        )

        val srcApk = File(context.cacheDir, "clone_repack_source.apk")
        ZipOutputStream(FileOutputStream(srcApk)).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(rawAxml)
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("resources.arsc"))
            zos.write(ByteArray(2048) { (it % 7).toByte() })
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write(ByteArray(4096) { (it % 11).toByte() })
            zos.closeEntry()
        }

        val config = CompressionConfig(profile = CompressionProfile.ZERO_RISK_BALANCED)
        val inspection = engine.inspectPackage(srcApk, "DemoPhotos.apk", config)
        val initialReport = engine.compressPackage(inspection, config) { }

        val clonedReport = engine.repackApkAsClone(initialReport, "pf2")
        assertEquals("com.demo.photos.pf2", clonedReport.effectivePackageId)
        assertNotNull(clonedReport.signedV2CertSha256)

        val clonedFile = File(clonedReport.outputFilePath)
        assertTrue(clonedFile.exists())
        ZipFile(clonedFile).use { zf ->
            val manifestBytes = zf.getInputStream(zf.getEntry("AndroidManifest.xml")).readBytes()
            assertEquals("com.demo.photos.pf2", AxmlManifestPatcher.readManifestPackage(manifestBytes))
            assertEquals(ZipEntry.STORED, zf.getEntry("resources.arsc").method)
        }

        srcApk.delete()
        clonedFile.delete()
    }

    @Test
    fun arscPackagePatcher_updatesPackageNameInPlaceWithoutChangingOffsets() {
        val packageChunkSize = 12 + 256 + 16
        val tableHeaderSize = 12
        val totalSize = tableHeaderSize + packageChunkSize
        val rawArsc = ByteArray(totalSize)
        val buf = ByteBuffer.wrap(rawArsc).order(ByteOrder.LITTLE_ENDIAN)

        // ResTable_header (type=0x0002, headerSize=12, size=totalSize)
        buf.putShort(0, 0x0002)
        buf.putShort(2, tableHeaderSize.toShort())
        buf.putInt(4, totalSize)
        buf.putInt(8, 1) // packageCount

        // ResTable_package header (type=0x0200, headerSize=268, id=0x7f, name[128] @ +12)
        val pkgOff = tableHeaderSize
        buf.putShort(pkgOff, 0x0200)
        buf.putShort(pkgOff + 2, 268.toShort())
        buf.putInt(pkgOff + 4, packageChunkSize)
        buf.putInt(pkgOff + 8, 0x7F)
        val oldBytes = "com.chrome.orig".toByteArray(StandardCharsets.UTF_16LE)
        System.arraycopy(oldBytes, 0, rawArsc, pkgOff + 12, oldBytes.size)
        // Trailing marker bytes after package header must stay at identical offset
        buf.putInt(pkgOff + 268, 0x12345678)

        val patched = ArscPackagePatcher.patchPackageName(
            rawArsc = rawArsc,
            oldPackageName = "com.chrome.orig",
            newPackageName = "com.chrome.orig.pkf"
        )
        assertEquals(rawArsc.size, patched.size)
        val patchedBuf = ByteBuffer.wrap(patched).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x12345678, patchedBuf.getInt(pkgOff + 268))

        val sb = StringBuilder()
        var i = 0
        while (i < 256) {
            val ch = patchedBuf.getShort(pkgOff + 12 + i).toInt() and 0xFFFF
            if (ch == 0) break
            sb.append(ch.toChar())
            i += 2
        }
        assertEquals("com.chrome.orig.pkf", sb.toString())
    }

    @Test
    fun axmlManifestPatcher_detectsStaticLibrariesAndRewritesComponentPermissions() {
        val rawAxml = buildSyntheticAxml(
            strings = listOf(
                "manifest", "package", "sharedUserId", "com.orig.app", "android.uid.shared",
                "uses-static-library", "name", "com.google.android.trichromelibrary",
                "permission", "com.orig.app.CUSTOM_PERM",
                "service", ".SyncService", "readPermission"
            ),
            elements = listOf(
                "manifest" to listOf("package" to "com.orig.app", "sharedUserId" to "android.uid.shared"),
                "uses-static-library" to listOf("name" to "com.google.android.trichromelibrary"),
                "permission" to listOf("name" to "com.orig.app.CUSTOM_PERM"),
                "service" to listOf("name" to ".SyncService", "readPermission" to "com.orig.app.CUSTOM_PERM")
            )
        )

        val risks = AxmlManifestPatcher.detectManifestRuntimeRisks(rawAxml)
        assertTrue(risks.any { it == "SHARED_USER_ID:android.uid.shared" })
        assertTrue(risks.any { it == "STATIC_SHARED_LIB:com.google.android.trichromelibrary" })

        val patched = AxmlManifestPatcher.patchManifestPackage(rawAxml, "com.orig.app.pkf", null)
        val buf = ByteBuffer.wrap(patched).order(ByteOrder.LITTLE_ENDIAN)
        val spOffset = buf.getShort(2).toInt() and 0xFFFF
        val spChunkSize = buf.getInt(spOffset + 4)
        val strCount = buf.getInt(spOffset + 8)
        val stringsStart = buf.getInt(spOffset + 20)
        val strings = ArrayList<String>(strCount)
        for (i in 0 until strCount) {
            val abs = spOffset + stringsStart + buf.getInt(spOffset + 28 + i * 4)
            val charLen = buf.getShort(abs).toInt() and 0xFFFF
            strings.add(String(patched, abs + 2, charLen * 2, StandardCharsets.UTF_16LE))
        }
        assertTrue("com.orig.app.pkf.CUSTOM_PERM" in strings)
        assertTrue("com.orig.app.SyncService" in strings)
        assertEquals(patched.size, buf.getInt(4))
        assertTrue(spOffset + spChunkSize <= patched.size)
    }

    @Test
    fun splitBundleCompressionAndClone_compressesAndSignsBaseAndMatchingSplitApks() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val telemetry = DeviceTelemetryProvider(context)
        val engine = SmartPackageEngine(context, telemetry)

        val baseAxml = buildSyntheticAxml(
            strings = listOf("manifest", "package", "com.split.demo", "application", "label", "SplitApp"),
            elements = listOf(
                "manifest" to listOf("package" to "com.split.demo"),
                "application" to listOf("label" to "SplitApp")
            )
        )
        val splitAxml = buildSyntheticAxml(
            strings = listOf("manifest", "package", "com.split.demo"),
            elements = listOf("manifest" to listOf("package" to "com.split.demo"))
        )

        val baseApk = File(context.cacheDir, "split_base.apk")
        ZipOutputStream(FileOutputStream(baseApk)).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(baseAxml)
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write(ByteArray(4096) { (it % 17).toByte() })
            zos.closeEntry()
        }

        val arm64Split = File(context.cacheDir, "split_config.arm64_v8a.apk")
        ZipOutputStream(FileOutputStream(arm64Split)).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(splitAxml)
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("lib/arm64-v8a/libcore.so"))
            zos.write(ByteArray(2048) { (it % 5).toByte() })
            zos.closeEntry()
        }

        val x86Split = File(context.cacheDir, "split_config.x86.apk")
        ZipOutputStream(FileOutputStream(x86Split)).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(splitAxml)
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("lib/x86/libcore.so"))
            zos.write(ByteArray(2048) { (it % 9).toByte() })
            zos.closeEntry()
        }

        val config = CompressionConfig(
            profile = CompressionProfile.ZERO_RISK_BALANCED,
            allowedAbis = setOf("arm64-v8a"),
            installIdentityMode = InstallIdentityMode.SAME_PACKAGE
        )
        val inspection = engine.inspectPackage(
            file = baseApk,
            displayName = "SplitApp.apk",
            config = config,
            splitPaths = listOf(arm64Split.absolutePath, x86Split.absolutePath)
        )
        assertEquals(2, inspection.splitSourcePaths.size)
        assertTrue(inspection.detectedAbis.containsAll(listOf("arm64-v8a", "x86")))

        val report = engine.compressPackage(inspection, config) { }
        // x86 split stripped -> only arm64 split remains in splitOutputPaths
        assertEquals(1, report.splitOutputPaths.size)
        val outSplitFile = File(report.splitOutputPaths.first())
        assertTrue(outSplitFile.exists())

        // Now clone both base and split APK via repackApkAsClone
        val clonedReport = engine.repackApkAsClone(report, "pfclone")
        assertEquals("com.split.demo.pfclone", clonedReport.effectivePackageId)
        assertEquals(1, clonedReport.splitOutputPaths.size)
        val clonedBaseFile = File(clonedReport.outputFilePath)
        val clonedSplitFile = File(clonedReport.splitOutputPaths.first())
        assertTrue(clonedBaseFile.exists())
        assertTrue(clonedSplitFile.exists())

        ZipFile(clonedSplitFile).use { zf ->
            val mf = zf.getInputStream(zf.getEntry("AndroidManifest.xml")).readBytes()
            assertEquals("com.split.demo.pfclone", AxmlManifestPatcher.readManifestPackage(mf))
            assertNotNull(zf.getEntry("lib/arm64-v8a/libcore.so"))
        }

        baseApk.delete()
        arm64Split.delete()
        x86Split.delete()
        clonedBaseFile.delete()
        clonedSplitFile.delete()
    }

    @Test
    fun arscMergerAndLocalePruner_mergesSplitsRemapsStringsAndPrunesUnusedLocales() {
        val baseArsc = buildSyntheticArsc(
            pkgName = "com.example.merged",
            strings = listOf("res/layout/main.xml", "Default Title"),
            configs = listOf("" to 1)
        )
        val frSplitArsc = buildSyntheticArsc(
            pkgName = "com.example.merged",
            strings = listOf("Titre Francais", "res/raw/fr_hint.txt"),
            configs = listOf("fr" to 0)
        )
        val arSplitArsc = buildSyntheticArsc(
            pkgName = "com.example.merged",
            strings = listOf("العنوان العربي", "res/raw/ar_audio.mp3"),
            configs = listOf("ar" to 0)
        )

        val merged = ArscMerger.merge(baseArsc, listOf(frSplitArsc, arSplitArsc))
        val mergedBuf = ByteBuffer.wrap(merged).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(merged.size, mergedBuf.getInt(4))
        assertEquals(listOf("ar", "fr"), ArscLocalePruner.extractAvailableLocales(merged))

        val pruned = ArscLocalePruner.pruneUnusedLocales(merged, allowedLocales = setOf("ar"))
        assertEquals(1, pruned.prunedConfigsCount)
        assertEquals(listOf("ar"), ArscLocalePruner.extractAvailableLocales(pruned.patchedArsc))
        val prunedBuf = ByteBuffer.wrap(pruned.patchedArsc).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(pruned.patchedArsc.size, prunedBuf.getInt(4))
    }

    @Test
    fun axmlManifestPatcher_stripsSharedUidRewritesCertDigestClearsSplitFlagAndOverridesLabelRef() {
        val strings = listOf(
            "manifest", "package", "com.sys.app", "sharedUserId", "android.uid.system",
            "isSplitRequired", "application", "label",
            "uses-static-library", "name", "com.google.android.trichromelibrary",
            "certDigest", "OLD_DIGEST_HEX"
        )
        val rawAxml = buildSyntheticAxmlWithTypedAttrs(
            strings = strings,
            elements = listOf(
                "manifest" to listOf(
                    Triple("package", 0x03, strings.indexOf("com.sys.app")),
                    Triple("sharedUserId", 0x03, strings.indexOf("android.uid.system")),
                    Triple("isSplitRequired", 0x12, -1)
                ),
                "application" to listOf(
                    Triple("label", 0x01, 0x7F0A0001) // TYPE_REFERENCE -> @string/app_name
                ),
                "uses-static-library" to listOf(
                    Triple("name", 0x03, strings.indexOf("com.google.android.trichromelibrary")),
                    Triple("certDigest", 0x03, strings.indexOf("OLD_DIGEST_HEX"))
                )
            )
        )

        val patched = AxmlManifestPatcher.patchManifestAdvanced(
            rawAxml = rawAxml,
            options = ManifestPatchOptions(
                newPackageName = "com.sys.app.pf",
                customAppLabel = "Custom App PF",
                stripSharedUserId = true,
                newStaticLibCertDigestHex = "AABBCCDD11223344",
                clearSplitRequiredFlags = true,
                cloneAffinitySuffix = "pf"
            )
        )

        assertEquals("com.sys.app.pf", AxmlManifestPatcher.readManifestPackage(patched))
        val postRisks = AxmlManifestPatcher.detectManifestRuntimeRisks(patched)
        assertFalse(postRisks.any { it.startsWith("SHARED_USER_ID:") })

        val buf = ByteBuffer.wrap(patched).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(patched.size, buf.getInt(4))
        val spOffset = buf.getShort(2).toInt() and 0xFFFF
        val spSize = buf.getInt(spOffset + 4)
        val strCount = buf.getInt(spOffset + 8)
        val stringsStart = buf.getInt(spOffset + 20)
        val parsedStrings = (0 until strCount).map { i ->
            val abs = spOffset + stringsStart + buf.getInt(spOffset + 28 + i * 4)
            val len = buf.getShort(abs).toInt() and 0xFFFF
            String(patched, abs + 2, len * 2, StandardCharsets.UTF_16LE)
        }
        assertTrue("Custom App PF" in parsedStrings)
        assertTrue("AABBCCDD11223344" in parsedStrings)

        var pos = spOffset + spSize
        var foundLabelType = -1
        var foundLabelVal = ""
        var splitRequiredVal = -99
        while (pos + 8 <= patched.size) {
            val cType = buf.getShort(pos).toInt() and 0xFFFF
            val cSize = buf.getInt(pos + 4)
            if (cType == 0x0102) {
                val elemName = parsedStrings[buf.getInt(pos + 20)]
                val attrStart = buf.getShort(pos + 24).toInt() and 0xFFFF
                val attrSize = buf.getShort(pos + 26).toInt() and 0xFFFF
                val attrCount = buf.getShort(pos + 28).toInt() and 0xFFFF
                for (a in 0 until attrCount) {
                    val aPos = pos + 16 + attrStart + a * attrSize
                    val aName = parsedStrings[buf.getInt(aPos + 4)]
                    val aType = (buf.getInt(aPos + 12) ushr 24) and 0xFF
                    val aData = buf.getInt(aPos + 16)
                    if (elemName == "application" && aName == "label") {
                        foundLabelType = aType
                        foundLabelVal = parsedStrings.getOrElse(aData) { "" }
                    }
                    if (elemName == "manifest" && aName == "isSplitRequired") {
                        splitRequiredVal = aData
                    }
                }
            }
            pos += cSize
        }
        assertEquals(0x03, foundLabelType)
        assertEquals("Custom App PF", foundLabelVal)
        assertEquals(0, splitRequiredVal)
    }

    @Test
    fun universalSplitMerger_mergesDexArscAndSplitsIntoSingleSignedUniversalApk() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val telemetry = DeviceTelemetryProvider(context)
        val engine = SmartPackageEngine(context, telemetry)

        val baseAxml = buildSyntheticAxml(
            strings = listOf("manifest", "package", "com.merge.demo", "application", "label", "MergeDemo"),
            elements = listOf(
                "manifest" to listOf("package" to "com.merge.demo"),
                "application" to listOf("label" to "MergeDemo")
            )
        )
        val baseArsc = buildSyntheticArsc("com.merge.demo", listOf("Base String"), listOf("" to 0))
        val splitArsc = buildSyntheticArsc("com.merge.demo", listOf("Arabic Split"), listOf("ar" to 0))

        val baseApk = File(context.cacheDir, "merge_base.apk")
        ZipOutputStream(FileOutputStream(baseApk)).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml")); zos.write(baseAxml); zos.closeEntry()
            zos.putNextEntry(ZipEntry("resources.arsc")); zos.write(baseArsc); zos.closeEntry()
            zos.putNextEntry(ZipEntry("classes.dex")); zos.write(ByteArray(2048) { (it % 7).toByte() }); zos.closeEntry()
        }

        val splitArm = File(context.cacheDir, "merge_split_arm64.apk")
        ZipOutputStream(FileOutputStream(splitArm)).use { zos ->
            zos.putNextEntry(ZipEntry("classes.dex")); zos.write(ByteArray(2048) { (it % 11).toByte() }); zos.closeEntry()
            zos.putNextEntry(ZipEntry("lib/arm64-v8a/libmerge.so")); zos.write(ByteArray(1024) { 3 }); zos.closeEntry()
            zos.putNextEntry(ZipEntry("resources.arsc")); zos.write(splitArsc); zos.closeEntry()
        }

        val config = CompressionConfig(
            profile = CompressionProfile.ULTRA_SHRINK,
            mergeSplitsIntoUniversal = true,
            allowedAbis = setOf("arm64-v8a")
        )
        val inspection = engine.inspectPackage(
            file = baseApk,
            displayName = "MergeDemo.apk",
            config = config,
            splitPaths = listOf(splitArm.absolutePath)
        )
        val report = engine.compressPackage(inspection, config) {}
        assertTrue(report.mergedUniversalApk)
        assertTrue(report.splitOutputPaths.isEmpty())
        assertEquals(0, report.corruptEntriesCount)
        assertNotNull(report.signedV2CertSha256)

        val outFile = File(report.outputFilePath)
        ZipFile(outFile).use { zf ->
            assertNotNull(zf.getEntry("classes.dex"))
            assertNotNull(zf.getEntry("classes2.dex"))
            assertNotNull(zf.getEntry("lib/arm64-v8a/libmerge.so"))
            val mergedArsc = zf.getInputStream(zf.getEntry("resources.arsc")).readBytes()
            assertEquals(listOf("ar"), ArscLocalePruner.extractAvailableLocales(mergedArsc))
        }

        baseApk.delete()
        splitArm.delete()
        outFile.delete()
    }

    @Test
    fun compatibilityAnalyzer_flagsBlockersAndGeneratesAutoFixConfig() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val telemetry = DeviceTelemetryProvider(context)
        val engine = SmartPackageEngine(context, telemetry)

        val rawAxml = buildSyntheticAxml(
            strings = listOf("manifest", "package", "sharedUserId", "com.compat.test", "android.uid.system"),
            elements = listOf(
                "manifest" to listOf("package" to "com.compat.test", "sharedUserId" to "android.uid.system")
            )
        )
        val apk = File(context.cacheDir, "compat_test.apk")
        ZipOutputStream(FileOutputStream(apk)).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml")); zos.write(rawAxml); zos.closeEntry()
            zos.putNextEntry(ZipEntry("lib/arm64-v8a/liba.so")); zos.write(byteArrayOf(1, 2, 3)); zos.closeEntry()
            zos.putNextEntry(ZipEntry("lib/x86_64/liba.so")); zos.write(byteArrayOf(4, 5, 6)); zos.closeEntry()
        }

        val inspection = engine.inspectPackage(apk, "compat_test.apk", CompressionConfig(), splitPaths = listOf(apk.absolutePath))
        val compat = CompatibilityAnalyzer.analyze(
            inspection = inspection,
            isInstalledWithDifferentSig = true,
            isSystemApp = true,
            shizukuReady = true
        )
        assertEquals(CompatibilityTier.AUTO_FIXABLE, compat.tier)
        assertTrue(compat.canAutoFix)

        val fixed = CompatibilityAnalyzer.buildAutoFixConfig(
            current = CompressionConfig(),
            inspection = inspection,
            isInstalledWithDifferentSig = true,
            isSystemApp = true,
            shizukuReady = true,
            devicePrimaryAbi = "arm64-v8a"
        )
        assertTrue(fixed.mergeSplitsIntoUniversal)
        assertTrue(fixed.stripSharedUserId)
        assertTrue(fixed.preferShizukuInstaller)
        assertEquals(setOf("arm64-v8a"), fixed.allowedAbis)
        assertEquals(InstallIdentityMode.CLONE_NEW_PACKAGE, fixed.installIdentityMode)
        apk.delete()
    }

    @Test
    fun tunedZipOutputStream_selectsValidOptimalDeflateStrategy() {
        val dexLike = ByteArray(16 * 1024) { ((it * 31) xor (it ushr 3)).toByte() }
        val chosen = TunedZipOutputStream.pickOptimalStrategy(dexLike, Deflater.BEST_COMPRESSION)
        assertTrue(chosen == Deflater.DEFAULT_STRATEGY || chosen == Deflater.FILTERED)
    }

    private fun buildSyntheticArsc(
        pkgName: String,
        strings: List<String>,
        configs: List<Pair<String, Int>>
    ): ByteArray {
        val spData = ByteArrayOutputStream()
        val offsets = IntArray(strings.size)
        for (i in strings.indices) {
            offsets[i] = spData.size()
            val s = strings[i]
            spData.writeShortLE(s.length)
            spData.write(s.toByteArray(StandardCharsets.UTF_16LE))
            spData.writeShortLE(0)
        }
        while (spData.size() % 4 != 0) spData.write(0)
        val spDataBytes = spData.toByteArray()
        val spHeader = 28
        val stringsStart = spHeader + strings.size * 4
        val spSize = stringsStart + spDataBytes.size
        val spOut = ByteArrayOutputStream(spSize).apply {
            writeShortLE(0x0001)
            writeShortLE(spHeader)
            writeIntLE(spSize)
            writeIntLE(strings.size)
            writeIntLE(0)
            writeIntLE(0)
            writeIntLE(stringsStart)
            writeIntLE(0)
            offsets.forEach { writeIntLE(it) }
            write(spDataBytes)
        }.toByteArray()

        val typeChunksOut = ByteArrayOutputStream()
        for ((lang, strIdx) in configs) {
            val typeHeaderSize = 68 // 20 + 48 byte config
            val entriesStart = typeHeaderSize + 4
            val entryBytes = ByteArrayOutputStream().apply {
                writeShortLE(8) // ResTable_entry size
                writeShortLE(0) // flags
                writeIntLE(0) // key str idx
                writeShortLE(8) // Res_value size
                write(0) // res0
                write(0x03) // TYPE_STRING
                writeIntLE(strIdx)
            }.toByteArray()
            val tSize = entriesStart + entryBytes.size
            val cfg = ByteArray(48)
            ByteBuffer.wrap(cfg).order(ByteOrder.LITTLE_ENDIAN).putInt(0, 48)
            if (lang.length >= 2) {
                cfg[8] = lang[0].code.toByte()
                cfg[9] = lang[1].code.toByte()
            }
            typeChunksOut.writeShortLE(0x0201) // RES_TABLE_TYPE_TYPE
            typeChunksOut.writeShortLE(typeHeaderSize)
            typeChunksOut.writeIntLE(tSize)
            typeChunksOut.write(1) // id
            typeChunksOut.write(0) // flags
            typeChunksOut.writeShortLE(0) // res1
            typeChunksOut.writeIntLE(1) // entryCount
            typeChunksOut.writeIntLE(entriesStart)
            typeChunksOut.write(cfg)
            typeChunksOut.writeIntLE(0) // entry offset 0
            typeChunksOut.write(entryBytes)
        }
        val typeBytes = typeChunksOut.toByteArray()

        val pkgHeaderSize = 268
        val pkgSize = pkgHeaderSize + typeBytes.size
        val pkgOut = ByteArray(pkgSize)
        val pBuf = ByteBuffer.wrap(pkgOut).order(ByteOrder.LITTLE_ENDIAN)
        pBuf.putShort(0, 0x0200)
        pBuf.putShort(2, pkgHeaderSize.toShort())
        pBuf.putInt(4, pkgSize)
        pBuf.putInt(8, 0x7F)
        val pkgNameBytes = pkgName.toByteArray(StandardCharsets.UTF_16LE)
        System.arraycopy(pkgNameBytes, 0, pkgOut, 12, pkgNameBytes.size.coerceAtMost(254))
        System.arraycopy(typeBytes, 0, pkgOut, pkgHeaderSize, typeBytes.size)

        val totalSize = 12 + spOut.size + pkgOut.size
        return ByteArrayOutputStream(totalSize).apply {
            writeShortLE(0x0002)
            writeShortLE(12)
            writeIntLE(totalSize)
            writeIntLE(1)
            write(spOut)
            write(pkgOut)
        }.toByteArray()
    }

    private fun buildSyntheticAxmlWithTypedAttrs(
        strings: List<String>,
        elements: List<Pair<String, List<Triple<String, Int, Int>>>>
    ): ByteArray {
        val spData = ByteArrayOutputStream()
        val offsets = IntArray(strings.size)
        for (i in strings.indices) {
            offsets[i] = spData.size()
            val s = strings[i]
            spData.writeShortLE(s.length)
            spData.write(s.toByteArray(StandardCharsets.UTF_16LE))
            spData.writeShortLE(0)
        }
        while (spData.size() % 4 != 0) spData.write(0)
        val spDataBytes = spData.toByteArray()
        val stringsStart = 28 + strings.size * 4
        val spChunkSize = stringsStart + spDataBytes.size
        val spBytes = ByteArrayOutputStream(spChunkSize).apply {
            writeShortLE(0x0001)
            writeShortLE(28)
            writeIntLE(spChunkSize)
            writeIntLE(strings.size)
            writeIntLE(0)
            writeIntLE(0)
            writeIntLE(stringsStart)
            writeIntLE(0)
            offsets.forEach { writeIntLE(it) }
            write(spDataBytes)
        }.toByteArray()

        val elemChunks = ByteArrayOutputStream()
        for ((elemName, attrs) in elements) {
            val chunkSize = 36 + attrs.size * 20
            elemChunks.writeShortLE(0x0102)
            elemChunks.writeShortLE(16)
            elemChunks.writeIntLE(chunkSize)
            elemChunks.writeIntLE(1)
            elemChunks.writeIntLE(-1)
            elemChunks.writeIntLE(-1)
            elemChunks.writeIntLE(strings.indexOf(elemName))
            elemChunks.writeShortLE(20)
            elemChunks.writeShortLE(20)
            elemChunks.writeShortLE(attrs.size)
            elemChunks.writeShortLE(0)
            elemChunks.writeShortLE(0)
            elemChunks.writeShortLE(0)
            for ((aName, aType, aData) in attrs) {
                elemChunks.writeIntLE(-1)
                elemChunks.writeIntLE(strings.indexOf(aName))
                elemChunks.writeIntLE(if (aType == 0x03) aData else -1)
                elemChunks.writeIntLE((aType shl 24) or 0x0008)
                elemChunks.writeIntLE(aData)
            }
        }
        val elemBytes = elemChunks.toByteArray()
        val totalSize = 8 + spBytes.size + elemBytes.size
        return ByteArrayOutputStream(totalSize).apply {
            writeShortLE(0x0003)
            writeShortLE(8)
            writeIntLE(totalSize)
            write(spBytes)
            write(elemBytes)
        }.toByteArray()
    }

    private fun buildSyntheticAxml(
        strings: List<String>,
        elements: List<Pair<String, List<Pair<String, String>>>>
    ): ByteArray {
        val spData = ByteArrayOutputStream()
        val offsets = IntArray(strings.size)
        for (i in strings.indices) {
            offsets[i] = spData.size()
            val s = strings[i]
            spData.writeShortLE(s.length)
            spData.write(s.toByteArray(StandardCharsets.UTF_16LE))
            spData.writeShortLE(0)
        }
        while (spData.size() % 4 != 0) spData.write(0)
        val spDataBytes = spData.toByteArray()
        val spHeaderSize = 28
        val stringsStart = spHeaderSize + strings.size * 4
        val spChunkSize = stringsStart + spDataBytes.size

        val spOut = ByteArrayOutputStream()
        spOut.writeShortLE(0x0001)
        spOut.writeShortLE(spHeaderSize)
        spOut.writeIntLE(spChunkSize)
        spOut.writeIntLE(strings.size)
        spOut.writeIntLE(0) // styleCount
        spOut.writeIntLE(0x0001) // SORTED_FLAG initially set
        spOut.writeIntLE(stringsStart)
        spOut.writeIntLE(0) // stylesStart
        offsets.forEach { spOut.writeIntLE(it) }
        spOut.write(spDataBytes)
        val spBytes = spOut.toByteArray()

        val elemChunks = ByteArrayOutputStream()
        for ((elemName, attrs) in elements) {
            val chunkSize = 36 + attrs.size * 20
            elemChunks.writeShortLE(0x0102) // CHUNK_START_ELEMENT
            elemChunks.writeShortLE(16)
            elemChunks.writeIntLE(chunkSize)
            elemChunks.writeIntLE(1) // lineNumber
            elemChunks.writeIntLE(-1) // comment
            elemChunks.writeIntLE(-1) // ns
            elemChunks.writeIntLE(strings.indexOf(elemName)) // name
            elemChunks.writeShortLE(20) // attributeStart relative to +16
            elemChunks.writeShortLE(20) // attributeSize
            elemChunks.writeShortLE(attrs.size) // attributeCount
            elemChunks.writeShortLE(0) // idIndex
            elemChunks.writeShortLE(0) // classIndex
            elemChunks.writeShortLE(0) // styleIndex
            for ((aName, aVal) in attrs) {
                val valIdx = strings.indexOf(aVal)
                elemChunks.writeIntLE(-1) // ns
                elemChunks.writeIntLE(strings.indexOf(aName)) // name
                elemChunks.writeIntLE(valIdx) // rawValue
                elemChunks.writeIntLE((0x03 shl 24) or 0x0008) // TYPE_STRING in byte 3, size 8
                elemChunks.writeIntLE(valIdx) // data
            }
        }
        val elemBytes = elemChunks.toByteArray()

        val totalSize = 8 + spBytes.size + elemBytes.size
        return ByteArrayOutputStream(totalSize).apply {
            writeShortLE(0x0003) // CHUNK_AXML_FILE
            writeShortLE(8)
            writeIntLE(totalSize)
            write(spBytes)
            write(elemBytes)
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeShortLE(v: Int) {
        write(v and 0xFF)
        write((v ushr 8) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeIntLE(v: Int) {
        write(v and 0xFF)
        write((v ushr 8) and 0xFF)
        write((v ushr 16) and 0xFF)
        write((v ushr 24) and 0xFF)
    }
}
