/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/designsystem/src/main/kotlin/com/fenix/studio/core/designsystem/theme/FenixColorTokens.kt
 * ================================================================================
 */

package com.fenix.studio.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Paleta semántica con cumplimiento estricto de contraste WCAG 2.2 Nivel AAA (>= 7.0:1)
 * y tokens de diferenciación proposicional (DT-ALTA-03).
 */
@Immutable
data class FenixSemanticColors(
    val background: Color,
    val surface: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val error: Color,
    val outline: Color,
    val borderStrokeWidth: Dp,
    val propositionPrimaryTheoryAccent: Color,
    val propositionPrimaryTheoryContainer: Color,
    val onPropositionPrimaryTheoryContainer: Color,
    val propositionComplementaryAccent: Color,
    val propositionComplementaryContainer: Color,
    val onPropositionComplementaryContainer: Color,
    val cooldownContainer: Color,
    val onCooldownContainer: Color,
    val cooldownAccent: Color,
)

/**
 * Catálogo inmutable de paletas auditadas bajo norma WCAG 2.2 AAA.
 */
object FenixPalettes {
    val PropositionBlue = Color(0xFF1A73E8)
    val PropositionAmber = Color(0xFFFA903E)

    val LightAAA = FenixSemanticColors(
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFF8FAFC),
        onBackground = Color(0xFF0F172A),
        onSurface = Color(0xFF0F172A),
        onSurfaceVariant = Color(0xFF334155),
        primary = Color(0xFF0B3D91),
        onPrimary = Color(0xFFFFFFFF),
        error = Color(0xFF991B1B),
        outline = Color(0xFF334155),
        borderStrokeWidth = 1.dp,
        propositionPrimaryTheoryAccent = PropositionBlue,
        propositionPrimaryTheoryContainer = Color(0xFFF0F7FF),
        onPropositionPrimaryTheoryContainer = Color(0xFF0A2540),
        propositionComplementaryAccent = PropositionAmber,
        propositionComplementaryContainer = Color(0xFFFFF8F1),
        onPropositionComplementaryContainer = Color(0xFF3B1D06),
        cooldownContainer = Color(0xFFFFFBEB),
        onCooldownContainer = Color(0xFF3B2106),
        cooldownAccent = Color(0xFF92400E),
    )

    val DarkAAA = FenixSemanticColors(
        background = Color(0xFF0B0F19),
        surface = Color(0xFF111827),
        onBackground = Color(0xFFF8FAFC),
        onSurface = Color(0xFFF8FAFC),
        onSurfaceVariant = Color(0xFFCBD5E1),
        primary = Color(0xFF93C5FD),
        onPrimary = Color(0xFF050811),
        error = Color(0xFFFCA5A5),
        outline = Color(0xFF94A3B8),
        borderStrokeWidth = 1.dp,
        propositionPrimaryTheoryAccent = PropositionBlue,
        propositionPrimaryTheoryContainer = Color(0xFF0F1D33),
        onPropositionPrimaryTheoryContainer = Color(0xFFF8FAFC),
        propositionComplementaryAccent = PropositionAmber,
        propositionComplementaryContainer = Color(0xFF26170A),
        onPropositionComplementaryContainer = Color(0xFFF8FAFC),
        cooldownContainer = Color(0xFF231B0C),
        onCooldownContainer = Color(0xFFFEF3C7),
        cooldownAccent = Color(0xFFFBBF24),
    )

    val HighContrastOledAAA = FenixSemanticColors(
        background = Color(0xFF000000),
        surface = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        onSurface = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFFFFD600),
        primary = Color(0xFF00E5FF),
        onPrimary = Color(0xFF000000),
        error = Color(0xFFFF8A80),
        outline = Color(0xFFFFFFFF),
        borderStrokeWidth = 2.dp,
        propositionPrimaryTheoryAccent = PropositionBlue,
        propositionPrimaryTheoryContainer = Color(0xFF000000),
        onPropositionPrimaryTheoryContainer = Color(0xFFFFFFFF),
        propositionComplementaryAccent = PropositionAmber,
        propositionComplementaryContainer = Color(0xFF000000),
        onPropositionComplementaryContainer = Color(0xFFFFD600),
        cooldownContainer = Color(0xFF000000),
        onCooldownContainer = Color(0xFFFFD600),
        cooldownAccent = Color(0xFFFFD600),
    )

    fun forMode(mode: FenixThemeMode): FenixSemanticColors = when (mode) {
        FenixThemeMode.LIGHT -> LightAAA
        FenixThemeMode.DARK -> DarkAAA
        FenixThemeMode.HIGH_CONTRAST_OLED -> HighContrastOledAAA
    }
}

/**
 * Calcula la luminancia relativa de un color sRGB conforme al estándar W3C WCAG 2.2.
 */
fun calculateRelativeLuminance(color: Color): Double {
    fun linearize(channel: Float): Double {
        val c = channel.toDouble().coerceIn(0.0, 1.0)
        return if (c <= 0.04045) {
            c / 12.92
        } else {
            ((c + 0.055) / 1.055).pow(2.4)
        }
    }

    val r = linearize(color.red)
    val g = linearize(color.green)
    val b = linearize(color.blue)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/**
 * Calcula la razón de contraste WCAG 2.2 entre un color de primer plano y uno de fondo.
 * Rango devuelto: [1.0, 21.0]. Nivel AAA exige >= 7.0 para texto normal.
 */
fun calculateContrastRatio(foreground: Color, background: Color): Double {
    val l1 = calculateRelativeLuminance(foreground)
    val l2 = calculateRelativeLuminance(background)
    val lighter = max(l1, l2)
    val darker = min(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)
}
