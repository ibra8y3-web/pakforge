package com.example.core.engine

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

data class LocalePruneResult(
    val patchedArsc: ByteArray,
    val prunedConfigsCount: Int,
    val keptLocales: Set<String>,
    val safeOrphanResPaths: Set<String>
)

/**
 * Real binary resources.arsc locale pruner.
 * Filters ResTable_type chunks by ResTable_config.language while strictly preserving all
 * default/fallback configs (language == "") and all selected locales, updating package and
 * table chunk sizes accurately. Also identifies resource file paths orphaned strictly by dropped locales.
 */
object ArscLocalePruner {

    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_TABLE_TYPE = 0x0002
    private const val RES_TABLE_PACKAGE_TYPE = 0x0200
    private const val RES_TABLE_TYPE_TYPE = 0x0201
    private const val FLAG_SPARSE = 0x01
    private const val FLAG_OFFSET16 = 0x02
    private const val ENTRY_FLAG_COMPLEX = 0x0001
    private const val ENTRY_FLAG_COMPACT = 0x0008
    private const val TYPE_STRING = 0x03

    fun extractAvailableLocales(rawArsc: ByteArray): List<String> {
        if (rawArsc.size < 12) return emptyList()
        val buf = ByteBuffer.wrap(rawArsc).order(ByteOrder.LITTLE_ENDIAN)
        if ((buf.getShort(0).toInt() and 0xFFFF) != RES_TABLE_TYPE) return emptyList()
        val rootHeaderSize = buf.getShort(2).toInt() and 0xFFFF
        if (rootHeaderSize < 12) return emptyList()

        val locales = LinkedHashSet<String>()
        var pos = rootHeaderSize
        while (pos + 8 <= rawArsc.size) {
            val type = buf.getShort(pos).toInt() and 0xFFFF
            val headerSize = buf.getShort(pos + 2).toInt() and 0xFFFF
            val size = buf.getInt(pos + 4)
            if (size < 8 || pos + size > rawArsc.size) break
            if (type == RES_TABLE_PACKAGE_TYPE && headerSize in 8..size) {
                var inner = pos + headerSize
                val pkgEnd = pos + size
                while (inner + 8 <= pkgEnd) {
                    val cType = buf.getShort(inner).toInt() and 0xFFFF
                    val cHeader = buf.getShort(inner + 2).toInt() and 0xFFFF
                    val cSize = buf.getInt(inner + 4)
                    if (cSize < 8 || inner + cSize > pkgEnd) break
                    if (cType == RES_TABLE_TYPE_TYPE && cHeader >= 32 && inner + 32 <= pkgEnd) {
                        val lang = readConfigLanguage(rawArsc, inner + 20)
                        if (lang.isNotEmpty()) locales.add(lang)
                    }
                    inner += cSize
                }
            }
            pos += size
        }
        return locales.sorted()
    }

