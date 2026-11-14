/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:40:15 -03:00
 * ARCHIVO: feature/auth/src/test/kotlin/com/fenix/studio/feature/auth/OnboardingAuthViewModelTest.kt
 * ================================================================================
 */

package com.fenix.studio.feature.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnboardingAuthViewModelTest {

    @Test
    fun `proceed is blocked until user scrolls to bottom and accepts current TyC version RN-16`() {
        val viewModel = OnboardingAuthViewModel()
        assertFalse(viewModel.uiState.value.isProceedEnabled)
        assertFalse(viewModel.onProceedToHomeChat())

        viewModel.onToggleTycAcceptance(checked = true, nowEpochMs = 1_730_000_000_000L)
        assertFalse(viewModel.uiState.value.isProceedEnabled)

        viewModel.onTycScrolledToBottom()
        viewModel.onToggleTycAcceptance(checked = true, nowEpochMs = 1_730_000_000_500L)

        assertTrue(viewModel.uiState.value.isProceedEnabled)
        assertEquals("1.1", viewModel.uiState.value.acceptedTycVersion)
        assertNotNull(viewModel.uiState.value.acceptedTimestampEpochMs)
        assertTrue(viewModel.onProceedToHomeChat())
        assertTrue(viewModel.uiState.value.hasCompletedOnboarding)
    }

    @Test
    fun `updating required TyC version invalidates prior acceptance until re-accepted RN-16`() {
        val viewModel = OnboardingAuthViewModel(initialStoredTycVersion = "1.1")
        assertTrue(viewModel.uiState.value.isProceedEnabled)

        viewModel.onRequiredTycVersionUpdated("1.2")
        assertFalse(viewModel.uiState.value.isProceedEnabled)
        assertFalse(viewModel.onProceedToHomeChat())
    }
}
