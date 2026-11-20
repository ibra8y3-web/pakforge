package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = ForgeEmerald,
    onPrimary = ForgeOnEmerald,
    primaryContainer = ForgeEmeraldContainer,
    onPrimaryContainer = ForgeEmerald,
    secondary = ForgeAmber,
    onSecondary = ForgeOnAmber,
    secondaryContainer = ForgeAmberContainer,
    onSecondaryContainer = ForgeAmber,
    tertiary = ForgeViolet,
    onTertiary = ForgeOnViolet,
    tertiaryContainer = ForgeVioletContainer,
    onTertiaryContainer = ForgeViolet,
    background = ObsidianBg,
    onBackground = ObsidianTextPrimary,
    surface = ObsidianSurface,
    onSurface = ObsidianTextPrimary,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = ObsidianTextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceHigh,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    surfaceContainerHighest = ObsidianSurfaceHighest,
    surfaceTint = Color.Transparent,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutline.copy(alpha = 0.55f),
    error = TelemetryError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = PrecisionPrimary,
    onPrimary = Color.White,
    primaryContainer = PrecisionPrimaryContainer,
    onPrimaryContainer = Color(0xFF00201C),
    secondary = PrecisionSecondary,
    onSecondary = Color.White,
    secondaryContainer = PrecisionSecondaryContainer,
    onSecondaryContainer = Color(0xFF451A03),
    tertiary = Color(0xFF5B49E0),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE4E0FF),
    onTertiaryContainer = Color(0xFF1A1050),
    background = PrecisionBg,
    onBackground = PrecisionTextPrimary,
    surface = PrecisionSurface,
    onSurface = PrecisionTextPrimary,
    surfaceVariant = PrecisionSurfaceHigh,
    onSurfaceVariant = PrecisionTextSecondary,
    surfaceContainerLowest = PrecisionSurface,
    surfaceContainerLow = PrecisionSurface,
    surfaceContainer = PrecisionSurfaceHigh,
    surfaceContainerHigh = Color(0xFFDCE3FA),
    surfaceContainerHighest = Color(0xFFD2DBF7),
    surfaceTint = Color.Transparent,
    outline = PrecisionOutline,
    outlineVariant = PrecisionOutline.copy(alpha = 0.6f),
    error = TelemetryError,
    onError = Color.White
)

val PakForgeShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun PakForgeTheme(
    themeMode: String = "dark",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PakForgeTypography,
        shapes = PakForgeShapes,
        content = content
    )
}
