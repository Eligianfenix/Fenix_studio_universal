/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-14 23:50:00 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/ingestion/PreIngestionEngine.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.ingestion

import com.fenix.studio.core.document.guardian.DetectedTocEntry
import com.fenix.studio.core.document.guardian.DocumentIngestionVerdict
import com.fenix.studio.core.document.guardian.PreIngestionGuardian

/**
 * Resultado de la pre-ingesta: lo que la pantalla muestra y lo que F4b podrá ingerir.
 *
 * [document] viene **sanitizado** (delimitadores neutralizados, líneas sospechosas
 * marcadas). Si es `null`, no hay nada que ingerir.
 */
data class PreIngestionOutcome(
    val fileName: String,
    val format: AcademicDocumentFormat,
    val sha256Hex: String,
    val verdict: DocumentIngestionVerdict,
    val warningMessage: String?,
    val totalPages: Int,
    val characterDensityPerPage: Double,
    val tocEntries: List<DetectedTocEntry>,
    val sanitizationFindings: List<SanitizationFinding>,
    val document: RawAcademicDocument?,
    val passivePreamble: String,
) {
    val isDuplicate: Boolean
        get() = verdict == DocumentIngestionVerdict.DUPLICATE_SHA256

    val needsOcr: Boolean
        get() = verdict == DocumentIngestionVerdict.OCR_REQUIRED_WARNING

    val isReadyForIngestion: Boolean
        get() = document != null &&
            verdict != DocumentIngestionVerdict.DUPLICATE_SHA256 &&
            verdict != DocumentIngestionVerdict.CORRUPT_OR_EMPTY &&
            verdict != DocumentIngestionVerdict.UNSUPPORTED_FORMAT

    val isSuspicious: Boolean
        get() = sanitizationFindings.any { it.kind == SanitizationFindingKind.PROMPT_INJECTION }
}

/**
 * Motor real de pre-ingesta multiformato (`F4a`).
 *
 * Orden del pipeline:
 * 1. Detecta el formato (extensión + cabecera).
 * 2. Calcula el SHA-256 en streaming y detecta duplicados contra lo ya guardado.
 * 3. Extrae hasta 15 páginas con el extractor del formato (OCR local si hace falta).
 * 4. Sanitiza el texto con la capa forense pasiva.
 * 5. Aplica el guardián pre-ingesta: densidad, TOC y veredicto.
 *
 * No mapea bibliografía ni toca el parser curricular: eso es F4b.
 */
