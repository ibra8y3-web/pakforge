package com.example.core.engine

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal data class UniversalMergeStats(
    val arscAligned: Boolean,
    val bypassedCount: Int,
    val prunedCount: Int,
    val strippedAbiCount: Int,
    val localesPrunedCount: Int,
    val imagesOptimizedCount: Int,
    val processedEntries: Int
)

/**
 * Merges base.apk + all kept split_*.apk files into one single Universal APK:
 * - Merges resources.arsc via ArscMerger + applies ArscLocalePruner + ArscPackagePatcher
 * - Renumbers classes*.dex across feature splits to prevent DEX collisions
 * - Clears split-required manifest flags and aligns .so to 16KB + resources.arsc to 4B
 */
internal object UniversalSplitMerger {

    private class CountingOut(out: OutputStream) : FilterOutputStream(out) {
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

    fun mergeIntoUnsignedApk(
        baseFile: File,
        keptSplitFiles: List<File>,
        outputUnsignedFile: File,
        origPackageId: String?,
        targetPackageId: String?,
        baseDisplayName: String,
        certDigestNoColons: String?,
        config: CompressionConfig,
        bufferSize: Int
    ): UniversalMergeStats {
        var arscAligned = false
        var bypassedCount = 0
        var prunedCount = 0
        var strippedAbiCount = 0
        var localesPrunedCount = 0
        var imagesOptimizedCount = 0
        var processedEntries = 0

        val isCloning = targetPackageId != null && targetPackageId != origPackageId
        val customLabel = if (isCloning) {
            config.customCloneLabel.trim().ifBlank { "$baseDisplayName (PF)" }
        } else null

        // Collect all split resources.arsc payloads first
        val splitArscs = ArrayList<ByteArray>()
        for (sf in keptSplitFiles) {
            runCatching {
                ZipFile(sf).use { sz ->
                    sz.getEntry("resources.arsc")?.let { ae ->
                        splitArscs.add(sz.getInputStream(ae).use { it.readBytes() })
                    }
                }
            }
        }

        // Determine highest classesN.dex in base APK
        var maxBaseDex = 0
        val seenPaths = HashSet<String>()
        var orphanResPaths: Set<String> = emptySet()

        ZipFile(baseFile).use { baseZip ->
            val en = baseZip.entries()
            while (en.hasMoreElements()) {
                val n = en.nextElement().name
                if (n == "classes.dex") maxBaseDex = maxOf(maxBaseDex, 1)
                else if (n.startsWith("classes") && n.endsWith(".dex")) {
                    n.removePrefix("classes").removeSuffix(".dex").toIntOrNull()?.let {
                        maxBaseDex = maxOf(maxBaseDex, it)
                    }
                }
            }

            val countingOut = CountingOut(BufferedOutputStream(FileOutputStream(outputUnsignedFile), bufferSize))
            TunedZipOutputStream(countingOut).use { zos ->
                // 1. Pre-compute merged + pruned + patched resources.arsc if present in base
                baseZip.getEntry("resources.arsc")?.let { baseArscEntry ->
                    var arscBytes = baseZip.getInputStream(baseArscEntry).use { it.readBytes() }
                    if (splitArscs.isNotEmpty()) {
                        arscBytes = ArscMerger.merge(arscBytes, splitArscs)
                    }
                    if (config.pruneUnusedLocales && config.keptLocales.isNotEmpty()) {
                        val pruned = ArscLocalePruner.pruneUnusedLocales(arscBytes, config.keptLocales)
                        arscBytes = pruned.patchedArsc
                        localesPrunedCount += pruned.prunedConfigsCount
                        orphanResPaths = pruned.safeOrphanResPaths
                    }
                    if (isCloning && origPackageId != null) {
                        arscBytes = ArscPackagePatcher.patchPackageName(arscBytes, origPackageId, targetPackageId)
                    }

                    seenPaths.add("resources.arsc")
                    val crc = CRC32().apply { update(arscBytes) }.value
                    val outEntry = ZipEntry("resources.arsc").apply {
                        time = baseArscEntry.time
                        method = ZipEntry.STORED
                        size = arscBytes.size.toLong()
                        compressedSize = arscBytes.size.toLong()
                        this.crc = crc
                    }
                    if (config.enforceResourcesArscAlign) {
                        zos.flush()
                        val baseDataOffset = countingOut.bytesWritten + 30L + "resources.arsc".length
                        val rem = (baseDataOffset % 4).toInt()
                        val pad = if (rem == 0) 0 else 4 - rem
                        if (pad > 0) outEntry.extra = ByteArray(pad)
                        arscAligned = ((baseDataOffset + pad) % 4L) == 0L
                    }
                    zos.putNextEntry(outEntry)
                    zos.write(arscBytes)
                    zos.closeEntry()
                    processedEntries++
                }

                // 2. Write remaining entries from base.apk
                val baseEntries = baseZip.entries()
                while (baseEntries.hasMoreElements()) {
                    val srcEntry = baseEntries.nextElement()
                    if (srcEntry.isDirectory) continue
                    val path = srcEntry.name
                    if (path == "resources.arsc" || isSignatureArtifact(path)) {
                        if (isSignatureArtifact(path)) prunedCount++
                        continue
                    }
                    if (config.pruneDebugArtifacts && isDebugArtifact(path)) {
                        prunedCount++
                        continue
                    }
                    val abi = extractAbi(path)
                    if (abi != null && config.allowedAbis.isNotEmpty() && abi !in config.allowedAbis) {
                        strippedAbiCount++
                        continue
                    }
                    if (path in orphanResPaths) {
                        prunedCount++
                        continue
                    }
                    if (!seenPaths.add(path)) continue

                    if (path == "AndroidManifest.xml") {
                        val rawMf = baseZip.getInputStream(srcEntry).use { it.readBytes() }
                        val patchedMf = runCatching {
                            AxmlManifestPatcher.patchManifestAdvanced(
                                rawAxml = rawMf,
                                options = ManifestPatchOptions(
                                    newPackageName = if (isCloning) targetPackageId else null,
                                    customAppLabel = customLabel,
                                    stripSharedUserId = config.stripSharedUserId,
                                    newStaticLibCertDigestHex = if (config.patchStaticLibCertDigest) certDigestNoColons else null,
                                    clearSplitRequiredFlags = true,
                                    cloneAffinitySuffix = config.cloneSuffix
                                )
                            )
                        }.getOrDefault(rawMf)

                        zos.setDeflateParams(Deflater.BEST_COMPRESSION, Deflater.DEFAULT_STRATEGY)
                        val outEntry = ZipEntry(path).apply {
                            time = srcEntry.time
                            method = ZipEntry.DEFLATED
                        }
                        zos.putNextEntry(outEntry)
                        zos.write(patchedMf)
                        zos.closeEntry()
                        processedEntries++
                        continue
                    }

                    val stats = writeEntrySmart(
                        zip = baseZip,
                        srcEntry = srcEntry,
                        outPath = path,
                        zos = zos,
                        countingOut = countingOut,
                        config = config,
                        isCloning = isCloning
                    )
                    if (stats.first) bypassedCount++
                    if (stats.second) imagesOptimizedCount++
                    processedEntries++
                }

                // 3. Merge entries from all kept split APKs
                var nextDex = maxBaseDex + 1
                for (sf in keptSplitFiles) {
                    ZipFile(sf).use { splitZip ->
                        val sEntries = splitZip.entries()
                        while (sEntries.hasMoreElements()) {
                            val srcEntry = sEntries.nextElement()
                            if (srcEntry.isDirectory) continue
                            val path = srcEntry.name
                            if (path == "AndroidManifest.xml" || path == "resources.arsc" || isSignatureArtifact(path)) {
                                continue
                            }
                            if (config.pruneDebugArtifacts && isDebugArtifact(path)) {
                                prunedCount++
                                continue
                            }
                            val abi = extractAbi(path)
                            if (abi != null && config.allowedAbis.isNotEmpty() && abi !in config.allowedAbis) {
                                strippedAbiCount++
                                continue
                            }
                            if (path in orphanResPaths) {
                                prunedCount++
                                continue
                            }

                            val finalOutPath = if (path == "classes.dex" || (path.startsWith("classes") && path.endsWith(".dex"))) {
                                val dexName = if (nextDex == 1) "classes.dex" else "classes${nextDex}.dex"
                                nextDex++
                                dexName
                            } else {
                                path
                            }

                            if (!seenPaths.add(finalOutPath)) continue

                            val stats = writeEntrySmart(
                                zip = splitZip,
                                srcEntry = srcEntry,
                                outPath = finalOutPath,
                                zos = zos,
                                countingOut = countingOut,
                                config = config,
                                isCloning = isCloning
                            )
                            if (stats.first) bypassedCount++
                            if (stats.second) imagesOptimizedCount++
                            processedEntries++
                        }
                    }
                }
            }
        }

        return UniversalMergeStats(
            arscAligned = arscAligned,
            bypassedCount = bypassedCount,
            prunedCount = prunedCount,
            strippedAbiCount = strippedAbiCount,
            localesPrunedCount = localesPrunedCount,
            imagesOptimizedCount = imagesOptimizedCount,
            processedEntries = processedEntries
        )
    }

