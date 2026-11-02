/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-01 20:39:00 -03:00
 * ARCHIVO: core/designsystem/src/main/kotlin/com/fenix/studio/core/designsystem/components/UniversalAIChatInput.kt
 * ================================================================================
 */

package com.fenix.studio.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
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
    val audioRmsDb: Float = 0f,
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
 * Pista de audio y simulador de onda sonora reactiva para grabación de voz nativa in-app.
 * Se ubica fuera de la caja de texto con altura fija para garantizar estabilidad visual absoluta.
 */
@Composable
fun AudioWaveformTracker(
    rmsDb: Float,
    modifier: Modifier = Modifier,
) {
    val colors = FenixTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.background,
        border = BorderStroke(colors.borderStrokeWidth, Color(0xFF22C55E).copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444)),
                )
                Text(
                    text = "Grabando audio...",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF22C55E),
                )
            }

            // 16 barras dinámicas de ecualizador
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val clampedRms = ((rmsDb + 2f) / 10f).coerceIn(0.15f, 1f)
                for (i in 0 until 16) {
                    val sinFactor = kotlin.math.sin(phase + (i * 0.45f)).toFloat()
                    val normalizedFactor = ((sinFactor + 1f) / 2f) * 0.6f + 0.4f
                    val heightDp = (6.dp + (18.dp * clampedRms * normalizedFactor))
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(heightDp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i % 2 == 0) Color(0xFF22C55E) else colors.primary),
                    )
                }
            }
        }
    }
}

/**
 * Caja universal de entrada conversacional multimodal (P-02, P-08 y P-09, `MF-D.2`).
 * Incorpora menú contextual [+], dictado por voz in-app [🎤] con onda animada externa y botón [🗑] de descarte.
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
    onCancelVoiceClick: () -> Unit = {},
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

        // Pista de audio ubicada ARRIBA y FUERA de la caja de texto (altura fija de 44.dp para evitar rebotes)
        if (state.isVoiceListening) {
            AudioWaveformTracker(
                rmsDb = state.audioRmsDb,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(bottom = 6.dp),
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = colors.surface,
            contentColor = colors.onSurface,
            border = BorderStroke(colors.borderStrokeWidth, colors.outline),
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
                                text = if (state.isVoiceListening) "Escuchando tu voz..." else "Pregunta o profundiza sobre tu cátedra...",
                                style = typography.bodyLarge,
                                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        innerTextField()
                    },
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Fila de Controles: [+] a la izquierda <---> [🗑 descartar] [🎤 voz/enviar] [➤ enviar] a la derecha
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Botón [+] alineado a la izquierda a la misma altura horizontal
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

                    // Controles derechos agrupados al mismo nivel: [🗑 descartar] + [🎤 micrófono/enviar] + [➤ enviar]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Botón rojo [🗑] para cancelar y descartar el audio grabado
                        if (state.isVoiceListening) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                                    .border(colors.borderStrokeWidth, Color(0xFFDC2626), CircleShape)
                                    .clickable(onClick = onCancelVoiceClick)
                                    .fenixMinTouchTarget()
                                    .semantics { contentDescription = "Cancelar y descartar grabación de audio" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "🗑",
                                    style = typography.bodyLarge,
                                )
                            }
                        }

                        // Botón de micrófono [🎤] — Color verde cuando graba; al tocarlo en verde envía el audio transcripto
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (state.isVoiceListening) Color(0xFF22C55E) else colors.background)
                                .border(
                                    colors.borderStrokeWidth,
                                    if (state.isVoiceListening) Color(0xFF16A34A) else colors.outline,
                                    CircleShape,
                                )
                                .clickable(onClick = onVoiceInputClick)
                                .fenixMinTouchTarget()
                                .semantics {
                                    contentDescription = if (state.isVoiceListening) "Finalizar y enviar audio transcripto" else "Iniciar dictado por voz"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "🎤",
                                style = typography.bodyMedium,
                            )
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
}