class PreIngestionEngine(
    private val extractionEngine: DocumentExtractionEngine = DocumentExtractionEngine(),
    private val sanitizer: PassiveContentSanitizer = PassiveContentSanitizer(),
    private val guardian: PreIngestionGuardian = PreIngestionGuardian(),
) {

    suspend fun prepare(
        source: DocumentSource,
        existingSha256Hashes: Set<String> = emptySet(),
    ): PreIngestionOutcome {
        val format = extractionEngine.detectFormat(source)
        val sha256 = extractionEngine.computeSha256Hex(source)

        if (sha256 == null) {
            return failed(
                fileName = source.fileName,
                format = format,
                sha256 = "",
                message = "No se pudo leer '${source.fileName}'. Verificá que el archivo no esté dañado.",
            )
        }

        if (sha256 in existingSha256Hashes) {
            return PreIngestionOutcome(
                fileName = source.fileName,
                format = format,
                sha256Hex = sha256,
                verdict = DocumentIngestionVerdict.DUPLICATE_SHA256,
                warningMessage = "'${source.fileName}' ya fue incorporado antes (SHA-256 idéntico).",
                totalPages = 0,
                characterDensityPerPage = 0.0,
                tocEntries = emptyList(),
                sanitizationFindings = emptyList(),
                document = null,
                passivePreamble = "",
            )
        }

        if (!extractionEngine.supports(format)) {
            return failed(
                fileName = source.fileName,
                format = format,
                sha256 = sha256,
                message = "El formato de '${source.fileName}' no está soportado todavía.",
                verdict = DocumentIngestionVerdict.UNSUPPORTED_FORMAT,
            )
        }

        return when (val extraction = extractionEngine.extract(source)) {
            is DocumentExtractionOutcome.Failed -> failed(
                fileName = source.fileName,
                format = format,
                sha256 = sha256,
                message = extraction.message,
            )

            is DocumentExtractionOutcome.Unsupported -> failed(
                fileName = source.fileName,
                format = format,
                sha256 = sha256,
                message = extraction.message,
                verdict = DocumentIngestionVerdict.UNSUPPORTED_FORMAT,
            )

            is DocumentExtractionOutcome.Success -> inspect(extraction.document)
        }
    }

    private fun inspect(document: RawAcademicDocument): PreIngestionOutcome {
        val sanitized = sanitizer.sanitizeDocument(document)
        val cleanDocument = sanitized.document
        val samplePages = cleanDocument.firstPagesText(PreIngestionGuardian.MAX_TOC_SCAN_PAGES)

        val scan = guardian.inspectDocument(
            documentName = cleanDocument.fileName,
            computedSha256Hex = cleanDocument.sha256Hex,
            totalPages = cleanDocument.totalPages,
            sampledPagesText = samplePages,
        )

        val (verdict, warning) = resolveVerdict(
            document = cleanDocument,
            guardianVerdict = scan.verdict,
            guardianWarning = scan.warningMessage,
            density = scan.sampleCharacterDensityPerPage,
        )

        return PreIngestionOutcome(
            fileName = cleanDocument.fileName,
            format = cleanDocument.format,
            sha256Hex = cleanDocument.sha256Hex,
            verdict = verdict,
            warningMessage = warning,
            totalPages = cleanDocument.totalPages,
            characterDensityPerPage = scan.sampleCharacterDensityPerPage,
            tocEntries = scan.detectedTocEntries,
            sanitizationFindings = sanitized.findings,
            document = cleanDocument,
            passivePreamble = sanitized.passivePreamble,
        )
    }

    /**
     * El guardián mide densidad de texto; no sabe si ese texto salió de OCR ni si el
     * extractor declaró el documento ilegible. Acá se corrige el veredicto para no
     * mentirle al estudiante.
     */
    private fun resolveVerdict(
        document: RawAcademicDocument,
        guardianVerdict: DocumentIngestionVerdict,
        guardianWarning: String?,
        density: Double,
    ): Pair<DocumentIngestionVerdict, String?> = when {
        document.extractionMode == ExtractionMode.UNSUPPORTED -> {
            DocumentIngestionVerdict.CORRUPT_OR_EMPTY to (
                document.warnings.firstOrNull()
                    ?: "'${document.fileName}' no tiene contenido legible."
                )
        }

        document.isScannedOnly && document.pages.any { it.text.isNotBlank() } -> {
            DocumentIngestionVerdict.OCR_APPLIED to buildString {
                append("'${document.fileName}' se leyó con OCR local: ")
                append("${document.pages.size} página(s) de ${document.totalPages}. ")
                append("El texto puede tener errores de reconocimiento.")
                if (document.warnings.isNotEmpty()) {
                    append(' ')
                    append(document.warnings.joinToString(" "))
                }
            }
        }

        // Un DOCX o un TXT corto no se arregla con OCR: no tiene sentido pedirlo.
        guardianVerdict == DocumentIngestionVerdict.OCR_REQUIRED_WARNING &&
            !document.format.usesOcr -> {
            DocumentIngestionVerdict.TEXT_LAYER_NATIVE to
                ("'${document.fileName}' tiene poco texto (${"%.1f".format(density)} chars/página), " +
                    "pero ${document.format.displayLabel} no requiere OCR.")
        }

        else -> guardianVerdict to guardianWarning
    }

    private fun failed(
        fileName: String,
        format: AcademicDocumentFormat,
        sha256: String,
        message: String,
        verdict: DocumentIngestionVerdict = DocumentIngestionVerdict.CORRUPT_OR_EMPTY,
    ): PreIngestionOutcome = PreIngestionOutcome(
        fileName = fileName,
        format = format,
        sha256Hex = sha256,
        verdict = verdict,
        warningMessage = message,
        totalPages = 0,
        characterDensityPerPage = 0.0,
        tocEntries = emptyList(),
        sanitizationFindings = emptyList(),
        document = null,
        passivePreamble = "",
    )
}
