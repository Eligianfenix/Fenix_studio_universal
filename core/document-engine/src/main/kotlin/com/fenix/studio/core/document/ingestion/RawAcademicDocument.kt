/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-14 22:15:00 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/ingestion/RawAcademicDocument.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.ingestion

/** Una página (PDF, slide, capítulo) de texto crudo, ya sin formato de origen. */
data class RawDocumentPage(
    val pageNumber: Int,
    val text: String,
)

/**
 * Resultado del extractor antes de cualquier análisis curricular.
 *
 * [sha256Hex] identifica el archivo físico: dos documentos con el mismo hash son el mismo
 * archivo, aunque el estudiante los haya subido con nombres distintos.
 */
data class RawAcademicDocument(
    val fileName: String,
    val format: AcademicDocumentFormat,
    val pages: List<RawDocumentPage>,
    val totalPages: Int,
    val sha256Hex: String,
    val extractionMode: ExtractionMode,
    val isScannedOnly: Boolean = false,
    val warnings: List<String> = emptyList(),
) {
    /** Texto de las primeras [count] páginas, para el guardián y el mapeo bibliográfico. */
    fun firstPagesText(count: Int): List<String> =
        pages.take(count).map { it.text }

    val fullText: String
        get() = pages.joinToString("\n") { it.text }

    /** Densidad media de caracteres por página sobre las páginas realmente extraídas. */
    val averageCharacterDensityPerPage: Double
        get() = if (pages.isEmpty()) {
            0.0
        } else {
            pages.sumOf { it.text.trim().length }.toDouble() / pages.size.toDouble()
        }
}
