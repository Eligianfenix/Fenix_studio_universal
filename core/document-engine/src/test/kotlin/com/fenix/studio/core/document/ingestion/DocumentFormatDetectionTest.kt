/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-15 00:05:00 -03:00
 * ARCHIVO: core/document-engine/src/test/kotlin/com/fenix/studio/core/document/ingestion/DocumentFormatDetectionTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.ingestion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DocumentFormatDetectionTest {

    @Test
    fun `extension decides the format for every supported family`() {
        assertEquals(AcademicDocumentFormat.PDF, AcademicDocumentFormat.fromExtension("programa.PDF"))
        assertEquals(AcademicDocumentFormat.DOCX, AcademicDocumentFormat.fromExtension("unidad-1.docx"))
        assertEquals(AcademicDocumentFormat.ODT, AcademicDocumentFormat.fromExtension("apunte.odt"))
        assertEquals(AcademicDocumentFormat.PPTX, AcademicDocumentFormat.fromExtension("clase.pptx"))
        assertEquals(AcademicDocumentFormat.EPUB, AcademicDocumentFormat.fromExtension("libro.epub"))
        assertEquals(AcademicDocumentFormat.HTML, AcademicDocumentFormat.fromExtension("index.xhtml"))
        assertEquals(AcademicDocumentFormat.MARKDOWN, AcademicDocumentFormat.fromExtension("notas.md"))
        assertEquals(AcademicDocumentFormat.PLAIN_TEXT, AcademicDocumentFormat.fromExtension("apunte.txt"))
        assertEquals(AcademicDocumentFormat.SOURCE_CODE, AcademicDocumentFormat.fromExtension("Main.kt"))
        assertEquals(AcademicDocumentFormat.IMAGE, AcademicDocumentFormat.fromExtension("pizarra.jpeg"))
    }

    @Test
    fun `unknown extensions and formats are not silently guessed`() {
        assertEquals(AcademicDocumentFormat.UNKNOWN, AcademicDocumentFormat.fromExtension("archivo.xyz"))
        assertEquals(AcademicDocumentFormat.UNKNOWN, AcademicDocumentFormat.fromExtension("sin_extension"))
        assertEquals(AcademicDocumentFormat.UNKNOWN, AcademicDocumentFormatDetector.detect("raro.qwerty"))
    }

    @Test
    fun `mime type is used when the name has no extension`() {
        val detected = AcademicDocumentFormatDetector.detect(
            fileName = "documento",
            mimeType = "application/pdf",
        )
        assertEquals(AcademicDocumentFormat.PDF, detected)
    }

    @Test
    fun `pdf and zip signatures are recognised`() {
        val pdf = "%PDF-1.7".toByteArray()
        val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        assertTrue(AcademicDocumentFormatDetector.looksLikePdf(pdf))
        assertTrue(AcademicDocumentFormatDetector.looksLikeZip(zip))
        assertFalse(AcademicDocumentFormatDetector.looksLikePdf("hola".toByteArray()))
        assertFalse(AcademicDocumentFormatDetector.looksLikeZip(pdf))
    }

    @Test
    fun `formats declare whether they can need local ocr`() {
        assertTrue(AcademicDocumentFormat.PDF.usesOcr)
        assertTrue(AcademicDocumentFormat.IMAGE.usesOcr)
        assertFalse(AcademicDocumentFormat.DOCX.usesOcr)
        assertFalse(AcademicDocumentFormat.MARKDOWN.usesOcr)
    }
}
