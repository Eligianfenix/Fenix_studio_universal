/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:34:40 -03:00
 * ARCHIVO: core/accessibility-engines/src/test/kotlin/com/fenix/studio/core/accessibility/AccessibilityEnginesTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.accessibility

import com.fenix.studio.core.accessibility.lsa.LsaInteractionMode
import com.fenix.studio.core.accessibility.lsa.LsaSignLanguagePipEngine
import com.fenix.studio.core.accessibility.tts.HybridTtsEngine
import com.fenix.studio.core.accessibility.tts.TtsPlaybackStatus
import com.fenix.studio.core.accessibility.voice.VoiceNavDestination
import com.fenix.studio.core.accessibility.voice.VoiceUiCommand
import com.fenix.studio.core.accessibility.voice.VoiceUiCommandEngine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccessibilityEnginesTest {

    @Test
    fun `hybrid tts engine clamps speech rate and manages playback states cleanly`() {
        val ttsEngine = HybridTtsEngine()

        // Verificamos delimitación estricta de velocidad 0.75x..2.0x
        ttsEngine.setSpeechRate(0.3f)
        assertEquals(0.75f, ttsEngine.playbackState.value.speechRate)

        ttsEngine.setSpeechRate(3.2f)
        assertEquals(2.0f, ttsEngine.playbackState.value.speechRate)

        ttsEngine.setSpeechRate(1.25f)
        assertEquals(1.25f, ttsEngine.playbackState.value.speechRate)

        val sampleParagraphs = listOf(
            "Primer párrafo con conceptos fundamentales.",
            "Segundo párrafo profundizando la estructura del protocolo.",
            "Tercer párrafo con las conclusiones analíticas.",
        )

        ttsEngine.startReading(sampleParagraphs)
        val state = ttsEngine.playbackState.value
        assertEquals(TtsPlaybackStatus.PLAYING, state.status)
        assertEquals(0, state.currentParagraphIndex)
        assertEquals(3, state.totalParagraphs)

        ttsEngine.pauseReading()
        assertEquals(TtsPlaybackStatus.PAUSED, ttsEngine.playbackState.value.status)

        ttsEngine.resumeReading()
        assertEquals(TtsPlaybackStatus.PLAYING, ttsEngine.playbackState.value.status)

        ttsEngine.stopReading()
        assertEquals(TtsPlaybackStatus.IDLE, ttsEngine.playbackState.value.status)
        assertEquals(0, ttsEngine.playbackState.value.currentParagraphIndex)

        ttsEngine.shutdown()
    }

    @Test
    fun `voice command engine normalizes spanish phonetics and routes navigation and actions`() {
        val voiceEngine = VoiceUiCommandEngine()

        // Normalización fonética en español
        val normalized = voiceEngine.normalizeSpokenText("¡HÓLA! ¿Podemos ir a Mis AULAS, por favor?")
        assertEquals("hola podemos ir a mis aulas por favor", normalized)

        // Navegación
        val cmdChat = voiceEngine.parseAndDispatchCommand("abrir el chat inicial")
        assertTrue(cmdChat is VoiceUiCommand.Navigate)
        assertEquals(VoiceNavDestination.CHAT, (cmdChat as VoiceUiCommand.Navigate).destination)

        val cmdAulas = voiceEngine.parseAndDispatchCommand("quiero ir a mis aulas")
        assertTrue(cmdAulas is VoiceUiCommand.Navigate)
        assertEquals(VoiceNavDestination.CLASSROOMS, (cmdAulas as VoiceUiCommand.Navigate).destination)

        val cmdDocs = voiceEngine.parseAndDispatchCommand("abrir fenix docs editor apa")
        assertTrue(cmdDocs is VoiceUiCommand.Navigate)
        assertEquals(VoiceNavDestination.FENIX_DOCS, (cmdDocs as VoiceUiCommand.Navigate).destination)

        val cmdCommunity = voiceEngine.parseAndDispatchCommand("ver comunidad y sitios utiles")
        assertTrue(cmdCommunity is VoiceUiCommand.Navigate)
        assertEquals(VoiceNavDestination.COMMUNITY, (cmdCommunity as VoiceUiCommand.Navigate).destination)

        // Control de reproducción y señas LSA
        val cmdLsa = voiceEngine.parseAndDispatchCommand("activar interprete de lengua de senas")
        assertEquals(VoiceUiCommand.ToggleLsaPip, cmdLsa)

        val cmdPlay = voiceEngine.parseAndDispatchCommand("reproducir lectura")
        assertEquals(VoiceUiCommand.TtsPlay, cmdPlay)

        val cmdSpeed = voiceEngine.parseAndDispatchCommand("ir mas rapido")
        assertEquals(VoiceUiCommand.TtsSpeedUp, cmdSpeed)

        val cmdStop = voiceEngine.parseAndDispatchCommand("detener ahora")
        assertEquals(VoiceUiCommand.TtsStop, cmdStop)

        // Comando desconocido
        val cmdUnknown = voiceEngine.parseAndDispatchCommand("palabra aleatoria desconocida")
        assertTrue(cmdUnknown is VoiceUiCommand.Unrecognized)
    }

    @Test
    fun `lsa sign language engine translates to 2_5D kinetic glosses within redmi 14c ram budget`() {
        val lsaEngine = LsaSignLanguagePipEngine()

        assertFalse(lsaEngine.uiState.value.isOverlayVisible)
        lsaEngine.toggleOverlayVisibility()
        assertTrue(lsaEngine.uiState.value.isOverlayVisible)

        lsaEngine.setInteractionMode(LsaInteractionMode.AUDIO_TO_LSA)
        assertEquals(LsaInteractionMode.AUDIO_TO_LSA, lsaEngine.uiState.value.activeMode)

        // Traducción a glosas LSA
        val glosses = lsaEngine.translateSpanishToLsaGlosses("Hola estudiar examen")
        assertEquals(3, glosses.size)
        assertEquals("LSA_HOLA", glosses[0].glossCode)
        assertEquals("LSA_ESTUDIAR", glosses[1].glossCode)
        assertEquals("LSA_EXAMEN", glosses[2].glossCode)

        // Avance en la secuencia
        assertNotNull(lsaEngine.uiState.value.currentGloss)
        assertEquals("LSA_HOLA", lsaEngine.uiState.value.currentGloss?.glossCode)

        lsaEngine.advanceToNextGloss()
        assertEquals("LSA_ESTUDIAR", lsaEngine.uiState.value.currentGloss?.glossCode)

        // Dactilología para términos fuera del léxico base
        val dactiloGlosses = lsaEngine.translateSpanishToLsaGlosses("termodinamica")
        assertEquals(1, dactiloGlosses.size)
        assertTrue(dactiloGlosses.first().glossCode.startsWith("LSA_DACTILOLOGIA_"))

        // Verificación de huella de memoria estricta para Redmi 14C (< 15 MB = 15360 KB)
        val ramEstimateKb = lsaEngine.getRamFootprintEstimateKb()
        assertTrue(
            ramEstimateKb < 15360,
            "La huella de memoria de LSA 2.5D ($ramEstimateKb KB) debe ser inferior a 15 MB",
        )
    }

    @Test
    fun `hybrid tts engine prioritizes natural edge voice profile before fallback MF-D6`() {
        val ttsEngine = HybridTtsEngine()
        assertEquals(com.fenix.studio.core.accessibility.tts.TtsVoiceProfile.LOCAL_DEVICE_STANDARD, ttsEngine.playbackState.value.activeVoiceProfile)

        ttsEngine.setVoiceProfilePreference(com.fenix.studio.core.accessibility.tts.TtsVoiceProfile.EDGE_NATURAL_NEURAL)
        assertEquals(com.fenix.studio.core.accessibility.tts.TtsVoiceProfile.EDGE_NATURAL_NEURAL, ttsEngine.playbackState.value.activeVoiceProfile)

        ttsEngine.configureNaturalVoiceOrFallback()
        assertNotNull(ttsEngine.playbackState.value.activeVoiceProfile)
    }
}

