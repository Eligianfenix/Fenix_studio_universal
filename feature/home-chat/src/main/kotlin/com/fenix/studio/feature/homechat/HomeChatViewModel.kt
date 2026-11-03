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

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fenix.studio.core.designsystem.components.FenixChatAgentMode
import com.fenix.studio.core.designsystem.components.FenixSelectableModel
import com.fenix.studio.core.designsystem.components.UniversalChatInputUiState
import com.fenix.studio.core.network.model.FenixStreamEvent
import com.fenix.studio.core.network.teacher.FenixTeacherKeylessService
import com.fenix.studio.core.network.teacher.TeacherChatHistoryTurn
import com.fenix.studio.domain.protocolv3.validation.AcademicDeontologicalGuard
import com.fenix.studio.domain.protocolv3.validation.DeontologicalDecision
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Destinos de navegación desde el lienzo principal y cajón de `P-02`.
 */
enum class FenixHubDestination(val displayLabel: String) {
    P02_HOME_CHAT("Chat Inicial Académico"),
    P03_SETTINGS("Ajustes y Accesibilidad"),
    P04_BYOK_VAULT("Bóveda Criptográfica BYOK"),
    P05_PROCESSOR("Procesador Académico"),
    P07_CLASSROOMS("Mis Aulas"),
    P09_FENIX_DOCS("Fenix Docs (APA 7.ª)"),
    P10_COMMUNITY("Comunidad y Recursos"),
    DEEP_RESEARCH("Investigación Profunda"),
    GET_CREDITS("Conseguir Créditos"),
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
    val senderLabel: String = "Fenix Teacher",
    val activeSlotIndex: Int? = null,
    val isDeontologicalWarning: Boolean = false,
    val isThinking: Boolean = false,
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
    val currentSessionId: String = "session-${System.currentTimeMillis()}",
    val selectedClassroomId: String? = null,
    val chatInputState: UniversalChatInputUiState = UniversalChatInputUiState(),
    val messages: List<HomeChatMessageItem> = emptyList(),
    val historicalSessions: List<HistoricalSessionItem> = emptyList(),
    val isLsaOverlayVisible: Boolean = false,
    val otaBannerNotice: String? = null,
    val otaDecision: OtaExecutionDecision? = null,
    val isMissingGroqKeyDialogOpen: Boolean = false,
    val isGroqConfigDialogOpen: Boolean = false,
    val isMissingAzureTtsKeyDialogOpen: Boolean = false,
    val isAzureTtsConfigDialogOpen: Boolean = false,
    val pendingSpeechText: String? = null,
    val activeTtsMessageId: String? = null,
    val isTtsLoading: Boolean = false,
)

/**
 * ViewModel de `P-02` (Chat Inicial de Encuadre Académico + Guardián OTA `RN-13` / `RN-27` + Preservación `RN-07`
 * + Síntesis pedagógica asistida de Fénix IA `MF-D.5`).
 */
