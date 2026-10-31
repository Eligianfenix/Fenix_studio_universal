/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:04:00 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/guardian/PreIngestionGuardian.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.guardian

import java.io.InputStream
import java.security.MessageDigest

/**
 * Veredicto del Guardián Pre-Ingesta (`MF-3.1`, `RN-05`, `RN-09`).
 */
enum class DocumentIngestionVerdict {
    TEXT_LAYER_NATIVE,
    OCR_REQUIRED_WARNING,
    DUPLICATE_SHA256,
    CORRUPT_OR_EMPTY,
}

/**
 * Entrada de índice o sumario (TOC) auto-detectada en las primeras 15 páginas del documento.
 */
data class DetectedTocEntry(
    val title: String,
    val pageNumber: Int,
    val level: Int,
)

/**
 * Resultado inmutable del escaneo quirúrgico pre-ingesta.
 */
data class PreIngestionScanResult(
    val documentName: String,
    val sha256Hex: String,
    val totalPages: Int,
    val sampleCharacterDensityPerPage: Double,
    val detectedTocEntries: List<DetectedTocEntry>,
    val verdict: DocumentIngestionVerdict,
    val warningMessage: String?,
)

/**
 * Guardián Pre-Ingesta y Detector Quirúrgico de Índice (`PreIngestionGuardian`, `MF-3.1`).
 * - Calcula el hash SHA-256 en streaming con buffer estricto de 8 KB (`8_192` bytes).
 * - Evalúa la densidad de caracteres en las páginas muestreadas: si es `< 45.0` chars/página,
 *   emite `OCR_REQUIRED_WARNING` advirtiendo al estudiante antes de procesar PDFs escaneados.
 * - Escanea las primeras `MAX_TOC_SCAN_PAGES = 15` páginas para extraer entradas de sumario/TOC.
 */
class PreIngestionGuardian {

    /**
     * Calcula el resumen SHA-256 hexadecimal en streaming con buffer de 8 KB sin cargar el archivo completo en RAM.
     */
    fun computeStreamingSha256Hex(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(STREAM_BUFFER_SIZE_BYTES)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            if (bytesRead > 0) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    /**
     * Evalúa un documento a partir de su stream binario y del texto muestreado de sus primeras páginas.
     */
    fun inspectDocument(
        documentName: String,
        inputStream: InputStream,
        totalPages: Int,
        sampledPagesText: List<String>,
        existingSha256Hashes: Set<String> = emptySet(),
    ): PreIngestionScanResult {
        val sha256Hex = computeStreamingSha256Hex(inputStream)

        if (totalPages <= 0) {
            return PreIngestionScanResult(
                documentName = documentName,
                sha256Hex = sha256Hex,
                totalPages = 0,
                sampleCharacterDensityPerPage = 0.0,
                detectedTocEntries = emptyList(),
                verdict = DocumentIngestionVerdict.CORRUPT_OR_EMPTY,
                warningMessage = "El documento '$documentName' está vacío o dañado.",
            )
        }

        if (existingSha256Hashes.contains(sha256Hex)) {
            return PreIngestionScanResult(
                documentName = documentName,
                sha256Hex = sha256Hex,
                totalPages = totalPages,
                sampleCharacterDensityPerPage = 0.0,
                detectedTocEntries = emptyList(),
                verdict = DocumentIngestionVerdict.DUPLICATE_SHA256,
                warningMessage = "El documento '$documentName' ya fue incorporado previamente (SHA-256 idéntico).",
            )
        }

        val pagesToInspect = sampledPagesText.take(MAX_TOC_SCAN_PAGES)
        val density = if (pagesToInspect.isEmpty()) {
            0.0
        } else {
            pagesToInspect.sumOf { page -> page.trim().length }.toDouble() / pagesToInspect.size.toDouble()
        }

        val tocEntries = extractTocEntriesFromFirstPages(pagesToInspect)

        return if (density < MIN_NATIVE_TEXT_CHARS_PER_PAGE) {
            val scanNotice = if (totalPages > SCANNED_PDF_PAGE_WARNING_THRESHOLD) {
                "Documento escaneado extenso ($totalPages páginas, densidad ${"%.1f".format(density)} chars/pág). " +
                    "Se requiere OCR secuencial RGB_565 y recorte por rango de páginas (RN-05)."
            } else {
                "Capa de texto nativa insuficiente (${"%.1f".format(density)} chars/pág). Se activará OCR local RGB_565."
            }
            PreIngestionScanResult(
                documentName = documentName,
                sha256Hex = sha256Hex,
                totalPages = totalPages,
                sampleCharacterDensityPerPage = density,
                detectedTocEntries = tocEntries,
                verdict = DocumentIngestionVerdict.OCR_REQUIRED_WARNING,
                warningMessage = scanNotice,
            )
        } else {
            PreIngestionScanResult(
                documentName = documentName,
                sha256Hex = sha256Hex,
                totalPages = totalPages,
                sampleCharacterDensityPerPage = density,
                detectedTocEntries = tocEntries,
                verdict = DocumentIngestionVerdict.TEXT_LAYER_NATIVE,
                warningMessage = null,
            )
        }
    }

    /**
     * Extrae líneas de Tabla de Contenidos (TOC) dentro de las primeras 15 páginas.
     */
    fun extractTocEntriesFromFirstPages(firstPagesText: List<String>): List<DetectedTocEntry> {
        val entries = mutableListOf<DetectedTocEntry>()
        val boundedPages = firstPagesText.take(MAX_TOC_SCAN_PAGES)

        for (pageContent in boundedPages) {
            val lines = pageContent.lines()
            for (rawLine in lines) {
                val cleanLine = rawLine.trim()
                if (cleanLine.length < 4) continue
                val match = TOC_LINE_REGEX.matchEntire(cleanLine) ?: continue
                val rawTitle = match.groupValues[1].trim().trimEnd('.', '-', '_', ' ')
                val pageNum = match.groupValues[2].toIntOrNull() ?: continue
                if (rawTitle.isNotEmpty() && pageNum > 0) {
                    val hierarchyLevel = inferTocLevel(rawTitle)
                    entries += DetectedTocEntry(
                        title = rawTitle,
                        pageNumber = pageNum,
                        level = hierarchyLevel,
                    )
                }
            }
        }
        return entries
    }

    /**
     * Calcula el desplazamiento (`offsetDelta`) entre la numeración impresa (tras prefacio romano)
     * y el índice físico real del PDF (`physicalPdfPage - printedPageNumber`, RN-09).
     */
    fun calculatePageOffsetDelta(
        physicalPdfPage: Int,
        printedPageNumber: Int,
    ): Int = physicalPdfPage - printedPageNumber

    private fun inferTocLevel(title: String): Int {
        val numericPrefix = LEADING_HIERARCHY_NUMBER_REGEX.find(title)?.value?.trim() ?: return 1
        val dotCount = numericPrefix.count { it == '.' }
        return (dotCount + 1).coerceIn(1, 4)
    }

    companion object {
        const val STREAM_BUFFER_SIZE_BYTES: Int = 8_192
        const val MAX_TOC_SCAN_PAGES: Int = 15
        const val MIN_NATIVE_TEXT_CHARS_PER_PAGE: Double = 45.0
        const val SCANNED_PDF_PAGE_WARNING_THRESHOLD: Int = 40

        private val TOC_LINE_REGEX = Regex(
            "^(.+?)(?:\\s*[.\\-_]{2,}\\s*|\\s{2,})(\\d{1,4})$",
        )
        private val LEADING_HIERARCHY_NUMBER_REGEX = Regex("^\\d+(?:\\.\\d+)*")
    }
}
