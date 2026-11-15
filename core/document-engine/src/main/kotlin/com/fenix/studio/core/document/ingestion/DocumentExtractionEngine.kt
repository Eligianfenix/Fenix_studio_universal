/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-14 23:25:00 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/ingestion/DocumentExtractionEngine.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.ingestion

import com.fenix.studio.core.document.guardian.PreIngestionGuardian
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest

/** Resultado del motor de extracción: documento crudo, formato no soportado o fallo. */
sealed interface DocumentExtractionOutcome {

    data class Success(val document: RawAcademicDocument) : DocumentExtractionOutcome

    data class Unsupported(
        val fileName: String,
        val format: AcademicDocumentFormat,
        val sha256Hex: String,
        val message: String,
    ) : DocumentExtractionOutcome

    data class Failed(
        val fileName: String,
        val sha256Hex: String?,
        val message: String,
    ) : DocumentExtractionOutcome
}

/**
 * Motor de extracción multiformato de la pre-ingesta (`F4a`).
 *
 * No interpreta el documento: solo lo convierte a texto por páginas, calcula el hash
 * forense y declara cuánto texto nativo encontró. El análisis curricular viene después.
 *
 * El archivo se lee **dos veces** (hash y extracción) y nunca completo en RAM: los
 * extractores de ZIP y de PDF trabajan en streaming o sobre un temporal en disco.
 */
class DocumentExtractionEngine(
    private val extractors: List<AcademicDocumentExtractor> = defaultExtractors(),
    private val maxSamplePages: Int = PreIngestionGuardian.MAX_TOC_SCAN_PAGES,
) {

    private val byFormat: Map<AcademicDocumentFormat, AcademicDocumentExtractor> =
        extractors.flatMap { extractor -> extractor.formats.map { format -> format to extractor } }
            .toMap()

    fun supports(format: AcademicDocumentFormat): Boolean = format in byFormat

    /** Formatos que esta instalación puede leer, sin contar `UNKNOWN`. */
    fun supportedFormats(): Set<AcademicDocumentFormat> = byFormat.keys

    fun detectFormat(source: DocumentSource): AcademicDocumentFormat =
        AcademicDocumentFormatDetector.detect(
            fileName = source.fileName,
            mimeType = source.mimeType,
            headerBytes = source.readHeader(),
        )

    /**
     * Hash SHA-256 en bloques de 8 KB. Devuelve `null` si el archivo no se puede abrir:
     * un error de lectura no debe convertirse en un hash inventado.
     */
    fun computeSha256Hex(source: DocumentSource): String? = runCatching {
        source.openStream().use { input -> digest(input) }
    }.getOrNull()

    suspend fun extract(
        source: DocumentSource,
        maxPages: Int = maxSamplePages,
    ): DocumentExtractionOutcome = withContext(Dispatchers.IO) {
        val format = detectFormat(source)
        val sha256 = computeSha256Hex(source)
            ?: return@withContext DocumentExtractionOutcome.Failed(
                fileName = source.fileName,
                sha256Hex = null,
                message = "No se pudo leer el archivo. Puede estar vacío o sin permisos de acceso.",
            )

        if (format == AcademicDocumentFormat.UNKNOWN) {
            return@withContext DocumentExtractionOutcome.Unsupported(
                fileName = source.fileName,
                format = format,
                sha256Hex = sha256,
                message = "El formato de '${source.fileName}' no está soportado por el motor.",
            )
        }

        val extractor = byFormat[format]
            ?: return@withContext DocumentExtractionOutcome.Unsupported(
                fileName = source.fileName,
                format = format,
                sha256Hex = sha256,
                message = "Esta instalación no tiene extractor para ${format.displayLabel}.",
            )

        val content = runCatching { extractor.extract(source, maxPages) }
            .getOrElse { error ->
                return@withContext DocumentExtractionOutcome.Failed(
                    fileName = source.fileName,
                    sha256Hex = sha256,
                    message = "No se pudo leer '${source.fileName}': " +
                        (error.message ?: "formato dañado o protegido") + ".",
                )
            }

        DocumentExtractionOutcome.Success(
            RawAcademicDocument(
                fileName = source.fileName,
                format = format,
                pages = content.pages,
                totalPages = content.totalPages.coerceAtLeast(content.pages.size),
                sha256Hex = sha256,
                extractionMode = content.mode,
                isScannedOnly = content.isScannedOnly,
                warnings = content.warnings,
            ),
        )
    }

    private fun digest(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(PreIngestionGuardian.STREAM_BUFFER_SIZE_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {

        /**
         * Extractores sin OCR. Sirve para tests JVM y para instalaciones donde el motor
         * óptico no está disponible: los PDF escaneados quedan como `OCR_REQUIRED`.
         */
        fun defaultExtractors(ocrEngine: LocalOcrEngine? = null): List<AcademicDocumentExtractor> = listOf(
            PlainTextDocumentExtractor(),
            DocxDocumentExtractor(),
            OdtDocumentExtractor(),
            PptxDocumentExtractor(),
            EpubDocumentExtractor(),
            ImageDocumentExtractor(ocrEngine),
            PdfDocumentExtractor(ocrEngine),
        )

        /** Instalación completa: lectura nativa + OCR local empaquetado. */
        fun withLocalOcr(ocrEngine: LocalOcrEngine): DocumentExtractionEngine =
            DocumentExtractionEngine(extractors = defaultExtractors(ocrEngine))
    }
}
