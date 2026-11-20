package com.example.core.engine

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Binary resources.arsc merger for Anti-Split (Universal APK Merger).
 * Merges base resources.arsc with split_config.* (density, locale, feature) resources.arsc:
 * - Appends unique global strings from splits after base styled strings (preserving base style spans)
 * - Remaps TYPE_STRING Res_value indices in normal, complex map, and compact ResTable_type entries
 * - Appends non-duplicate ResTable_type / ResTable_typeSpec chunks into ResTable_package cleanly.
 */
object ArscMerger {

    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_TABLE_TYPE = 0x0002
    private const val RES_TABLE_PACKAGE_TYPE = 0x0200
    private const val RES_TABLE_TYPE_TYPE = 0x0201
    private const val RES_TABLE_TYPE_SPEC_TYPE = 0x0202
    private const val FLAG_SPARSE = 0x01
    private const val FLAG_OFFSET16 = 0x02
    private const val ENTRY_FLAG_COMPLEX = 0x0001
    private const val ENTRY_FLAG_COMPACT = 0x0008
    private const val TYPE_STRING = 0x03

    fun merge(baseArsc: ByteArray, splitArscs: List<ByteArray>): ByteArray {
        if (splitArscs.isEmpty() || baseArsc.size < 12) return baseArsc
        val baseBuf = ByteBuffer.wrap(baseArsc).order(ByteOrder.LITTLE_ENDIAN)
        if ((baseBuf.getShort(0).toInt() and 0xFFFF) != RES_TABLE_TYPE) return baseArsc
        val rootHeaderSize = baseBuf.getShort(2).toInt() and 0xFFFF
        if (rootHeaderSize < 12 || rootHeaderSize >= baseArsc.size) return baseArsc

        // Find base global string pool & base package chunk
        var spOffset = -1
        var spSize = 0
        var pkgOffset = -1
        var pkgSize = 0
        var pos = rootHeaderSize
        while (pos + 8 <= baseArsc.size) {
            val type = baseBuf.getShort(pos).toInt() and 0xFFFF
            val size = baseBuf.getInt(pos + 4)
            if (size < 8 || pos + size > baseArsc.size) break
            if (type == RES_STRING_POOL_TYPE && spOffset < 0) {
                spOffset = pos
                spSize = size
            } else if (type == RES_TABLE_PACKAGE_TYPE && pkgOffset < 0) {
                pkgOffset = pos
                pkgSize = size
            }
            pos += size
        }
        if (spOffset < 0 || pkgOffset < 0) return baseArsc

        val baseStyleCount = baseBuf.getInt(spOffset + 12)
        val baseFlags = baseBuf.getInt(spOffset + 16)
        val baseStylesStart = baseBuf.getInt(spOffset + 24)
        val baseHeaderSizeSp = baseBuf.getShort(spOffset + 2).toInt() and 0xFFFF
        val baseStrCount = baseBuf.getInt(spOffset + 8)

        val mergedStrings = ArrayList<String>(baseStrCount + 256)
        mergedStrings.addAll(ArscLocalePruner.readStringPool(baseArsc, spOffset, spSize))

        val unstyledIndexLookup = HashMap<String, Int>(mergedStrings.size)
        for (i in baseStyleCount until mergedStrings.size) {
            unstyledIndexLookup.putIfAbsent(mergedStrings[i], i)
        }

        // Preserve base styles bytes verbatim (indices 0 until baseStyleCount stay untouched!)
        val baseStyleOffsetsBytes = if (baseStyleCount > 0) {
            val start = spOffset + baseHeaderSizeSp + baseStrCount * 4
            baseArsc.copyOfRange(start, start + baseStyleCount * 4)
        } else ByteArray(0)
        val baseStyleDataBytes = if (baseStyleCount > 0 && baseStylesStart > 0) {
            val absStart = spOffset + baseStylesStart
            val absEnd = spOffset + spSize
            if (absStart < absEnd) baseArsc.copyOfRange(absStart, absEnd) else ByteArray(0)
        } else ByteArray(0)

        val basePkgHeaderSize = baseBuf.getShort(pkgOffset + 2).toInt() and 0xFFFF
        val basePkgBody = ByteArrayOutputStream(pkgSize)
        basePkgBody.write(baseArsc, pkgOffset, pkgSize)

        val seenTypeSpecs = HashSet<Int>()
        val seenTypeConfigs = HashSet<String>()
        run {
            var p = pkgOffset + basePkgHeaderSize
            val end = pkgOffset + pkgSize
            while (p + 8 <= end) {
                val ct = baseBuf.getShort(p).toInt() and 0xFFFF
                val ch = baseBuf.getShort(p + 2).toInt() and 0xFFFF
                val cs = baseBuf.getInt(p + 4)
                if (cs < 8 || p + cs > end) break
                if (ct == RES_TABLE_TYPE_SPEC_TYPE && p + 12 <= end) {
                    val typeId = baseArsc[p + 8].toInt() and 0xFF
                    seenTypeSpecs.add(typeId)
                } else if (ct == RES_TABLE_TYPE_TYPE && ch >= 32 && p + 32 <= end) {
                    val typeId = baseArsc[p + 8].toInt() and 0xFF
                    val cfgSize = baseBuf.getInt(p + 20).coerceIn(4, ch - 20)
                    val sig = "$typeId:${baseArsc.copyOfRange(p + 20, p + 20 + cfgSize).joinToString(",")}"
                    seenTypeConfigs.add(sig)
                }
                p += cs
            }
        }

        fun internSplitString(s: String): Int {
            unstyledIndexLookup[s]?.let { return it }
            val idx = mergedStrings.size
            mergedStrings.add(s)
            unstyledIndexLookup[s] = idx
            return idx
        }

        for (splitBytes in splitArscs) {
            if (splitBytes.size < 12) continue
            val sBuf = ByteBuffer.wrap(splitBytes).order(ByteOrder.LITTLE_ENDIAN)
            if ((sBuf.getShort(0).toInt() and 0xFFFF) != RES_TABLE_TYPE) continue
            val sRootHeader = sBuf.getShort(2).toInt() and 0xFFFF
            if (sRootHeader < 12 || sRootHeader >= splitBytes.size) continue

            var sSpOff = -1
            var sSpSize = 0
            var sPkgOff = -1
            var sPkgSize = 0
            var spPos = sRootHeader
            while (spPos + 8 <= splitBytes.size) {
                val ct = sBuf.getShort(spPos).toInt() and 0xFFFF
                val cs = sBuf.getInt(spPos + 4)
                if (cs < 8 || spPos + cs > splitBytes.size) break
                if (ct == RES_STRING_POOL_TYPE && sSpOff < 0) {
                    sSpOff = spPos
                    sSpSize = cs
                } else if (ct == RES_TABLE_PACKAGE_TYPE && sPkgOff < 0) {
                    sPkgOff = spPos
                    sPkgSize = cs
                }
                spPos += cs
            }
            if (sSpOff < 0 || sPkgOff < 0) continue

            val splitStrings = ArscLocalePruner.readStringPool(splitBytes, sSpOff, sSpSize)
            val remap = IntArray(splitStrings.size) { i -> internSplitString(splitStrings[i]) }

            val sPkgHeader = sBuf.getShort(sPkgOff + 2).toInt() and 0xFFFF
            var p = sPkgOff + sPkgHeader
            val end = sPkgOff + sPkgSize
            while (p + 8 <= end) {
                val ct = sBuf.getShort(p).toInt() and 0xFFFF
                val ch = sBuf.getShort(p + 2).toInt() and 0xFFFF
                val cs = sBuf.getInt(p + 4)
                if (cs < 8 || p + cs > end) break

                when (ct) {
                    RES_TABLE_TYPE_SPEC_TYPE -> {
                        val typeId = splitBytes[p + 8].toInt() and 0xFF
                        if (seenTypeSpecs.add(typeId)) {
                            basePkgBody.write(splitBytes, p, cs)
                        }
                    }
                    RES_TABLE_TYPE_TYPE -> {
                        if (ch >= 32 && p + 32 <= end) {
                            val typeId = splitBytes[p + 8].toInt() and 0xFF
                            val cfgSize = sBuf.getInt(p + 20).coerceIn(4, ch - 20)
                            val sig = "$typeId:${splitBytes.copyOfRange(p + 20, p + 20 + cfgSize).joinToString(",")}"
                            if (seenTypeConfigs.add(sig)) {
                                val chunkCopy = splitBytes.copyOfRange(p, p + cs)
                                remapTypeChunkStrings(chunkCopy, ch, remap)
                                basePkgBody.write(chunkCopy)
                            }
                        }
                    }
                }
                p += cs
            }
        }

        // Rebuild merged global StringPool as UTF-16LE
        val rebuiltSp = encodeUtf16StringPool(
            strings = mergedStrings,
            styleCount = baseStyleCount,
            originalFlags = baseFlags,
            styleOffsetsBytes = baseStyleOffsetsBytes,
            styleDataBytes = baseStyleDataBytes
        )
        val mergedPkgBytes = basePkgBody.toByteArray()
        ByteBuffer.wrap(mergedPkgBytes).order(ByteOrder.LITTLE_ENDIAN).putInt(4, mergedPkgBytes.size)

        val finalOut = ByteArrayOutputStream(rootHeaderSize + rebuiltSp.size + mergedPkgBytes.size)
        val rootHdr = baseArsc.copyOfRange(0, rootHeaderSize)
        finalOut.write(rootHdr)
        finalOut.write(rebuiltSp)
        finalOut.write(mergedPkgBytes)
        val result = finalOut.toByteArray()
        ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN).putInt(4, result.size)
        return result
    }

    private fun remapTypeChunkStrings(chunk: ByteArray, headerSize: Int, remap: IntArray) {
        if (chunk.size < 20) return
        val buf = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
        val flags = chunk[9].toInt() and 0xFF
        val entryCount = buf.getInt(12)
        val entriesStart = buf.getInt(16)
        if (entryCount <= 0 || entriesStart < headerSize || entriesStart > chunk.size) return

        val isSparse = (flags and FLAG_SPARSE) != 0
        val isOffset16 = (flags and FLAG_OFFSET16) != 0

        for (i in 0 until entryCount) {
            val relOff = when {
                isSparse -> {
                    val p = headerSize + i * 4
                    if (p + 4 > entriesStart) break
                    val off16 = buf.getShort(p + 2).toInt() and 0xFFFF
                    if (off16 == 0xFFFF) -1 else off16 * 4
                }
                isOffset16 -> {
                    val p = headerSize + i * 2
                    if (p + 2 > entriesStart) break
                    val off16 = buf.getShort(p).toInt() and 0xFFFF
                    if (off16 == 0xFFFF) -1 else off16 * 4
                }
                else -> {
                    val p = headerSize + i * 4
                    if (p + 4 > entriesStart) break
                    buf.getInt(p)
                }
            }
            if (relOff < 0) continue
            val ePos = entriesStart + relOff
            if (ePos + 8 > chunk.size) continue

            val eFlags = buf.getShort(ePos + 2).toInt() and 0xFFFF
            if ((eFlags and ENTRY_FLAG_COMPACT) != 0) {
                val dataType = (eFlags ushr 8) and 0xFF
                if (dataType == TYPE_STRING) {
                    val oldIdx = buf.getInt(ePos + 4)
                    if (oldIdx in remap.indices) buf.putInt(ePos + 4, remap[oldIdx])
                }
            } else if ((eFlags and ENTRY_FLAG_COMPLEX) == 0) {
                if (ePos + 16 <= chunk.size && (chunk[ePos + 11].toInt() and 0xFF) == TYPE_STRING) {
                    val oldIdx = buf.getInt(ePos + 12)
                    if (oldIdx in remap.indices) buf.putInt(ePos + 12, remap[oldIdx])
                }
            } else if (ePos + 16 <= chunk.size) {
                val mapCount = buf.getInt(ePos + 12).coerceIn(0, 512)
                var mPos = ePos + 16
                for (m in 0 until mapCount) {
                    if (mPos + 12 > chunk.size) break
                    if ((chunk[mPos + 7].toInt() and 0xFF) == TYPE_STRING) {
                        val oldIdx = buf.getInt(mPos + 8)
                        if (oldIdx in remap.indices) buf.putInt(mPos + 8, remap[oldIdx])
                    }
                    mPos += 12
                }
            }
        }
    }

    private fun encodeUtf16StringPool(
        strings: List<String>,
        styleCount: Int,
        originalFlags: Int,
        styleOffsetsBytes: ByteArray,
        styleDataBytes: ByteArray
    ): ByteArray {
        val stringDataOut = ByteArrayOutputStream()
        val offsets = IntArray(strings.size)
        for (i in strings.indices) {
            offsets[i] = stringDataOut.size()
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

        val paddedStrings = stringDataOut.toByteArray()
        val headerSize = 28
        val stringsStart = headerSize + strings.size * 4 + styleOffsetsBytes.size
        val stylesStart = if (styleCount > 0 && styleDataBytes.isNotEmpty()) {
            stringsStart + paddedStrings.size
        } else 0
        val unpaddedSize = stringsStart + paddedStrings.size + styleDataBytes.size
        val spPad = (4 - (unpaddedSize % 4)) % 4
        val totalSize = unpaddedSize + spPad

        return ByteArrayOutputStream(totalSize).apply {
            writeShortLE(RES_STRING_POOL_TYPE)
            writeShortLE(headerSize)
            writeIntLE(totalSize)
            writeIntLE(strings.size)
            writeIntLE(styleCount)
            writeIntLE(originalFlags and 0x0101.inv())
            writeIntLE(stringsStart)
            writeIntLE(stylesStart)
            offsets.forEach { writeIntLE(it) }
            write(styleOffsetsBytes)
            write(paddedStrings)
            write(styleDataBytes)
            repeat(spPad) { write(0) }
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
