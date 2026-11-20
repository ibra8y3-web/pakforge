package com.example.core.engine

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

data class ManifestPatchOptions(
    val newPackageName: String? = null,
    val customAppLabel: String? = null,
    val appendAppLabelSuffix: String? = null,
    val stripSharedUserId: Boolean = false,
    val newStaticLibCertDigestHex: String? = null,
    val clearSplitRequiredFlags: Boolean = false,
    val cloneAffinitySuffix: String? = null
)

/**
 * Binary Android XML (AXML) patcher for AndroidManifest.xml.
 * Supports:
 * 1) Full Side-by-Side Clone relocation (package, authorities, permissions, component permissions, taskAffinity, meta-data)
 * 2) Direct override of <application android:label> even when encoded as TYPE_REFERENCE (@string/app_name)
 * 3) Clean attribute removal for android:sharedUserId / sharedUserLabel
 * 4) Rewriting android:certDigest on <uses-static-library> to match PakForge's signing key
 * 5) Clearing android:isSplitRequired & com.android.vending.splits.required when merging splits into a Universal APK.
 */
object AxmlManifestPatcher {

    private const val CHUNK_AXML_FILE = 0x0003
    private const val CHUNK_STRING_POOL = 0x0001
    private const val CHUNK_START_ELEMENT = 0x0102
    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_BOOLEAN = 0x12

    fun readManifestPackage(rawAxml: ByteArray): String? {
        if (rawAxml.size < 8) return null
        val buf = ByteBuffer.wrap(rawAxml).order(ByteOrder.LITTLE_ENDIAN)
        if ((buf.getShort(0).toInt() and 0xFFFF) != CHUNK_AXML_FILE) return null
        val spOffset = buf.getShort(2).toInt() and 0xFFFF
        if (spOffset + 28 > rawAxml.size) return null
        if ((buf.getShort(spOffset).toInt() and 0xFFFF) != CHUNK_STRING_POOL) return null

        val spHeaderSize = buf.getShort(spOffset + 2).toInt() and 0xFFFF
        val spChunkSize = buf.getInt(spOffset + 4)
        val stringCount = buf.getInt(spOffset + 8)
        val flags = buf.getInt(spOffset + 16)
        val stringsStart = buf.getInt(spOffset + 20)
        val isUtf8 = (flags and 0x0100) != 0
        val offsetsStart = spOffset + spHeaderSize
        val stringDataBase = spOffset + stringsStart
        val strings = ArrayList<String>(stringCount)
        for (i in 0 until stringCount) {
            val strRelOffset = buf.getInt(offsetsStart + i * 4)
            val absPos = stringDataBase + strRelOffset
            strings.add(if (isUtf8) readUtf8String(rawAxml, absPos) else readUtf16String(rawAxml, absPos))
        }

        var pos = spOffset + spChunkSize
        while (pos + 8 <= rawAxml.size) {
            val chunkType = buf.getShort(pos).toInt() and 0xFFFF
            val chunkSize = buf.getInt(pos + 4)
            if (chunkSize < 8 || pos + chunkSize > rawAxml.size) break
            if (chunkType == CHUNK_START_ELEMENT && pos + 36 <= rawAxml.size) {
                val elemName = strings.getOrNull(buf.getInt(pos + 20))
                if (elemName == "manifest") {
                    val attrStart = buf.getShort(pos + 24).toInt() and 0xFFFF
                    val attrSize = buf.getShort(pos + 26).toInt() and 0xFFFF
                    val attrCount = buf.getShort(pos + 28).toInt() and 0xFFFF
                    val attrsBase = pos + 16 + attrStart
                    for (a in 0 until attrCount) {
                        val attrPos = attrsBase + a * attrSize
                        if (attrPos + 20 > pos + chunkSize) break
                        val attrName = strings.getOrNull(buf.getInt(attrPos + 4))
                        val valType = (buf.getInt(attrPos + 12) ushr 24) and 0xFF
                        val valData = buf.getInt(attrPos + 16)
                        if (attrName == "package" && valType == TYPE_STRING) {
                            return strings.getOrNull(valData)
                        }
                    }
                }
            }
            pos += chunkSize
        }
        return null
    }

