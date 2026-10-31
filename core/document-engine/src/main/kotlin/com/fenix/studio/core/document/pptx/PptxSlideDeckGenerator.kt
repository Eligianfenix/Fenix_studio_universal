/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:06:00 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/pptx/PptxSlideDeckGenerator.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.pptx

import java.io.BufferedOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Modelo de diapositiva académica con soporte para el **Modo Proyector Nativo Horizontal**
 * y tarjeta de notas de orador para defensas orales (`MF-8.3`).
 */
data class FenixSlideModel(
    val slideNumber: Int,
    val title: String,
    val keyBulletPoints: List<String>,
    val speakerNotes: String,
    val epistemicBadge: String = "[FUENTE]",
)

/**
 * Estado del Modo Proyector Nativo Horizontal con tarjetas de apoyo al orador.
 */
data class NativeHorizontalProjectorDeck(
    val deckTitle: String,
    val slides: List<FenixSlideModel>,
    val aspectRatioLabel: String = "16:9",
)

/**
 * Generador Nativo en Streaming ZIP de Presentaciones OpenXML `.pptx` (`PptxSlideDeckGenerator`, `MF-8.3`).
 * Construye archivos `.pptx` estándar (`[Content_Types].xml`, `_rels/.rels`, `ppt/presentation.xml`,
 * `ppt/slides/slide1..N.xml`) con buffer de 8 KB y cero librerías pesadas en RAM.
 */
class PptxSlideDeckGenerator {

    fun generatePptxStream(
        deck: NativeHorizontalProjectorDeck,
        outputStream: OutputStream,
    ) {
        require(deck.slides.isNotEmpty()) { "La presentación debe contener al menos una diapositiva." }

        ZipOutputStream(BufferedOutputStream(outputStream, STREAM_BUFFER_SIZE_BYTES)).use { zos ->
            writeEntryUtf8(zos, "[Content_Types].xml", buildContentTypesXml(deck.slides.size))
            writeEntryUtf8(zos, "_rels/.rels", buildRootRelsXml())
            writeEntryUtf8(zos, "ppt/_rels/presentation.xml.rels", buildPresentationRelsXml(deck.slides.size))
            writeEntryUtf8(zos, "ppt/presentation.xml", buildPresentationXml(deck.slides.size))

            deck.slides.forEachIndexed { idx, slide ->
                val slideIndex = idx + 1
                writeEntryUtf8(
                    zos = zos,
                    entryName = "ppt/slides/slide$slideIndex.xml",
                    content = buildSlideXml(slide),
                )
            }
            zos.flush()
        }
    }

    private fun buildContentTypesXml(slideCount: Int): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        appendLine("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        appendLine("""  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        appendLine("""  <Default Extension="xml" ContentType="application/xml"/>""")
        appendLine("""  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>""")
        for (i in 1..slideCount) {
            appendLine("""  <Override PartName="/ppt/slides/slide$i.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""")
        }
        append("""</Types>""")
    }

    private fun buildRootRelsXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>"""

    private fun buildPresentationRelsXml(slideCount: Int): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        appendLine("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..slideCount) {
            appendLine("""  <Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide$i.xml"/>""")
        }
        append("""</Relationships>""")
    }

    private fun buildPresentationXml(slideCount: Int): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        appendLine("""<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""")
        appendLine("""  <p:sldIdLst>""")
        for (i in 1..slideCount) {
            val sldId = 255 + i
            appendLine("""    <p:sldId id="$sldId" r:id="rId$i"/>""")
        }
        appendLine("""  </p:sldIdLst>""")
        appendLine("""  <p:sldSz cx="12192000" cy="6858000"/>""")
        append("""</p:presentation>""")
    }

    private fun buildSlideXml(slide: FenixSlideModel): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""")
        append("""<p:cSld><p:spTree>""")
        append("""<p:sp><p:txBody><a:p><a:r><a:t>${escapeXml(slide.title)} ${escapeXml(slide.epistemicBadge)}</a:t></a:r></a:p>""")
        for (bullet in slide.keyBulletPoints) {
            append("""<a:p><a:r><a:t>• ${escapeXml(bullet)}</a:t></a:r></a:p>""")
        }
        append("""</p:txBody></p:sp>""")
        append("""</p:spTree></p:cSld></p:sld>""")
    }

    private fun writeEntryUtf8(zos: ZipOutputStream, entryName: String, content: String) {
        zos.putNextEntry(ZipEntry(entryName))
        zos.write(content.toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()
    }

    private fun escapeXml(raw: String): String =
        raw.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    companion object {
        const val STREAM_BUFFER_SIZE_BYTES: Int = 8_192
    }
}