class HomeChatViewModel(
    private val otaExecutor: TaskGuardedUpdateExecutor = TaskGuardedUpdateExecutor(),
    private val fenixTeacherService: FenixTeacherKeylessService = FenixTeacherKeylessService(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeChatUiState())
    val uiState: StateFlow<HomeChatUiState> = _uiState.asStateFlow()
    private var rotatingEndpointCursor: Int = 0
    private var sessionStorage: HomeChatSessionStorage? = null

    /**
     * Inicializa el almacenamiento persistente local (`HomeChatSessionStorage`) y restaura las sesiones.
     */
    fun initializeStorage(context: Context) {
        if (sessionStorage != null) return
        val storage = HomeChatSessionStorage(context)
        sessionStorage = storage
        val historical = storage.loadHistoricalSessions()
        _uiState.update { current ->
            current.copy(
                historicalSessions = historical,
            )
        }
    }

    fun onSelectModel(model: FenixSelectableModel) {
        _uiState.update { current ->
            current.copy(
                chatInputState = current.chatInputState.copy(selectedModel = model),
            )
        }
    }

    fun onSelectAgentMode(mode: FenixChatAgentMode) {
        _uiState.update { current ->
            current.copy(
                chatInputState = current.chatInputState.copy(agentMode = mode),
            )
        }
    }

    /**
     * Mantiene en el selector de modelos únicamente la opción por defecto "Fenix Teacher"
     * y debajo agrega los proveedores/modelos que haya configurado el usuario.
     */
    fun syncUserConfiguredModels(userModels: List<FenixSelectableModel>) {
        _uiState.update { current ->
            val combined = listOf(FenixSelectableModel.DEFAULT_FENIX_TEACHER) +
                userModels.filterNot { it.id == FenixSelectableModel.DEFAULT_FENIX_TEACHER.id }
            val currentSelected = combined.find { it.id == current.chatInputState.selectedModel.id }
                ?: FenixSelectableModel.DEFAULT_FENIX_TEACHER
            current.copy(
                chatInputState = current.chatInputState.copy(
                    availableModels = combined,
                    selectedModel = currentSelected,
                ),
            )
        }
    }

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

    fun onShowMissingGroqKeyDialog(show: Boolean) {
        _uiState.update { it.copy(isMissingGroqKeyDialogOpen = show) }
    }

    fun onShowGroqConfigDialog(show: Boolean) {
        _uiState.update { it.copy(isGroqConfigDialogOpen = show) }
    }

    fun onShowMissingAzureTtsDialog(show: Boolean, pendingText: String? = null) {
        _uiState.update { it.copy(isMissingAzureTtsKeyDialogOpen = show, pendingSpeechText = pendingText) }
    }

    fun onShowAzureTtsConfigDialog(show: Boolean) {
        _uiState.update { it.copy(isAzureTtsConfigDialogOpen = show) }
    }

    fun onActivateDeepResearchMode() {
        _uiState.update { current ->
            current.copy(
                chatInputState = current.chatInputState.copy(
                    promptText = "[Investigación Profunda] Tema a investigar: ",
                ),
            )
        }
    }

    /**
     * Inicia una nueva sesión conversacional archivando y persistiendo la actual en almacenamiento local.
     */
    fun onNewChatSession() {
        val current = _uiState.value
        if (current.messages.isNotEmpty()) {
            sessionStorage?.saveSession(current.currentSessionId, current.messages)
        }
        val refreshed = sessionStorage?.loadHistoricalSessions() ?: current.historicalSessions
        val newSessionId = "session-${System.currentTimeMillis()}"
        _uiState.update { state ->
            state.copy(
                currentSessionId = newSessionId,
                messages = emptyList(),
                historicalSessions = refreshed,
            )
        }
    }

    /**
     * Carga y activa una sesión histórica tocada desde el menú lateral (`ModalNavigationDrawer`).
     */
    fun onSelectHistoricalSession(session: HistoricalSessionItem) {
        val current = _uiState.value
        if (current.messages.isNotEmpty()) {
            sessionStorage?.saveSession(current.currentSessionId, current.messages)
        }
        val loadedMessages = sessionStorage?.loadSessionMessages(session.id).orEmpty()
        val refreshed = sessionStorage?.loadHistoricalSessions() ?: current.historicalSessions
        _uiState.update { state ->
            state.copy(
                currentSessionId = session.id,
                messages = loadedMessages,
                historicalSessions = refreshed,
            )
        }
    }

    /**
     * Evalúa el Guardia Deontológico (`RN-17`) y procesa el envío o activa enfriamiento empático (`RN-07`)
     * preservando el 100% del prompt del estudiante. Emite respuesta académica asistida de Fénix IA (`MF-D.5`).
     */
    fun onSubmitPrompt(
        isAttachedToAcademicMaterial: Boolean = false,
        simulatedStreamEvent: FenixStreamEvent? = null,
        useLiveNetwork: Boolean = false,
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

        if (!useLiveNetwork) {
            // Envío síncrono determinista para pruebas unitarias (MF-D.5)
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
                    senderLabel = "Tú",
                )
                val aiMsg = HomeChatMessageItem(
                    messageId = "msg-${state.messages.size + 2}",
                    isUser = false,
                    text = replyText,
                    senderLabel = state.chatInputState.selectedModel.displayName,
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
            return
        }

        val activeModel = _uiState.value.chatInputState.selectedModel
        val activeMode = _uiState.value.chatInputState.agentMode
        val nowMs = System.currentTimeMillis()
        val replyMsgId = "msg-$nowMs-ai"
        val activeSessionId = _uiState.value.currentSessionId

        // Construimos el historial de memoria conversacional previa (omitiendo pensando y avisos deontológicos)
        val conversationHistory = _uiState.value.messages
            .filterNot { it.isThinking || it.isDeontologicalWarning }
            .map { TeacherChatHistoryTurn(role = if (it.isUser) "user" else "assistant", content = it.text) }

        _uiState.update { state ->
            val userMsg = HomeChatMessageItem(
                messageId = "msg-$nowMs-user",
                isUser = true,
                text = currentPrompt,
                senderLabel = "Tú",
            )
            val thinkingMsg = HomeChatMessageItem(
                messageId = replyMsgId,
                isUser = false,
                text = "Pensando...",
                senderLabel = activeModel.displayName,
                activeSlotIndex = 0,
                isThinking = true,
            )
            state.copy(
                messages = state.messages + listOf(userMsg, thinkingMsg),
                chatInputState = state.chatInputState.copy(
                    promptText = "",
                    isStreamingResponse = true,
                    isVoiceListening = false,
                    isVoiceProcessing = false,
                    audioRmsDb = 0f,
                ),
            )
        }

        viewModelScope.launch {
            val systemPrompt = buildSystemPromptForMode(activeMode)
            val startIdx = rotatingEndpointCursor
            rotatingEndpointCursor += 1

            val aiReply = fenixTeacherService.sendChatToFenixTeacher(
                userPrompt = currentPrompt,
                systemInstruction = systemPrompt,
                conversationHistory = conversationHistory,
                startEndpointIndex = startIdx,
            )

            _uiState.update { state ->
                val updatedMessages = state.messages.map { msg ->
                    if (msg.messageId == replyMsgId) {
                        msg.copy(
                            text = aiReply,
                            senderLabel = activeModel.displayName,
                            isThinking = false,
                        )
                    } else {
                        msg
                    }
                }
                sessionStorage?.saveSession(activeSessionId, updatedMessages)
                val refreshedSessions = sessionStorage?.loadHistoricalSessions() ?: state.historicalSessions
                state.copy(
                    messages = updatedMessages,
                    historicalSessions = refreshedSessions,
                    chatInputState = state.chatInputState.copy(
                        isStreamingResponse = false,
                    ),
                )
            }
        }
    }

    fun onRetryMessage(item: HomeChatMessageItem) {
        if (item.isUser || item.isThinking) return
        val currentMessages = _uiState.value.messages
        val idx = currentMessages.indexOfFirst { it.messageId == item.messageId }
        if (idx <= 0) return
        val precedingUserMsg = currentMessages.subList(0, idx).lastOrNull { it.isUser } ?: return

        val activeModel = _uiState.value.chatInputState.selectedModel
        val activeMode = _uiState.value.chatInputState.agentMode
        val activeSessionId = _uiState.value.currentSessionId

        val conversationHistory = currentMessages.subList(0, idx)
            .filterNot { it.isThinking || it.isDeontologicalWarning || it.messageId == precedingUserMsg.messageId }
            .map { TeacherChatHistoryTurn(role = if (it.isUser) "user" else "assistant", content = it.text) }

        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.messageId == item.messageId) {
                        msg.copy(text = "Pensando...", isThinking = true)
                    } else {
                        msg
                    }
                },
                chatInputState = state.chatInputState.copy(isStreamingResponse = true),
            )
        }

        viewModelScope.launch {
            val systemPrompt = buildSystemPromptForMode(activeMode)
            val startIdx = rotatingEndpointCursor
            rotatingEndpointCursor += 1

            val aiReply = fenixTeacherService.sendChatToFenixTeacher(
                userPrompt = precedingUserMsg.text,
                systemInstruction = systemPrompt,
                conversationHistory = conversationHistory,
                startEndpointIndex = startIdx,
            )

            _uiState.update { state ->
                val updatedMessages = state.messages.map { msg ->
                    if (msg.messageId == item.messageId) {
                        msg.copy(
                            text = aiReply,
                            senderLabel = activeModel.displayName,
                            isThinking = false,
                        )
                    } else {
                        msg
                    }
                }
                sessionStorage?.saveSession(activeSessionId, updatedMessages)
                val refreshedSessions = sessionStorage?.loadHistoricalSessions() ?: state.historicalSessions
                state.copy(
                    messages = updatedMessages,
                    historicalSessions = refreshedSessions,
                    chatInputState = state.chatInputState.copy(isStreamingResponse = false),
                )
            }
        }
    }

    private fun buildSystemPromptForMode(mode: FenixChatAgentMode): String = when (mode) {
        FenixChatAgentMode.ACADEMIC_FRAMING ->
            "Eres Fenix Teacher, un profesor y asistente académico riguroso, claro y pedagógico. Responde siempre en español con precisión conceptual, estructurando las ideas para facilitar el estudio universitario."
        FenixChatAgentMode.COOPERATIVE_TUTOR ->
            "Eres Fenix Teacher en modo Tutor Cooperativo. Guía al estudiante paso a paso en español mediante ejemplos claros y preguntas orientadoras para consolidar su comprensión."
        FenixChatAgentMode.DEEP_RESEARCH ->
            "Eres Fenix Teacher en modo Investigación Profunda. Analiza el tema en español con exhaustividad académica, desglosando antecedentes, ejes teóricos, metodologías y perspectivas críticas."
        FenixChatAgentMode.APA_EDITOR_ASSISTANT ->
            "Eres Fenix Teacher en modo Asistente de Redacción Académica APA 7.ª edición. Ayuda a redactar, estructurar y citar con rigor académico en español."
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

    fun onTtsPlaybackRequested(messageId: String) {
        _uiState.update { it.copy(activeTtsMessageId = messageId, isTtsLoading = true) }
    }

    fun onTtsPlaybackStarted(messageId: String) {
        _uiState.update { if (it.activeTtsMessageId == messageId) it.copy(isTtsLoading = false) else it }
    }

    fun onTtsPlaybackFinished() {
        _uiState.update { it.copy(activeTtsMessageId = null, isTtsLoading = false) }
    }
}
