/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:54:40 -03:00
 * ARCHIVO: feature/processor/src/main/kotlin/com/fenix/studio/feature/processor/CurricularMatrixScreen.kt
 * ================================================================================
 */

package com.fenix.studio.feature.processor

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fenix.studio.core.designsystem.modifiers.fenixMinTouchTarget
import com.fenix.studio.core.designsystem.theme.FenixTheme
import com.fenix.studio.core.document.matching.MatchConfidence

/**
 * Pantalla `P-05b` (Matriz de Vinculación Curricular con semáforo Verde $\ge 85\%$, Ámbar $60..84\%$,
 * Rojo Huérfano $< 60\%$, ajuste de Offset de Página `RN-09` e Ingesta Directa de Huérfanos en 1-Clic `RN-26`).
 */
@Composable
fun CurricularMatrixScreen(
    state: ProcessorWizardUiState,
    onIncrementPageOffset: (String) -> Unit,
    onDecrementPageOffset: (String) -> Unit,
    onConvertOrphanDirectClick: (CurricularMatrixItemUi) -> Unit,
    onStartQueueProcessing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FenixTheme.colors
    val typography = MaterialTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Matriz de Vinculación Curricular (P-05b)",
            style = typography.titleMedium,
            color = colors.onBackground,
        )

        state.matrixItems.forEach { item ->
            val bandColor = when (item.confidenceBand) {
                MatchConfidence.HIGH_GREEN -> colors.primary
                MatchConfidence.MEDIUM_AMBER -> colors.cooldownAccent
                MatchConfidence.LOW_RED_ORPHAN -> colors.error
            }
            val pct = (item.confidenceScore * 100.0).toInt()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .border(1.5.dp, bandColor, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Unidad ${item.unitNumber}: ${item.unitTitle}",
                        style = typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface,
                    )
                    Text(
                        text = "${item.confidenceBand.name} ($pct%)",
                        style = typography.labelLarge,
                        color = bandColor,
                    )
                }

                Text(
                    text = "Fuente vinculada: ${item.matchedFileName ?: "Sin archivo vinculado (Huérfano)"}",
                    style = typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Offset Página Impresa (RN-09): ${item.printedPageOffset} (Pág. inicial: ${item.effectivePrintedStartPage})",
                        style = typography.bodySmall,
                        color = colors.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "-1",
                            style = typography.labelLarge,
                            color = colors.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.background)
                                .clickable { onDecrementPageOffset(item.unitId) }
                                .fenixMinTouchTarget()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                        Text(
                            text = "+1",
                            style = typography.labelLarge,
                            color = colors.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.background)
                                .clickable { onIncrementPageOffset(item.unitId) }
                                .fenixMinTouchTarget()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }

                if (item.confidenceBand == MatchConfidence.LOW_RED_ORPHAN) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.background)
                            .border(colors.borderStrokeWidth, colors.cooldownAccent, RoundedCornerShape(8.dp))
                            .clickable { onConvertOrphanDirectClick(item) }
                            .fenixMinTouchTarget()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "1-Clic: Procesar Huérfano como Apunte Directo (RN-26)",
                            style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.cooldownAccent,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.primary)
                .clickable(onClick = onStartQueueProcessing)
                .fenixMinTouchTarget()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Iniciar Cola en Monitor por Unidad (P-06)",
                style = typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.onPrimary,
            )
        }
    }
}
