/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/designsystem/src/test/kotlin/com/fenix/studio/core/designsystem/ComponentsStateContractTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.designsystem

import com.fenix.studio.core.designsystem.components.AuditedPropositionUiModel
import com.fenix.studio.core.designsystem.components.EmpathicCooldownUiState
import com.fenix.studio.core.designsystem.components.FenixChatAgentMode
import com.fenix.studio.core.designsystem.components.FenixEpistemicState
import com.fenix.studio.core.designsystem.components.FenixOntologicalTag
import com.fenix.studio.core.designsystem.components.UniversalChatInputUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComponentsStateContractTest {

    @Test
    fun `EmpathicCooldownUiState calculates deterministic progressFraction at 1 Hz ticks`() {
        val startState = EmpathicCooldownUiState(isVisible = true, remainingSeconds = 5, totalSeconds = 5)
        val midState = EmpathicCooldownUiState(isVisible = true, remainingSeconds = 2, totalSeconds = 5)
        val endState = EmpathicCooldownUiState(isVisible = true, remainingSeconds = 0, totalSeconds = 5)

        assertEquals(0.0f, startState.progressFraction, 0.0001f)
        assertEquals(0.6f, midState.progressFraction, 0.0001f)
        assertEquals(1.0f, endState.progressFraction, 0.0001f)
    }

    @Test
    fun `EmpathicCooldownUiState emits Polite TalkBack announcement only at start and 0s completion`() {
        val total = 5
        val announcements = (total downTo 0).map { sec ->
            EmpathicCooldownUiState(
                isVisible = true,
                remainingSeconds = sec,
                totalSeconds = total,
                activeProviderName = "Fénix IA",
            ).buildPoliteLiveAnnouncement()
        }

        // Exactamente 2 anuncios en todo el ciclo de 5 segundos (al inicio t=5 y al final t=0)
        assertNotNull(announcements.first())
        assertTrue(announcements.first()!!.contains("Pausa empática de red activa"))
        assertNull(announcements[1]) // t = 4s
        assertNull(announcements[2]) // t = 3s
        assertNull(announcements[3]) // t = 2s
        assertNull(announcements[4]) // t = 1s
        assertNotNull(announcements.last()) // t = 0s
        assertTrue(announcements.last()!!.contains("Reintentando envío automáticamente"))
        assertEquals(2, announcements.filterNotNull().size)
    }

    @Test
    fun `UniversalChatInputUiState unconditionally preserves promptText across cooldown lifecycle`() {
        val originalPrompt = "Analizar las relaciones entre los conceptos centrales de la unidad 3."
        val initialState = UniversalChatInputUiState(
            promptText = originalPrompt,
            agentMode = FenixChatAgentMode.COOPERATIVE_TUTOR,
        )
        assertTrue(initialState.canSendPrompt)

        val cooldownStarted = initialState.onCooldownActivated(remainingSeconds = 4, totalSeconds = 4)
        assertEquals(originalPrompt, cooldownStarted.promptText)
        assertFalse(cooldownStarted.canSendPrompt)

        val cooldownTick2 = cooldownStarted.onCooldownTick(remainingSeconds = 2)
        assertEquals(originalPrompt, cooldownTick2.promptText)
        assertFalse(cooldownTick2.canSendPrompt)

        val cooldownCompleted = cooldownTick2.onCooldownDismissed()
        assertEquals(originalPrompt, cooldownCompleted.promptText)
        assertTrue(cooldownCompleted.canSendPrompt)
    }

    @Test
    fun `AuditedPropositionUiModel differentiates primary source from complementary inference and external states`() {
        val primaryModel = AuditedPropositionUiModel(
            id = "prop-1",
            epistemicState = FenixEpistemicState.FUENTE,
            ontologicalTag = FenixOntologicalTag.EMPIRICAL_FACT,
            propositionText = "Los registros experimentales verifican el umbral térmico.",
        )
        val inferenceModel = AuditedPropositionUiModel(
            id = "prop-2",
            epistemicState = FenixEpistemicState.INFERENCIA,
            ontologicalTag = FenixOntologicalTag.THEORETICAL_HYPOTHESIS,
            propositionText = "Por analogía estructural se modela el comportamiento límite.",
        )
        val externalModel = AuditedPropositionUiModel(
            id = "prop-3",
            epistemicState = FenixEpistemicState.EXTERNO,
            ontologicalTag = FenixOntologicalTag.DEONTIC_NORM_RULE,
            propositionText = "Contexto general complementario fuera del documento cargado.",
        )

        assertTrue(primaryModel.epistemicState.isPrimarySource)
        assertEquals("[ \uD83D\uDCD8 TEORÍA PRINCIPAL ]", primaryModel.epistemicState.headerText)

        assertFalse(inferenceModel.epistemicState.isPrimarySource)
        assertEquals("[ \uD83D\uDCCE APORTE COMPLEMENTARIO ]", inferenceModel.epistemicState.headerText)

        assertFalse(externalModel.epistemicState.isPrimarySource)
        assertEquals("[ \uD83D\uDCCE APORTE COMPLEMENTARIO ]", externalModel.epistemicState.headerText)
        assertEquals(6, FenixOntologicalTag.entries.size)
    }
}
