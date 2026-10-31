/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/designsystem/src/test/kotlin/com/fenix/studio/core/designsystem/DesignSystemAccessibilityContractTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import com.fenix.studio.core.designsystem.modifiers.FenixSubpointUiModel
import com.fenix.studio.core.designsystem.modifiers.formatSubpointSpokenDescription
import com.fenix.studio.core.designsystem.theme.DyslexicSafeLineHeightStyle
import com.fenix.studio.core.designsystem.theme.FenixPalettes
import com.fenix.studio.core.designsystem.theme.FenixThemeMode
import com.fenix.studio.core.designsystem.theme.FenixTouchTargetProfile
import com.fenix.studio.core.designsystem.theme.FenixTypographyFactory
import com.fenix.studio.core.designsystem.theme.calculateContrastRatio
import com.fenix.studio.core.designsystem.theme.isTouchTargetCompliant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignSystemAccessibilityContractTest {

    @Test
    fun `LightAAA palette meets WCAG 2_2 AAA 7_0 to 1 contrast ratio across all semantic text pairs`() {
        val light = FenixPalettes.LightAAA

        assertTrue(calculateContrastRatio(light.onBackground, light.background) >= 7.0)
        assertTrue(calculateContrastRatio(light.onSurface, light.surface) >= 7.0)
        assertTrue(calculateContrastRatio(light.onSurfaceVariant, light.surface) >= 7.0)
        assertTrue(calculateContrastRatio(light.primary, light.background) >= 7.0)
        assertTrue(calculateContrastRatio(light.onPrimary, light.primary) >= 7.0)
        assertTrue(calculateContrastRatio(light.error, light.background) >= 7.0)
        assertTrue(
            calculateContrastRatio(
                light.onPropositionPrimaryTheoryContainer,
                light.propositionPrimaryTheoryContainer,
            ) >= 7.0,
        )
        assertTrue(
            calculateContrastRatio(
                light.onPropositionComplementaryContainer,
                light.propositionComplementaryContainer,
            ) >= 7.0,
        )
        assertTrue(calculateContrastRatio(light.onCooldownContainer, light.cooldownContainer) >= 7.0)
    }

    @Test
    fun `DarkAAA palette meets WCAG 2_2 AAA 7_0 to 1 contrast ratio across all semantic text pairs`() {
        val dark = FenixPalettes.DarkAAA

        assertTrue(calculateContrastRatio(dark.onBackground, dark.background) >= 7.0)
        assertTrue(calculateContrastRatio(dark.onSurface, dark.surface) >= 7.0)
        assertTrue(calculateContrastRatio(dark.onSurfaceVariant, dark.surface) >= 7.0)
        assertTrue(calculateContrastRatio(dark.primary, dark.background) >= 7.0)
        assertTrue(calculateContrastRatio(dark.onPrimary, dark.primary) >= 7.0)
        assertTrue(calculateContrastRatio(dark.error, dark.background) >= 7.0)
        assertTrue(
            calculateContrastRatio(
                dark.onPropositionPrimaryTheoryContainer,
                dark.propositionPrimaryTheoryContainer,
            ) >= 7.0,
        )
        assertTrue(
            calculateContrastRatio(
                dark.onPropositionComplementaryContainer,
                dark.propositionComplementaryContainer,
            ) >= 7.0,
        )
        assertTrue(calculateContrastRatio(dark.onCooldownContainer, dark.cooldownContainer) >= 7.0)
    }

    @Test
    fun `HighContrastOledAAA enforces pure OLED black 21_1 primary text 14_1 yellow variant and 2dp borders`() {
        val oled = FenixPalettes.forMode(FenixThemeMode.HIGH_CONTRAST_OLED)

        assertEquals(Color(0xFF000000), oled.background)
        assertEquals(Color(0xFFFFFFFF), oled.onBackground)
        assertEquals(Color(0xFFFFD600), oled.onSurfaceVariant)
        assertEquals(2.dp, oled.borderStrokeWidth)
        assertEquals(Color(0xFF1A73E8), oled.propositionPrimaryTheoryAccent)
        assertEquals(Color(0xFFFA903E), oled.propositionComplementaryAccent)

        val primaryTextRatio = calculateContrastRatio(oled.onBackground, oled.background)
        assertEquals(21.0, primaryTextRatio, 0.01)

        val variantRatio = calculateContrastRatio(oled.onSurfaceVariant, oled.background)
        assertTrue(variantRatio >= 14.0, "Expected variant contrast >= 14.0, got $variantRatio")
    }

    @Test
    fun `FenixTypographyFactory enforces LineHeightStyle Trim None and lineHeight at least 1_5x fontSize`() {
        val standardTypography = FenixTypographyFactory.createTypography(
            useOpenDyslexicFont = false,
            fontScaleMultiplier = 1.0f,
        )
        val dyslexicTypography = FenixTypographyFactory.createTypography(
            useOpenDyslexicFont = true,
            fontScaleMultiplier = 1.25f,
        )

        val stylesToVerify = listOf(
            standardTypography.headlineLarge,
            standardTypography.headlineMedium,
            standardTypography.titleLarge,
            standardTypography.bodyLarge,
            standardTypography.bodyMedium,
            standardTypography.labelLarge,
            dyslexicTypography.headlineLarge,
            dyslexicTypography.bodyLarge,
            dyslexicTypography.bodyMedium,
            dyslexicTypography.labelLarge,
        )

        stylesToVerify.forEach { style ->
            assertEquals(LineHeightStyle.Trim.None, style.lineHeightStyle?.trim)
            assertEquals(DyslexicSafeLineHeightStyle, style.lineHeightStyle)
            val ratio = style.lineHeight.value / style.fontSize.value
            assertTrue(ratio >= 1.5f - 0.001f, "Expected lineHeight/fontSize >= 1.5, got $ratio")
            assertTrue(style.letterSpacing.value >= 0.04f - 0.0001f)
        }

        assertEquals(0.05f, dyslexicTypography.bodyLarge.letterSpacing.value, 0.0001f)
    }

    @Test
    fun `formatSubpointSpokenDescription strips synthetic star glyph and prepends Subpunto tematico`() {
        val syntheticSubpoint = FenixSubpointUiModel(
            displayCode = "2.1.a \u2726",
            title = "Concepto derivado por densidad \u2726",
            isSyntheticDensityNode = true,
        )

        val spoken = formatSubpointSpokenDescription(syntheticSubpoint)

        assertFalse(spoken.contains("\u2726"), "TalkBack description must never contain \u2726")
        assertTrue(spoken.startsWith("Subpunto temático 2.1.a:"))
        assertEquals("Subpunto temático 2.1.a: Concepto derivado por densidad", spoken)
    }

    @Test
    fun `Macro UI touch target profiles enforce minimum 48dp 56dp and 64dp dimensions`() {
        assertEquals(48.dp, FenixTouchTargetProfile.STANDARD.minSize)
        assertEquals(56.dp, FenixTouchTargetProfile.MACRO_MEDIUM.minSize)
        assertEquals(64.dp, FenixTouchTargetProfile.MACRO_LARGE.minSize)

        assertFalse(isTouchTargetCompliant(44.dp, FenixTouchTargetProfile.STANDARD))
        assertTrue(isTouchTargetCompliant(48.dp, FenixTouchTargetProfile.STANDARD))
        assertTrue(isTouchTargetCompliant(64.dp, FenixTouchTargetProfile.MACRO_LARGE))
    }
}
