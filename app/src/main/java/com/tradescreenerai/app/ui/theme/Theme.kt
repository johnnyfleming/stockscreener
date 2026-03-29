package com.tradescreenerai.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = DarkNavy,
    primaryContainer = SlateBlue,
    onPrimaryContainer = SoftBlue,
    secondary = AccentTeal,
    onSecondary = DarkNavy,
    secondaryContainer = Color(0xFF1A3A3A),
    onSecondaryContainer = AccentTeal,
    tertiary = AccentPurple,
    onTertiary = Color.White,
    background = DarkNavy,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDarkCard,
    onSurfaceVariant = TextSecondary,
    outline = DividerDark,
    outlineVariant = Color(0xFF1E2A3A),
    error = LossRed,
    onError = Color.White,
    inverseSurface = SurfaceLight,
    inverseOnSurface = TextPrimaryLight,
    surfaceContainerHighest = SurfaceDarkElevated,
    surfaceContainerHigh = SurfaceDarkCard,
    surfaceContainer = Color(0xFF152030),
    surfaceContainerLow = SurfaceDark,
    surfaceContainerLowest = DarkNavy
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    tertiary = AccentPurple,
    onTertiary = Color.White,
    background = SurfaceLight,
    onBackground = TextPrimaryLight,
    surface = Color.White,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFF0F3F8),
    onSurfaceVariant = TextSecondaryLight,
    outline = DividerLight,
    outlineVariant = Color(0xFFE8ECF2),
    error = LossRedDark,
    onError = Color.White,
    inverseSurface = DarkNavy,
    inverseOnSurface = TextPrimary,
    surfaceContainerHighest = Color(0xFFE8ECF2),
    surfaceContainerHigh = Color(0xFFF0F3F8),
    surfaceContainer = Color(0xFFF5F7FA),
    surfaceContainerLow = Color(0xFFFAFBFD),
    surfaceContainerLowest = Color.White
)

@Composable
fun MyScreenerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}