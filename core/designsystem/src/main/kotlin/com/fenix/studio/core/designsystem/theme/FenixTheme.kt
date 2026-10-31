/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/designsystem/src/main/kotlin/com/fenix/studio/core/designsystem/theme/FenixTheme.kt
 * ================================================================================
 */

package com.fenix.studio.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalFenixThemeMode = staticCompositionLocalOf { FenixThemeMode.LIGHT }

val LocalFenixColors = staticCompositionLocalOf { FenixPalettes.LightAAA }

val LocalFenixAccessibilityConfig = staticCompositionLocalOf { FenixAccessibilityConfig() }

/**
 * Punto de acceso de solo lectura a los tokens semánticos e invariantes de accesibilidad de Fenix Studio.
 */
object FenixTheme {
    val colors: FenixSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFenixColors.current

    val mode: FenixThemeMode
        @Composable
        @ReadOnlyComposable
        get() = LocalFenixThemeMode.current

    val accessibilityConfig: FenixAccessibilityConfig
        @Composable
        @ReadOnlyComposable
        get() = LocalFenixAccessibilityConfig.current
}

private fun FenixSemanticColors.toMaterialColorScheme(mode: FenixThemeMode): ColorScheme {
    return when (mode) {
        FenixThemeMode.LIGHT -> lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant,
            error = error,
            outline = outline,
        )
        FenixThemeMode.DARK, FenixThemeMode.HIGH_CONTRAST_OLED -> darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant,
            error = error,
            outline = outline,
        )
    }
}

/**
 * Contenedor raíz del Sistema de Diseño de Adaptabilidad Inversa de Fenix Studio.
 */
@Composable
fun FenixTheme(
    config: FenixAccessibilityConfig = FenixAccessibilityConfig(),
    content: @Composable () -> Unit,
) {
    val semanticColors = FenixPalettes.forMode(config.themeMode)
    val materialColorScheme = semanticColors.toMaterialColorScheme(config.themeMode)
    val typography = FenixTypographyFactory.createTypography(
        useOpenDyslexicFont = config.useOpenDyslexicFont,
        fontScaleMultiplier = config.fontScaleMultiplier,
    )

    CompositionLocalProvider(
        LocalFenixThemeMode provides config.themeMode,
        LocalFenixColors provides semanticColors,
        LocalFenixTouchTargetSize provides config.touchTargetProfile.minSize,
        LocalFenixAccessibilityConfig provides config,
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = typography,
            content = content,
        )
    }
}
