/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-15 01:20:00 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/ingestion/DocumentCandidateMetadata.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.ingestion

import com.fenix.studio.core.document.matching.AcademicTextNormalizer
import kotlinx.serialization.Serializable

/**
 * Metadatos que el motor difuso obtiene de la **portada** de un archivo del lote
 * (primeras 3 páginas + nombre del archivo), sin interpretar el contenido curricular.
 *
 * Es el insumo de `BibliographicFuzzyMatcher` y de `CatedraPreorderedMapper`.
 */
@Serializable
data class DocumentCandidateMetadata(
    val documentId: String,
    val originalFileName: String,
    val fileExtension: String,
    val sha256Hash: String,
    val totalPages: Int,
    val isScannedOnly: Boolean,
    val rawHeaderText: String,
    val normalizedHeaderText: String,
    val candidateAuthors: List<String>,
    val candidateTitle: String,
    val significantTokens: Set<String>,
    val headerSimHash64: Long,
    val romanPrelimEndPage: Int,
    val calculatedPageOffset: Int,
)

/**
 * Construye [DocumentCandidateMetadata] a partir del documento ya extraído y sanitizado.
 *
 * No necesita un PDF: sirve igual para DOCX, EPUB o una foto de portada con OCR.
 */
class DocumentCandidateExtractor(
    private val headerPageLimit: Int = 3,
) {

    fun extract(document: RawAcademicDocument): DocumentCandidateMetadata {
        val headerText = document.pages
            .take(headerPageLimit)
            .joinToString("\n") { it.text }

        val fileNameStem = document.fileName.substringBeforeLast('.', document.fileName)
        val normalizedHeader = AcademicTextNormalizer.normalize("$headerText\n$fileNameStem")

        val candidateAuthors = (
            COVER_AUTHOR_REGEX.findAll(headerText)
                .map { match -> "${match.groupValues[1].trim()}, ${match.groupValues[2].trim()}" } +
                NAME_ONLY_LINE.findAll(headerText).map { match -> match.value.trim() }
            )
            .filter { author -> INSTITUTIONAL_WORDS.none { author.contains(it, ignoreCase = true) } }
            .distinct()
            .take(MAX_AUTHORS)
            .toList()

        return DocumentCandidateMetadata(
            documentId = document.sha256Hex,
            originalFileName = document.fileName,
            fileExtension = document.fileName.substringAfterLast('.', "").lowercase(),
            sha256Hash = document.sha256Hex,
            totalPages = document.totalPages,
            isScannedOnly = document.isScannedOnly,
            rawHeaderText = headerText,
            normalizedHeaderText = normalizedHeader,
            candidateAuthors = candidateAuthors,
            candidateTitle = detectTitle(headerText, fileNameStem),
            significantTokens = AcademicTextNormalizer.tokenizeSignificant(normalizedHeader),
            headerSimHash64 = AcademicTextNormalizer.simHash64(normalizedHeader),
            romanPrelimEndPage = detectRomanPreliminaryEnd(document),
            calculatedPageOffset = detectPrintedPageOffset(document),
        )
    }

    /** Primera línea con forma de título; si no hay, el nombre del archivo. */
    private fun detectTitle(headerText: String, fileNameStem: String): String =
        headerText.lines()
            .map { it.trim() }
            .filter { line -> line.length in MIN_TITLE_LENGTH..MAX_TITLE_LENGTH }
            .firstOrNull { line -> line.any { it.isLetter() } && !PAGE_NUMBER_LINE.matches(line) }
            ?: fileNameStem

    /**
     * Último pie de página en números romanos dentro de las primeras páginas.
     * Si la numeración romana termina en la página física 6 (`vi`), el cuerpo empieza después.
     */
    private fun detectRomanPreliminaryEnd(document: RawAcademicDocument): Int {
        var lastRomanPage = 0
        for (page in document.pages.take(ROMAN_SCAN_PAGES)) {
            val hasRomanFooter = page.text.lines()
                .map { it.trim() }
                .any { line -> ROMAN_FOOTER_LINE.matches(line) }
            if (hasRomanFooter) lastRomanPage = page.pageNumber
        }
        return lastRomanPage
    }

    /**
     * Diferencia entre la página impresa y la página física del archivo.
     *
     * Se ancla en una línea que sea exactamente `1` en una página posterior a la primera:
     * eso indica que el cuerpo del documento arranca ahí. Si no hay ancla, el offset es 0.
     */
    private fun detectPrintedPageOffset(document: RawAcademicDocument): Int {
        for (page in document.pages.take(OFFSET_SCAN_PAGES)) {
            if (page.pageNumber == 1) continue
            val anchored = page.text.lines()
                .map { it.trim() }
                .any { line -> PRINTED_PAGE_ONE.matches(line) }
            if (anchored) return (page.pageNumber - 1).coerceAtLeast(0)
        }
        return 0
    }

    private companion object {
        const val MAX_AUTHORS = 6
        const val MIN_TITLE_LENGTH = 6
        const val MAX_TITLE_LENGTH = 140
        const val ROMAN_SCAN_PAGES = 8
        const val OFFSET_SCAN_PAGES = 15

        /** "Aquín, Nora" o "De la Vega, María" en una portada. */
        val COVER_AUTHOR_REGEX = Regex(
            """^\s*([A-ZÁÉÍÓÚÑÜ][\p{L}'’\-]{2,25}(?:\s+(?:de|del|de\s+la|vda\.?\s+de|van|von)\s+[\p{L}'’\-]{2,25})?)\s*,\s*([A-ZÁÉÍÓÚÑÜ][\p{L}'’\-\.]*)""",
            RegexOption.MULTILINE,
        )

        /**
         * Portadas que escriben el nombre completo sin coma ("Nora Aquín"). Es un
         * candidato, no una certeza: el matcher solo lo usa si el apellido coincide
         * con la cita del programa.
         */
        val NAME_ONLY_LINE = Regex(
            """^\s*([A-ZÁÉÍÓÚÑÜ][\p{L}'’\-]{1,20}(?:\s+(?:de|del|van|von)\s+[\p{L}'’\-]{2,20})?\s+[A-ZÁÉÍÓÚÑÜ][\p{L}'’\-]{2,25})\s*$""",
            RegexOption.MULTILINE,
        )

        val INSTITUTIONAL_WORDS = setOf(
            "universidad", "facultad", "carrera", "licenciatura", "profesorado", "instituto",
            "cátedra", "catedra", "departamento", "seminario", "materia", "asignatura",
            "programa", "plan de estudios", "trabajo social", "ciencias sociales",
        )

        val ROMAN_FOOTER_LINE = Regex("""^(?:[-–—]\s*)?[ivxlcdm]{1,7}(?:\s*[-–—])?$""")
        val PRINTED_PAGE_ONE = Regex("""^(?:[-–—]\s*)?1(?:\s*[-–—])?$""")
        val PAGE_NUMBER_LINE = Regex("""^\d{1,4}$""")
    }
}
