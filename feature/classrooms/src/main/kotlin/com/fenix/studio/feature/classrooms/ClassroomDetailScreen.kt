/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:03:20 -03:00
 * ARCHIVO: feature/classrooms/src/main/kotlin/com/fenix/studio/feature/classrooms/ClassroomDetailScreen.kt
 * ================================================================================
 */

package com.fenix.studio.feature.classrooms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fenix.studio.core.designsystem.modifiers.FenixSubpointUiModel
import com.fenix.studio.core.designsystem.modifiers.fenixMinTouchTarget
import com.fenix.studio.core.designsystem.modifiers.fenixSubpointAccessibility
import com.fenix.studio.core.designsystem.theme.FenixTheme
import com.fenix.studio.domain.fsrs.FsrsRating

/**
 * Pantalla `P-08` (Aula en Detalle):
 * - Árbol modular por Unidades, Puntos y Subpuntos por Densidad Teórica (`N.N.a ✦` con locución limpia TalkBack).
 * - Mazo interactivo de flashcards FSRS v6 (`[ Otra Vez ]`, `[ Difícil ]`, `[ Bien ]`, `[ Fácil ]`) a Costo 0 de tokens.
 * - Acción `[ + Agregar Material Complementario ]` para Ingesta Incremental (`RN-22`).
 */
@Composable
fun ClassroomDetailScreen(
    state: ClassroomsUiState,
    onRevealFlashcardClick: () -> Unit,
    onRateFlashcardClick: (FsrsRating) -> Unit,
    onAddIncrementalMaterialClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FenixTheme.colors
    val typography = MaterialTheme.typography

    var activeIncrementalPoint by remember { mutableStateOf<Pair<String, String>?>(null) }
    var inputIncrementalFileName by remember { mutableStateOf("") }

    if (activeIncrementalPoint != null) {
        val (unitId, pointCode) = activeIncrementalPoint!!
        AlertDialog(
            onDismissRequest = { activeIncrementalPoint = null },
            title = {
                Text(
                    text = "Ingesta Incremental (RN-22)",
                    style = typography.titleMedium,
                    color = colors.onSurface,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Vincular nuevo material exclusivamente al Punto $pointCode sin reiniciar el aula:",
                        style = typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    BasicTextField(
                        value = inputIncrementalFileName,
                        onValueChange = { inputIncrementalFileName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.background)
                            .border(colors.borderStrokeWidth, colors.outline, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        textStyle = typography.bodyMedium.copy(color = colors.onBackground),
                    )
                    Text(
                        text = "Ejemplo: capitulo_complementario.pdf (págs. 1-15)",
                        style = typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val fileName = inputIncrementalFileName.ifBlank { "material_complementario.pdf" }
                        onAddIncrementalMaterialClick(unitId, pointCode)
                        activeIncrementalPoint = null
                        inputIncrementalFileName = ""
                    },
                ) {
                    Text("[ Ingestar Material ]")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { activeIncrementalPoint = null }) {
                    Text("[ Cancelar ]")
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Aula en Detalle y Mazo FSRS v6 (P-08)",
            style = typography.titleMedium,
            color = colors.onBackground,
        )

        state.fsrsSession.activeCard?.let { activeCard ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .border(colors.borderStrokeWidth, colors.primary, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Mazo de Repaso Espaciado FSRS v6 (Costo 0 Tokens · Punto ${activeCard.pointCode})",
                    style = typography.labelLarge,
                    color = colors.primary,
                )
                Text(
                    text = activeCard.frontPrompt,
                    style = typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                )

                if (!state.fsrsSession.isBackAnswerRevealed) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.background)
                            .border(colors.borderStrokeWidth, colors.primary, RoundedCornerShape(8.dp))
                            .clickable(onClick = onRevealFlashcardClick)
                            .fenixMinTouchTarget()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Mostrar Respuesta Verificada",
                            style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.primary,
                        )
                    }
                } else {
                    Text(
                        text = activeCard.backAnswer,
                        style = typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val ratingConfigs = listOf(
                            Triple(FsrsRating.AGAIN, "[ Otra Vez ]", Color(0xFFD32F2F)),
                            Triple(FsrsRating.HARD, "[ Difícil ]", Color(0xFFE65100)),
                            Triple(FsrsRating.GOOD, "[ Bien ]", colors.primary),
                            Triple(FsrsRating.EASY, "[ Fácil ]", Color(0xFF2E7D32)),
                        )
                        ratingConfigs.forEach { (rating, label, accentColor) ->
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(accentColor.copy(alpha = 0.12f))
                                    .border(colors.borderStrokeWidth, accentColor, RoundedCornerShape(8.dp))
                                    .clickable { onRateFlashcardClick(rating) }
                                    .fenixMinTouchTarget()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = label,
                                    style = typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = accentColor,
                                )
                            }
                        }
                    }
                }
            }
        }

        state.selectedClassroomUnits.forEach { unit ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .border(colors.borderStrokeWidth, colors.outline, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Unidad ${unit.unitNumber}: ${unit.unitTitle}",
                    style = typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                )

                unit.points.forEach { point ->
                    val subpointModel = FenixSubpointUiModel(
                        displayCode = point.pointCode,
                        title = point.title,
                        isSyntheticDensityNode = point.isSyntheticDensityNode,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.background)
                            .border(colors.borderStrokeWidth, colors.outline, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "${point.pointCode} ${point.title}",
                            style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onBackground,
                            modifier = Modifier.fenixSubpointAccessibility(subpointModel),
                        )

                        if (point.linkedMaterialNames.isNotEmpty()) {
                            Text(
                                text = "Fuentes vinculadas: ${point.linkedMaterialNames.joinToString(", ")}",
                                style = typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.surface)
                                .border(colors.borderStrokeWidth, colors.primary, RoundedCornerShape(6.dp))
                                .clickable { activeIncrementalPoint = unit.unitId to point.pointCode }
                                .fenixMinTouchTarget()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "[ + Agregar Material Complementario (Ingesta Incremental RN-22) ]",
                                style = typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
