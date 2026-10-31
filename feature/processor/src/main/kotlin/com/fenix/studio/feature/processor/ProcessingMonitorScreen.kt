/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:54:55 -03:00
 * ARCHIVO: feature/processor/src/main/kotlin/com/fenix/studio/feature/processor/ProcessingMonitorScreen.kt
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
import com.fenix.studio.core.document.queue.PointQueueStatus

/**
 * Pantalla `P-06` (Monitor de Procesamiento por Unidad con Lectura Anticipada `RN-06`
 * mientras el resto de la cola sigue procesándose, y exportación soberana `.docx`, `.pdf`, `.pptx`, `.mp4`).
 */
@Composable
fun ProcessingMonitorScreen(
    state: ProcessorWizardUiState,
    onOpenEarlyReading: (String) -> Unit,
    onExportFormatClick: (SovereignExportFormat) -> Unit,
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
            text = "Monitor de Procesamiento por Unidad (P-06)",
            style = typography.titleMedium,
            color = colors.onBackground,
        )

        state.queueTasks.forEach { task ->
            val isCompleted = task.status == PointQueueStatus.COMPLETED
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .border(
                        width = colors.borderStrokeWidth,
                        color = if (isCompleted) colors.primary else colors.outline,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Unidad ${task.unitNumber}: ${task.unitTitle}",
                        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface,
                    )
                    Text(
                        text = "${task.status.name} · Bloques ${task.completedBlocksCount}/${task.totalBlocksCount}",
                        style = typography.labelLarge,
                        color = if (isCompleted) colors.primary else colors.onSurfaceVariant,
                    )
                }

                if (isCompleted) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.background)
                            .border(colors.borderStrokeWidth, colors.primary, RoundedCornerShape(8.dp))
                            .clickable { onOpenEarlyReading(task.unitId) }
                            .fenixMinTouchTarget()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Lectura Anticipada Inmediata (RN-06): Abrir y Escuchar TTS Ahora",
                            style = typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.primary,
                        )
                    }
                }
            }
        }

        Text(
            text = "Exportación Soberana Multiformato (OpenXML / PDF / PPTX / MP4 Local)",
            style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onBackground,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SovereignExportFormat.entries.forEach { format ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .border(colors.borderStrokeWidth, colors.primary, RoundedCornerShape(8.dp))
                        .clickable { onExportFormatClick(format) }
                        .fenixMinTouchTarget()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = format.name.substringBefore("_"),
                        style = typography.labelLarge,
                        color = colors.primary,
                    )
                }
            }
        }
    }
}
