/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-15 00:20:00 -03:00
 * ARCHIVO: core/document-engine/src/test/kotlin/com/fenix/studio/core/document/ingestion/DocumentExtractionEngineTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.ingestion

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocumentExtractionEngineTest {

    private val engine = DocumentExtractionEngine()

    @Test
    fun `plain text is chunked into readable pages with a stable sha256`() = runBlocking {
        val text = "Unidad 1\n\n" + "La intervención profesional se organiza en tres momentos. ".repeat(120)
        val source = sourceOf("apunte.txt", text.toByteArray())

        val outcome = engine.extract(source)

        assertTrue(outcome is DocumentExtractionOutcome.Success)
        val document = (outcome as DocumentExtractionOutcome.Success).document
        assertEquals(AcademicDocumentFormat.PLAIN_TEXT, document.format)
        assertEquals(ExtractionMode.NATIVE_TEXT, document.extractionMode)
        assertTrue(document.pages.size >= 2)
        assertEquals(64, document.sha256Hex.length)
        assertEquals(document.sha256Hex, engine.computeSha256Hex(source))
    }

    @Test
    fun `html is stripped to reading text`() = runBlocking {
        val html = """
            <html><head><style>p{color:red}</style></head>
            <body><h1>Unidad 3</h1><p>Modelos de Estado.</p><script>alert('x')</script></body>
        """.trimIndent()

        val document = (engine.extract(sourceOf("clase.html", html.toByteArray()))
            as DocumentExtractionOutcome.Success).document

        assertTrue(document.fullText.contains("Unidad 3"))
        assertTrue(document.fullText.contains("Modelos de Estado."))
        assertFalse(document.fullText.contains("alert"))
        assertFalse(document.fullText.contains("color:red"))
    }

    @Test
    fun `docx reads word document xml`() = runBlocking {
        val docx = zipOf(
            "word/document.xml" to """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p><w:r><w:t>Unidad 1: Introducción</w:t></w:r></w:p>
                    <w:p><w:r><w:t>Bibliografía obligatoria</w:t></w:r></w:p>
                  </w:body>
                </w:document>
            """.trimIndent(),
        )

        val document = (engine.extract(sourceOf("programa.docx", docx))
            as DocumentExtractionOutcome.Success).document

        assertEquals(AcademicDocumentFormat.DOCX, document.format)
        assertTrue(document.fullText.contains("Unidad 1: Introducción"))
        assertTrue(document.fullText.contains("Bibliografía obligatoria"))
    }

    @Test
    fun `odt reads content xml`() = runBlocking {
        val odt = zipOf(
            "content.xml" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                    xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                  <office:body><office:text>
                    <text:h text:outline-level="1">Eje temático 2</text:h>
                    <text:p>Ciudadanía y derechos.</text:p>
                  </office:text></office:body>
                </office:document-content>
            """.trimIndent(),
        )

        val document = (engine.extract(sourceOf("apunte.odt", odt))
            as DocumentExtractionOutcome.Success).document

        assertEquals(AcademicDocumentFormat.ODT, document.format)
        assertTrue(document.fullText.contains("Eje temático 2"))
        assertTrue(document.fullText.contains("Ciudadanía y derechos."))
    }

    @Test
    fun `pptx orders slides numerically and not lexicographically`() = runBlocking {
        val pptx = zipOf(
            "ppt/slides/slide10.xml" to slideXml("Diapositiva diez"),
            "ppt/slides/slide2.xml" to slideXml("Diapositiva dos"),
            "ppt/slides/slide1.xml" to slideXml("Diapositiva uno"),
            "ppt/slides/_rels/slide1.xml.rels" to "<Relationships/>",
        )

        val document = (engine.extract(sourceOf("clase.pptx", pptx))
            as DocumentExtractionOutcome.Success).document

        assertEquals(3, document.pages.size)
        assertTrue(document.pages[0].text.contains("Diapositiva uno"))
        assertTrue(document.pages[1].text.contains("Diapositiva dos"))
        assertTrue(document.pages[2].text.contains("Diapositiva diez"))
    }

    @Test
    fun `epub follows the spine order of the opf`() = runBlocking {
        val epub = zipOf(
            "META-INF/container.xml" to """
                <?xml version="1.0"?>
                <container version="1.0"><rootfiles>
                  <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles></container>
            """.trimIndent(),
            "OEBPS/content.opf" to """
                <?xml version="1.0"?>
                <package><manifest>
                  <item id="c1" href="cap1.xhtml" media-type="application/xhtml+xml"/>
                  <item id="c2" href="cap2.xhtml" media-type="application/xhtml+xml"/>
                </manifest>
                <spine><itemref idref="c2"/><itemref idref="c1"/></spine></package>
            """.trimIndent(),
            "OEBPS/cap1.xhtml" to "<html><body><p>Capítulo uno</p></body></html>",
            "OEBPS/cap2.xhtml" to "<html><body><p>Capítulo dos</p></body></html>",
        )

        val document = (engine.extract(sourceOf("libro.epub", epub))
            as DocumentExtractionOutcome.Success).document

        assertEquals(2, document.pages.size)
        assertTrue(document.pages[0].text.contains("Capítulo dos"))
        assertTrue(document.pages[1].text.contains("Capítulo uno"))
    }

    @Test
    fun `unsupported extension returns a typed unsupported outcome`() = runBlocking {
        val outcome = engine.extract(sourceOf("planilla.xlsx", byteArrayOf(1, 2, 3)))

        assertTrue(outcome is DocumentExtractionOutcome.Unsupported)
        assertEquals(AcademicDocumentFormat.UNKNOWN, (outcome as DocumentExtractionOutcome.Unsupported).format)
    }

    @Test
    fun `a broken docx does not throw and reports unsupported content`() = runBlocking {
        val broken = zipOf("word/styles.xml" to "<styles/>")

        val document = (engine.extract(sourceOf("roto.docx", broken))
            as DocumentExtractionOutcome.Success).document

        assertEquals(ExtractionMode.UNSUPPORTED, document.extractionMode)
        assertTrue(document.warnings.any { it.contains("word/document.xml") })
    }

    @Test
    fun `engine lists every format it can read without ocr`() {
        val formats = engine.supportedFormats()
        assertTrue(AcademicDocumentFormat.DOCX in formats)
        assertTrue(AcademicDocumentFormat.ODT in formats)
        assertTrue(AcademicDocumentFormat.PPTX in formats)
        assertTrue(AcademicDocumentFormat.EPUB in formats)
        assertTrue(AcademicDocumentFormat.MARKDOWN in formats)
        assertTrue(AcademicDocumentFormat.SOURCE_CODE in formats)
        assertTrue(AcademicDocumentFormat.PDF in formats)
    }

    private fun sourceOf(name: String, bytes: ByteArray): DocumentSource =
        DocumentSource(fileName = name, sizeBytes = bytes.size.toLong()) {
            ByteArrayInputStream(bytes)
        }

    private fun slideXml(text: String): String = """
        <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
               xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
          <p:cSld><p:spTree><p:sp><p:txBody>
            <a:p><a:r><a:t>$text</a:t></a:r></a:p>
          </p:txBody></p:sp></p:spTree></p:cSld>
        </p:sld>
    """.trimIndent()

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
