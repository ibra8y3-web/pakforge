package com.example.core.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Stamps a crisp high-contrast "PF" badge on cloned APK launcher PNG icons so users can
 * immediately tell the compressed clone apart from the original app on their home screen.
 */
object CloneIconStyler {

    fun isLauncherPng(path: String): Boolean {
        val lower = path.lowercase(Locale.US)
        if (!lower.endsWith(".png") || lower.endsWith(".9.png")) return false
        return (lower.startsWith("res/mipmap-") || lower.startsWith("res/drawable-")) &&
            lower.contains("ic_launcher")
    }

    fun badgeIfLauncherPng(path: String, rawBytes: ByteArray): ByteArray {
        if (!isLauncherPng(path) || rawBytes.isEmpty()) return rawBytes
        val decoded = runCatching {
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
        }.getOrNull() ?: return rawBytes

        return try {
            val w = decoded.width.coerceAtLeast(1)
            val h = decoded.height.coerceAtLeast(1)
            if (w < 24 || h < 24) return rawBytes

            val mutable = decoded.copy(Bitmap.Config.ARGB_8888, true) ?: return rawBytes
            val canvas = Canvas(mutable)
            val badgeH = (h * 0.34f).coerceAtLeast(14f)
            val badgeW = (w * 0.48f).coerceAtLeast(20f)
            val margin = (w * 0.05f).coerceAtLeast(2f)
            val rect = RectF(
                w - badgeW - margin,
                h - badgeH - margin,
                w - margin,
                h - margin
            )

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#00C896")
                style = Paint.Style.FILL
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = (w * 0.03f).coerceAtLeast(1.5f)
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#042419")
                textSize = badgeH * 0.64f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }

            val radius = badgeH * 0.32f
            canvas.drawRoundRect(rect, radius, radius, bgPaint)
            canvas.drawRoundRect(rect, radius, radius, borderPaint)
            val textY = rect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText("PF", rect.centerX(), textY, textPaint)

            val out = ByteArrayOutputStream(rawBytes.size)
            val ok = mutable.compress(Bitmap.CompressFormat.PNG, 100, out)
            mutable.recycle()
            if (ok) out.toByteArray() else rawBytes
        } catch (_: Throwable) {
            rawBytes
        } finally {
            decoded.recycle()
        }
    }
}
