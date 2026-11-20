package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ForgeBrandEnd
import com.example.ui.theme.ForgeBrandMid
import com.example.ui.theme.ForgeBrandStart

/**
 * Premium "aurora" backdrop: a deep gradient with two soft colour glows.
 * Replaces the flat near-black background everywhere.
 */
@Composable
fun Modifier.forgeBackdrop(): Modifier {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    val top = cs.background
    val bottom = lerp(cs.background, cs.tertiary, if (dark) 0.14f else 0.07f)
    val primary = cs.primary
    val tertiary = cs.tertiary
    val glowA = if (dark) 0.20f else 0.13f
    val glowB = if (dark) 0.26f else 0.12f
    return this
        .background(Brush.verticalGradient(listOf(top, bottom)))
        .drawBehind {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = glowA), Color.Transparent),
                    center = Offset(size.width * 0.92f, size.height * 0.04f),
                    radius = size.width * 0.95f
                )
            )
            drawRect(
                Brush.radialGradient(
                    colors = listOf(tertiary.copy(alpha = glowB), Color.Transparent),
                    center = Offset(size.width * 0.04f, size.height * 0.96f),
                    radius = size.width * 1.1f
                )
            )
        }
}

@Composable
fun ForgeBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize().forgeBackdrop(), content = content)
}

/** Brand gradient (blue → violet → teal) used for logo, hero and accents. */
fun forgeBrandBrush(): Brush = Brush.linearGradient(
    colors = listOf(ForgeBrandStart, ForgeBrandMid, ForgeBrandEnd)
)

/** Circular gradient-ringed icon badge used by every empty state. */
@Composable
fun ForgeEmptyIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(cs.primary.copy(alpha = 0.22f), cs.tertiary.copy(alpha = 0.22f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size - 14.dp)
                .clip(CircleShape)
                .background(cs.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(size * 0.42f)
            )
        }
    }
}


/** Rounded square icon tile with a soft accent tint; the visual anchor of every section header. */
@Composable
fun ForgeIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.32f), RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

/** Small rounded status/info badge. */
@Composable
fun ForgePill(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

/**
 * The single card used for every settings-style section: icon tile + title (+ subtitle)
 * header, then content. One padding, one radius, one spacing everywhere.
 */
@Composable
fun ForgeSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    highlighted: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (highlighted) accent.copy(alpha = 0.55f) else cs.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (icon != null) {
                    ForgeIconTile(icon = icon, accent = accent)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
                if (trailing != null) trailing()
            }
            content()
        }
    }
}

/** Consistent filled-outline text field colours for every input in the app. */
@Composable
fun forgeFieldColors(): TextFieldColors {
    val cs = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = cs.surface,
        unfocusedContainerColor = cs.surface,
        focusedBorderColor = cs.primary,
        unfocusedBorderColor = cs.outline
    )
}
