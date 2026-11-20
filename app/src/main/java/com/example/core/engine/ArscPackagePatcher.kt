package com.example.core.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * In-place binary patcher for Android resources.arsc ResTable_package.name[128].
 * Ensures cloned APKs keep Resources.getIdentifier(..., context.packageName) working
 * without altering any chunk offsets or string pool tables.
 */
object ArscPackagePatcher {

    private const val RES_TABLE_TYPE = 0x0002
    private const val RES_TABLE_PACKAGE_TYPE = 0x0200
    private const val PACKAGE_NAME_BYTES = 256 // 128 UTF-16LE chars

    fun patchPackageName(
        rawArsc: ByteArray,
        oldPackageName: String,
        newPackageName: String
    ): ByteArray {
        if (rawArsc.size < 12 || newPackageName.length >= 128) return rawArsc

        val out = rawArsc.copyOf()
        val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)

        val rootType = buf.getShort(0).toInt() and 0xFFFF
        val rootHeaderSize = buf.getShort(2).toInt() and 0xFFFF
        if (rootType != RES_TABLE_TYPE || rootHeaderSize < 12) return rawArsc

        var pos = rootHeaderSize
        while (pos + 8 <= out.size) {
            val chunkType = buf.getShort(pos).toInt() and 0xFFFF
            val chunkHeaderSize = buf.getShort(pos + 2).toInt() and 0xFFFF
            val chunkSize = buf.getInt(pos + 4)
            if (chunkSize < 8 || pos + chunkSize > out.size) break

            if (chunkType == RES_TABLE_PACKAGE_TYPE &&
                chunkHeaderSize >= 268 &&
                pos + 268 <= out.size
            ) {
                val existingName = readFixedUtf16Name(out, pos + 12)
                if (existingName == oldPackageName) {
                    java.util.Arrays.fill(out, pos + 12, pos + 12 + PACKAGE_NAME_BYTES, 0.toByte())
                    val newBytes = newPackageName.toByteArray(StandardCharsets.UTF_16LE)
                    System.arraycopy(newBytes, 0, out, pos + 12, newBytes.size.coerceAtMost(PACKAGE_NAME_BYTES - 2))
                }
            }
            pos += chunkSize
        }
        return out
    }

    private fun readFixedUtf16Name(data: ByteArray, offset: Int): String {
        val sb = StringBuilder()
        var i = 0
        while (i + 1 < PACKAGE_NAME_BYTES) {
            val ch = (data[offset + i].toInt() and 0xFF) or ((data[offset + i + 1].toInt() and 0xFF) shl 8)
            if (ch == 0) break
            sb.append(ch.toChar())
            i += 2
        }
        return sb.toString()
    }
}