    fun detectManifestRuntimeRisks(rawAxml: ByteArray): List<String> {
        if (rawAxml.size < 8) return emptyList()
        val buf = ByteBuffer.wrap(rawAxml).order(ByteOrder.LITTLE_ENDIAN)
        if ((buf.getShort(0).toInt() and 0xFFFF) != CHUNK_AXML_FILE) return emptyList()
        val spOffset = buf.getShort(2).toInt() and 0xFFFF
        if (spOffset + 28 > rawAxml.size) return emptyList()
        if ((buf.getShort(spOffset).toInt() and 0xFFFF) != CHUNK_STRING_POOL) return emptyList()

        val spHeaderSize = buf.getShort(spOffset + 2).toInt() and 0xFFFF
        val spChunkSize = buf.getInt(spOffset + 4)
        val stringCount = buf.getInt(spOffset + 8)
        val flags = buf.getInt(spOffset + 16)
        val stringsStart = buf.getInt(spOffset + 20)
        val isUtf8 = (flags and 0x0100) != 0
        val offsetsStart = spOffset + spHeaderSize
        val stringDataBase = spOffset + stringsStart
        val strings = ArrayList<String>(stringCount)
        for (i in 0 until stringCount) {
            val strRelOffset = buf.getInt(offsetsStart + i * 4)
            val absPos = stringDataBase + strRelOffset
            strings.add(if (isUtf8) readUtf8String(rawAxml, absPos) else readUtf16String(rawAxml, absPos))
        }

        val risks = LinkedHashSet<String>()
        var pos = spOffset + spChunkSize
        while (pos + 8 <= rawAxml.size) {
            val chunkType = buf.getShort(pos).toInt() and 0xFFFF
            val chunkSize = buf.getInt(pos + 4)
            if (chunkSize < 8 || pos + chunkSize > rawAxml.size) break
            if (chunkType == CHUNK_START_ELEMENT && pos + 36 <= rawAxml.size) {
                val elemName = strings.getOrNull(buf.getInt(pos + 20))
                val attrStart = buf.getShort(pos + 24).toInt() and 0xFFFF
                val attrSize = buf.getShort(pos + 26).toInt() and 0xFFFF
                val attrCount = buf.getShort(pos + 28).toInt() and 0xFFFF
                val attrsBase = pos + 16 + attrStart
                for (a in 0 until attrCount) {
                    val attrPos = attrsBase + a * attrSize
                    if (attrPos + 20 > pos + chunkSize) break
                    val attrName = strings.getOrNull(buf.getInt(attrPos + 4))
                    val valType = (buf.getInt(attrPos + 12) ushr 24) and 0xFF
                    val valData = buf.getInt(attrPos + 16)
                    if (valType != TYPE_STRING) continue
                    val value = strings.getOrNull(valData) ?: continue

                    if (elemName == "uses-static-library" && attrName == "name") {
                        risks.add("STATIC_SHARED_LIB:$value")
                    } else if (elemName == "manifest" && attrName == "sharedUserId" && value.isNotBlank()) {
                        risks.add("SHARED_USER_ID:$value")
                    }
                }
            }
            pos += chunkSize
        }
        return risks.toList()
    }

    fun patchManifestPackage(
        rawAxml: ByteArray,
        newPackageName: String,
        appendAppLabelSuffix: String? = null
    ): ByteArray {
        return patchManifestAdvanced(
            rawAxml = rawAxml,
            options = ManifestPatchOptions(
                newPackageName = newPackageName,
                appendAppLabelSuffix = appendAppLabelSuffix
            )
        )
    }

