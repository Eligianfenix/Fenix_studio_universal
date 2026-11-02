/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 03:42:30 -03:00
 * ARCHIVO: feature/home-chat/src/test/kotlin/com/fenix/studio/feature/homechat/HomeChatAndOtaGuardTest.kt
 * ================================================================================
 */

package com.fenix.studio.feature.homechat

import com.fenix.studio.core.network.model.FenixStreamEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeChatAndOtaGuardTest {

    @Test
    fun `TaskGuardedUpdateExecutor defers OTA installation while processing queue or Fenix Docs draft is active and releases when idle RN-27`() {
        val viewModel = HomeChatViewModel()

        // Llega OTA mientras hay procesamiento de cátedra y un borrador sin guardar en Fenix Docs
        viewModel.onOtaUpdateAvailable(
            versionName = "3.1.0",
            sha256Hex = "a".repeat(64),
            activitySnapshot = CriticalTaskActivitySnapshot(
                isDocumentProcessingQueueActive = true,
                isUnsavedFenixDocsDraftActive = true,
                isSseStreamingActive = false,
            ),
        )

        assertEquals(OtaExecutionDecision.DEFERRED_UNTIL_IDLE, viewModel.uiState.value.otaDecision)
        assertTrue(viewModel.uiState.value.otaBannerNotice!!.contains("procesamiento en curso"))

        // Cuando finalizan todas las tareas críticas (idle), libera la instalación OTA inmediatamente
        viewModel.refreshOtaGuardState(
            CriticalTaskActivitySnapshot(
                isDocumentProcessingQueueActive = false,
                isUnsavedFenixDocsDraftActive = false,
                isSseStreamingActive = false,
            ),
        )
        assertEquals(OtaExecutionDecision.READY_TO_INSTALL_NOW, viewModel.uiState.value.otaDecision)
    }

    @Test
    fun `HomeChatViewModel preserves student prompt unconditionally when PoolCooldownActive occurs RN-07`() {
        val viewModel = HomeChatViewModel()
        val prompt = "Comparar los modelos expuestos en la Unidad 2 según la bibliografía."
        viewModel.onPromptChanged(prompt)

        viewModel.onSubmitPrompt(
            isAttachedToAcademicMaterial = true,
            simulatedStreamEvent = FenixStreamEvent.PoolCooldownActive(
                preservedUserPrompt = prompt,
                waitSeconds = 5,
                providerName = "Fénix IA (Gratuito Asistido)",
            ),
        )

        val inputState = viewModel.uiState.value.chatInputState
        assertEquals(prompt, inputState.promptText)
        assertTrue(inputState.cooldownState?.isVisible == true)
        assertEquals(5, inputState.cooldownState?.remainingSeconds)
    }

    @Test
    fun `HomeChatViewModel emits academic empathetic reply from Fenix IA on greetings and general inquiries MF-D5`() {
        val viewModel = HomeChatViewModel()
        viewModel.onPromptChanged("¡Hola! Buenos días Fénix")
        viewModel.onSubmitPrompt()

        val messages = viewModel.uiState.value.messages
        assertEquals(2, messages.size)

        // Mensaje del usuario
        assertTrue(messages[0].isUser)
        assertEquals("¡Hola! Buenos días Fénix", messages[0].text)

        // Respuesta de Fénix IA
        assertFalse(messages[1].isUser)
        assertTrue(messages[1].text.contains("Soy Fénix IA"))
        assertEquals("", viewModel.uiState.value.chatInputState.promptText)
    }

    @Test
    fun `HomeChatViewModel manages voice listening state and appends voice transcription to prompt`() {
        val viewModel = HomeChatViewModel()
        assertFalse(viewModel.uiState.value.chatInputState.isVoiceListening)

        viewModel.onVoiceListeningChanged(true)
        assertTrue(viewModel.uiState.value.chatInputState.isVoiceListening)

        viewModel.onVoiceTranscriptionReceived("Consulta sobre resumen académico")
        assertTrue(viewModel.uiState.value.chatInputState.isVoiceListening)
        assertEquals("Consulta sobre resumen académico", viewModel.uiState.value.chatInputState.promptText)

        // Verificamos que si habla nuevamente continúa actualizando la transcripción acumulada
        viewModel.onVoiceTranscriptionReceived("Consulta sobre resumen académico y bibliografía obligatoria")
        assertEquals("Consulta sobre resumen académico y bibliografía obligatoria", viewModel.uiState.value.chatInputState.promptText)
        assertTrue(viewModel.uiState.value.chatInputState.isVoiceListening)

        // Cancelar grabación restaura el texto previo o limpia
        viewModel.onPromptChanged("Texto previo")
        viewModel.onVoiceListeningChanged(true)
        viewModel.onVoicePartialTranscription("Texto descartado")
        viewModel.onCancelVoiceRecording()
        assertFalse(viewModel.uiState.value.chatInputState.isVoiceListening)
        assertEquals("", viewModel.uiState.value.chatInputState.promptText)
    }
}
