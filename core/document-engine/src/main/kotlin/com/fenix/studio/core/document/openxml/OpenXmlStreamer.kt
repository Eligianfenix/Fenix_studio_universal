/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:05:40 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/openxml/OpenXmlStreamer.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.openxml

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Estilo estructural APA 7.ª Edición para cada nodo de párrafo en `.docx` (`MF-3.5`).
 */
enum class Apa7ParagraphStyle(val styleId: String) {
    APA_HEADING_1("ApaHeading1"),
    APA_HEADING_2("ApaHeading2"),
    APA_HEADING_3("ApaHeading3"),
    APA_HEADING_4("ApaHeading4"),
    APA_HEADING_5("ApaHeading5"),
    APA_BODY_PARAGRAPH("ApaBodyText"),
    APA_BLOCK_QUOTE_40_WORDS("ApaBlockQuote"),
    APA_HANGING_REFERENCE("ApaReference"),
    FENIX_SUBPOINT_CALLOUT("FenixSubpointCallout"),
}

/**
 * Nodo de párrafo inmutable para exportación y lectura en streaming de archivos OpenXML `.docx`.
 */
data class DocxParagraphNode(
    val style: Apa7ParagraphStyle,
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
)

/**
 * Compilador y Lector Nativo de Paquetes OpenXML `.docx` por Streaming ZIP (`OpenXmlStreamer`, `MF-3.5`).
 * - Cero uso de Apache POI ni `docx-js` (0 KB extra en APK y < 4 MB de RAM).
 * - Escribe entradas OpenXML con buffer de `8_192` bytes (`8 KB`).
 * - Aplica las reglas normativas de **APA 7.ª Edición**:
 *   - Márgenes de 1 pulgada (`1440` twips en los 4 lados: `w:top="1440"`, `w:right="1440"`, `w:bottom="1440"`, `w:left="1440"`).
 *   - Interlineado doble (`w:line="480" w:lineRule="auto"`).
 *   - Sangría de primera línea de 0.5 pulgadas (`w:firstLine="720"`) en cuerpo y sangría francesa (`w:left="720" w:hanging="720"`) en referencias.
 *   - Sangría izquierda de 0.5 pulgadas (`w:left="720"`) en citas largas (> 40 palabras).
 */
class OpenXmlStreamer {

