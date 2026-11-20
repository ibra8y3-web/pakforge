package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.engine.CategoryBreakdown
import com.example.core.engine.PackageCategory
import com.example.core.engine.formatBytes
import com.example.core.telemetry.DeviceTelemetryState
import com.example.ui.theme.BucketArsc
import com.example.ui.theme.BucketDex
import com.example.ui.theme.BucketManifest
import com.example.ui.theme.BucketMedia
import com.example.ui.theme.BucketMeta
import com.example.ui.theme.BucketNative
import com.example.ui.theme.TelemetryError
import com.example.ui.theme.TelemetrySuccess
import com.example.ui.theme.TelemetryWarning

@Composable
fun DrawableAppIcon(
    drawable: Drawable?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val bitmap: ImageBitmap? = remember(drawable) {
        drawable?.let { d ->
            val w = d.intrinsicWidth.coerceIn(48, 192)
            val h = d.intrinsicHeight.coerceIn(48, 192)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            d.setBounds(0, 0, canvas.width, canvas.height)
            d.draw(canvas)
            bmp.asImageBitmap()
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Surface(
            modifier = modifier.size(size),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Storage,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun HeroForgeBanner(
    telemetry: DeviceTelemetryState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(forgeBrandBrush())) {
            Image(
                painter = painterResource(id = R.drawable.img_forge_hero),
                contentDescription = stringResource(R.string.hero_title),
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.16f),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 56.dp, y = (-64).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
            )
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-40).dp, y = 48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.07f))
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = 0.40f)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.hero_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.hero_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )

                Text(
                    text = stringResource(R.string.hero_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE3E8FF)
                )

                Spacer(modifier = Modifier.height(2.dp))
                TelemetryRibbon(telemetry = telemetry)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelemetryRibbon(
    telemetry: DeviceTelemetryState,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TelemetryPill(
            icon = Icons.Rounded.Memory,
            label = stringResource(R.string.telemetry_ram),
            value = "${telemetry.freeRamMb} MB",
            accent = MaterialTheme.colorScheme.primary
        )
        TelemetryPill(
            icon = Icons.Rounded.Speed,
            label = stringResource(R.string.telemetry_cores),
            value = "${telemetry.cpuCores}x",
            accent = MaterialTheme.colorScheme.tertiary
        )
        TelemetryPill(
            icon = Icons.Rounded.Bolt,
            label = stringResource(R.string.telemetry_buffer),
            value = "${telemetry.recommendedBufferKb} KB",
            accent = if (telemetry.isPowerSaveMode || telemetry.isLowRamDevice) {
                TelemetryWarning
            } else {
                TelemetrySuccess
            }
        )
    }
}

@Composable
private fun TelemetryPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0x33FFFFFF),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "$label:",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFDCE2FF)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun categoryColor(category: PackageCategory): Color = when (category) {
    PackageCategory.DEX_BYTECODE -> BucketDex
    PackageCategory.NATIVE_LIBS -> BucketNative
    PackageCategory.RESOURCES_ARSC -> BucketArsc
    PackageCategory.MANIFEST_XML -> BucketManifest
    PackageCategory.MEDIA_ASSETS -> BucketMedia
    PackageCategory.METADATA_SIGNING -> BucketMeta
    PackageCategory.OTHER -> Color(0xFF64748B)
}

@Composable
fun categoryLabel(category: PackageCategory): String = when (category) {
    PackageCategory.DEX_BYTECODE -> stringResource(R.string.cat_dex)
    PackageCategory.NATIVE_LIBS -> stringResource(R.string.cat_native)
    PackageCategory.RESOURCES_ARSC -> stringResource(R.string.cat_arsc)
    PackageCategory.MANIFEST_XML -> stringResource(R.string.cat_manifest)
    PackageCategory.MEDIA_ASSETS -> stringResource(R.string.cat_media)
    PackageCategory.METADATA_SIGNING -> stringResource(R.string.cat_meta)
    PackageCategory.OTHER -> "Other"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PackageBreakdownCanvasBar(
    breakdown: List<CategoryBreakdown>,
    totalRawBytes: Long,
    modifier: Modifier = Modifier
) {
    val safeTotal = totalRawBytes.coerceAtLeast(1L).toFloat()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            var xOffset = 0f
            breakdown.forEach { bucket ->
                val widthFraction = (bucket.rawBytes.toFloat() / safeTotal).coerceIn(0f, 1f)
                val segWidth = (size.width * widthFraction)
                if (segWidth > 1f) {
                    drawRect(
                        color = categoryColor(bucket.category),
                        topLeft = Offset(xOffset, 0f),
                        size = Size(segWidth, size.height)
                    )
                    xOffset += segWidth
                }
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            breakdown.forEach { bucket ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(categoryColor(bucket.category))
                    )
                    Text(
                        text = "${categoryLabel(bucket.category)} (${formatBytes(bucket.rawBytes)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun EntropyGauge(
    entropy: Double,
    modifier: Modifier = Modifier
) {
    val fraction = (entropy / 8.0).toFloat().coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(),
        label = "entropy_bar"
    )
    val tint = when {
        entropy > 7.85 -> TelemetryWarning
        entropy > 6.0 -> MaterialTheme.colorScheme.tertiary
        else -> TelemetrySuccess
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            drawRoundRect(
                color = tint,
                topLeft = Offset.Zero,
                size = Size(size.width * animatedFraction, size.height),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
        Text(
            text = String.format("%.2f b/B", entropy),
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

@Composable
fun CompressionSavingsRing(
    savedPercent: Double,
    label: String,
    accentColor: Color = TelemetrySuccess,
    modifier: Modifier = Modifier
) {
    val sweepTarget = ((savedPercent / 100.0).toFloat().coerceIn(0f, 1f)) * 360f
    val animatedSweep by animateFloatAsState(
        targetValue = sweepTarget,
        animationSpec = spring(stiffness = 220f),
        label = "savings_ring_sweep"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center
        ) {
            val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 7.dp.toPx()
                val inset = strokeWidth / 2f
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
                if (animatedSweep > 1f) {
                    drawArc(
                        color = accentColor,
                        startAngle = -90f,
                        sweepAngle = animatedSweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = strokeWidth,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                }
            }
            Text(
                text = String.format(java.util.Locale.US, "-%.0f%%", savedPercent.coerceIn(0.0, 99.9)),
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

