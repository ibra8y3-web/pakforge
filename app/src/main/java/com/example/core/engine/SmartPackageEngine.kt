package com.example.core.engine

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.example.core.signing.ApkV2Signer
import com.example.core.telemetry.DeviceTelemetryProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.ln
import kotlin.math.max

class SmartPackageEngine(
    private val context: Context,
    private val telemetryProvider: DeviceTelemetryProvider,
    private val apkV2Signer: ApkV2Signer = ApkV2Signer(context)
) {

    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        var bytesWritten: Long = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            bytesWritten++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            bytesWritten += len
        }
    }

    fun devicePrimaryAbi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

    suspend fun loadInstalledApps(): List<InstalledAppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        val seenPackages = HashSet<String>()
        resolveInfos.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (!seenPackages.add(pkg)) return@mapNotNull null
            val appInfo = info.activityInfo.applicationInfo ?: return@mapNotNull null
            val sourceDir = appInfo.sourceDir ?: return@mapNotNull null
            val apkFile = File(sourceDir)
            if (!apkFile.exists() || !apkFile.canRead()) return@mapNotNull null

            val pkgInfo = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
            } catch (_: Exception) {
                null
            }

            val splits = appInfo.splitSourceDirs?.filter { File(it).canRead() }.orEmpty()
            val totalBundleSize = apkFile.length() + splits.sumOf { File(it).length() }
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            InstalledAppItem(
                appName = info.loadLabel(pm).toString(),
                packageName = pkg,
                versionName = pkgInfo?.versionName ?: "1.0",
                apkPath = sourceDir,
                splitApkPaths = splits,
                apkSizeBytes = totalBundleSize,
                isSystemApp = isSystem,
                icon = runCatching { info.loadIcon(pm) }.getOrNull()
            )
        }.sortedWith(
            compareByDescending<InstalledAppItem> { it.packageName == context.packageName }
                .thenBy { it.appName.lowercase(Locale.US) }
        )
    }

    suspend fun stageUriToCache(uri: Uri): Pair<File, String> = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri) ?: "package_${System.currentTimeMillis()}.apk"
        val workDir = File(context.cacheDir, "pakforge_work").apply { mkdirs() }
        workDir.listFiles()?.forEach { old ->
            if (System.currentTimeMillis() - old.lastModified() > 3600_000L) {
                old.delete()
            }
        }
        val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val stagedFile = File(workDir, "staged_${System.currentTimeMillis()}_$safeName")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(stagedFile).use { output ->
                input.copyTo(output, bufferSize = 32 * 1024)
            }
        } ?: throw IllegalStateException("Cannot open selected package URI")

        stagedFile to displayName
    }

    suspend fun inspectPackage(
        file: File,
        displayName: String,
        config: CompressionConfig,
        splitPaths: List<String> = emptyList(),
        shizukuReady: Boolean = false
    ): PackageInspectionResult = withContext(Dispatchers.IO) {
        require(file.exists() && file.canRead()) { "Package file is not accessible" }

        val ext = displayName.substringAfterLast('.', "apk").lowercase(Locale.US)
        val pm = context.packageManager
        val archiveInfo = if (ext == "apk") {
            runCatching {
                val baseFlags = PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageArchiveInfo(
                        file.absolutePath,
                        PackageManager.PackageInfoFlags.of((baseFlags or PackageManager.GET_SIGNING_CERTIFICATES).toLong())
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    @Suppress("DEPRECATION")
                    pm.getPackageArchiveInfo(file.absolutePath, baseFlags or PackageManager.GET_SIGNING_CERTIFICATES)
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageArchiveInfo(file.absolutePath, baseFlags or PackageManager.GET_SIGNATURES)
                }
            }.getOrNull()
        } else null

        archiveInfo?.applicationInfo?.apply {
            sourceDir = file.absolutePath
            publicSourceDir = file.absolutePath
        }

        val resolvedLabel = archiveInfo?.applicationInfo?.loadLabel(pm)?.toString()
            ?.takeIf { it.isNotBlank() } ?: displayName
        val appIcon = archiveInfo?.applicationInfo?.let { runCatching { it.loadIcon(pm) }.getOrNull() }
        val securityAudit = archiveInfo?.let { extractSecurityAudit(it) }

        val entries = ArrayList<EntryAnalysis>()
        val abiSet = LinkedHashSet<String>()
        val manifestRisks = LinkedHashSet<String>()
        val localeSet = LinkedHashSet<String>()
        var totalRaw = 0L
        var weightedEntropySum = 0.0
        var hasArsc = false
        var hasUncompressedSo = false
        var debugCount = 0

        ZipFile(file).use { zip ->
            if (ext == "apk") {
                zip.getEntry("AndroidManifest.xml")?.let { mfEntry ->
                    val rawMf = zip.getInputStream(mfEntry).use { it.readBytes() }
                    manifestRisks.addAll(AxmlManifestPatcher.detectManifestRuntimeRisks(rawMf))
                }
                zip.getEntry("resources.arsc")?.let { arscEntry ->
                    val rawArsc = zip.getInputStream(arscEntry).use { it.readBytes() }
                    localeSet.addAll(ArscLocalePruner.extractAvailableLocales(rawArsc))
                }
            }
            val enumEntries = zip.entries()
            val sampleBuf = ByteArray(16 * 1024)
            while (enumEntries.hasMoreElements()) {
                coroutineContext.ensureActive()
                val ze = enumEntries.nextElement()
                if (ze.isDirectory) continue

                val path = ze.name
                val rawSize = ze.size.coerceAtLeast(0L)
                val compSize = ze.compressedSize.coerceAtLeast(0L)
                val isStored = ze.method == ZipEntry.STORED
                val crc = ze.crc

                val entropy = if (rawSize == 0L) {
                    0.0
                } else {
                    zip.getInputStream(ze).use { stream ->
                        computeShannonEntropy(stream, sampleBuf)
                    }
                }

                val category = classifyEntry(path)
                val abiName = extractAbiName(path)
                if (abiName != null) abiSet.add(abiName)
                if (category == PackageCategory.RESOURCES_ARSC) hasArsc = true
                if (category == PackageCategory.NATIVE_LIBS && isStored) hasUncompressedSo = true
                if (isSafeDebugArtifact(path)) debugCount++

                val strategy = decideStrategy(path, category, abiName, isStored, entropy, config)
                val predicted = estimateOutputBytes(rawSize, compSize, isStored, entropy, strategy)

                totalRaw += rawSize
                weightedEntropySum += entropy * rawSize.coerceAtLeast(1L)

                entries.add(
                    EntryAnalysis(
                        path = path,
                        category = category,
                        abiName = abiName,
                        rawBytes = rawSize,
                        sourceCompressedBytes = compSize,
                        sourceStored = isStored,
                        crc32 = crc,
                        shannonEntropy = entropy,
                        strategy = strategy,
                        predictedOutputBytes = predicted
                    )
                )
            }
        }

        val avgEntropy = if (totalRaw > 0L) (weightedEntropySum / totalRaw).coerceIn(0.0, 8.0) else 0.0
        val breakdown = entries
            .groupBy { it.category }
            .map { (cat, list) ->
                CategoryBreakdown(
                    category = cat,
                    rawBytes = list.sumOf { it.rawBytes },
                    archiveBytes = list.sumOf { it.sourceCompressedBytes },
                    entryCount = list.size
                )
            }
            .sortedByDescending { it.rawBytes }

        val duplicateGroups = entries
            .filter { it.rawBytes > 64L && it.crc32 > 0L }
            .groupBy { it.rawBytes to it.crc32 }
            .values
            .filter { it.size > 1 }
            .map { group ->
                DuplicateAssetGroup(
                    crc32 = group.first().crc32,
                    fileSizeBytes = group.first().rawBytes,
                    paths = group.map { it.path }
                )
            }
            .sortedByDescending { it.wastedBytes }

        val validSplits = splitPaths.filter { File(it).canRead() }
        var splitArchiveBytes = 0L
        for (splitPath in validSplits) {
            val sf = File(splitPath)
            splitArchiveBytes += sf.length()
            runCatching {
                ZipFile(sf).use { sz ->
                    val se = sz.entries()
                    while (se.hasMoreElements()) {
                        val ze = se.nextElement()
                        if (!ze.isDirectory) {
                            extractAbiName(ze.name)?.let { abiSet.add(it) }
                        }
                    }
                    sz.getEntry("resources.arsc")?.let { sa ->
                        val rawSplitArsc = sz.getInputStream(sa).use { it.readBytes() }
                        localeSet.addAll(ArscLocalePruner.extractAvailableLocales(rawSplitArsc))
                    }
                }
            }
        }

        val hasSharedUid = manifestRisks.any { it.startsWith("SHARED_USER_ID:") }
        val requiredStaticLibs = manifestRisks
            .filter { it.startsWith("STATIC_SHARED_LIB:") }
            .map { it.substringAfter(':') }
        val discoveredStaticLibs = if (ext == "apk" && requiredStaticLibs.isNotEmpty()) {
            StaticLibraryInspector.findInstalledStaticLibraries(context, requiredStaticLibs)
        } else emptyList()

        val installedCheck = archiveInfo?.packageName?.let { pkgId ->
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkgId, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkgId, PackageManager.GET_SIGNING_CERTIFICATES)
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkgId, PackageManager.GET_SIGNATURES)
                }
            }.getOrNull()
        }
        val isSysApp = (installedCheck?.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) ?: 0) != 0
        val localCert = if (installedCheck != null) runCatching { apkV2Signer.getLocalCertSha256() }.getOrNull() else null
        val installedCert = installedCheck?.let { extractSecurityAudit(it).signingCertSha256 }
        val isSigConflict = installedCheck != null && (localCert == null || installedCert == null || localCert != installedCert)

        val totalArchiveAll = file.length() + splitArchiveBytes
        val predictedOutputBytes = entries.sumOf { it.predictedOutputBytes } + (entries.size * 76L) + (splitArchiveBytes * 0.85).toLong()
        val safetyScore = computeSafetyScore(hasArsc, hasUncompressedSo, config)
        val versionCode = archiveInfo?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong()
        }

        val baseInspection = PackageInspectionResult(
            workingFilePath = file.absolutePath,
            displayName = resolvedLabel,
            fileExtension = ext,
            packageId = archiveInfo?.packageName,
            versionName = archiveInfo?.versionName,
            versionCode = versionCode,
            minSdk = archiveInfo?.applicationInfo?.minSdkVersion,
            targetSdk = archiveInfo?.applicationInfo?.targetSdkVersion,
            appIcon = appIcon,
            totalArchiveBytes = totalArchiveAll,
            totalRawBytes = totalRaw,
            predictedOutputBytes = predictedOutputBytes.coerceAtLeast(0L),
            entryCount = entries.size,
            averageEntropy = avgEntropy,
            hasResourcesArsc = hasArsc,
            hasUncompressedNativeLibs = hasUncompressedSo,
            detectedAbis = abiSet.sorted(),
            debugArtifactsCount = debugCount,
            safetyScore = safetyScore,
            duplicateGroups = duplicateGroups,
            securityAudit = securityAudit,
            breakdown = breakdown,
            entries = entries.sortedByDescending { it.rawBytes },
            splitSourcePaths = validSplits,
            manifestRuntimeRisks = manifestRisks.toList(),
            detectedLocales = localeSet.sorted(),
            hasSharedUserId = hasSharedUid,
            staticLibraries = discoveredStaticLibs
        )

        val compat = if (ext == "apk") {
            CompatibilityAnalyzer.analyze(
                inspection = baseInspection,
                isInstalledWithDifferentSig = isSigConflict,
                isSystemApp = isSysApp,
                shizukuReady = shizukuReady
            )
        } else null

        baseInspection.copy(compatibilityReport = compat)
    }

    fun recalculatePrediction(
        inspection: PackageInspectionResult,
        config: CompressionConfig
    ): PackageInspectionResult {
        val updatedEntries = inspection.entries.map { entry ->
            val newStrategy = decideStrategy(
                path = entry.path,
                category = entry.category,
                abiName = entry.abiName,
                sourceStored = entry.sourceStored,
                entropy = entry.shannonEntropy,
                config = config
            )
            val newPredicted = estimateOutputBytes(
                rawBytes = entry.rawBytes,
                sourceCompBytes = entry.sourceCompressedBytes,
                sourceStored = entry.sourceStored,
                entropy = entry.shannonEntropy,
                strategy = newStrategy
            )
            entry.copy(strategy = newStrategy, predictedOutputBytes = newPredicted)
        }

        val activeCount = updatedEntries.count {
            it.strategy != EntryStrategy.PRUNE_SAFE_DEBUG && it.strategy != EntryStrategy.STRIP_UNUSED_ABI
        }
        val predictedTotal = updatedEntries.sumOf { it.predictedOutputBytes } + (activeCount * 76L)

        return inspection.copy(
            entries = updatedEntries,
            predictedOutputBytes = predictedTotal,
            safetyScore = computeSafetyScore(
                inspection.hasResourcesArsc,
                inspection.hasUncompressedNativeLibs,
                config
            )
        )
    }

    suspend fun compressPackage(
        inspection: PackageInspectionResult,
        config: CompressionConfig,
        onProgress: (CompressionProgress) -> Unit
    ): CompressionReport = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val inputFile = File(inspection.workingFilePath)
        require(inputFile.exists()) { "Source package file no longer exists" }

        val telemetry = telemetryProvider.snapshot()
        val bufferSize = when {
            config.profile == CompressionProfile.LOW_RESOURCE_ECO -> 8 * 1024
            config.adaptiveRamThrottle -> telemetry.recommendedBufferKb * 1024
            else -> 64 * 1024
        }
        val activeBufferKb = bufferSize / 1024

        val outputDir = File(context.cacheDir, "pakforge_output").apply { mkdirs() }
        outputDir.listFiles()?.forEach { it.delete() }

        val baseName = inspection.displayName
            .substringBeforeLast('.')
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(40)
            .ifBlank { "package" }
        val ext = inspection.fileExtension.ifBlank { "apk" }
        val outputFileName = "${baseName}_pakforge.$ext"
        val unsignedFile = File(outputDir, "unsigned_$outputFileName")
        val outputFile = File(outputDir, outputFileName)

        var bypassedCount = 0
        var prunedCount = 0
        var strippedAbiCount = 0
        var localesPrunedCount = 0
        var imagesOptimizedCount = 0
        var arscAligned = false
        var peakHeapMb = telemetry.heapUsedMb
        var processedEntries = 0
        var bytesProcessed = 0L

        val strategyByPath = inspection.entries.associateBy({ it.path }, { it })
        val isApk = ext.equals("apk", ignoreCase = true)
        val sanitizedSuffix = config.cloneSuffix
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_]"), "")
            .ifBlank { "pkf" }
            .let { if (it.first().isDigit()) "p$it" else it }
        val targetPackageId = if (isApk && config.installIdentityMode == InstallIdentityMode.CLONE_NEW_PACKAGE && inspection.packageId != null) {
            "${inspection.packageId}.$sanitizedSuffix"
        } else {
            inspection.packageId
        }
        val certDigestNoColons = if (isApk && config.patchStaticLibCertDigest) {
            runCatching { apkV2Signer.getLocalCertSha256().replace(":", "").uppercase(Locale.US) }.getOrNull()
        } else null
        val shouldPatchClone = isApk &&
            config.installIdentityMode == InstallIdentityMode.CLONE_NEW_PACKAGE &&
            targetPackageId != null &&
            targetPackageId != inspection.packageId
        val customCloneLabel = if (shouldPatchClone) {
            config.customCloneLabel.trim().ifBlank { "${inspection.displayName} (PF)" }
        } else null

        onProgress(
            CompressionProgress(
                phase = CompressionPhase.INSPECTING,
                currentEntry = inspection.displayName,
                processedEntries = 0,
                totalEntries = inspection.entryCount,
                bytesProcessed = 0L,
                totalBytes = inspection.totalRawBytes,
                activeBufferKb = activeBufferKb,
                elapsedMs = 0L
            )
        )

        val buffer = ByteArray(bufferSize)
        val useUniversalMerge = isApk && config.mergeSplitsIntoUniversal && inspection.splitSourcePaths.isNotEmpty()
        var splitOriginalTotal = 0L
        val keptSplits = ArrayList<File>()
        if (isApk && inspection.splitSourcePaths.isNotEmpty()) {
            for (splitPath in inspection.splitSourcePaths) {
                val sf = File(splitPath)
                if (!sf.exists() || !sf.canRead()) continue
                splitOriginalTotal += sf.length()
                if (shouldSkipSplitByAbi(sf, config.allowedAbis)) {
                    strippedAbiCount++
                    continue
                }
                if (config.pruneUnusedLocales && config.keptLocales.isNotEmpty() &&
                    shouldSkipSplitByLocale(sf, config.keptLocales)
                ) {
                    localesPrunedCount++
                    continue
                }
                keptSplits.add(sf)
            }
        }

        if (useUniversalMerge) {
            onProgress(
                CompressionProgress(
                    phase = CompressionPhase.COMPRESSING,
                    currentEntry = "Merging ${keptSplits.size + 1} APKs → Universal APK…",
                    processedEntries = 1,
                    totalEntries = inspection.entryCount.coerceAtLeast(1),
                    bytesProcessed = 0L,
                    totalBytes = inspection.totalRawBytes,
                    activeBufferKb = activeBufferKb,
                    elapsedMs = System.currentTimeMillis() - startTime
                )
            )
            val mergeStats = UniversalSplitMerger.mergeIntoUnsignedApk(
                baseFile = inputFile,
                keptSplitFiles = keptSplits,
                outputUnsignedFile = unsignedFile,
                origPackageId = inspection.packageId,
                targetPackageId = targetPackageId,
                baseDisplayName = inspection.displayName,
                certDigestNoColons = certDigestNoColons,
                config = config,
                bufferSize = bufferSize
            )
            arscAligned = mergeStats.arscAligned
            bypassedCount = mergeStats.bypassedCount
            prunedCount = mergeStats.prunedCount
            strippedAbiCount += mergeStats.strippedAbiCount
            localesPrunedCount += mergeStats.localesPrunedCount
            imagesOptimizedCount = mergeStats.imagesOptimizedCount
            processedEntries = mergeStats.processedEntries
        } else {
            var baseOrphanResPaths: Set<String> = emptySet()
            var prePatchedBaseArsc: ByteArray? = null
            if (isApk) {
                ZipFile(inputFile).use { preloadZip ->
                    preloadZip.getEntry("resources.arsc")?.let { arscEntry ->
                        var arscBytes = preloadZip.getInputStream(arscEntry).use { it.readBytes() }
                        var modified = false
                        if (config.pruneUnusedLocales && config.keptLocales.isNotEmpty()) {
                            val pruned = ArscLocalePruner.pruneUnusedLocales(arscBytes, config.keptLocales)
                            if (pruned.prunedConfigsCount > 0) {
                                arscBytes = pruned.patchedArsc
                                localesPrunedCount += pruned.prunedConfigsCount
                                baseOrphanResPaths = pruned.safeOrphanResPaths
                                modified = true
                            }
                        }
                        if (shouldPatchClone && inspection.packageId != null) {
                            arscBytes = ArscPackagePatcher.patchPackageName(
                                rawArsc = arscBytes,
                                oldPackageName = inspection.packageId,
                                newPackageName = targetPackageId
                            )
                            modified = true
                        }
                        if (modified) prePatchedBaseArsc = arscBytes
                    }
                }
            }

            ZipFile(inputFile).use { sourceZip ->
                val countingOut = CountingOutputStream(BufferedOutputStream(FileOutputStream(unsignedFile), bufferSize))
                TunedZipOutputStream(countingOut).use { zos ->
                    val entriesEnum = sourceZip.entries()
                    while (entriesEnum.hasMoreElements()) {
                        coroutineContext.ensureActive()
                        val srcEntry = entriesEnum.nextElement()
                        if (srcEntry.isDirectory) continue

                        val path = srcEntry.name
                        val analysis = strategyByPath[path]
                        val category = analysis?.category ?: classifyEntry(path)
                        val abiName = analysis?.abiName ?: extractAbiName(path)
                        val entropy = analysis?.shannonEntropy ?: 6.0
                        val strategy = analysis?.strategy ?: decideStrategy(
                            path = path,
                            category = category,
                            abiName = abiName,
                            sourceStored = srcEntry.method == ZipEntry.STORED,
                            entropy = entropy,
                            config = config
                        )

                        if (isApk && isOldV1SignatureArtifact(path)) {
                            prunedCount++
                            processedEntries++
                            continue
                        }

                        if (strategy == EntryStrategy.PRUNE_SAFE_DEBUG || path in baseOrphanResPaths) {
                            prunedCount++
                            processedEntries++
                            continue
                        }

                        if (strategy == EntryStrategy.STRIP_UNUSED_ABI) {
                            strippedAbiCount++
                            processedEntries++
                            continue
                        }

                        val outEntry = ZipEntry(path)
                        outEntry.time = srcEntry.time

                        val needsManifestPatch = isApk && path == "AndroidManifest.xml" &&
                            (shouldPatchClone || config.stripSharedUserId || certDigestNoColons != null)

                        if (needsManifestPatch) {
                            val rawManifest = sourceZip.getInputStream(srcEntry).use { it.readBytes() }
                            val patchedManifest = runCatching {
                                AxmlManifestPatcher.patchManifestAdvanced(
                                    rawAxml = rawManifest,
                                    options = ManifestPatchOptions(
                                        newPackageName = if (shouldPatchClone) targetPackageId else null,
                                        customAppLabel = customCloneLabel,
                                        stripSharedUserId = config.stripSharedUserId,
                                        newStaticLibCertDigestHex = certDigestNoColons,
                                        clearSplitRequiredFlags = false,
                                        cloneAffinitySuffix = config.cloneSuffix
                                    )
                                )
                            }.getOrDefault(rawManifest)

                            zos.setDeflateParams(Deflater.BEST_COMPRESSION, Deflater.DEFAULT_STRATEGY)
                            outEntry.method = ZipEntry.DEFLATED
                            zos.putNextEntry(outEntry)
                            zos.write(patchedManifest)
                            zos.closeEntry()
                            bytesProcessed += rawManifest.size
                            processedEntries++
                            continue
                        }

                        val patchedArscCandidate = prePatchedBaseArsc
                        if (path == "resources.arsc" && patchedArscCandidate != null) {
                            val patchedArsc = patchedArscCandidate
                            val crc = CRC32().apply { update(patchedArsc) }.value
                            outEntry.method = ZipEntry.STORED
                            outEntry.size = patchedArsc.size.toLong()
                            outEntry.compressedSize = patchedArsc.size.toLong()
                            outEntry.crc = crc
                            if (config.enforceResourcesArscAlign) {
                                zos.flush()
                                val nameBytesLen = path.toByteArray(StandardCharsets.UTF_8).size
                                val baseDataOffset = countingOut.bytesWritten + 30L + nameBytesLen
                                val rem = (baseDataOffset % 4).toInt()
                                val pad = if (rem == 0) 0 else 4 - rem
                                if (pad > 0) outEntry.extra = ByteArray(pad)
                                arscAligned = ((baseDataOffset + pad) % 4L) == 0L
                            }
                            zos.putNextEntry(outEntry)
                            zos.write(patchedArsc)
                            zos.closeEntry()
                            bytesProcessed += patchedArsc.size
                            processedEntries++
                            continue
                        }

                        // Check if entry needs byte-level image optimization or clone launcher icon badging
                        val wantsByteTransform = (config.optimizeLargeImages && AssetImageOptimizer.isOptimizableImagePath(path)) ||
                            (shouldPatchClone && config.tintCloneIcon && CloneIconStyler.isLauncherPng(path))

                        if (wantsByteTransform) {
                            var payload = sourceZip.getInputStream(srcEntry).use { it.readBytes() }
                            if (config.optimizeLargeImages && AssetImageOptimizer.isOptimizableImagePath(path)) {
                                val opt = AssetImageOptimizer.optimizeIfBeneficial(path, payload)
                                if (opt !== payload) {
                                    payload = opt
                                    imagesOptimizedCount++
                                }
                            }
                            if (shouldPatchClone && config.tintCloneIcon && CloneIconStyler.isLauncherPng(path)) {
                                payload = CloneIconStyler.badgeIfLauncherPng(path, payload)
                            }
                            val crc = CRC32().apply { update(payload) }.value
                            outEntry.method = ZipEntry.STORED
                            outEntry.size = payload.size.toLong()
                            outEntry.compressedSize = payload.size.toLong()
                            outEntry.crc = crc
                            zos.flush()
                            val nameBytesLen = path.toByteArray(StandardCharsets.UTF_8).size
                            val baseDataOffset = countingOut.bytesWritten + 30L + nameBytesLen
                            val rem = (baseDataOffset % 4).toInt()
                            val pad = if (rem == 0) 0 else 4 - rem
                            if (pad > 0) outEntry.extra = ByteArray(pad)
                            zos.putNextEntry(outEntry)
                            zos.write(payload)
                            zos.closeEntry()
                            if (strategy == EntryStrategy.BYPASS_HIGH_ENTROPY) bypassedCount++
                            bytesProcessed += payload.size
                            processedEntries++
                            continue
                        }

                        val shouldStoreUncompressed = when (strategy) {
                            EntryStrategy.PRESERVE_STORED_4ALIGN,
                            EntryStrategy.PRESERVE_STORED_4KB,
                            EntryStrategy.BYPASS_HIGH_ENTROPY -> true
                            else -> false
                        }

                        if (strategy == EntryStrategy.BYPASS_HIGH_ENTROPY) {
                            bypassedCount++
                        }

                        if (shouldStoreUncompressed) {
                            val (exactSize, exactCrc) = if (srcEntry.size >= 0L && srcEntry.crc >= 0L) {
                                srcEntry.size to srcEntry.crc
                            } else {
                                computeExactSizeAndCrc(sourceZip, srcEntry, buffer)
                            }

                            outEntry.method = ZipEntry.STORED
                            outEntry.size = exactSize
                            outEntry.compressedSize = exactSize
                            outEntry.crc = exactCrc

                            val alignment = when (strategy) {
                                EntryStrategy.PRESERVE_STORED_4KB -> 16384
                                EntryStrategy.PRESERVE_STORED_4ALIGN -> 4
                                else -> if (config.enforceResourcesArscAlign) 4 else 1
                            }
                            if (alignment > 1) {
                                zos.flush()
                                val nameBytesLen = path.toByteArray(StandardCharsets.UTF_8).size
                                val baseDataOffset = countingOut.bytesWritten + 30L + nameBytesLen
                                val rem = (baseDataOffset % alignment).toInt()
                                val pad = if (rem == 0) 0 else alignment - rem
                                if (pad > 0) outEntry.extra = ByteArray(pad)
                                if (category == PackageCategory.RESOURCES_ARSC) {
                                    arscAligned = ((baseDataOffset + pad) % 4L) == 0L
                                }
                            }

                            zos.putNextEntry(outEntry)
                            sourceZip.getInputStream(srcEntry).use { input ->
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    zos.write(buffer, 0, read)
                                    bytesProcessed += read
                                }
                            }
                            zos.closeEntry()
                        } else {
                            val deflateLevel = when (strategy) {
                                EntryStrategy.DEFLATE_MAX -> Deflater.BEST_COMPRESSION
                                EntryStrategy.DEFLATE_BALANCED -> if (config.profile == CompressionProfile.LOW_RESOURCE_ECO) 4 else Deflater.DEFAULT_COMPRESSION
                                else -> Deflater.DEFAULT_COMPRESSION
                            }
                            if (config.profile == CompressionProfile.ULTRA_SHRINK &&
                                (category == PackageCategory.DEX_BYTECODE || category == PackageCategory.MANIFEST_XML)
                            ) {
                                val rawPayload = sourceZip.getInputStream(srcEntry).use { it.readBytes() }
                                val bestStrategy = TunedZipOutputStream.pickOptimalStrategy(rawPayload, deflateLevel)
                                zos.setDeflateParams(deflateLevel, bestStrategy)
                                outEntry.method = ZipEntry.DEFLATED
                                zos.putNextEntry(outEntry)
                                zos.write(rawPayload)
                                zos.closeEntry()
                                bytesProcessed += rawPayload.size
                            } else {
                                zos.setDeflateParams(deflateLevel, Deflater.DEFAULT_STRATEGY)
                                outEntry.method = ZipEntry.DEFLATED
                                zos.putNextEntry(outEntry)
                                sourceZip.getInputStream(srcEntry).use { input ->
                                    while (true) {
                                        val read = input.read(buffer)
                                        if (read <= 0) break
                                        zos.write(buffer, 0, read)
                                        bytesProcessed += read
                                    }
                                }
                                zos.closeEntry()
                            }
                        }

                        processedEntries++
                        val currentHeap = ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024L * 1024L)).toInt()
                        peakHeapMb = max(peakHeapMb, currentHeap)

                        if (processedEntries % 12 == 0 || processedEntries == inspection.entryCount) {
                            onProgress(
                                CompressionProgress(
                                    phase = CompressionPhase.COMPRESSING,
                                    currentEntry = path,
                                    processedEntries = processedEntries,
                                    totalEntries = inspection.entryCount,
                                    bytesProcessed = bytesProcessed,
                                    totalBytes = inspection.totalRawBytes,
                                    activeBufferKb = activeBufferKb,
                                    elapsedMs = System.currentTimeMillis() - startTime
                                )
                            )
                        }
                    }
                }
            }
        }

        val signedV2CertSha256 = if (isApk) {
            val certFingerprint = apkV2Signer.signApkV2(unsignedFile, outputFile)
            unsignedFile.delete()
            certFingerprint
        } else {
            unsignedFile.renameTo(outputFile)
            null
        }

        onProgress(
            CompressionProgress(
                phase = CompressionPhase.VERIFYING,
                currentEntry = outputFileName,
                processedEntries = inspection.entryCount,
                totalEntries = inspection.entryCount,
                bytesProcessed = inspection.totalRawBytes,
                totalBytes = inspection.totalRawBytes,
                activeBufferKb = activeBufferKb,
                elapsedMs = System.currentTimeMillis() - startTime
            )
        )

        var verifiedCount = 0
        var corruptCount = 0
        ZipFile(outputFile).use { verifyZip ->
            val enumVerify = verifyZip.entries()
            while (enumVerify.hasMoreElements()) {
                coroutineContext.ensureActive()
                val ze = enumVerify.nextElement()
                if (ze.isDirectory) continue
                try {
                    val crc = CRC32()
                    verifyZip.getInputStream(ze).use { stream ->
                        while (true) {
                            val read = stream.read(buffer)
                            if (read <= 0) break
                            crc.update(buffer, 0, read)
                        }
                    }
                    if (ze.crc >= 0L && crc.value != ze.crc) {
                        corruptCount++
                    } else {
                        verifiedCount++
                    }
                } catch (_: Exception) {
                    corruptCount++
                }
            }
        }

        val splitOutputs = ArrayList<String>()
        var splitCompressedTotal = 0L

        if (!useUniversalMerge && isApk && keptSplits.isNotEmpty()) {
            keptSplits.forEachIndexed { idx, splitSrcFile ->
                val splitOut = compressSingleSplitApk(
                    splitSource = splitSrcFile,
                    outputDir = outputDir,
                    splitIndex = idx + 1,
                    config = config,
                    oldPackageId = inspection.packageId,
                    newPackageId = if (shouldPatchClone) targetPackageId else null,
                    buffer = buffer
                )
                splitOutputs.add(splitOut.absolutePath)
                splitCompressedTotal += splitOut.length()
            }
        }

        // Companion static library re-signing (when app declares <uses-static-library> & files exist on device)
        val companionBundles = ArrayList<List<String>>()
        if (isApk && config.bundleCompanionStaticLibs && inspection.staticLibraries.isNotEmpty()) {
            inspection.staticLibraries.forEachIndexed { libIdx, lib ->
                if (lib.apkPaths.isNotEmpty()) {
                    val bundleOut = ArrayList<String>()
                    lib.apkPaths.forEachIndexed { apkIdx, libPath ->
                        val srcLib = File(libPath)
                        if (srcLib.exists() && srcLib.canRead()) {
                            val resigned = compressSingleSplitApk(
                                splitSource = srcLib,
                                outputDir = outputDir,
                                splitIndex = apkIdx + 1,
                                config = CompressionConfig(profile = CompressionProfile.ZERO_RISK_BALANCED),
                                oldPackageId = null,
                                newPackageId = null,
                                buffer = buffer,
                                filePrefix = "companion_lib_${libIdx + 1}"
                            )
                            bundleOut.add(resigned.absolutePath)
                        }
                    }
                    if (bundleOut.isNotEmpty()) companionBundles.add(bundleOut)
                }
            }
        }

        val sha256Hex = computeSha256(outputFile, buffer)
        val originalSize = inputFile.length() + splitOriginalTotal
        val finalSize = outputFile.length() + splitCompressedTotal
        val savedBytes = (originalSize - finalSize).coerceAtLeast(0L)
        val savedPercent = if (originalSize > 0L) (savedBytes.toDouble() / originalSize.toDouble()) * 100.0 else 0.0

        CompressionReport(
            sourceDisplayName = inspection.displayName,
            packageId = inspection.packageId,
            effectivePackageId = targetPackageId,
            originalBytes = originalSize,
            compressedBytes = finalSize,
            savedBytes = savedBytes,
            savedPercent = savedPercent,
            elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L),
            peakHeapMb = peakHeapMb,
            verifiedEntriesCount = verifiedCount,
            corruptEntriesCount = corruptCount,
            resourcesArscAligned = if (inspection.hasResourcesArsc) arscAligned else true,
            bypassedMediaCount = bypassedCount,
            prunedArtifactsCount = prunedCount,
            strippedAbiEntriesCount = strippedAbiCount,
            averageEntropy = inspection.averageEntropy,
            sha256Hex = sha256Hex,
            signedV2CertSha256 = signedV2CertSha256,
            outputFilePath = outputFile.absolutePath,
            outputFileName = outputFileName,
            splitOutputPaths = splitOutputs,
            companionLibraryOutputs = companionBundles,
            manifestRuntimeRisks = inspection.manifestRuntimeRisks,
            mergedUniversalApk = useUniversalMerge,
            localesPrunedCount = localesPrunedCount,
            imagesOptimizedCount = imagesOptimizedCount,
            strippedSharedUserId = config.stripSharedUserId && inspection.hasSharedUserId,
            isApkArchive = isApk,
            reSignRequiredWarning = false
        )
    }

    private fun shouldSkipSplitByAbi(splitFile: File, allowedAbis: Set<String>): Boolean {
        if (allowedAbis.isEmpty()) return false
        val splitAbis = HashSet<String>()
        runCatching {
            ZipFile(splitFile).use { zip ->
                val en = zip.entries()
                while (en.hasMoreElements()) {
                    val ze = en.nextElement()
                    if (!ze.isDirectory) {
                        extractAbiName(ze.name)?.let { splitAbis.add(it) }
                    }
                }
            }
        }
        return splitAbis.isNotEmpty() && splitAbis.none { it in allowedAbis }
    }

    private fun shouldSkipSplitByLocale(splitFile: File, keptLocales: Set<String>): Boolean {
        val nameLower = splitFile.name.lowercase(Locale.US)
        if (!nameLower.contains("config.")) return false
        val splitLocales = runCatching {
            ZipFile(splitFile).use { zip ->
                val arsc = zip.getEntry("resources.arsc") ?: return@use emptyList<String>()
                ArscLocalePruner.extractAvailableLocales(zip.getInputStream(arsc).use { it.readBytes() })
            }
        }.getOrDefault(emptyList())
        if (splitLocales.isEmpty()) return false
        val normalizedKept = keptLocales.map { it.lowercase(Locale.US).substringBefore('-').substringBefore('_') }.toSet()
        return splitLocales.none { it in normalizedKept }
    }

    private fun compressSingleSplitApk(
        splitSource: File,
        outputDir: File,
        splitIndex: Int,
        config: CompressionConfig,
        oldPackageId: String?,
        newPackageId: String?,
        buffer: ByteArray,
        filePrefix: String = "split"
    ): File {
        val tempUnsigned = File(outputDir, "unsigned_${filePrefix}_${splitIndex}_${System.currentTimeMillis()}.apk")
        val finalSplit = File(outputDir, "${filePrefix}_${splitIndex}_pakforge.apk")

        ZipFile(splitSource).use { sourceZip ->
            val countingOut = CountingOutputStream(BufferedOutputStream(FileOutputStream(tempUnsigned), buffer.size))
            ZipOutputStream(countingOut).use { zos ->
                val entriesEnum = sourceZip.entries()
                while (entriesEnum.hasMoreElements()) {
                    val srcEntry = entriesEnum.nextElement()
                    if (srcEntry.isDirectory) continue
                    val path = srcEntry.name
                    if (isOldV1SignatureArtifact(path)) continue
                    if (config.pruneDebugArtifacts && isSafeDebugArtifact(path)) continue

                    val outEntry = ZipEntry(path).apply { time = srcEntry.time }

                    if (path == "AndroidManifest.xml" && newPackageId != null) {
                        val rawManifest = sourceZip.getInputStream(srcEntry).use { it.readBytes() }
                        val patched = runCatching {
                            AxmlManifestPatcher.patchManifestPackage(
                                rawAxml = rawManifest,
                                newPackageName = newPackageId,
                                appendAppLabelSuffix = null
                            )
                        }.getOrDefault(rawManifest)
                        zos.setLevel(Deflater.BEST_COMPRESSION)
                        outEntry.method = ZipEntry.DEFLATED
                        zos.putNextEntry(outEntry)
                        zos.write(patched)
                        zos.closeEntry()
                        continue
                    }

                    if (path == "resources.arsc" && oldPackageId != null && newPackageId != null) {
                        val rawArsc = sourceZip.getInputStream(srcEntry).use { it.readBytes() }
                        val patchedArsc = ArscPackagePatcher.patchPackageName(rawArsc, oldPackageId, newPackageId)
                        val crc = CRC32().apply { update(patchedArsc) }.value
                        outEntry.method = ZipEntry.STORED
                        outEntry.size = patchedArsc.size.toLong()
                        outEntry.compressedSize = patchedArsc.size.toLong()
                        outEntry.crc = crc
                        if (config.enforceResourcesArscAlign) {
                            zos.flush()
                            val nameBytesLen = path.toByteArray(StandardCharsets.UTF_8).size
                            val baseDataOffset = countingOut.bytesWritten + 30L + nameBytesLen
                            val rem = (baseDataOffset % 4).toInt()
                            val pad = if (rem == 0) 0 else 4 - rem
                            if (pad > 0) outEntry.extra = ByteArray(pad)
                        }
                        zos.putNextEntry(outEntry)
                        zos.write(patchedArsc)
                        zos.closeEntry()
                        continue
                    }

                    val category = classifyEntry(path)
                    val isStoredRequired = (category == PackageCategory.RESOURCES_ARSC && config.enforceResourcesArscAlign) ||
                        (category == PackageCategory.NATIVE_LIBS && srcEntry.method == ZipEntry.STORED) ||
                        (config.smartEntropyBypass && isCompressedMediaExt(path.lowercase(Locale.US)))

                    if (isStoredRequired) {
                        val (exactSize, exactCrc) = if (srcEntry.size >= 0L && srcEntry.crc >= 0L) {
                            srcEntry.size to srcEntry.crc
                        } else {
                            computeExactSizeAndCrc(sourceZip, srcEntry, buffer)
                        }
                        outEntry.method = ZipEntry.STORED
                        outEntry.size = exactSize
                        outEntry.compressedSize = exactSize
                        outEntry.crc = exactCrc
                        val alignment = if (category == PackageCategory.NATIVE_LIBS) 16384 else 4
                        zos.flush()
                        val nameBytesLen = path.toByteArray(StandardCharsets.UTF_8).size
                        val baseDataOffset = countingOut.bytesWritten + 30L + nameBytesLen
                        val rem = (baseDataOffset % alignment).toInt()
                        val pad = if (rem == 0) 0 else alignment - rem
                        if (pad > 0) outEntry.extra = ByteArray(pad)
                        zos.putNextEntry(outEntry)
                        sourceZip.getInputStream(srcEntry).use { input ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                zos.write(buffer, 0, read)
                            }
                        }
                        zos.closeEntry()
                    } else {
                        zos.setLevel(if (config.profile == CompressionProfile.ULTRA_SHRINK) Deflater.BEST_COMPRESSION else Deflater.DEFAULT_COMPRESSION)
                        outEntry.method = ZipEntry.DEFLATED
                        zos.putNextEntry(outEntry)
                        sourceZip.getInputStream(srcEntry).use { input ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                zos.write(buffer, 0, read)
                            }
                        }
                        zos.closeEntry()
                    }
                }
            }
        }

        apkV2Signer.signApkV2(tempUnsigned, finalSplit)
        tempUnsigned.delete()
        return finalSplit
    }

    suspend fun repackApkAsClone(
        report: CompressionReport,
        cloneSuffix: String,
        customCloneLabel: String = "",
        stripSharedUserId: Boolean = true,
        tintCloneIcon: Boolean = true
    ): CompressionReport = withContext(Dispatchers.IO) {
        require(report.isApkArchive) { "Only APK packages can be cloned" }
        val currentFile = File(report.outputFilePath)
        require(currentFile.exists() && currentFile.canRead()) { "Compressed APK file not found" }

        val sanitizedSuffix = cloneSuffix
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_]"), "")
            .ifBlank { "pkf" }
            .let { if (it.first().isDigit()) "p$it" else it }

        val outputDir = File(context.cacheDir, "pakforge_output").apply { mkdirs() }
        val baseStem = report.outputFileName.substringBeforeLast('.')
        val tempUnsigned = File(outputDir, "repack_unsigned_${System.currentTimeMillis()}.apk")
        val finalName = "${baseStem}_clone_${sanitizedSuffix}.apk"
        val finalOutput = File(outputDir, finalName)
        val buffer = ByteArray(32 * 1024)

        var currentArscPkg: String? = report.effectivePackageId ?: report.packageId
        var targetPkgResult: String? = report.packageId?.let { "$it.$sanitizedSuffix" }
        val resolvedCustomLabel = customCloneLabel.trim().takeIf { it.isNotEmpty() }

        ZipFile(currentFile).use { sourceZip ->
            val detectedCurrentPkg = sourceZip.getEntry("AndroidManifest.xml")?.let { mf ->
                sourceZip.getInputStream(mf).use { AxmlManifestPatcher.readManifestPackage(it.readBytes()) }
            }
            if (detectedCurrentPkg != null) {
                currentArscPkg = detectedCurrentPkg
                if (targetPkgResult == null) {
                    targetPkgResult = "$detectedCurrentPkg.$sanitizedSuffix"
                }
            }

            val countingOut = CountingOutputStream(BufferedOutputStream(FileOutputStream(tempUnsigned), buffer.size))
            ZipOutputStream(countingOut).use { zos ->
                val entriesEnum = sourceZip.entries()
                while (entriesEnum.hasMoreElements()) {
                    coroutineContext.ensureActive()
                    val srcEntry = entriesEnum.nextElement()
                    if (srcEntry.isDirectory) continue

                    val path = srcEntry.name
                    if (isOldV1SignatureArtifact(path)) continue

                    val outEntry = ZipEntry(path)
                    outEntry.time = srcEntry.time

                    if (path == "AndroidManifest.xml") {
                        val rawManifest = sourceZip.getInputStream(srcEntry).use { it.readBytes() }
                        val finalNewPkg = targetPkgResult
                        val patched = if (finalNewPkg != null) {
                            runCatching {
                                AxmlManifestPatcher.patchManifestAdvanced(
                                    rawAxml = rawManifest,
                                    options = ManifestPatchOptions(
                                        newPackageName = finalNewPkg,
                                        customAppLabel = resolvedCustomLabel,
                                        appendAppLabelSuffix = if (resolvedCustomLabel == null) " (PF)" else null,
                                        stripSharedUserId = stripSharedUserId,
                                        cloneAffinitySuffix = sanitizedSuffix
                                    )
                                )
                            }.getOrDefault(rawManifest)
                        } else rawManifest

                        zos.setLevel(Deflater.BEST_COMPRESSION)
                        outEntry.method = ZipEntry.DEFLATED
                        zos.putNextEntry(outEntry)
                        zos.write(patched)
                        zos.closeEntry()
                        continue
                    }

                    if (path == "resources.arsc" && currentArscPkg != null && targetPkgResult != null) {
                        val rawArsc = sourceZip.getInputStream(srcEntry).use { it.readBytes() }
                        val patchedArsc = ArscPackagePatcher.patchPackageName(rawArsc, currentArscPkg, targetPkgResult)
                        val crc = CRC32().apply { update(patchedArsc) }.value
                        outEntry.method = ZipEntry.STORED
                        outEntry.size = patchedArsc.size.toLong()
                        outEntry.compressedSize = patchedArsc.size.toLong()
                        outEntry.crc = crc
                        zos.flush()
                        val nameBytesLen = path.toByteArray(StandardCharsets.UTF_8).size
                        val baseDataOffset = countingOut.bytesWritten + 30L + nameBytesLen
                        val rem = (baseDataOffset % 4).toInt()
                        val pad = if (rem == 0) 0 else 4 - rem
                        if (pad > 0) outEntry.extra = ByteArray(pad)
                        zos.putNextEntry(outEntry)
                        zos.write(patchedArsc)
                        zos.closeEntry()
                        continue
                    }

                    if (tintCloneIcon && CloneIconStyler.isLauncherPng(path)) {
                        val rawIcon = sourceZip.getInputStream(srcEntry).use { it.readBytes() }
                        val badgedIcon = CloneIconStyler.badgeIfLauncherPng(path, rawIcon)
                        val crc = CRC32().apply { update(badgedIcon) }.value
                        outEntry.method = ZipEntry.STORED
                        outEntry.size = badgedIcon.size.toLong()
                        outEntry.compressedSize = badgedIcon.size.toLong()
                        outEntry.crc = crc
                        zos.flush()
                        val nameBytesLen = path.toByteArray(StandardCharsets.UTF_8).size
                        val baseDataOffset = countingOut.bytesWritten + 30L + nameBytesLen
                        val rem = (baseDataOffset % 4).toInt()
                        val pad = if (rem == 0) 0 else 4 - rem
                        if (pad > 0) outEntry.extra = ByteArray(pad)
                        zos.putNextEntry(outEntry)
                        zos.write(badgedIcon)
                        zos.closeEntry()
                        continue
                    }

                    if (srcEntry.method == ZipEntry.STORED) {
                        val (exactSize, exactCrc) = if (srcEntry.size >= 0L && srcEntry.crc >= 0L) {
                            srcEntry.size to srcEntry.crc
                        } else {
                            computeExactSizeAndCrc(sourceZip, srcEntry, buffer)
                        }
                        outEntry.method = ZipEntry.STORED
                        outEntry.size = exactSize
                        outEntry.compressedSize = exactSize
                        outEntry.crc = exactCrc

                        val alignment = if (path.startsWith("lib/") && path.endsWith(".so")) 16384 else 4
                        zos.flush()
                        val nameBytesLen = path.toByteArray(StandardCharsets.UTF_8).size
                        val baseDataOffset = countingOut.bytesWritten + 30L + nameBytesLen
                        val rem = (baseDataOffset % alignment).toInt()
                        val pad = if (rem == 0) 0 else alignment - rem
                        if (pad > 0) outEntry.extra = ByteArray(pad)

                        zos.putNextEntry(outEntry)
                        sourceZip.getInputStream(srcEntry).use { input ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                zos.write(buffer, 0, read)
                            }
                        }
                        zos.closeEntry()
                    } else {
                        zos.setLevel(Deflater.DEFAULT_COMPRESSION)
                        outEntry.method = ZipEntry.DEFLATED
                        zos.putNextEntry(outEntry)
                        sourceZip.getInputStream(srcEntry).use { input ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                zos.write(buffer, 0, read)
                            }
                        }
                        zos.closeEntry()
                    }
                }
            }
        }

        val certFingerprint = apkV2Signer.signApkV2(tempUnsigned, finalOutput)
        tempUnsigned.delete()
        if (currentFile.absolutePath != finalOutput.absolutePath) {
            currentFile.delete()
        }

        // Also clone & re-sign any split APKs so base + splits stay matched
        val clonedSplits = ArrayList<String>()
        var clonedSplitBytes = 0L
        report.splitOutputPaths.forEachIndexed { idx, oldSplitPath ->
            val oldSplitFile = File(oldSplitPath)
            if (oldSplitFile.exists() && targetPkgResult != null) {
                val repackedSplit = compressSingleSplitApk(
                    splitSource = oldSplitFile,
                    outputDir = outputDir,
                    splitIndex = idx + 1,
                    config = CompressionConfig(profile = CompressionProfile.ZERO_RISK_BALANCED),
                    oldPackageId = currentArscPkg,
                    newPackageId = targetPkgResult,
                    buffer = buffer,
                    filePrefix = "split_clone_${sanitizedSuffix}"
                )
                if (oldSplitFile.absolutePath != repackedSplit.absolutePath) {
                    oldSplitFile.delete()
                }
                clonedSplits.add(repackedSplit.absolutePath)
                clonedSplitBytes += repackedSplit.length()
            }
        }

        val finalSize = finalOutput.length() + clonedSplitBytes
        val savedBytes = (report.originalBytes - finalSize).coerceAtLeast(0L)
        val savedPercent = if (report.originalBytes > 0L) {
            (savedBytes.toDouble() / report.originalBytes.toDouble()) * 100.0
        } else 0.0

        report.copy(
            effectivePackageId = targetPkgResult,
            compressedBytes = finalSize,
            savedBytes = savedBytes,
            savedPercent = savedPercent,
            sha256Hex = computeSha256(finalOutput, buffer),
            signedV2CertSha256 = certFingerprint,
            outputFilePath = finalOutput.absolutePath,
            outputFileName = finalName,
            splitOutputPaths = clonedSplits,
            reSignRequiredWarning = false
        )
    }

    private fun extractSecurityAudit(pkgInfo: PackageInfo): PackageSecurityAudit {
        val allPerms = pkgInfo.requestedPermissions?.toList().orEmpty()
        val sensitiveKeywords = setOf(
            "CAMERA", "RECORD_AUDIO", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION",
            "ACCESS_BACKGROUND_LOCATION", "READ_CONTACTS", "WRITE_CONTACTS",
            "READ_SMS", "SEND_SMS", "CALL_PHONE", "READ_CALL_LOG",
            "SYSTEM_ALERT_WINDOW", "REQUEST_INSTALL_PACKAGES", "MANAGE_EXTERNAL_STORAGE",
            "QUERY_ALL_PACKAGES", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE"
        )
        val dangerous = allPerms.filter { perm ->
            val shortName = perm.substringAfterLast('.')
            shortName in sensitiveKeywords
        }

        val flags = pkgInfo.applicationInfo?.flags ?: 0
        val isDebuggable = (flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val allowsBackup = (flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0

        val certBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
                ?: pkgInfo.signingInfo?.signingCertificateHistory?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures?.firstOrNull()?.toByteArray()
        }

        val certSha256 = certBytes?.let { bytes ->
            runCatching {
                MessageDigest.getInstance("SHA-256")
                    .digest(bytes)
                    .joinToString(":") { "%02X".format(it) }
            }.getOrNull()
        }

        return PackageSecurityAudit(
            requestedPermissions = allPerms,
            dangerousPermissions = dangerous,
            isDebuggable = isDebuggable,
            allowsBackup = allowsBackup,
            signingCertSha256 = certSha256
        )
    }

    private fun computeExactSizeAndCrc(zip: ZipFile, entry: ZipEntry, buffer: ByteArray): Pair<Long, Long> {
        val crc = CRC32()
        var size = 0L
        zip.getInputStream(entry).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                crc.update(buffer, 0, read)
                size += read
            }
        }
        return size to crc.value
    }

    private fun computeSha256(file: File, buffer: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(file), buffer.size).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun computeShannonEntropy(stream: InputStream, sampleBuf: ByteArray): Double {
        val counts = IntArray(256)
        var total = 0
        var iterations = 0
        while (iterations < 4) {
            val read = stream.read(sampleBuf)
            if (read <= 0) break
            for (i in 0 until read) {
                counts[sampleBuf[i].toInt() and 0xFF]++
            }
            total += read
            iterations++
        }
        if (total == 0) return 0.0
        var entropy = 0.0
        val totalD = total.toDouble()
        val ln2 = ln(2.0)
        for (c in counts) {
            if (c > 0) {
                val p = c / totalD
                entropy -= p * (ln(p) / ln2)
            }
        }
        return entropy.coerceIn(0.0, 8.0)
    }

    private fun extractAbiName(path: String): String? {
        val lower = path.lowercase(Locale.US)
        if (!lower.startsWith("lib/")) return null
        val parts = lower.split('/')
        return if (parts.size >= 3 && parts[1].isNotBlank()) parts[1] else null
    }

    private fun classifyEntry(path: String): PackageCategory {
        val lower = path.lowercase(Locale.US)
        return when {
            lower == "resources.arsc" -> PackageCategory.RESOURCES_ARSC
            lower.endsWith(".dex") -> PackageCategory.DEX_BYTECODE
            lower.startsWith("lib/") && lower.endsWith(".so") -> PackageCategory.NATIVE_LIBS
            lower == "androidmanifest.xml" || (lower.startsWith("res/") && lower.endsWith(".xml")) -> PackageCategory.MANIFEST_XML
            lower.startsWith("meta-inf/") || lower.endsWith(".kotlin_builtins") || lower.endsWith(".version") || lower == "debugprobeskt.bin" -> PackageCategory.METADATA_SIGNING
            lower.startsWith("res/") || lower.startsWith("assets/") || isCompressedMediaExt(lower) -> PackageCategory.MEDIA_ASSETS
            else -> PackageCategory.OTHER
        }
    }

    private fun isCompressedMediaExt(lowerPath: String): Boolean {
        return lowerPath.endsWith(".png") ||
            lowerPath.endsWith(".webp") ||
            lowerPath.endsWith(".jpg") ||
            lowerPath.endsWith(".jpeg") ||
            lowerPath.endsWith(".ogg") ||
            lowerPath.endsWith(".mp3") ||
            lowerPath.endsWith(".mp4") ||
            lowerPath.endsWith(".aac") ||
            lowerPath.endsWith(".gif")
    }

    private fun isSafeDebugArtifact(path: String): Boolean {
        val lower = path.lowercase(Locale.US)
        return lower == "debugprobeskt.bin" ||
            (lower.startsWith("meta-inf/") && lower.endsWith(".version")) ||
            lower.startsWith("meta-inf/com.android.tools/")
    }

    private fun isOldV1SignatureArtifact(path: String): Boolean {
        val upper = path.uppercase(Locale.US)
        return upper == "META-INF/MANIFEST.MF" ||
            (upper.startsWith("META-INF/") && (
                upper.endsWith(".SF") ||
                    upper.endsWith(".RSA") ||
                    upper.endsWith(".DSA") ||
                    upper.endsWith(".EC")
                ))
    }

    private fun decideStrategy(
        path: String,
        category: PackageCategory,
        abiName: String?,
        sourceStored: Boolean,
        entropy: Double,
        config: CompressionConfig
    ): EntryStrategy {
        if (abiName != null && config.allowedAbis.isNotEmpty() && abiName !in config.allowedAbis) {
            return EntryStrategy.STRIP_UNUSED_ABI
        }
        if (config.pruneDebugArtifacts && isSafeDebugArtifact(path)) {
            return EntryStrategy.PRUNE_SAFE_DEBUG
        }
        if (category == PackageCategory.RESOURCES_ARSC && config.enforceResourcesArscAlign) {
            return EntryStrategy.PRESERVE_STORED_4ALIGN
        }
        if (category == PackageCategory.NATIVE_LIBS && sourceStored) {
            return EntryStrategy.PRESERVE_STORED_4KB
        }
        if (config.smartEntropyBypass && (entropy > 7.85 || isCompressedMediaExt(path.lowercase(Locale.US)))) {
            return EntryStrategy.BYPASS_HIGH_ENTROPY
        }
        return when (config.profile) {
            CompressionProfile.ULTRA_SHRINK -> EntryStrategy.DEFLATE_MAX
            CompressionProfile.ZERO_RISK_BALANCED -> EntryStrategy.DEFLATE_BALANCED
            CompressionProfile.LOW_RESOURCE_ECO -> EntryStrategy.DEFLATE_BALANCED
        }
    }

    private fun estimateOutputBytes(
        rawBytes: Long,
        sourceCompBytes: Long,
        sourceStored: Boolean,
        entropy: Double,
        strategy: EntryStrategy
    ): Long {
        if (rawBytes <= 0L) return 0L
        return when (strategy) {
            EntryStrategy.PRUNE_SAFE_DEBUG,
            EntryStrategy.STRIP_UNUSED_ABI -> 0L
            EntryStrategy.PRESERVE_STORED_4ALIGN,
            EntryStrategy.PRESERVE_STORED_4KB,
            EntryStrategy.BYPASS_HIGH_ENTROPY -> rawBytes
            EntryStrategy.DEFLATE_MAX -> {
                val factor = ((entropy / 8.0) * 0.82).coerceIn(0.22, 0.96)
                val est = (rawBytes * factor).toLong()
                if (!sourceStored && sourceCompBytes in 1 until est) (sourceCompBytes * 0.96).toLong() else est
            }
            EntryStrategy.DEFLATE_BALANCED -> {
                val factor = ((entropy / 8.0) * 0.88).coerceIn(0.28, 0.98)
                val est = (rawBytes * factor).toLong()
                if (!sourceStored && sourceCompBytes in 1 until est) sourceCompBytes else est
            }
        }
    }

    private fun computeSafetyScore(hasArsc: Boolean, hasUncompressedSo: Boolean, config: CompressionConfig): Int {
        var score = 70
        if (!hasArsc || config.enforceResourcesArscAlign) score += 15
        if (config.smartEntropyBypass) score += 10
        if (!hasUncompressedSo || config.enforceResourcesArscAlign) score += 5
        return score.coerceIn(0, 100)
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: uri.lastPathSegment
    }
}
