/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:05:50 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/converter/DocxPdfBidirectionalConverter.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.converter

import com.fenix.studio.core.document.openxml.Apa7ParagraphStyle
import com.fenix.studio.core.document.openxml.DocxParagraphNode
import com.fenix.studio.core.document.openxml.OpenXmlStreamer
import java.io.ByteArrayOutputStream

/**
 * Página paginada en representación intermedia APA 7.ª Edición para exportación PDF offline en < 1 s (`P-09`).
 */
data class PaginatedApa7Page(
    val pageNumber: Int,
    val runningHeadShortTitle: String,
    val marginTwipsAllSides: Int = OpenXmlStreamer.ONE_INCH_TWIPS,
    val lineSpacingTwips: Int = OpenXmlStreamer.APA7_DOUBLE_SPACING_LINE,
    val paragraphs: List<DocxParagraphNode>,
)

/**
 * Conversor Bidireccional Offline DOCX ↔ PDF para Fenix Docs (`P-09`, `MF-3.5`).
 * - Opera 100% local a Costo 0 sin servicios cloud.
 * - Convierte nodos `.docx` en páginas paginadas con encuadre APA 7.ª Edición y reconstruye
 *   paquetes `.docx` desde texto extraído de páginas PDF.
 */
class DocxPdfBidirectionalConverter(
    private val openXmlStreamer: OpenXmlStreamer = OpenXmlStreamer(),
) {

    /**
     * Pagina una lista de párrafos `.docx` en hojas APA 7.ª Edición (máximo `MAX_PARAGRAPHS_PER_PAGE` por carilla).
     */
    fun convertDocxNodesToPaginatedPdfModel(
        runningHeadTitle: String,
        nodes: List<DocxParagraphNode>,
    ): List<PaginatedApa7Page> {
        if (nodes.isEmpty()) return emptyList()
        val shortRunningHead = runningHeadTitle.trim().uppercase().take(50)
        return nodes.chunked(MAX_PARAGRAPHS_PER_PAGE).mapIndexed { idx, chunk ->
            PaginatedApa7Page(
                pageNumber = idx + 1,
                runningHeadShortTitle = shortRunningHead,
                paragraphs = chunk,
            )
        }
    }

    /**
     * Convierte páginas de texto extraídas de un PDF nuevamente en un paquete `.docx` válido bajo APA 7.ª Edición.
     */
    fun convertPdfPagesToDocxBytes(
        pagesText: List<String>,
    ): ByteArray {
        val paragraphNodes = pagesText.flatMap { page ->
            page.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { line ->
                    val style = when {
                        line.startsWith("# ") -> Apa7ParagraphStyle.APA_HEADING_1
                        line.startsWith("## ") -> Apa7ParagraphStyle.APA_HEADING_2
                        line.length > 240 && line.startsWith("\"") -> Apa7ParagraphStyle.APA_BLOCK_QUOTE_40_WORDS
                        else -> Apa7ParagraphStyle.APA_BODY_PARAGRAPH
                    }
                    DocxParagraphNode(
                        style = style,
                        text = line.removePrefix("# ").removePrefix("## ").trim(),
                    )
                }
        }
        val out = ByteArrayOutputStream()
        openXmlStreamer.streamWriteDocx(paragraphNodes, out)
        return out.toByteArray()
    }

    companion object {
        const val MAX_PARAGRAPHS_PER_PAGE: Int = 12
    }
}