    fun pruneUnusedLocales(
        rawArsc: ByteArray,
        allowedLocales: Set<String>
    ): LocalePruneResult {
        if (rawArsc.size < 12 || allowedLocales.isEmpty()) {
            return LocalePruneResult(rawArsc, 0, allowedLocales, emptySet())
        }
        val buf = ByteBuffer.wrap(rawArsc).order(ByteOrder.LITTLE_ENDIAN)
        if ((buf.getShort(0).toInt() and 0xFFFF) != RES_TABLE_TYPE) {
            return LocalePruneResult(rawArsc, 0, allowedLocales, emptySet())
        }
        val rootHeaderSize = buf.getShort(2).toInt() and 0xFFFF
        if (rootHeaderSize < 12 || rootHeaderSize > rawArsc.size) {
            return LocalePruneResult(rawArsc, 0, allowedLocales, emptySet())
        }

        val normalizedAllowed = allowedLocales.map { it.lowercase().substringBefore('-').substringBefore('_') }.toSet()
        var globalStrings: List<String> = emptyList()
        val keptResPaths = HashSet<String>()
        val droppedResPaths = HashSet<String>()
        var prunedCount = 0

        val out = ByteArrayOutputStream(rawArsc.size)
        out.write(rawArsc, 0, rootHeaderSize)

        var pos = rootHeaderSize
        while (pos + 8 <= rawArsc.size) {
            val cType = buf.getShort(pos).toInt() and 0xFFFF
            val cHeader = buf.getShort(pos + 2).toInt() and 0xFFFF
            val cSize = buf.getInt(pos + 4)
            if (cSize < 8 || pos + cSize > rawArsc.size) {
                out.write(rawArsc, pos, rawArsc.size - pos)
                break
            }

            if (cType == RES_STRING_POOL_TYPE && globalStrings.isEmpty()) {
                globalStrings = readStringPool(rawArsc, pos, cSize)
                out.write(rawArsc, pos, cSize)
            } else if (cType == RES_TABLE_PACKAGE_TYPE && cHeader in 8..cSize) {
                val pkgOut = ByteArrayOutputStream(cSize)
                pkgOut.write(rawArsc, pos, cHeader)

                var inner = pos + cHeader
                val pkgEnd = pos + cSize
                while (inner + 8 <= pkgEnd) {
                    val icType = buf.getShort(inner).toInt() and 0xFFFF
                    val icHeader = buf.getShort(inner + 2).toInt() and 0xFFFF
                    val icSize = buf.getInt(inner + 4)
                    if (icSize < 8 || inner + icSize > pkgEnd) {
                        pkgOut.write(rawArsc, inner, pkgEnd - inner)
                        break
                    }

                    if (icType == RES_TABLE_TYPE_TYPE && icHeader >= 32 && inner + 32 <= pkgEnd) {
                        val lang = readConfigLanguage(rawArsc, inner + 20)
                        val keep = lang.isEmpty() || lang in normalizedAllowed
                        val referencedPaths = collectTypeStringPaths(rawArsc, inner, icSize, icHeader, globalStrings)
                        if (keep) {
                            keptResPaths.addAll(referencedPaths)
                            pkgOut.write(rawArsc, inner, icSize)
                        } else {
                            droppedResPaths.addAll(referencedPaths)
                            prunedCount++
                        }
                    } else {
                        pkgOut.write(rawArsc, inner, icSize)
                    }
                    inner += icSize
                }

                val pkgBytes = pkgOut.toByteArray()
                ByteBuffer.wrap(pkgBytes).order(ByteOrder.LITTLE_ENDIAN).putInt(4, pkgBytes.size)
                out.write(pkgBytes)
            } else {
                out.write(rawArsc, pos, cSize)
            }
            pos += cSize
        }

        val finalBytes = out.toByteArray()
        ByteBuffer.wrap(finalBytes).order(ByteOrder.LITTLE_ENDIAN).putInt(4, finalBytes.size)
        val orphanPaths = (droppedResPaths - keptResPaths).filter { it.startsWith("res/") }.toSet()
        return LocalePruneResult(
            patchedArsc = finalBytes,
            prunedConfigsCount = prunedCount,
            keptLocales = normalizedAllowed,
            safeOrphanResPaths = orphanPaths
        )
    }

    private fun readConfigLanguage(data: ByteArray, configOffset: Int): String {
        val b0 = data[configOffset + 8].toInt() and 0xFF
        val b1 = data[configOffset + 9].toInt() and 0xFF
        if (b0 == 0 && b1 == 0) return ""
        if ((b0 and 0x80) != 0) {
            // Packed 3-letter ISO-639-2
            val c1 = ((b1 and 0x1F) + 'a'.code).toChar()
            val c2 = ((((b1 ushr 5) and 0x07) or ((b0 and 0x03) shl 3)) + 'a'.code).toChar()
            val c3 = (((b0 ushr 2) and 0x1F) + 'a'.code).toChar()
            return charArrayOf(c1, c2, c3).concatToString().lowercase()
        }
        return charArrayOf(b0.toChar(), b1.toChar()).concatToString().lowercase()
    }

