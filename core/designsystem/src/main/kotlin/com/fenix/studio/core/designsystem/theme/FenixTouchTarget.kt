/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/designsystem/src/main/kotlin/com/fenix/studio/core/designsystem/theme/FenixTouchTarget.kt
 * ================================================================================
 */

package com.fenix.studio.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Provee el tamaño mínimo de objetivo táctil (Macro-UI) a través del árbol de composición.
 */
val LocalFenixTouchTargetSize = staticCompositionLocalOf<Dp> {
    FenixTouchTargetProfile.STANDARD.minSize
}

/**
 * Valida si una dimensión en Dp satisface el umbral mínimo de accesibilidad motora WCAG 2.2 (48.dp).
 */
fun isTouchTargetCompliant(
    targetSize: Dp,
    profile: FenixTouchTargetProfile = FenixTouchTargetProfile.STANDARD,
): Boolean = targetSize >= profile.minSize && targetSize >= 48.dp
