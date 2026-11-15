/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-15 03:05:00 -03:00
 * ARCHIVO: feature/processor/src/main/kotlin/com/fenix/studio/feature/processor/PreIngestionUiModels.kt
 * ================================================================================
 */

package com.fenix.studio.feature.processor

import androidx.compose.runtime.Immutable
import com.fenix.studio.core.document.guardian.DetectedTocEntry
import com.fenix.studio.core.document.guardian.DocumentIngestionVerdict
import com.fenix.studio.core.document.ingestion.PreIngestionOutcome
import com.fenix.studio.core.document.ingestion.SanitizationFinding

/** Archivo elegido por la persona usuaria con el selector del sistema. */
@Immutable
data class PickedDocument(
    val uri: String,
    val fileName: String,
    val sizeBytes: Long? = null,
    val mimeType: String? = null,
)

/** Un archivo ya pasado por el motor de pre-ingesta, listo para mostrar. */
@Immutable
data class PreIngestionFileUi(
    val key: String,
    val fileName: String,
    val sha256Hex: String,
    val formatLabel: String,
    val verdict: DocumentIngestionVerdict,
    val verdictLabel: String,
    val totalPages: Int,
    val characterDensityPerPage: Double,
    val sizeBytes: Long?,
    val tocEntries: List<DetectedTocEntry>,
    val findings: List<SanitizationFinding>,
    val warningMessage: String?,
    val isReady: Boolean,
    val isSuspicious: Boolean,
) {
    val densityLabel: String
        get() = if (totalPages <= 0) "—" else "${"%.0f".format(characterDensityPerPage)} car./pág."

    val pagesLabel: String
        get() = if (totalPages <= 0) "sin páginas" else if (totalPages == 1) "1 página" else "$totalPages páginas"

    val sizeLabel: String?
        get() = sizeBytes?.takeIf { it > 0 }?.let { bytes ->
            when {
                bytes >= 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
                bytes >= 1024 -> "${bytes / 1024} KB"
                else -> "$bytes B"
            }
        }
}

/** Estado completo de la pantalla `P-05a`. */
@Immutable
data class PreIngestionUiState(
    val items: List<PreIngestionFileUi> = emptyList(),
    val isScanning: Boolean = false,
    val scannedCount: Int = 0,
    val totalToScan: Int = 0,
    val currentFileName: String? = null,
    val globalError: String? = null,
) {
    val readyCount: Int get() = items.count { it.isReady }

    val duplicateCount: Int get() = items.count { it.verdict == DocumentIngestionVerdict.DUPLICATE_SHA256 }

    val needsOcrCount: Int get() = items.count { it.verdict == DocumentIngestionVerdict.OCR_REQUIRED_WARNING }

    val failedCount: Int get() = items.count {
        it.verdict == DocumentIngestionVerdict.CORRUPT_OR_EMPTY ||
            it.verdict == DocumentIngestionVerdict.UNSUPPORTED_FORMAT
    }

    val suspiciousCount: Int get() = items.count { it.isSuspicious }

    val hasReadyDocuments: Boolean get() = readyCount > 0
}

/**
 * Traduce el resultado del motor a algo que se pueda leer sin conocer los enums.
 * El veredicto siempre lleva texto además del color: nunca se distingue solo por tono.
 */
fun PreIngestionOutcome.toUi(picked: PickedDocument): PreIngestionFileUi = PreIngestionFileUi(
    key = picked.uri,
    fileName = fileName,
    sha256Hex = sha256Hex,
    formatLabel = format.displayLabel,
    verdict = verdict,
    verdictLabel = when (verdict) {
        DocumentIngestionVerdict.TEXT_LAYER_NATIVE -> "Texto nativo listo"
        DocumentIngestionVerdict.OCR_APPLIED -> "Leído con OCR local"
        DocumentIngestionVerdict.OCR_REQUIRED_WARNING -> "Necesita OCR"
        DocumentIngestionVerdict.DUPLICATE_SHA256 -> "Ya lo tenías"
        DocumentIngestionVerdict.CORRUPT_OR_EMPTY -> "No se pudo leer"
        DocumentIngestionVerdict.UNSUPPORTED_FORMAT -> "Formato no soportado"
    },
    totalPages = totalPages,
    characterDensityPerPage = characterDensityPerPage,
    sizeBytes = picked.sizeBytes,
    tocEntries = tocEntries,
    findings = sanitizationFindings,
    warningMessage = warningMessage,
    isReady = isReadyForIngestion,
    isSuspicious = isSuspicious,
)
