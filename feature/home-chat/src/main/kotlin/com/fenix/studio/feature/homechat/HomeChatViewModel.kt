/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 03:42:00 -03:00
 * ARCHIVO: feature/home-chat/src/main/kotlin/com/fenix/studio/feature/homechat/HomeChatViewModel.kt
 * ================================================================================
 */

package com.fenix.studio.feature.homechat

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.fenix.studio.core.designsystem.components.UniversalChatInputUiState
import com.fenix.studio.core.network.model.FenixStreamEvent
import com.fenix.studio.domain.protocolv3.validation.AcademicDeontologicalGuard
import com.fenix.studio.domain.protocolv3.validation.DeontologicalDecision
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Destinos de navegación desde el lienzo principal y cajón de `P-02`.
 */
enum class FenixHubDestination(val displayLabel: String) {
    P02_HOME_CHAT("Chat Inicial Académico"),
    P03_SETTINGS("Ajustes y Accesibilidad"),
    P04_BYOK_VAULT("Bóveda Criptográfica BYOK"),
    P05_PROCESSOR("Procesador Académico Autónomo"),
    P07_CLASSROOMS("Mis Aulas"),
    P09_FENIX_DOCS("Fenix Docs (APA 7.ª)"),
    P10_COMMUNITY("Comunidad y Sitios Útiles"),
}

/**
 * Mensaje renderizado en el lienzo conversacional de `P-02` con su botonera universal
 * (copiar, reintentar rotando clave, regenerar, accesibilidad).
 */
@Immutable
data class HomeChatMessageItem(
    val messageId: String,
    val isUser: Boolean,
    val text: String,
    val activeSlotIndex: Int? = null,
    val isDeontologicalWarning: Boolean = false,
)

/**
 * Sesión histórica registrada en el cajón de navegación lateral de `P-02`.
 */
@Immutable
data class HistoricalSessionItem(
    val id: String,
    val title: String,
    val preview: String = "",
)

/**
 * Estado inmutable de la pantalla `P-02` (`MF-4.2`).
 */
@Immutable
data class HomeChatUiState(
    val selectedClassroomId: String? = null,
    val chatInputState: UniversalChatInputUiState = UniversalChatInputUiState(),
    val messages: List<HomeChatMessageItem> = emptyList(),
    val historicalSessions: List<HistoricalSessionItem> = emptyList(),
    val isLsaOverlayVisible: Boolean = false,
    val otaBannerNotice: String? = null,
    val otaDecision: OtaExecutionDecision? = null,
)

/**
 * ViewModel de `P-02` (Chat Inicial de Encuadre Académico + Guardián OTA `RN-13` / `RN-27` + Preservación `RN-07`
 * + Síntesis pedagógica asistida de Fénix IA `MF-D.5`).
 */