    private fun writeEntrySmart(
        zip: ZipFile,
        srcEntry: ZipEntry,
        outPath: String,
        zos: TunedZipOutputStream,
        countingOut: CountingOut,
        config: CompressionConfig,
        isCloning: Boolean
    ): Pair<Boolean, Boolean> {
        val lower = outPath.lowercase(Locale.US)
        val isNativeSo = lower.startsWith("lib/") && lower.endsWith(".so")
        val rawBytes = zip.getInputStream(srcEntry).use { it.readBytes() }

        var payload = rawBytes
        var optimizedImg = false
        if (config.optimizeLargeImages && AssetImageOptimizer.isOptimizableImagePath(outPath)) {
            val crunched = AssetImageOptimizer.optimizeIfBeneficial(outPath, payload)
            if (crunched !== payload) {
                payload = crunched
                optimizedImg = true
            }
        }
        if (isCloning && config.tintCloneIcon && CloneIconStyler.isLauncherPng(outPath)) {
            payload = CloneIconStyler.badgeIfLauncherPng(outPath, payload)
        }

        val isMedia = isMediaExt(lower)
        val storeUncompressed = (isNativeSo && srcEntry.method == ZipEntry.STORED) ||
            (config.smartEntropyBypass && isMedia)

        val outEntry = ZipEntry(outPath).apply { time = srcEntry.time }
        return if (storeUncompressed) {
            val crc = CRC32().apply { update(payload) }.value
            outEntry.method = ZipEntry.STORED
            outEntry.size = payload.size.toLong()
            outEntry.compressedSize = payload.size.toLong()
            outEntry.crc = crc
            val align = if (isNativeSo) 16384 else 4
            zos.flush()
            val nameLen = outPath.toByteArray(StandardCharsets.UTF_8).size
            val dataOffset = countingOut.bytesWritten + 30L + nameLen
            val rem = (dataOffset % align).toInt()
            val pad = if (rem == 0) 0 else align - rem
            if (pad > 0) outEntry.extra = ByteArray(pad)
            zos.putNextEntry(outEntry)
            zos.write(payload)
            zos.closeEntry()
            (config.smartEntropyBypass && isMedia) to optimizedImg
        } else {
            val level = when (config.profile) {
                CompressionProfile.ULTRA_SHRINK -> Deflater.BEST_COMPRESSION
                CompressionProfile.LOW_RESOURCE_ECO -> 4
                CompressionProfile.ZERO_RISK_BALANCED -> Deflater.DEFAULT_COMPRESSION
            }
            val strategy = if (config.profile == CompressionProfile.ULTRA_SHRINK &&
                (lower.endsWith(".dex") || lower.endsWith(".xml"))
            ) {
                TunedZipOutputStream.pickOptimalStrategy(payload, level)
            } else {
                Deflater.DEFAULT_STRATEGY
            }
            zos.setDeflateParams(level, strategy)
            outEntry.method = ZipEntry.DEFLATED
            zos.putNextEntry(outEntry)
            zos.write(payload)
            zos.closeEntry()
            false to optimizedImg
        }
    }

    private fun extractAbi(path: String): String? {
        val lower = path.lowercase(Locale.US)
        if (!lower.startsWith("lib/")) return null
        val parts = lower.split('/')
        return if (parts.size >= 3 && parts[1].isNotBlank()) parts[1] else null
    }

    private fun isMediaExt(lower: String): Boolean {
        return lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") || lower.endsWith(".ogg") || lower.endsWith(".mp3") ||
            lower.endsWith(".mp4") || lower.endsWith(".aac") || lower.endsWith(".gif")
    }

    private fun isDebugArtifact(path: String): Boolean {
        val lower = path.lowercase(Locale.US)
        return lower == "debugprobeskt.bin" ||
            (lower.startsWith("meta-inf/") && lower.endsWith(".version")) ||
            lower.startsWith("meta-inf/com.android.tools/")
    }

    private fun isSignatureArtifact(path: String): Boolean {
        val upper = path.uppercase(Locale.US)
        return upper == "META-INF/MANIFEST.MF" ||
            (upper.startsWith("META-INF/") && (
                upper.endsWith(".SF") || upper.endsWith(".RSA") ||
                    upper.endsWith(".DSA") || upper.endsWith(".EC")
                ))
    }
}