    fun patchManifestAdvanced(
        rawAxml: ByteArray,
        options: ManifestPatchOptions
    ): ByteArray {
        require(rawAxml.size >= 8) { "Invalid AXML manifest" }
        val buf = ByteBuffer.wrap(rawAxml).order(ByteOrder.LITTLE_ENDIAN)
        val fileType = buf.getShort(0).toInt() and 0xFFFF
        require(fileType == CHUNK_AXML_FILE) { "Not a binary AXML file" }

        val spOffset = buf.getShort(2).toInt() and 0xFFFF
        val spType = buf.getShort(spOffset).toInt() and 0xFFFF
        require(spType == CHUNK_STRING_POOL) { "Missing AXML StringPool" }

        val spHeaderSize = buf.getShort(spOffset + 2).toInt() and 0xFFFF
        val spChunkSize = buf.getInt(spOffset + 4)
        val stringCount = buf.getInt(spOffset + 8)
        val styleCount = buf.getInt(spOffset + 12)
        val flags = buf.getInt(spOffset + 16)
        val stringsStart = buf.getInt(spOffset + 20)
        val stylesStart = buf.getInt(spOffset + 24)
        val isUtf8 = (flags and 0x0100) != 0

        val strings = ArrayList<String>(stringCount + 24)
        val offsetsStart = spOffset + spHeaderSize
        val stringDataBase = spOffset + stringsStart
        for (i in 0 until stringCount) {
            val strRelOffset = buf.getInt(offsetsStart + i * 4)
            val absPos = stringDataBase + strRelOffset
            strings.add(if (isUtf8) readUtf8String(rawAxml, absPos) else readUtf16String(rawAxml, absPos))
        }

        val styleOffsetsBytes = if (styleCount > 0) {
            val start = offsetsStart + stringCount * 4
            rawAxml.copyOfRange(start, start + styleCount * 4)
        } else ByteArray(0)

        val styleDataBytes = if (styleCount > 0 && stylesStart > 0) {
            val absStylesStart = spOffset + stylesStart
            val absSpEnd = spOffset + spChunkSize
            if (absStylesStart < absSpEnd) rawAxml.copyOfRange(absStylesStart, absSpEnd) else ByteArray(0)
        } else ByteArray(0)

        val trailingOffset = spOffset + spChunkSize
        val rawTrailing = rawAxml.copyOfRange(trailingOffset, rawAxml.size)

        fun internNewString(value: String): Int {
            val existing = strings.indexOf(value)
            if (existing >= 0) return existing
            strings.add(value)
            return strings.lastIndex
        }

        // Process trailing chunks via ByteArrayOutputStream so attribute stripping can cleanly shrink chunks
        val rebuiltTrailing = ByteArrayOutputStream(rawTrailing.size)
        val origBuf = ByteBuffer.wrap(rawTrailing).order(ByteOrder.LITTLE_ENDIAN)

        var oldPackageName: String? = null
        val customPermissionsRenamed = HashMap<String, String>()
        var pos = 0

        while (pos + 8 <= rawTrailing.size) {
            val chunkType = origBuf.getShort(pos).toInt() and 0xFFFF
            val chunkSize = origBuf.getInt(pos + 4)
            if (chunkSize < 8 || pos + chunkSize > rawTrailing.size) {
                rebuiltTrailing.write(rawTrailing, pos, rawTrailing.size - pos)
                break
            }

            if (chunkType != CHUNK_START_ELEMENT || pos + 36 > rawTrailing.size) {
                rebuiltTrailing.write(rawTrailing, pos, chunkSize)
                pos += chunkSize
                continue
            }

            val chunkCopy = rawTrailing.copyOfRange(pos, pos + chunkSize)
            val cBuf = ByteBuffer.wrap(chunkCopy).order(ByteOrder.LITTLE_ENDIAN)
            val elemName = strings.getOrNull(cBuf.getInt(20))
            val attrStart = cBuf.getShort(24).toInt() and 0xFFFF
            val attrSize = cBuf.getShort(26).toInt() and 0xFFFF
            val attrCount = cBuf.getShort(28).toInt() and 0xFFFF
            val attrsBase = 16 + attrStart

            val stripAttrIndices = ArrayList<Int>()
            var metaDataNameAttr: String? = null
            var metaDataValueIdx = -1

            for (a in 0 until attrCount) {
                val attrPos = attrsBase + a * attrSize
                if (attrPos + 20 > chunkCopy.size) break
                val attrName = strings.getOrNull(cBuf.getInt(attrPos + 4))
                val rawValIdx = cBuf.getInt(attrPos + 8)
                val valType = (cBuf.getInt(attrPos + 12) ushr 24) and 0xFF
                val valData = cBuf.getInt(attrPos + 16)

                fun setAttrStringValue(newStrIdx: Int) {
                    cBuf.putInt(attrPos + 8, newStrIdx)
                    cBuf.putInt(attrPos + 12, (TYPE_STRING shl 24) or 0x0008)
                    cBuf.putInt(attrPos + 16, newStrIdx)
                }

                // 1. Clear split requirements when converting to Universal APK
                if (options.clearSplitRequiredFlags) {
                    if ((elemName == "application" || elemName == "manifest") && attrName == "isSplitRequired") {
                        if (valType == TYPE_INT_BOOLEAN) {
                            cBuf.putInt(attrPos + 16, 0)
                        }
                    }
                    if (elemName == "manifest" && (attrName == "requiredSplitTypes" || attrName == "splitTypes")) {
                        stripAttrIndices.add(a)
                        continue
                    }
                    if (elemName == "meta-data" && attrName == "name" && valType == TYPE_STRING) {
                        metaDataNameAttr = strings.getOrNull(valData)
                    }
                    if (elemName == "meta-data" && attrName == "value") {
                        metaDataValueIdx = attrPos
                    }
                }

                // 2. Strip android:sharedUserId / sharedUserLabel when requested
                if (options.stripSharedUserId && elemName == "manifest" &&
                    (attrName == "sharedUserId" || attrName == "sharedUserLabel")
                ) {
                    stripAttrIndices.add(a)
                    continue
                }

                // 3. Patch android:certDigest inside <uses-static-library>
                if (!options.newStaticLibCertDigestHex.isNullOrBlank() &&
                    elemName == "uses-static-library" &&
                    attrName == "certDigest" &&
                    valType == TYPE_STRING
                ) {
                    setAttrStringValue(internNewString(options.newStaticLibCertDigestHex))
                    continue
                }

                // 4. Override <application android:label> even if originally TYPE_REFERENCE!
                if (elemName == "application" && attrName == "label") {
                    val overrideLabel = options.customAppLabel?.takeIf { it.isNotBlank() }
                        ?: if (valType == TYPE_STRING && options.appendAppLabelSuffix != null) {
                            (strings.getOrNull(valData) ?: "") + options.appendAppLabelSuffix
                        } else null
                    if (overrideLabel != null) {
                        setAttrStringValue(internNewString(overrideLabel))
                        continue
                    }
                }

                // 5. Package ID cloning & deep manifest relocation
                if (valType != TYPE_STRING) continue
                val currentStr = strings.getOrNull(valData) ?: continue
                val newPkg = options.newPackageName

                if (elemName == "manifest" && attrName == "package") {
                    oldPackageName = currentStr
                    if (!newPkg.isNullOrBlank() && newPkg != currentStr) {
                        setAttrStringValue(internNewString(newPkg))
                    }
                } else if (!newPkg.isNullOrBlank() && oldPackageName != null && newPkg != oldPackageName) {
                    val origPkg = oldPackageName
                    when {
                        (elemName in setOf("activity", "activity-alias", "service", "receiver", "provider", "application")) &&
                            (attrName == "name" || attrName == "targetActivity") -> {
                            val resolved = when {
                                currentStr.startsWith(".") -> "$origPkg$currentStr"
                                !currentStr.contains('.') -> "$origPkg.$currentStr"
                                else -> null
                            }
                            if (resolved != null) {
                                setAttrStringValue(internNewString(resolved))
                            }
                        }
                        elemName == "provider" && attrName == "authorities" -> {
                            val updatedAuth = currentStr.split(';').joinToString(";") { part ->
                                val trimmed = part.trim()
                                when {
                                    trimmed.isEmpty() -> trimmed
                                    trimmed.contains(origPkg) -> trimmed.replace(origPkg, newPkg)
                                    else -> "$trimmed.pakforge"
                                }
                            }
                            setAttrStringValue(internNewString(updatedAuth))
                        }
                        elemName == "permission" && attrName == "name" -> {
                            if (!currentStr.startsWith("android.permission.")) {
                                val rewritten = if (currentStr.contains(origPkg)) {
                                    currentStr.replace(origPkg, newPkg)
                                } else {
                                    "$currentStr.pakforge"
                                }
                                customPermissionsRenamed[currentStr] = rewritten
                                setAttrStringValue(internNewString(rewritten))
                            }
                        }
                        elemName == "uses-permission" && attrName == "name" -> {
                            val mapped = customPermissionsRenamed[currentStr]
                                ?: if (currentStr.startsWith("$origPkg.")) {
                                    currentStr.replaceFirst(origPkg, newPkg)
                                } else null
                            if (mapped != null) {
                                setAttrStringValue(internNewString(mapped))
                            }
                        }
                        (elemName in setOf("activity", "activity-alias", "service", "receiver", "provider", "application")) &&
                            (attrName == "permission" || attrName == "readPermission" || attrName == "writePermission") -> {
                            val mapped = customPermissionsRenamed[currentStr]
                                ?: if (currentStr.startsWith("$origPkg.")) {
                                    currentStr.replaceFirst(origPkg, newPkg)
                                } else null
                            if (mapped != null) {
                                setAttrStringValue(internNewString(mapped))
                            }
                        }
                        (elemName == "application" || elemName == "activity") && attrName == "taskAffinity" -> {
                            val suffix = options.cloneAffinitySuffix?.takeIf { it.isNotBlank() } ?: "pkf"
                            val updated = if (currentStr.contains(origPkg)) {
                                currentStr.replace(origPkg, newPkg)
                            } else {
                                "$currentStr.$suffix"
                            }
                            setAttrStringValue(internNewString(updated))
                        }
                        elemName == "meta-data" && (attrName == "name" || attrName == "value") -> {
                            if (currentStr.contains(origPkg)) {
                                setAttrStringValue(internNewString(currentStr.replace(origPkg, newPkg)))
                            }
                        }
                        elemName == "data" && attrName == "scheme" && currentStr == origPkg -> {
                            setAttrStringValue(internNewString(newPkg))
                        }
                    }
                }
            }

            if (options.clearSplitRequiredFlags &&
                metaDataNameAttr == "com.android.vending.splits.required" &&
                metaDataValueIdx >= 0
            ) {
                val mType = (cBuf.getInt(metaDataValueIdx + 12) ushr 24) and 0xFF
                if (mType == TYPE_INT_BOOLEAN) {
                    cBuf.putInt(metaDataValueIdx + 16, 0)
                }
            }

            if (stripAttrIndices.isEmpty()) {
                rebuiltTrailing.write(chunkCopy)
            } else {
                val filteredChunk = removeAttributesFromElementChunk(
                    chunkCopy = chunkCopy,
                    attrsBase = attrsBase,
                    attrSize = attrSize,
                    attrCount = attrCount,
                    stripIndices = stripAttrIndices.toSet()
                )
                rebuiltTrailing.write(filteredChunk)
            }
            pos += chunkSize
        }

        val trailingBytes = rebuiltTrailing.toByteArray()

        // Rebuild StringPool cleanly as UTF-16LE
        val newStringCount = strings.size
        val stringDataOut = ByteArrayOutputStream()
        val newOffsets = IntArray(newStringCount)

        for (i in 0 until newStringCount) {
            newOffsets[i] = stringDataOut.size()
            val s = strings[i]
            val charLen = s.length
            if (charLen >= 0x8000) {
                stringDataOut.writeShortLE(((charLen ushr 16) and 0x7FFF) or 0x8000)
                stringDataOut.writeShortLE(charLen and 0xFFFF)
            } else {
                stringDataOut.writeShortLE(charLen)
            }
            stringDataOut.write(s.toByteArray(StandardCharsets.UTF_16LE))
            stringDataOut.writeShortLE(0)
        }
        while (stringDataOut.size() % 4 != 0) stringDataOut.write(0)

        val paddedStringBytes = stringDataOut.toByteArray()
        val newHeaderSize = 28
        val newStringsStart = newHeaderSize + (newStringCount * 4) + styleOffsetsBytes.size
        val newStylesStart = if (styleCount > 0 && styleDataBytes.isNotEmpty()) {
            newStringsStart + paddedStringBytes.size
        } else 0
        val unpaddedSpSize = newStringsStart + paddedStringBytes.size + styleDataBytes.size
        val spPad = (4 - (unpaddedSpSize % 4)) % 4
        val newSpChunkSize = unpaddedSpSize + spPad

        val newSpOut = ByteArrayOutputStream(newSpChunkSize)
        newSpOut.writeShortLE(CHUNK_STRING_POOL)
        newSpOut.writeShortLE(newHeaderSize)
        newSpOut.writeIntLE(newSpChunkSize)
        newSpOut.writeIntLE(newStringCount)
        newSpOut.writeIntLE(styleCount)
        newSpOut.writeIntLE(flags and 0x0101.inv())
        newSpOut.writeIntLE(newStringsStart)
        newSpOut.writeIntLE(newStylesStart)
        for (off in newOffsets) newSpOut.writeIntLE(off)
        newSpOut.write(styleOffsetsBytes)
        newSpOut.write(paddedStringBytes)
        newSpOut.write(styleDataBytes)
        repeat(spPad) { newSpOut.write(0) }

        val newSpBytes = newSpOut.toByteArray()
        val newTotalSize = spOffset + newSpBytes.size + trailingBytes.size

        val finalOut = ByteArrayOutputStream(newTotalSize)
        val headerCopy = rawAxml.copyOfRange(0, spOffset)
        ByteBuffer.wrap(headerCopy).order(ByteOrder.LITTLE_ENDIAN).putInt(4, newTotalSize)
        finalOut.write(headerCopy)
        finalOut.write(newSpBytes)
        finalOut.write(trailingBytes)
        return finalOut.toByteArray()
    }