class HomeChatViewModel(
    private val otaExecutor: TaskGuardedUpdateExecutor = TaskGuardedUpdateExecutor(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeChatUiState())
    val uiState: StateFlow<HomeChatUiState> = _uiState.asStateFlow()

    fun onPromptChanged(newText: String) {
        _uiState.update { current ->
            current.copy(
                chatInputState = current.chatInputState.copy(promptText = newText),
            )
        }
    }

    fun onVoiceListeningChanged(isListening: Boolean) {
        _uiState.update { current ->
            current.copy(
                chatInputState = current.chatInputState.copy(
                    isVoiceListening = isListening,
                    audioRmsDb = if (isListening) current.chatInputState.audioRmsDb else 0f,
                ),
            )
        }
    }

    fun onAudioRmsChanged(rmsDb: Float) {
        _uiState.update { current ->
            current.copy(
                chatInputState = current.chatInputState.copy(audioRmsDb = rmsDb),
            )
        }
    }

    fun onVoiceProcessingStarted() {
        _uiState.update { current ->
            current.copy(
                chatInputState = current.chatInputState.copy(
                    promptText = "Enviando audio...",
                    isVoiceListening = false,
                    isVoiceProcessing = true,
                    audioRmsDb = 0f,
                ),
            )
        }
    }

    fun onVoicePartialTranscription(partialText: String) {
        _uiState.update { current ->
            current.copy(
                chatInputState = current.chatInputState.copy(
                    promptText = partialText,
                ),
            )
        }
    }

    fun onCancelVoiceRecording() {
        _uiState.update { current ->
            current.copy(
                chatInputState = current.chatInputState.copy(
                    promptText = "",
                    isVoiceListening = false,
                    isVoiceProcessing = false,
                    audioRmsDb = 0f,
                ),
            )
        }
    }

    fun onVoiceTranscriptionReceived(transcribedText: String) {
        _uiState.update { current ->
            current.copy(
                chatInputState = current.chatInputState.copy(
                    promptText = transcribedText,
                    isVoiceListening = false,
                    isVoiceProcessing = false,
                    audioRmsDb = 0f,
                ),
            )
        }
    }

    fun onSelectActiveClassroom(classroomId: String?) {
        _uiState.update { it.copy(selectedClassroomId = classroomId) }
    }

    fun onToggleLsaQuickAccess() {
        _uiState.update { it.copy(isLsaOverlayVisible = !it.isLsaOverlayVisible) }
    }

    /**
     * Inicia una nueva sesión conversacional archivando la actual si contenía mensajes.
     */
    fun onNewChatSession() {
        _uiState.update { current ->
            if (current.messages.isNotEmpty()) {
                val firstUserMsg = current.messages.firstOrNull { it.isUser }?.text?.take(35) ?: "Sesión académica"
                val lastMsg = current.messages.lastOrNull()?.text?.take(40) ?: ""
                val newSession = HistoricalSessionItem(
                    id = "session-${System.currentTimeMillis()}",
                    title = firstUserMsg,
                    preview = lastMsg,
                )
                current.copy(
                    messages = emptyList(),
                    historicalSessions = listOf(newSession) + current.historicalSessions,
                )
            } else {
                current.copy(messages = emptyList())
            }
        }
    }

    /**
     * Evalúa el Guardia Deontológico (`RN-17`) y procesa el envío o activa enfriamiento empático (`RN-07`)
     * preservando el 100% del prompt del estudiante. Emite respuesta académica asistida de Fénix IA (`MF-D.5`).
     */
    fun onSubmitPrompt(
        isAttachedToAcademicMaterial: Boolean = false,
        simulatedStreamEvent: FenixStreamEvent? = null,
    ) {
        val currentPrompt = _uiState.value.chatInputState.promptText.trim()
        if (currentPrompt.isEmpty()) return

        when (
            val guardDecision = AcademicDeontologicalGuard.evaluateUserRequest(
                userPrompt = currentPrompt,
                isAttachedToAcademicMaterial = isAttachedToAcademicMaterial || (_uiState.value.selectedClassroomId != null),
            )
        ) {
            is DeontologicalDecision.BlockedPersonalProfessionalPractice -> {
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + HomeChatMessageItem(
                            messageId = "msg-${state.messages.size + 1}",
                            isUser = false,
                            text = guardDecision.safeRedirectionMessage,
                            isDeontologicalWarning = true,
                        ),
                    )
                }
                return
            }
            DeontologicalDecision.AllowedAcademicProcessing -> Unit
        }

        if (simulatedStreamEvent is FenixStreamEvent.PoolCooldownActive) {
            // RN-07: Cuando el pool entra en enfriamiento, el texto del estudiante queda intacto en la caja de entrada
            _uiState.update { state ->
                state.copy(
                    chatInputState = state.chatInputState
                        .copy(promptText = simulatedStreamEvent.preservedUserPrompt)
                        .onCooldownActivated(
                            remainingSeconds = simulatedStreamEvent.waitSeconds,
                            totalSeconds = simulatedStreamEvent.waitSeconds,
                            providerName = simulatedStreamEvent.providerName,
                        ),
                )
            }
            return
        }

        // Envío exitoso normal: registra mensaje del estudiante y sintetiza respuesta pedagógica de Fénix IA
        val normPrompt = currentPrompt.lowercase()
        val replyText = if (normPrompt.contains("hola") || normPrompt.contains("buen día") || normPrompt.contains("buenos días") || normPrompt.contains("buenas tardes") || normPrompt.contains("saludos")) {
            "¡Hola! Soy Fénix IA, tu asistente académico de soberanía local. ¿Qué materia, apunte o texto deseas procesar o consultar hoy?"
        } else {
            "He recibido tu consulta: \"$currentPrompt\". Conforme a la regla de no alucinación, para brindarte un análisis riguroso y fundamentado en tus fuentes bibliográficas, puedes vincular este chat a un aula o adjuntar tus apuntes directamente mediante el botón [+]."
        }

        _uiState.update { state ->
            val userMsg = HomeChatMessageItem(
                messageId = "msg-${state.messages.size + 1}",
                isUser = true,
                text = currentPrompt,
            )
            val aiMsg = HomeChatMessageItem(
                messageId = "msg-${state.messages.size + 2}",
                isUser = false,
                text = replyText,
                activeSlotIndex = 0,
            )
            state.copy(
                messages = state.messages + listOf(userMsg, aiMsg),
                chatInputState = state.chatInputState.copy(
                    promptText = "",
                    isVoiceListening = false,
                    isVoiceProcessing = false,
                    audioRmsDb = 0f,
                ),
            )
        }
    }

    fun onOtaUpdateAvailable(
        versionName: String,
        sha256Hex: String,
        activitySnapshot: CriticalTaskActivitySnapshot,
    ) {
        otaExecutor.notifyOtaUpdateDownloaded(versionName, sha256Hex)
        refreshOtaGuardState(activitySnapshot)
    }

    fun refreshOtaGuardState(activitySnapshot: CriticalTaskActivitySnapshot) {
        val decision = otaExecutor.evaluateOtaInstallation(activitySnapshot)
        val banner = otaExecutor.buildUserFacingOtaNotice(activitySnapshot)
        _uiState.update { current ->
            current.copy(
                otaDecision = decision,
                otaBannerNotice = banner,
            )
        }
    }
}
