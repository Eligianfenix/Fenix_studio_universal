/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:03:00 -03:00
 * ARCHIVO: feature/classrooms/src/main/kotlin/com/fenix/studio/feature/classrooms/ClassroomListScreen.kt
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fenix.studio.core.designsystem.modifiers.fenixMinTouchTarget
import com.fenix.studio.core.designsystem.theme.FenixTheme

/**
 * Pantalla `P-07` (Mis Aulas): lista accesible de espacios curriculares, documentos aislados
 * y proyectos guiados con **Progreso Ponderado de Dos Niveles (`RN-24`)**:
 * - Nivel A: Avance Teórico de Bloques Completados.
 * - Nivel B: Dominio Mnemónico FSRS v6.
 */
@Composable
fun ClassroomListScreen(
    state: ClassroomsUiState,
    onSelectClassroom: (String) -> Unit,
    onCreateNewClassroomClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FenixTheme.colors
    val typography = MaterialTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Mis Aulas y Espacios de Estudio (P-07)",
            style = typography.titleMedium,
            color = colors.onBackground,
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.classrooms, key = { it.catedraId }) { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .border(colors.borderStrokeWidth, colors.outline, RoundedCornerShape(12.dp))
                        .clickable { onSelectClassroom(item.catedraId) }
                        .fenixMinTouchTarget()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.displayTitle,
                            style = typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        Text(
                            text = "Dominio Académico Consolidado: ${item.weightedOverallProgressPercent}%",
                            style = typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.primary,
                        )
                    }

                    LinearProgressIndicator(
                        progress = { item.weightedOverallProgressPercent / 100f },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        color = colors.primary,
                        trackColor = colors.outline.copy(alpha = 0.3f),
                    )

                    Text(
                        text = "Modalidad: ${item.modality.name}" +
                            (item.programaYear?.let { " · Programa $it" } ?: ""),
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Nivel A (Teórico): ${item.theoreticalProgressPercent}% (${item.completedBlocksCount}/${item.totalExpectedBlocksCount})",
                            style = typography.bodySmall,
                            color = colors.onSurface,
                        )
                        Text(
                            text = "Nivel B (FSRS v6): ${item.fsrsMasteryPercent}% (${item.masteredFsrsCardsCount}/${item.totalFsrsCardsCount})",
                            style = typography.bodySmall,
                            color = colors.primary,
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
                .clickable(onClick = onCreateNewClassroomClick)
                .fenixMinTouchTarget()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "[ + Nueva Cátedra / Documento ]",
                style = typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.onPrimary,
            )
        }
    }
}
