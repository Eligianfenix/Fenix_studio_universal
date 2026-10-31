/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/designsystem/src/main/kotlin/com/fenix/studio/core/designsystem/theme/FenixTypography.kt
 * ================================================================================
 */

package com.fenix.studio.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Configuración de altura de línea segura para tipografías inclusivas (`OpenDyslexic`),
 * desactivando el recorte superior/inferior para impedir mutilación de glifos (DT-BAJA-01).
 */
val DyslexicSafeLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * Factoría determinista de tipografía accesible.
 * Garantiza en todos los estilos:
 * - `lineHeight >= 1.5x fontSize`
 * - `letterSpacing >= 0.04.em` (`0.05.em` cuando `useOpenDyslexicFont == true`)
 * - `LineHeightStyle.Trim.None`
 */
object FenixTypographyFactory {

    fun createTypography(
        useOpenDyslexicFont: Boolean = false,
        fontScaleMultiplier: Float = 1.0f,
        customFontFamily: FontFamily? = null,
    ): Typography {
        val safeMultiplier = fontScaleMultiplier.coerceIn(0.85f, 2.0f)
        val activeFontFamily = customFontFamily ?: if (useOpenDyslexicFont) {
            FontFamily.Monospace
        } else {
            FontFamily.SansSerif
        }
        val activeLetterSpacing: TextUnit = if (useOpenDyslexicFont) 0.05.em else 0.04.em
        val lineHeightFactor = if (useOpenDyslexicFont) 1.6f else 1.5f

        fun buildAccessibleStyle(
            baseFontSizeSp: Float,
            weight: FontWeight,
        ): TextStyle {
            val scaledSize = (baseFontSizeSp * safeMultiplier).sp
            val scaledLineHeight = (baseFontSizeSp * safeMultiplier * lineHeightFactor).sp
            return TextStyle(
                fontFamily = activeFontFamily,
                fontWeight = weight,
                fontSize = scaledSize,
                lineHeight = scaledLineHeight,
                letterSpacing = activeLetterSpacing,
                lineHeightStyle = DyslexicSafeLineHeightStyle,
            )
        }

        return Typography(
            headlineLarge = buildAccessibleStyle(28f, FontWeight.Bold),
            headlineMedium = buildAccessibleStyle(24f, FontWeight.Bold),
            headlineSmall = buildAccessibleStyle(20f, FontWeight.SemiBold),
            titleLarge = buildAccessibleStyle(18f, FontWeight.SemiBold),
            titleMedium = buildAccessibleStyle(16f, FontWeight.SemiBold),
            titleSmall = buildAccessibleStyle(14f, FontWeight.Medium),
            bodyLarge = buildAccessibleStyle(16f, FontWeight.Normal),
            bodyMedium = buildAccessibleStyle(14f, FontWeight.Normal),
            bodySmall = buildAccessibleStyle(12f, FontWeight.Normal),
            labelLarge = buildAccessibleStyle(14f, FontWeight.SemiBold),
            labelMedium = buildAccessibleStyle(12f, FontWeight.Medium),
            labelSmall = buildAccessibleStyle(11f, FontWeight.Medium),
        )
    }
}
