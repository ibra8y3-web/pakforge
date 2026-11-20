package com.example.core.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Real Android BitmapFactory-powered image optimizer for res/ and assets/ raster images.
 * Strictly skips nine-patch (.9.png) files and small icons (< 12 KB), and commits
 * optimized bytes ONLY when genuine byte savings >= 12% are achieved.
 */
object AssetImageOptimizer {

    private const val MIN_IMAGE_BYTES = 12 * 1024

    fun isOptimizableImagePath(path: String): Boolean {
        val lower = path.lowercase(Locale.US)
        if (!lower.startsWith("res/") && !lower.startsWith("assets/")) return false
        if (lower.endsWith(".9.png")) return false
        return lower.endsWith(".png") ||
            lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".webp")
    }

    fun optimizeIfBeneficial(path: String, rawBytes: ByteArray): ByteArray {
        if (rawBytes.size < MIN_IMAGE_BYTES || !isOptimizableImagePath(path)) return rawBytes

        val lower = path.lowercase(Locale.US)
        val bitmap = runCatching {
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
        }.getOrNull() ?: return rawBytes

        return try {
            val out = ByteArrayOutputStream(rawBytes.size)
            val format: Bitmap.CompressFormat
            val quality: Int
            when {
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> {
                    format = Bitmap.CompressFormat.JPEG
                    quality = 85
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    format = Bitmap.CompressFormat.WEBP_LOSSLESS
                    quality = 100
                }
                else -> {
                    @Suppress("DEPRECATION")
                    format = Bitmap.CompressFormat.WEBP
                    quality = 92
                }
            }
            val compressed = bitmap.compress(format, quality, out)
            if (!compressed) return rawBytes
            val optimized = out.toByteArray()
            if (optimized.isNotEmpty() && optimized.size <= (rawBytes.size * 0.88).toInt()) {
                optimized
            } else {
                rawBytes
            }
        } catch (_: Throwable) {
            rawBytes
        } finally {
            bitmap.recycle()
        }
    }
}