    /**
     * Genera un archivo `.docx` OpenXML válido directamente sobre el `OutputStream` en streaming con buffer de 8 KB.
     */
    fun streamWriteDocx(
        paragraphs: List<DocxParagraphNode>,
        outputStream: OutputStream,
    ) {
        ZipOutputStream(BufferedOutputStream(outputStream, STREAM_BUFFER_SIZE_BYTES)).use { zos ->
            writeZipEntryUtf8(zos, "[Content_Types].xml", buildContentTypesXml())
            writeZipEntryUtf8(zos, "_rels/.rels", buildRootRelsXml())
            writeZipEntryUtf8(zos, "word/_rels/document.xml.rels", buildDocumentRelsXml())
            writeZipEntryUtf8(zos, "word/styles.xml", buildApa7StylesXml())

            zos.putNextEntry(ZipEntry("word/document.xml"))
            val headerBytes = DOCUMENT_XML_HEADER.toByteArray(StandardCharsets.UTF_8)
            zos.write(headerBytes)

            val chunkBuffer = StringBuilder(STREAM_BUFFER_SIZE_BYTES)
            for (node in paragraphs) {
                chunkBuffer.append(buildParagraphXml(node))
                if (chunkBuffer.length >= STREAM_BUFFER_SIZE_BYTES / 2) {
                    val bytes = chunkBuffer.toString().toByteArray(StandardCharsets.UTF_8)
                    zos.write(bytes)
                    chunkBuffer.setLength(0)
                }
            }
            if (chunkBuffer.isNotEmpty()) {
                zos.write(chunkBuffer.toString().toByteArray(StandardCharsets.UTF_8))
                chunkBuffer.setLength(0)
            }

            zos.write(buildSectionPropertiesApa7Xml().toByteArray(StandardCharsets.UTF_8))
            zos.write(DOCUMENT_XML_FOOTER.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
            zos.flush()
        }
    }

    /**
     * Lee los párrafos de un archivo `.docx` en streaming mediante `ZipInputStream` con buffer de 8 KB
     * para alimentar el visor/editor Fenix Docs (`P-09`) con consumo mínimo de memoria.
     */
    fun readDocxParagraphsStreaming(inputStream: InputStream): List<DocxParagraphNode> {
        ZipInputStream(inputStream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xmlString = readEntryUtf8With8KbBuffer(zis)
                    return parseDocumentXmlParagraphs(xmlString)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return emptyList()
    }

    private fun parseDocumentXmlParagraphs(documentXml: String): List<DocxParagraphNode> {
        val result = mutableListOf<DocxParagraphNode>()
        val paragraphMatches = PARAGRAPH_BLOCK_REGEX.findAll(documentXml)
        for (pMatch in paragraphMatches) {
            val innerXml = pMatch.groupValues[1]
            val styleId = PSTYLE_VAL_REGEX.find(innerXml)?.groupValues?.get(1)
            val resolvedStyle = Apa7ParagraphStyle.entries.find { it.styleId == styleId }
                ?: Apa7ParagraphStyle.APA_BODY_PARAGRAPH

            val textBuilder = StringBuilder()
            for (tMatch in WT_TEXT_REGEX.findAll(innerXml)) {
                textBuilder.append(unescapeXml(tMatch.groupValues[1]))
            }
            val finalText = textBuilder.toString()
            if (finalText.isNotEmpty()) {
                result += DocxParagraphNode(
                    style = resolvedStyle,
                    text = finalText,
                    isBold = innerXml.contains("<w:b/>"),
                    isItalic = innerXml.contains("<w:i/>"),
                )
            }
        }
        return result
    }

    private fun buildParagraphXml(node: DocxParagraphNode): String {
        val indentationXml = when (node.style) {
            Apa7ParagraphStyle.APA_BODY_PARAGRAPH ->
                """<w:ind w:firstLine="$HALF_INCH_TWIPS"/>"""
            Apa7ParagraphStyle.APA_HANGING_REFERENCE ->
                """<w:ind w:left="$HALF_INCH_TWIPS" w:hanging="$HALF_INCH_TWIPS"/>"""
            Apa7ParagraphStyle.APA_BLOCK_QUOTE_40_WORDS ->
                """<w:ind w:left="$HALF_INCH_TWIPS"/>"""
            else -> ""
        }

        val shadingXml = if (node.style == Apa7ParagraphStyle.FENIX_SUBPOINT_CALLOUT) {
            """<w:shd w:val="clear" w:color="auto" w:fill="FFF3E0"/>"""
        } else {
            ""
        }

        val boldRunXml = if (node.isBold || node.style.name.startsWith("APA_HEADING_")) "<w:b/>" else ""
        val italicRunXml = if (node.isItalic || node.style == Apa7ParagraphStyle.APA_HEADING_3 || node.style == Apa7ParagraphStyle.APA_HEADING_5) "<w:i/>" else ""

        return buildString {
            append("<w:p>")
            append("<w:pPr>")
            append("""<w:pStyle w:val="${node.style.styleId}"/>""")
            append("""<w:spacing w:before="0" w:after="0" w:line="$APA7_DOUBLE_SPACING_LINE" w:lineRule="auto"/>""")
            append(indentationXml)
            append(shadingXml)
            append("</w:pPr>")
            append("<w:r>")
            if (boldRunXml.isNotEmpty() || italicRunXml.isNotEmpty()) {
                append("<w:rPr>$boldRunXml$italicRunXml</w:rPr>")
            }
            append("""<w:t xml:space="preserve">${escapeXml(node.text)}</w:t>""")
            append("</w:r>")
            append("</w:p>")
        }
    }

    private fun buildSectionPropertiesApa7Xml(): String =
        """<w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="$ONE_INCH_TWIPS" w:right="$ONE_INCH_TWIPS" w:bottom="$ONE_INCH_TWIPS" w:left="$ONE_INCH_TWIPS" w:header="720" w:footer="720" w:gutter="0"/></w:sectPr>"""

    private fun buildContentTypesXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

    private fun buildRootRelsXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private fun buildDocumentRelsXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun buildApa7StylesXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman" w:cs="Times New Roman"/>
        <w:sz w:val="24"/>
      </w:rPr>
    </w:rPrDefault>
    <w:pPrDefault>
      <w:pPr>
        <w:spacing w:before="0" w:after="0" w:line="$APA7_DOUBLE_SPACING_LINE" w:lineRule="auto"/>
      </w:pPr>
    </w:pPrDefault>
  </w:docDefaults>
  <w:style w:type="paragraph" w:default="1" w:styleId="ApaBodyText">
    <w:name w:val="APA 7 Body Text"/>
    <w:pPr>
      <w:spacing w:before="0" w:after="0" w:line="$APA7_DOUBLE_SPACING_LINE" w:lineRule="auto"/>
      <w:ind w:firstLine="$HALF_INCH_TWIPS"/>
    </w:pPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="ApaReference">
    <w:name w:val="APA 7 Hanging Reference"/>
    <w:pPr>
      <w:spacing w:before="0" w:after="0" w:line="$APA7_DOUBLE_SPACING_LINE" w:lineRule="auto"/>
      <w:ind w:left="$HALF_INCH_TWIPS" w:hanging="$HALF_INCH_TWIPS"/>
    </w:pPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="ApaBlockQuote">
    <w:name w:val="APA 7 Block Quote"/>
    <w:pPr>
      <w:spacing w:before="0" w:after="0" w:line="$APA7_DOUBLE_SPACING_LINE" w:lineRule="auto"/>
      <w:ind w:left="$HALF_INCH_TWIPS"/>
    </w:pPr>
  </w:style>
</w:styles>"""

    private fun writeZipEntryUtf8(zos: ZipOutputStream, entryName: String, content: String) {
        zos.putNextEntry(ZipEntry(entryName))
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        var offset = 0
        while (offset < bytes.size) {
            val len = minOf(STREAM_BUFFER_SIZE_BYTES, bytes.size - offset)
            zos.write(bytes, offset, len)
            offset += len
        }
        zos.closeEntry()
    }

    private fun readEntryUtf8With8KbBuffer(zis: ZipInputStream): String {
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(STREAM_BUFFER_SIZE_BYTES)
        var read: Int
        while (zis.read(buffer).also { read = it } != -1) {
            baos.write(buffer, 0, read)
        }
        return String(baos.toByteArray(), StandardCharsets.UTF_8)
    }

    private fun escapeXml(raw: String): String = buildString(raw.length) {
        for (ch in raw) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }

    private fun unescapeXml(escaped: String): String =
        escaped
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")

    companion object {
        const val STREAM_BUFFER_SIZE_BYTES: Int = 8_192
        const val ONE_INCH_TWIPS: Int = 1_440
        const val HALF_INCH_TWIPS: Int = 720
        const val APA7_DOUBLE_SPACING_LINE: Int = 480

        private const val DOCUMENT_XML_HEADER =
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>"""
        private const val DOCUMENT_XML_FOOTER = """</w:body></w:document>"""

        private val PARAGRAPH_BLOCK_REGEX = Regex("<w:p>(.*?)</w:p>", RegexOption.DOT_MATCHES_ALL)
        private val PSTYLE_VAL_REGEX = Regex("""<w:pStyle\s+w:val="([^"]+)"""")
        private val WT_TEXT_REGEX = Regex("""<w:t(?:\s+[^>]*)?>(.*?)</w:t>""", RegexOption.DOT_MATCHES_ALL)
    }
}
