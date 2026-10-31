/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 03:28:45 -03:00
 * ARCHIVO: core/designsystem/src/main/kotlin/com/fenix/studio/core/designsystem/components/UniversalAIChatInput.kt
 * ================================================================================
 */

package com.fenix.studio.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fenix.studio.core.designsystem.modifiers.fenixMinTouchTarget
import com.fenix.studio.core.designsystem.theme.FenixTheme

/**
 * Modos operativos del agente conversacional transversal (reutilizable en P-02, P-08 y P-09).
 */
enum class FenixChatAgentMode(val displayLabel: String) {
    ACADEMIC_FRAMING("Encuadre Académico"),
    COOPERATIVE_TUTOR("Tutor Cooperativo"),
    DEEP_RESEARCH("Deep Research"),
    APA_EDITOR_ASSISTANT("Asistente APA 7.ª"),
}

/**
 * Estado inmutable del componente `UniversalAIChatInput`.
 * Garantiza por invariante que el texto del estudiante (`promptText`) jamás se borre
 * ni se pierda cuando se activa o transcurre un enfriamiento de red (`cooldownState`).
 */
@Immutable
data class UniversalChatInputUiState(
    val promptText: String = "",
    val agentMode: FenixChatAgentMode = FenixChatAgentMode.ACADEMIC_FRAMING,
    val isStreamingResponse: Boolean = false,
    val isLsaQuickAccessEnabled: Boolean = true,
    val isVoiceListening: Boolean = false,
    val cooldownState: EmpathicCooldownUiState? = null,
) {
    val canSendPrompt: Boolean
        get() = promptText.isNotBlank() &&
            !isStreamingResponse &&
            (cooldownState?.isVisible != true)

    /**
     * Activa el enfriamiento empático preservando incondicionalmente `promptText`.
     */
    fun onCooldownActivated(
        remainingSeconds: Int,
        totalSeconds: Int = remainingSeconds,
        providerName: String = "Fénix IA",
    ): UniversalChatInputUiState = copy(
        promptText = this.promptText,
        cooldownState = EmpathicCooldownUiState(
            isVisible = true,
            remainingSeconds = remainingSeconds,
            totalSeconds = totalSeconds,
            activeProviderName = providerName,
        ),
    )

    /**
     * Actualiza el segundo restante a 1 Hz preservando incondicionalmente `promptText`.
     */
    fun onCooldownTick(remainingSeconds: Int): UniversalChatInputUiState {
        val currentCooldown = cooldownState ?: return this
        return copy(
            promptText = this.promptText,
            cooldownState = currentCooldown.copy(remainingSeconds = remainingSeconds),
        )
    }

    /**
     * Cierra el enfriamiento preservando incondicionalmente `promptText` para el reenvío manos libres.
     */
    fun onCooldownDismissed(): UniversalChatInputUiState = copy(
        promptText = this.promptText,
        cooldownState = cooldownState?.copy(isVisible = false, remainingSeconds = 0),
    )
}

/**
 * Caja universal de entrada conversacional multimodal (P-02, P-08 y P-09, `MF-D.2`).
 * Incorpora menú contextual [+], dictado por voz [🎤], botón [➤] inmutable y zonas táctiles Macro-UI.
 */
@Composable
fun UniversalAIChatInput(
    state: UniversalChatInputUiState,
    onPromptTextChange: (String) -> Unit,
    onSendPromptClick: () -> Unit,
    onAttachMaterialClick: () -> Unit,
    onLsaToggleClick: () -> Unit,
    onAutoRetryTriggered: () -> Unit,
    onSwitchProviderClick: () -> Unit,
    modifier: Modifier = Modifier,
    onVoiceInputClick: () -> Unit = {},
    onAgentModeChange: (FenixChatAgentMode) -> Unit = {},
    onCrossClassroomsClick: () -> Unit = {},
) {
    val colors = FenixTheme.colors
    val typography = MaterialTheme.typography
    var isPlusMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        val activeCooldown = state.cooldownState
        if (activeCooldown != null && activeCooldown.isVisible) {
            EmpathicCooldownCard(
                state = activeCooldown,
                onAutoRetryTriggered = onAutoRetryTriggered,
                onSwitchProviderClick = onSwitchProviderClick,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = colors.surface,
            contentColor = colors.onSurface,
            border = androidx.compose.foundation.BorderStroke(colors.borderStrokeWidth, colors.outline),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                // Etiqueta del modo activo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Modo: ${state.agentMode.displayLabel}",
                        style = typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.primary,
                    )
                    if (state.isLsaQuickAccessEnabled) {
                        Text(
                            text = "🤟 LSA Activo",
                            style = typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier
                                .clickable(onClick = onLsaToggleClick)
                                .padding(2.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Campo de texto principal sin bordes duros internos
                BasicTextField(
                    value = state.promptText,
                    onValueChange = onPromptTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    textStyle = typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    decorationBox = { innerTextField ->
                        if (state.promptText.isEmpty()) {
                            Text(
                                text = "Pregunta o profundiza sobre tu cátedra...",
                                style = typography.bodyLarge,
                                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        innerTextField()
                    },
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Fila de Controles Multimodales: [+] | [🎤] ... [➤]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Controles izquierdos
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Botón [+] con menú contextual desplegable
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(colors.background)
                                    .border(colors.borderStrokeWidth, colors.outline, CircleShape)
                                    .clickable { isPlusMenuExpanded = true }
                                    .fenixMinTouchTarget()
                                    .semantics { contentDescription = "Menú de acciones y materiales" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "+",
                                    style = typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.primary,
                                )
                            }

                            DropdownMenu(
                                expanded = isPlusMenuExpanded,
                                onDismissRequest = { isPlusMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("📎 Adjuntar PDF o apunte (SAF)") },
                                    onClick = {
                                        isPlusMenuExpanded = false
                                        onAttachMaterialClick()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("🔀 Cruzar con otra cátedra") },
                                    onClick = {
                                        isPlusMenuExpanded = false
                                        onCrossClassroomsClick()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("🎓 Modo: Tutor Cooperativo") },
                                    onClick = {
                                        isPlusMenuExpanded = false
                                        onAgentModeChange(FenixChatAgentMode.COOPERATIVE_TUTOR)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("🔍 Modo: Deep Research") },
                                    onClick = {
                                        isPlusMenuExpanded = false
                                        onAgentModeChange(FenixChatAgentMode.DEEP_RESEARCH)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("📝 Modo: Asistente APA 7.ª") },
                                    onClick = {
                                        isPlusMenuExpanded = false
                                        onAgentModeChange(FenixChatAgentMode.APA_EDITOR_ASSISTANT)
                                    },
                                )
                            }
                        }

                        // Botón de micrófono [🎤]
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (state.isVoiceListening) colors.primary.copy(alpha = 0.2f) else colors.background)
                                .border(colors.borderStrokeWidth, colors.outline, CircleShape)
                                .clickable(onClick = onVoiceInputClick)
                                .fenixMinTouchTarget()
                                .semantics { contentDescription = "Entrada por voz local a costo cero" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "🎤",
                                style = typography.bodyMedium,
                            )
                        }
                    }

                    // Botón [➤] circular de envío inmutable
                    val canSend = state.canSendPrompt
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (canSend) colors.primary else colors.outline.copy(alpha = 0.3f))
                            .clickable(enabled = canSend, onClick = onSendPromptClick)
                            .fenixMinTouchTarget()
                            .semantics { contentDescription = "Enviar consulta a Fénix IA" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "➤",
                            style = typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (canSend) colors.onPrimary else colors.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}
