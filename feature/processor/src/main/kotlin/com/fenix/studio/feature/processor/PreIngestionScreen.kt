/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-15 03:40:00 -03:00
 * ARCHIVO: feature/processor/src/main/kotlin/com/fenix/studio/feature/processor/PreIngestionScreen.kt
 * ================================================================================
 */

package com.fenix.studio.feature.processor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fenix.studio.core.designsystem.modifiers.fenixMinTouchTarget
import com.fenix.studio.core.designsystem.theme.FenixTheme
import com.fenix.studio.core.document.guardian.DocumentIngestionVerdict
import com.fenix.studio.core.document.ingestion.SanitizationFindingKind

/**
 * Pantalla `P-05a` (Carga de Material y Pre-ingesta multiformato).
 *
 * Muestra el veredicto real del motor por archivo: texto nativo, OCR aplicado, OCR
 * requerido, duplicado por SHA-256, archivo corrupto o formato no soportado. El estado
 * nunca se comunica solo con color: siempre hay texto y detalle.
 */
@Composable
fun PreIngestionScreen(
    state: PreIngestionUiState,
    onDocumentsPicked: (List<String>) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FenixTheme.colors
    val typography = MaterialTheme.typography

    // `*/*` deja elegir cualquier formato; el motor detecta el tipo real por extensión y cabecera.
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> onDocumentsPicked(uris.map { it.toString() }) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Carga de Material (P-05a)",
                    style = typography.titleMedium,
                    color = colors.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "Elegí los archivos de la cátedra. Se leen en tu teléfono, sin subirlos a ningún servidor.",
                    style = typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = "Formatos: PDF, Word, ODT, PPTX, EPUB, HTML, Markdown, texto, código e imágenes.",
                    style = typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        item {
            FenixActionRow(
                label = if (state.isScanning) "Leyendo archivos…" else "Elegir archivos",
                enabled = !state.isScanning,
                filled = true,
                onClick = { picker.launch(arrayOf("*/*")) },
            )
        }

        if (state.isScanning) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val total = state.totalToScan.coerceAtLeast(1)
                    LinearProgressIndicator(
                        progress = { state.scannedCount.toFloat() / total },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp)),
                        color = colors.primary,
                        trackColor = colors.outline.copy(alpha = 0.3f),
                    )
                    Text(
                        text = "Escaneando ${state.scannedCount} de ${state.totalToScan}" +
                            (state.currentFileName?.let { " · $it" } ?: ""),
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }

        state.globalError?.let { error ->
            item {
                Text(
                    text = error,
                    style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.error,
                )
            }
        }

        if (state.items.isNotEmpty()) {
            item {
                Text(
                    text = buildSummary(state),
                    style = typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        items(items = state.items, key = { it.key }) { item ->
            PreIngestionFileCard(item = item, onRemove = { onRemove(item.key) })
        }

        if (state.items.isEmpty() && !state.isScanning) {
            item {
                Text(
                    text = "Todavía no cargaste archivos.",
                    style = typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        if (state.items.isNotEmpty()) {
            item {
                FenixActionRow(
                    label = "Vaciar lote",
                    enabled = !state.isScanning,
                    filled = false,
                    onClick = onClearAll,
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FenixActionRow(
                    label = "Continuar a Matriz Curricular (P-05b)",
                    enabled = state.hasReadyDocuments && !state.isScanning,
                    filled = true,
                    onClick = onContinue,
                )
                if (!state.hasReadyDocuments) {
                    Text(
                        text = "Cargá al menos un documento legible para continuar.",
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreIngestionFileCard(
    item: PreIngestionFileUi,
    onRemove: () -> Unit,
) {
    val colors = FenixTheme.colors
    val typography = MaterialTheme.typography
    var showToc by rememberSaveable(item.key) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(colors.borderStrokeWidth, colors.outline, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = item.fileName,
            style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface,
        )

        Text(
            text = "${item.formatLabel} · ${item.verdictLabel}",
            style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = verdictColor(item),
        )

        val details = buildList {
            add(item.pagesLabel)
            if (item.totalPages > 0) add(item.densityLabel)
            item.sizeLabel?.let { add(it) }
        }
        Text(
            text = details.joinToString(" · "),
            style = typography.bodySmall,
            color = colors.onSurfaceVariant,
        )

        item.warningMessage?.takeIf { it.isNotBlank() }?.let { warning ->
            Text(
                text = warning,
                style = typography.bodySmall,
                color = if (item.isReady) colors.onSurfaceVariant else colors.error,
            )
        }

        if (item.tocEntries.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showToc = !showToc }
                    .fenixMinTouchTarget()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (showToc) {
                        "Ocultar índice detectado (${item.tocEntries.size}) ▲"
                    } else {
                        "Ver índice detectado (${item.tocEntries.size}) ▼"
                    },
                    style = typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.primary,
                )
            }

            if (showToc) {
                item.tocEntries.forEach { entry ->
                    Text(
                        text = "${"  ".repeat(entry.level.coerceIn(0, 2))}• ${entry.title} — pág. ${entry.pageNumber}",
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }

        if (item.findings.isNotEmpty()) {
            val visible = item.findings.take(3)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Hallazgos forenses (${item.findings.size})",
                    style = typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (item.isSuspicious) colors.error else colors.onSurfaceVariant,
                )
                visible.forEach { finding ->
                    val label = if (finding.kind == SanitizationFindingKind.PROMPT_INJECTION) {
                        "⚠ ${finding.kind.displayLabel}"
                    } else {
                        finding.kind.displayLabel
                    }
                    Text(
                        text = "$label · pág. ${finding.pageNumber}, línea ${finding.lineNumber}",
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (item.findings.size > visible.size) {
                    Text(
                        text = "y ${item.findings.size - visible.size} más",
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }

        FenixActionRow(
            label = "Quitar del lote",
            enabled = true,
            filled = false,
            onClick = onRemove,
        )
    }
}

@Composable
private fun verdictColor(item: PreIngestionFileUi): Color {
    val colors = FenixTheme.colors
    return when (item.verdict) {
        DocumentIngestionVerdict.TEXT_LAYER_NATIVE,
        DocumentIngestionVerdict.OCR_APPLIED,
        -> colors.primary

        DocumentIngestionVerdict.OCR_REQUIRED_WARNING -> colors.propositionComplementaryAccent
        DocumentIngestionVerdict.DUPLICATE_SHA256 -> colors.onSurfaceVariant
        DocumentIngestionVerdict.CORRUPT_OR_EMPTY,
        DocumentIngestionVerdict.UNSUPPORTED_FORMAT,
        -> colors.error
    }
}

@Composable
private fun FenixActionRow(
    label: String,
    enabled: Boolean,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val colors = FenixTheme.colors
    val typography = MaterialTheme.typography
    val background = when {
        !enabled -> colors.surface
        filled -> colors.primary
        else -> colors.background
    }
    val contentColor = when {
        !enabled -> colors.onSurfaceVariant
        filled -> colors.onPrimary
        else -> colors.onBackground
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(
                width = colors.borderStrokeWidth,
                color = if (enabled) colors.primary else colors.outline,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .fenixMinTouchTarget()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = contentColor,
        )
    }
}

private fun buildSummary(state: PreIngestionUiState): String = buildString {
    append("${state.items.size} archivo(s) en el lote: ")
    append("${state.readyCount} listo(s)")
    if (state.needsOcrCount > 0) append(" · ${state.needsOcrCount} necesita(n) OCR")
    if (state.duplicateCount > 0) append(" · ${state.duplicateCount} duplicado(s)")
    if (state.failedCount > 0) append(" · ${state.failedCount} con problemas")
    if (state.suspiciousCount > 0) append(" · ${state.suspiciousCount} con alerta forense")
}
