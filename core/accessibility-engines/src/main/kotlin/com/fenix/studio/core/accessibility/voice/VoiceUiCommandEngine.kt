/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:33:10 -03:00
 * ARCHIVO: core/accessibility-engines/src/main/kotlin/com/fenix/studio/core/accessibility/voice/VoiceUiCommandEngine.kt
 * ================================================================================
 */

package com.fenix.studio.core.accessibility.voice

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.Normalizer

/**
 * Destinos de navegación por comandos de voz accesibles.
 */
enum class VoiceNavDestination(val identifier: String) {
    ONBOARDING("p01_onboarding"),
    CHAT("p02_home_chat"),
    SETTINGS("p03_settings"),
    VAULT("p04_vault"),
    PROCESSOR("p05_processor"),
    CLASSROOMS("p07_classrooms"),
    FENIX_DOCS("p09_fenix_docs"),
    COMMUNITY("p10_community"),
}

/**
 * Comandos reconocidos por la interfaz de voz soberana a Costo 0 (`MF-8.2`).
 */
sealed interface VoiceUiCommand {
    data class Navigate(val destination: VoiceNavDestination) : VoiceUiCommand
    data object TtsPlay : VoiceUiCommand
    data object TtsPause : VoiceUiCommand
    data object TtsStop : VoiceUiCommand
    data object TtsSpeedUp : VoiceUiCommand
    data object TtsSlowDown : VoiceUiCommand
    data object ToggleLsaPip : VoiceUiCommand
    data class Unrecognized(val rawSpokenText: String, val normalizedText: String) : VoiceUiCommand
}

/**
 * Motor de Comandos de Voz Local a Costo 0 (`MF-8.2`).
 * Procesa transcripciones locales de audio de Android (`SpeechRecognizer` offline / entradas de micrófono)
 * sin requerir servicios en la nube de pago ni APIs comerciales.
 */
class VoiceUiCommandEngine {

    private val _commandFlow = MutableSharedFlow<VoiceUiCommand>(extraBufferCapacity = 16)
    val commandFlow: SharedFlow<VoiceUiCommand> = _commandFlow.asSharedFlow()

    /**
     * Normaliza el texto oral en español: minúsculas, eliminación de diacríticos y puntuación.
     */
    fun normalizeSpokenText(raw: String): String {
        val lower = raw.trim().lowercase()
        val normalized = Normalizer.normalize(lower, Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Interpreta la frase oral y emite el `VoiceUiCommand` determinístico correspondiente.
     */
    fun parseAndDispatchCommand(spokenText: String): VoiceUiCommand {
        val norm = normalizeSpokenText(spokenText)

        val command = when {
            // Comandos de Lengua de Señas Argentina (LSA)
            norm.contains("lengua de senas") || norm.contains("interprete") || norm.contains("senas") -> {
                VoiceUiCommand.ToggleLsaPip
            }

            // Comandos de Control TTS
            norm.contains("leer") || norm.contains("reproducir") || norm.contains("continuar lectura") -> {
                VoiceUiCommand.TtsPlay
            }
            norm.contains("pausa") || norm.contains("pausar") -> {
                VoiceUiCommand.TtsPause
            }
            norm.contains("detener") || norm.contains("parar") || norm.contains("silencio") -> {
                VoiceUiCommand.TtsStop
            }
            norm.contains("mas rapido") || norm.contains("acelerar") -> {
                VoiceUiCommand.TtsSpeedUp
            }
            norm.contains("mas lento") || norm.contains("desacelerar") -> {
                VoiceUiCommand.TtsSlowDown
            }

            // Comandos de Navegación Raíz (P-01..P-10)
            norm.contains("inicio") || norm.contains("bienvenida") || norm.contains("onboarding") -> {
                VoiceUiCommand.Navigate(VoiceNavDestination.ONBOARDING)
            }
            norm.contains("chat") || norm.contains("preguntar") || norm.contains("conversacion") -> {
                VoiceUiCommand.Navigate(VoiceNavDestination.CHAT)
            }
            norm.contains("ajustes") || norm.contains("configuracion") || norm.contains("accesibilidad") -> {
                VoiceUiCommand.Navigate(VoiceNavDestination.SETTINGS)
            }
            norm.contains("boveda") || norm.contains("claves") || norm.contains("llaves") || norm.contains("byok") -> {
                VoiceUiCommand.Navigate(VoiceNavDestination.VAULT)
            }
            norm.contains("procesar") || norm.contains("procesador") || norm.contains("cargar material") -> {
                VoiceUiCommand.Navigate(VoiceNavDestination.PROCESSOR)
            }
            norm.contains("aulas") || norm.contains("mis aulas") || norm.contains("estudiar") || norm.contains("repasar") -> {
                VoiceUiCommand.Navigate(VoiceNavDestination.CLASSROOMS)
            }
            norm.contains("fenix docs") || norm.contains("editor") || norm.contains("documentos") || norm.contains("apa") -> {
                VoiceUiCommand.Navigate(VoiceNavDestination.FENIX_DOCS)
            }
            norm.contains("comunidad") || norm.contains("sitios utiles") || norm.contains("repositorio") -> {
                VoiceUiCommand.Navigate(VoiceNavDestination.COMMUNITY)
            }

            else -> VoiceUiCommand.Unrecognized(rawSpokenText = spokenText, normalizedText = norm)
        }

        _commandFlow.tryEmit(command)
        return command
    }
}
