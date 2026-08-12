package com.noop.ui.designsystem

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = NoopBrandColors.Moss900,
    onPrimary = Color.White,
    primaryContainer = NoopBrandColors.Moss700,
    onPrimaryContainer = Color(0xFFBDE2C5),
    secondary = NoopBrandColors.Clay700,
    tertiary = NoopBrandColors.Mist700,
    background = NoopBrandColors.Canvas,
    onBackground = NoopBrandColors.Ink,
    surface = NoopBrandColors.SurfaceLowest,
    onSurface = NoopBrandColors.Ink,
    surfaceVariant = NoopBrandColors.Surface,
    onSurfaceVariant = NoopBrandColors.InkMuted,
    outline = NoopBrandColors.Outline,
    outlineVariant = NoopBrandColors.OutlineSoft,
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFABCDB3),
    onPrimary = Color(0xFF173822),
    primaryContainer = Color(0xFF2D4E38),
    onPrimaryContainer = Color(0xFFC6ECCE),
    secondary = Color(0xFFE4BEB1),
    tertiary = Color(0xFFB0CAD9),
    background = NoopBrandColors.DarkCanvas,
    onBackground = NoopBrandColors.DarkInk,
    surface = NoopBrandColors.DarkSurfaceLow,
    onSurface = NoopBrandColors.DarkInk,
    surfaceVariant = NoopBrandColors.DarkSurface,
    onSurfaceVariant = NoopBrandColors.DarkInkMuted,
    outline = Color(0xFF8B958E),
    outlineVariant = Color(0xFF3D4640),
    error = Color(0xFFFFB4AB),
)

val LocalNoopSemanticColors = staticCompositionLocalOf { NoopLightSemanticColors }

object NoopTheme {
    val semanticColors: NoopSemanticColors
        @Composable get() = LocalNoopSemanticColors.current
}

@Composable
fun NoopDesignTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val semantics = if (darkTheme) NoopDarkSemanticColors else NoopLightSemanticColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalNoopSemanticColors provides semantics) {
        MaterialTheme(
            colorScheme = colors,
            typography = NoopTypography,
            content = content,
        )
    }
}
