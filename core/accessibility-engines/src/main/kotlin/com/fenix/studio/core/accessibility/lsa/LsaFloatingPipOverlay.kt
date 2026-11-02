/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-02 16:58:00 -03:00
 * ARCHIVO: core/accessibility-engines/src/main/kotlin/com/fenix/studio/core/accessibility/lsa/LsaFloatingPipOverlay.kt
 * ================================================================================
 */

package com.fenix.studio.core.accessibility.lsa

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.fenix.studio.core.designsystem.theme.FenixTheme
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Ventana Flotante Picture-in-Picture (PiP) no invasiva y arrastrable para el Intérprete LSA 2.5D (`MF-10.3`).
 * Cumple con RN-04 (prohibición de diálogos que secuestren la pantalla) y renderiza un avatar esquelético
 * vectorial en Canvas a 60 FPS mediante interpolación lineal determinista (Lerp).
 */
@Composable
fun LsaFloatingPipOverlay(
    lsaState: LsaPipUiState,
    isVoiceListening: Boolean,
    onSelectMode: (LsaInteractionMode) -> Unit,
    onTranslateText: (String) -> Unit,
    onAdvanceNextGloss: () -> Unit,
    onRestartSequence: () -> Unit,
    onToggleLiveMicClick: () -> Unit,
    onSpeakAloudForDeafUser: (String) -> Unit,
    onCloseOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FenixTheme.colors
    val typography = MaterialTheme.typography

    var offsetX by remember { mutableFloatStateOf(24f) }
    var offsetY by remember { mutableFloatStateOf(140f) }
    var isMinimized by remember { mutableStateOf(false) }
    var isAutoPlaying by remember { mutableStateOf(true) }
    var customInputText by remember { mutableStateOf("") }
    var activeLetterIndex by remember { mutableIntStateOf(0) }

    val currentGloss = lsaState.currentGloss

    // Avance automático por glosa y letra dactilológica
    LaunchedEffect(isAutoPlaying, currentGloss, lsaState.pendingGlossQueue.size) {
        activeLetterIndex = 0
        if (!isAutoPlaying || currentGloss == null) return@LaunchedEffect

        val letters = currentGloss.fingerSpellingFrames
        if (letters.isNotEmpty()) {
            val stepMs = (currentGloss.durationMs / letters.size.coerceAtLeast(1)).coerceAtLeast(240L)
            for (i in letters.indices) {
                activeLetterIndex = i
                delay(stepMs)
            }
        } else {
            delay(currentGloss.durationMs.coerceAtLeast(850L))
        }

        if (lsaState.pendingGlossQueue.size > 1) {
            onAdvanceNextGloss()
        } else {
            isAutoPlaying = false
        }
    }

    Surface(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .width(if (isMinimized) 210.dp else 310.dp),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        contentColor = colors.onSurface,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(2.dp, colors.primary),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Barra superior arrastrable (Drag Handle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.primary)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x).coerceIn(-60f, 520f)
                            offsetY = (offsetY + dragAmount.y).coerceIn(60f, 1300f)
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "🤟 LSA 2.5D · Arrastrar",
                    style = typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onPrimary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(colors.onPrimary.copy(alpha = 0.2f))
                            .clickable { isMinimized = !isMinimized },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isMinimized) "▢" else "—",
                            style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.onPrimary,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(colors.onPrimary.copy(alpha = 0.2f))
                            .clickable(onClick = onCloseOverlay),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "✕",
                            style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.onPrimary,
                        )
                    }
                }
            }

            if (isMinimized) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = currentGloss?.spanishEquivalent?.uppercase() ?: "En espera",
                        style = typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${lsaState.currentIndexInSequence + 1}/${lsaState.fullSequence.size.coerceAtLeast(1)}",
                        style = typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Selector de los 3 modos del Puente de Inclusión (Sección 12 Interacciones)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val modes = listOf(
                            LsaInteractionMode.LIVE_CLASS to "🎙️ Clase",
                            LsaInteractionMode.FACE_TO_FACE to "🗣️↔🤟 Diálogo",
                            LsaInteractionMode.AUDIO_TO_LSA to "💬 Chat a LSA",
                        )
                        modes.forEach { (mode, label) ->
                            val selected = lsaState.activeMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) colors.primary else colors.background)
                                    .border(1.dp, if (selected) colors.primary else colors.outline, RoundedCornerShape(6.dp))
                                    .clickable { onSelectMode(mode) }
                                    .padding(vertical = 5.dp, horizontal = 2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = typography.labelSmall,
                                    color = if (selected) colors.onPrimary else colors.onSurface,
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    // Lienzo 2.5D a 60 FPS del Avatar Esquelético LSA
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(175.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, colors.outline, RoundedCornerShape(12.dp)),
                    ) {
                        LsaAvatar25DCanvas(
                            gloss = currentGloss,
                            activeLetterIndex = activeLetterIndex,
                            isAnimating = isAutoPlaying,
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Subtítulo de Glosa / Dactilología superpuesto en la base del lienzo
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color(0xCC090D16))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (currentGloss != null) {
                                val letters = currentGloss.fingerSpellingFrames
                                val titleText = if (letters.isNotEmpty()) {
                                    val idx = activeLetterIndex.coerceIn(0, letters.lastIndex)
                                    val word = currentGloss.spanishEquivalent.uppercase()
                                    "DACTILOLOGÍA: ${word.take(idx)}[${letters[idx].first}]${word.drop(idx + 1)}"
                                } else {
                                    "GLOSA LSA: ${currentGloss.spanishEquivalent.uppercase()}"
                                }
                                Text(
                                    text = titleText,
                                    style = typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color(0xFF38BDF8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${currentGloss.handShape} · Seña ${(lsaState.currentIndexInSequence + 1).coerceAtMost(lsaState.fullSequence.size.coerceAtLeast(1))}/${lsaState.fullSequence.size.coerceAtLeast(1)}",
                                    style = typography.labelSmall,
                                    color = Color(0xFFE2E8F0),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Text(
                                    text = "Avatar LSA en reposo",
                                    style = typography.labelSmall,
                                    color = Color(0xFF94A3B8),
                                )
                            }
                        }
                    }

                    // Controles de reproducción cinética
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Button(
                            onClick = {
                                if (lsaState.pendingGlossQueue.size <= 1 && !isAutoPlaying) {
                                    onRestartSequence()
                                    isAutoPlaying = true
                                } else {
                                    isAutoPlaying = !isAutoPlaying
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp),
                        ) {
                            Text(
                                text = if (isAutoPlaying) "⏸ Pausar" else "▶ Signar",
                                style = typography.labelSmall,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                isAutoPlaying = false
                                onAdvanceNextGloss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp),
                            enabled = lsaState.pendingGlossQueue.size > 1,
                        ) {
                            Text("⏭ Sig.", style = typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = {
                                onRestartSequence()
                                isAutoPlaying = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp),
                            enabled = lsaState.fullSequence.isNotEmpty(),
                        ) {
                            Text("↺ Repetir", style = typography.labelSmall)
                        }
                    }

                    // Controles específicos según el modo seleccionado
                    when (lsaState.activeMode) {
                        LsaInteractionMode.LIVE_CLASS -> {
                            Button(
                                onClick = onToggleLiveMicClick,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = if (isVoiceListening) "⏹ Detener Escucha de Clase" else "🎙️ Escuchar Docente y Signar en Vivo",
                                    style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                )
                            }
                        }

                        LsaInteractionMode.FACE_TO_FACE -> {
                            // Puente Bidireccional: el sordo escribe o toca respuesta y la voz neuronal habla por él
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.background)
                                    .border(1.dp, colors.outline, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BasicTextField(
                                    value = customInputText,
                                    onValueChange = { customInputText = it },
                                    modifier = Modifier.weight(1f),
                                    textStyle = typography.bodySmall.copy(color = colors.onSurface),
                                    cursorBrush = SolidColor(colors.primary),
                                    decorationBox = { inner ->
                                        if (customInputText.isEmpty()) {
                                            Text(
                                                text = "Escribe para hablar en voz alta o signar...",
                                                style = typography.labelSmall,
                                                color = colors.onSurfaceVariant,
                                            )
                                        }
                                        inner()
                                    },
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Button(
                                    onClick = {
                                        val msg = customInputText.ifBlank { "Hola, soy una persona sorda. ¿Me podés repetir por favor?" }
                                        onSpeakAloudForDeafUser(msg)
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("🔊 Hablar en Voz Alta", style = typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = onToggleLiveMicClick,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = if (isVoiceListening) "⏹ Oyendo..." else "🎤 Oír al Oyente",
                                        style = typography.labelSmall,
                                    )
                                }
                            }
                        }

                        LsaInteractionMode.AUDIO_TO_LSA -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.background)
                                    .border(1.dp, colors.outline, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BasicTextField(
                                    value = customInputText,
                                    onValueChange = { customInputText = it },
                                    modifier = Modifier.weight(1f),
                                    textStyle = typography.bodySmall.copy(color = colors.onSurface),
                                    cursorBrush = SolidColor(colors.primary),
                                    decorationBox = { inner ->
                                        if (customInputText.isEmpty()) {
                                            Text(
                                                text = "Frase a traducir a glosas LSA...",
                                                style = typography.labelSmall,
                                                color = colors.onSurfaceVariant,
                                            )
                                        }
                                        inner()
                                    },
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Signar ➔",
                                    style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.primary,
                                    modifier = Modifier.clickable {
                                        if (customInputText.isNotBlank()) {
                                            onTranslateText(customInputText)
                                            isAutoPlaying = true
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renderizador Vectorial 2.5D en Canvas a 60 FPS con interpolación lineal determinista (Lerp)
 * y cinemática articular de brazos, muñecas, 5 dedos por mano y rasgos faciales no manuales.
 */
@Composable
private fun LsaAvatar25DCanvas(
    gloss: LsaKineticGloss?,
    activeLetterIndex: Int,
    isAnimating: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "lsa_avatar_60fps")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 680, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "lerp_phase",
    )

    val t = if (isAnimating) phase else 0.5f

    val pose = remember(gloss, activeLetterIndex, t) {
        if (gloss == null) {
            LsaPoseKeyframe(rightHandX = 0.42f, rightHandY = 0.48f)
        } else if (gloss.fingerSpellingFrames.isNotEmpty()) {
            val idx = activeLetterIndex.coerceIn(0, gloss.fingerSpellingFrames.lastIndex)
            val base = gloss.fingerSpellingFrames[idx].second
            base.copy(
                rightHandX = base.rightHandX + sin(t * 3.14f) * 0.03f,
                rightHandY = base.rightHandY - sin(t * 3.14f) * 0.03f,
            )
        } else {
            lerpPose(gloss.startPose, gloss.endPose, t)
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val chestY = h * 0.54f
        val headRadius = h * 0.14f
        val headCenter = Offset(cx + pose.headTiltDeg * 0.6f, h * 0.23f)

        // Retícula sutil 2.5D de referencia espacial
        drawLine(
            color = Color(0xFF1E293B),
            start = Offset(cx, 0f),
            end = Offset(cx, h),
            strokeWidth = 1.dp.toPx(),
        )
        drawLine(
            color = Color(0xFF1E293B),
            start = Offset(0f, chestY),
            end = Offset(w, chestY),
            strokeWidth = 1.dp.toPx(),
        )

        // Torso 2.5D
        val torsoWidth = w * 0.34f
        val torsoHeight = h * 0.42f
        drawRoundRect(
            color = Color(0xFF1E3A8A),
            topLeft = Offset(cx - torsoWidth / 2f, chestY - h * 0.10f),
            size = Size(torsoWidth, torsoHeight),
            cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
        )
        // Cuello
        drawLine(
            color = Color(0xFFFBBF24),
            start = Offset(cx, headCenter.y + headRadius * 0.85f),
            end = Offset(cx, chestY - h * 0.08f),
            strokeWidth = 10.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // Cabeza
        drawCircle(
            color = Color(0xFFFDE68A),
            radius = headRadius,
            center = headCenter,
        )
        drawCircle(
            color = Color(0xFFF59E0B),
            radius = headRadius,
            center = headCenter,
            style = Stroke(width = 2.dp.toPx()),
        )

        // Ojos y Cejas (Rasgos No Manuales obligatorios en gramática LSA)
        val eyeOffsetX = headRadius * 0.36f
        val eyeY = headCenter.y - headRadius * 0.10f
        drawCircle(color = Color(0xFF0F172A), radius = 3.dp.toPx(), center = Offset(headCenter.x - eyeOffsetX, eyeY))
        drawCircle(color = Color(0xFF0F172A), radius = 3.dp.toPx(), center = Offset(headCenter.x + eyeOffsetX, eyeY))

        val browShift = -pose.eyebrowRaise * 4.dp.toPx()
        drawLine(
            color = Color(0xFF0F172A),
            start = Offset(headCenter.x - eyeOffsetX - 5.dp.toPx(), eyeY - 6.dp.toPx() + browShift),
            end = Offset(headCenter.x - eyeOffsetX + 5.dp.toPx(), eyeY - 6.dp.toPx() - browShift * 0.5f),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color(0xFF0F172A),
            start = Offset(headCenter.x + eyeOffsetX - 5.dp.toPx(), eyeY - 6.dp.toPx() - browShift * 0.5f),
            end = Offset(headCenter.x + eyeOffsetX + 5.dp.toPx(), eyeY - 6.dp.toPx() + browShift),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // Boca articulada
        val mouthY = headCenter.y + headRadius * 0.42f
        val mouthPath = Path().apply {
            moveTo(headCenter.x - 6.dp.toPx(), mouthY)
            quadraticTo(
                headCenter.x,
                mouthY + (pose.mouthOpen * 8.dp.toPx()),
                headCenter.x + 6.dp.toPx(),
                mouthY,
            )
        }
        drawPath(
            path = mouthPath,
            color = Color(0xFF0F172A),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )

        // Hombros (en vista frontal del interlocutor: mano dominante derecha del signante se ve a la izquierda o derecha clara)
        val leftShoulder = Offset(cx - torsoWidth * 0.48f, chestY - h * 0.04f)
        val rightShoulder = Offset(cx + torsoWidth * 0.48f, chestY - h * 0.04f)

        // Mano Izquierda (No dominante / Base en señas bimanuales)
        val leftWrist = Offset(
            x = cx + pose.leftHandX * (w * 0.36f),
            y = chestY + pose.leftHandY * (h * 0.36f),
        )
        val leftElbow = Offset(
            x = (leftShoulder.x + leftWrist.x) * 0.5f - 14.dp.toPx(),
            y = maxOf(leftShoulder.y, leftWrist.y) + 16.dp.toPx(),
        )
        drawArmAndArticulatedHand(
            shoulder = leftShoulder,
            elbow = leftElbow,
            wrist = leftWrist,
            fingers = pose.leftFingers,
            isRightHand = false,
            isActiveHand = pose.bimanual,
        )

        // Mano Derecha Dominante (Signante Principal)
        val rightWrist = Offset(
            x = cx + pose.rightHandX * (w * 0.36f),
            y = chestY + pose.rightHandY * (h * 0.36f),
        )
        val rightElbow = Offset(
            x = (rightShoulder.x + rightWrist.x) * 0.5f + 14.dp.toPx(),
            y = maxOf(rightShoulder.y, rightWrist.y) + 16.dp.toPx(),
        )
        drawArmAndArticulatedHand(
            shoulder = rightShoulder,
            elbow = rightElbow,
            wrist = rightWrist,
            fingers = pose.rightFingers,
            isRightHand = true,
            isActiveHand = true,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArmAndArticulatedHand(
    shoulder: Offset,
    elbow: Offset,
    wrist: Offset,
    fingers: List<Float>,
    isRightHand: Boolean,
    isActiveHand: Boolean,
) {
    val sleeveColor = if (isActiveHand) Color(0xFF38BDF8) else Color(0xFF3B82F6)
    val palmColor = if (isActiveHand) Color(0xFFFDE68A) else Color(0xFFFCD34D)

    // Brazo y antebrazo
    drawLine(
        color = sleeveColor,
        start = shoulder,
        end = elbow,
        strokeWidth = 9.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = sleeveColor,
        start = elbow,
        end = wrist,
        strokeWidth = 8.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawCircle(color = Color(0xFF0284C7), radius = 4.dp.toPx(), center = elbow)

    // Palma de la mano
    val palmRadius = 9.dp.toPx()
    drawCircle(color = palmColor, radius = palmRadius, center = wrist)
    drawCircle(
        color = Color(0xFFD97706),
        radius = palmRadius,
        center = wrist,
        style = Stroke(width = 1.5.dp.toPx()),
    )

    // 5 Dedos articulados: [Pulgar, Índice, Medio, Anular, Meñique]
    val baseAnglesDeg = if (isRightHand) {
        floatArrayOf(-150f, -112f, -90f, -68f, -46f)
    } else {
        floatArrayOf(-30f, -68f, -90f, -112f, -134f)
    }
    val maxLengthsDp = floatArrayOf(11f, 15f, 16.5f, 15f, 12.5f)

    for (i in 0 until 5) {
        val extension = fingers.getOrElse(i) { 1f }.coerceIn(0.15f, 1f)
        val angleRad = Math.toRadians(baseAnglesDeg[i].toDouble())
        val lenPx = maxLengthsDp[i].dp.toPx() * (0.35f + 0.65f * extension)
        val start = Offset(
            x = wrist.x + (cos(angleRad) * (palmRadius * 0.7f)).toFloat(),
            y = wrist.y + (sin(angleRad) * (palmRadius * 0.7f)).toFloat(),
        )
        val tip = Offset(
            x = start.x + (cos(angleRad) * lenPx).toFloat(),
            y = start.y + (sin(angleRad) * lenPx).toFloat(),
        )
        drawLine(
            color = palmColor,
            start = start,
            end = tip,
            strokeWidth = 3.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = Color(0xFFF59E0B),
            radius = 1.8.dp.toPx(),
            center = tip,
        )
    }
}

private fun lerpPose(a: LsaPoseKeyframe, b: LsaPoseKeyframe, t: Float): LsaPoseKeyframe {
    fun lerp(v0: Float, v1: Float) = v0 + (v1 - v0) * t
    val rightF = List(5) { idx ->
        lerp(a.rightFingers.getOrElse(idx) { 1f }, b.rightFingers.getOrElse(idx) { 1f })
    }
    val leftF = List(5) { idx ->
        lerp(a.leftFingers.getOrElse(idx) { 0.2f }, b.leftFingers.getOrElse(idx) { 0.2f })
    }
    return LsaPoseKeyframe(
        rightHandX = lerp(a.rightHandX, b.rightHandX),
        rightHandY = lerp(a.rightHandY, b.rightHandY),
        leftHandX = lerp(a.leftHandX, b.leftHandX),
        leftHandY = lerp(a.leftHandY, b.leftHandY),
        rightFingers = rightF,
        leftFingers = leftF,
        bimanual = a.bimanual || b.bimanual,
        palmFacingFront = a.palmFacingFront,
        eyebrowRaise = lerp(a.eyebrowRaise, b.eyebrowRaise),
        mouthOpen = lerp(a.mouthOpen, b.mouthOpen),
        headTiltDeg = lerp(a.headTiltDeg, b.headTiltDeg),
    )
}