    private fun collectTypeStringPaths(
        data: ByteArray,
        chunkStart: Int,
        chunkSize: Int,
        headerSize: Int,
        globalStrings: List<String>
    ): List<String> {
        if (globalStrings.isEmpty() || chunkStart + 20 > data.size) return emptyList()
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val flags = data[chunkStart + 9].toInt() and 0xFF
        val entryCount = buf.getInt(chunkStart + 12)
        val entriesStart = buf.getInt(chunkStart + 16)
        if (entryCount <= 0 || entriesStart < headerSize || entriesStart > chunkSize) return emptyList()

        val offsetsBase = chunkStart + headerSize
        val dataBase = chunkStart + entriesStart
        val chunkEnd = chunkStart + chunkSize
        val isSparse = (flags and FLAG_SPARSE) != 0
        val isOffset16 = (flags and FLAG_OFFSET16) != 0

        val found = ArrayList<String>()
        for (i in 0 until entryCount) {
            val relOff = when {
                isSparse -> {
                    val p = offsetsBase + i * 4
                    if (p + 4 > dataBase) break
                    val off16 = buf.getShort(p + 2).toInt() and 0xFFFF
                    if (off16 == 0xFFFF) -1 else off16 * 4
                }
                isOffset16 -> {
                    val p = offsetsBase + i * 2
                    if (p + 2 > dataBase) break
                    val off16 = buf.getShort(p).toInt() and 0xFFFF
                    if (off16 == 0xFFFF) -1 else off16 * 4
                }
                else -> {
                    val p = offsetsBase + i * 4
                    if (p + 4 > dataBase) break
                    buf.getInt(p)
                }
            }
            if (relOff < 0) continue
            val ePos = dataBase + relOff
            if (ePos + 8 > chunkEnd) continue

            val eFlags = buf.getShort(ePos + 2).toInt() and 0xFFFF
            if ((eFlags and ENTRY_FLAG_COMPACT) != 0) {
                val dataType = (eFlags ushr 8) and 0xFF
                if (dataType == TYPE_STRING) {
                    val strIdx = buf.getInt(ePos + 4)
                    globalStrings.getOrNull(strIdx)?.takeIf { it.startsWith("res/") }?.let { found.add(it) }
                }
            } else if ((eFlags and ENTRY_FLAG_COMPLEX) == 0) {
                if (ePos + 16 <= chunkEnd) {
                    val dataType = data[ePos + 11].toInt() and 0xFF
                    if (dataType == TYPE_STRING) {
                        val strIdx = buf.getInt(ePos + 12)
                        globalStrings.getOrNull(strIdx)?.takeIf { it.startsWith("res/") }?.let { found.add(it) }
                    }
                }
            } else if (ePos + 16 <= chunkEnd) {
                val mapCount = buf.getInt(ePos + 12).coerceIn(0, 256)
                var mPos = ePos + 16
                for (m in 0 until mapCount) {
                    if (mPos + 12 > chunkEnd) break
                    val dataType = data[mPos + 7].toInt() and 0xFF
                    if (dataType == TYPE_STRING) {
                        val strIdx = buf.getInt(mPos + 8)
                        globalStrings.getOrNull(strIdx)?.takeIf { it.startsWith("res/") }?.let { found.add(it) }
                    }
                    mPos += 12
                }
            }
        }
        return found
    }

    internal fun readStringPool(data: ByteArray, spOffset: Int, spSize: Int): List<String> {
        if (spOffset + 28 > data.size || spOffset + spSize > data.size) return emptyList()
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val headerSize = buf.getShort(spOffset + 2).toInt() and 0xFFFF
        val strCount = buf.getInt(spOffset + 8)
        val flags = buf.getInt(spOffset + 16)
        val stringsStart = buf.getInt(spOffset + 20)
        val isUtf8 = (flags and 0x0100) != 0
        val offsetsBase = spOffset + headerSize
        val dataBase = spOffset + stringsStart
        val result = ArrayList<String>(strCount.coerceAtLeast(0))
        for (i in 0 until strCount) {
            val rel = buf.getInt(offsetsBase + i * 4)
            val abs = dataBase + rel
            if (abs < 0 || abs >= spOffset + spSize) {
                result.add("")
                continue
            }
            result.add(if (isUtf8) readUtf8(data, abs) else readUtf16(data, abs))
        }
        return result
    }

    private fun readUtf8(data: ByteArray, start: Int): String {
        var idx = start
        if (idx >= data.size) return ""
        if ((data[idx].toInt() and 0x80) != 0) idx += 2 else idx += 1
        if (idx >= data.size) return ""
        val b0 = data[idx].toInt() and 0xFF
        val byteLen = if ((b0 and 0x80) != 0 && idx + 1 < data.size) {
            val b1 = data[idx + 1].toInt() and 0xFF
            idx += 2
            ((b0 and 0x7F) shl 8) or b1
        } else {
            idx += 1
            b0
        }
        if (idx + byteLen > data.size) return ""
        return String(data, idx, byteLen, StandardCharsets.UTF_8)
    }

    private fun readUtf16(data: ByteArray, start: Int): String {
        var idx = start
        if (idx + 2 > data.size) return ""
        val s0 = (data[idx].toInt() and 0xFF) or ((data[idx + 1].toInt() and 0xFF) shl 8)
        idx += 2
        val charLen = if ((s0 and 0x8000) != 0 && idx + 2 <= data.size) {
            val s1 = (data[idx].toInt() and 0xFF) or ((data[idx + 1].toInt() and 0xFF) shl 8)
            idx += 2
            ((s0 and 0x7FFF) shl 16) or s1
        } else {
            s0
        }
        val byteLen = charLen * 2
        if (idx + byteLen > data.size) return ""
        return String(data, idx, byteLen, StandardCharsets.UTF_16LE)
    }
}
