/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/designsystem/src/main/kotlin/com/fenix/studio/core/designsystem/theme/FenixThemeMode.kt
 * ================================================================================
 */

package com.fenix.studio.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Modos cromáticos accesibles del Sistema de Diseño de Adaptabilidad Inversa (WCAG 2.2 AAA).
 */
enum class FenixThemeMode {
    LIGHT,
    DARK,
    HIGH_CONTRAST_OLED,
}

/**
 * Perfiles de zona táctil mínima (Macro-UI) para usuarios con desafíos motores finos.
 */
enum class FenixTouchTargetProfile(val minSize: Dp) {
    STANDARD(48.dp),
    MACRO_MEDIUM(56.dp),
    MACRO_LARGE(64.dp),
}

/**
 * Configuración inmutable de accesibilidad transversal para Fenix Studio.
 */
@Immutable
data class FenixAccessibilityConfig(
    val themeMode: FenixThemeMode = FenixThemeMode.LIGHT,
    val useOpenDyslexicFont: Boolean = false,
    val touchTargetProfile: FenixTouchTargetProfile = FenixTouchTargetProfile.STANDARD,
    val fontScaleMultiplier: Float = 1.0f,
)