    private fun removeAttributesFromElementChunk(
        chunkCopy: ByteArray,
        attrsBase: Int,
        attrSize: Int,
        attrCount: Int,
        stripIndices: Set<Int>
    ): ByteArray {
        val removedBytes = stripIndices.size * attrSize
        val newSize = chunkCopy.size - removedBytes
        val out = ByteArray(newSize)
        System.arraycopy(chunkCopy, 0, out, 0, attrsBase)
        var writePos = attrsBase
        for (i in 0 until attrCount) {
            if (i !in stripIndices) {
                System.arraycopy(chunkCopy, attrsBase + i * attrSize, out, writePos, attrSize)
                writePos += attrSize
            }
        }
        val origAfterAttrs = attrsBase + attrCount * attrSize
        if (origAfterAttrs < chunkCopy.size) {
            System.arraycopy(chunkCopy, origAfterAttrs, out, writePos, chunkCopy.size - origAfterAttrs)
        }
        val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(4, newSize)
        buf.putShort(28, (attrCount - stripIndices.size).toShort())
        // Adjust 1-based idIndex / classIndex / styleIndex if any attributes shifted
        for (idxOffset in intArrayOf(30, 32, 34)) {
            val oneBased = buf.getShort(idxOffset).toInt() and 0xFFFF
            if (oneBased > 0) {
                val zeroBased = oneBased - 1
                if (zeroBased in stripIndices) {
                    buf.putShort(idxOffset, 0)
                } else {
                    val shift = stripIndices.count { it < zeroBased }
                    buf.putShort(idxOffset, (oneBased - shift).toShort())
                }
            }
        }
        return out
    }

    private fun readUtf8String(data: ByteArray, start: Int): String {
        var idx = start
        if ((data[idx].toInt() and 0x80) != 0) idx += 2 else idx += 1
        val b0 = data[idx].toInt() and 0xFF
        val byteLen: Int
        if ((b0 and 0x80) != 0) {
            val b1 = data[idx + 1].toInt() and 0xFF
            byteLen = ((b0 and 0x7F) shl 8) or b1
            idx += 2
        } else {
            byteLen = b0
            idx += 1
        }
        return String(data, idx, byteLen, StandardCharsets.UTF_8)
    }

    private fun readUtf16String(data: ByteArray, start: Int): String {
        var idx = start
        val s0 = (data[idx].toInt() and 0xFF) or ((data[idx + 1].toInt() and 0xFF) shl 8)
        idx += 2
        val charLen = if ((s0 and 0x8000) != 0) {
            val s1 = (data[idx].toInt() and 0xFF) or ((data[idx + 1].toInt() and 0xFF) shl 8)
            idx += 2
            ((s0 and 0x7FFF) shl 16) or s1
        } else {
            s0
        }
        return String(data, idx, charLen * 2, StandardCharsets.UTF_16LE)
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
