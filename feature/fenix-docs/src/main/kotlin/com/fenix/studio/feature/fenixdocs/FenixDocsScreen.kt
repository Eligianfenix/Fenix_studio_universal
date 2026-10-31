/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:05:15 -03:00
 * ARCHIVO: feature/fenix-docs/src/main/kotlin/com/fenix/studio/feature/fenixdocs/FenixDocsScreen.kt
 * ================================================================================
 */

package com.fenix.studio.feature.fenixdocs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.fenix.studio.core.designsystem.modifiers.fenixMinTouchTarget
import com.fenix.studio.core.designsystem.theme.FenixTheme
import kotlin.math.roundToInt

/**
 * Categorías de agrupamiento ergonómico de las 20 Herramientas APA 7ma para navegación fluida.
 */
enum class Apa7ToolCategory(val title: String, val range: IntRange) {
    STRUCTURE("Estructura (1-6)", 1..6),
    CITATIONS("Citas (7-10)", 7..10),
    TABLES_NOTES("Tablas y Notas (11-17)", 11..17),
    NORMS_BIBLIO("Normas y Bibliografía (18-20)", 18..20),
}

/**
 * Pantalla `P-09` (Editor Académico **Fenix Docs**, `MF-7.1`):
 * - Barra horizontal desplazable con las **20 Herramientas APA 7.ª Edición**.
 * - Lienzo paginado estilo hoja A4 con márgenes visuales de 1 pulgada (`1440 twips`) e interlineado doble (`480`).
 * - **Ventana Flotante Arrastrable del Asistente IA (`RN-21`)** con `pointerInput` + `detectDragGestures`.
 */
@Composable
fun FenixDocsScreen(
    state: FenixDocsEditorState,
    onApplyApa7ToolClick: (Apa7DocumentTool) -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onDragAiAssistant: (Float, Float) -> Unit,
    onToggleAiAssistantExpanded: (Boolean) -> Unit,
    onExportDocxClick: () -> Unit,
    onExportPdfClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FenixTheme.colors
    val typography = MaterialTheme.typography
    var selectedCategory by remember { mutableStateOf(Apa7ToolCategory.STRUCTURE) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Fenix Docs · Editor APA 7ma (P-09)",
                        style = typography.titleMedium,
                        color = colors.onBackground,
                    )
                    if (state.isDraftUnsaved) {
                        Text(
                            text = "[ ● Borrador no guardado ]",
                            style = typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFFE65100),
                        )
                    } else {
                        Text(
                            text = "[ ✓ Guardado localmente ]",
                            style = typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF2E7D32),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "[ Deshacer (${state.undoStackSize}) ]",
                        style = typography.labelMedium,
                        color = colors.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.surface)
                            .clickable(onClick = onUndoClick)
                            .fenixMinTouchTarget()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    Text(
                        text = "[ Rehacer (${state.redoStackSize}) ]",
                        style = typography.labelMedium,
                        color = colors.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.surface)
                            .clickable(onClick = onRedoClick)
                            .fenixMinTouchTarget()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }

            // Chips superiores de agrupamiento ergonómico de las 20 herramientas APA
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Apa7ToolCategory.entries.forEach { category ->
                    val isSelected = category == selectedCategory
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) colors.primary else colors.surface)
                            .border(
                                colors.borderStrokeWidth,
                                if (isSelected) colors.primary else colors.outline,
                                RoundedCornerShape(20.dp),
                            )
                            .clickable { selectedCategory = category }
                            .fenixMinTouchTarget()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = category.title,
                            style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) colors.onPrimary else colors.onSurface,
                        )
                    }
                }
            }

            // Herramientas filtradas por categoría
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Apa7DocumentTool.entries
                    .filter { it.toolNumber in selectedCategory.range }
                    .forEach { tool ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surface)
                                .border(colors.borderStrokeWidth, colors.outline, RoundedCornerShape(8.dp))
                                .clickable { onApplyApa7ToolClick(tool) }
                                .fenixMinTouchTarget()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = tool.displayLabel,
                                style = typography.labelMedium,
                                color = colors.onSurface,
                            )
                        }
                    }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .border(colors.borderStrokeWidth, colors.outline, RoundedCornerShape(12.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.visibleWindowParagraphs) { paragraph ->
                    Text(
                        text = paragraph.text,
                        style = typography.bodyLarge.copy(
                            fontWeight = if (paragraph.isBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (paragraph.isItalic) FontStyle.Italic else FontStyle.Normal,
                        ),
                        color = colors.onSurface,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.primary)
                        .clickable(onClick = onExportDocxClick)
                        .fenixMinTouchTarget()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Exportar .DOCX (OpenXML)",
                        style = typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onPrimary,
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface)
                        .border(colors.borderStrokeWidth, colors.primary, RoundedCornerShape(10.dp))
                        .clickable(onClick = onExportPdfClick)
                        .fenixMinTouchTarget()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Exportar .PDF (Paginado)",
                        style = typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary,
                    )
                }
            }
        }

        // Ventana Flotante Arrastrable del Asistente IA (RN-21)
        val assistant = state.assistantState
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = assistant.offsetX.roundToInt(),
                        y = assistant.offsetY.roundToInt(),
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDragAiAssistant(dragAmount.x, dragAmount.y)
                    }
                }
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .border(2.dp, colors.primary, RoundedCornerShape(16.dp))
                .clickable { onToggleAiAssistantExpanded(!assistant.isExpanded) }
                .fenixMinTouchTarget()
                .padding(12.dp),
        ) {
            if (!assistant.isExpanded) {
                Text(
                    text = "Asistente IA Flotante (RN-21)",
                    style = typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.primary,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Asistente IA Contextual (Arrastrable · RN-21)",
                        style = typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary,
                    )
                    Text(
                        text = assistant.lastAssistantResponse ?: "Arrastra libremente este panel sin tapar tu línea de edición activa.",
                        style = typography.bodySmall,
                        color = colors.onSurface,
                    )
                }
            }
        }
    }
}
