/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:07:20 -03:00
 * ARCHIVO: core/document-engine/src/test/kotlin/com/fenix/studio/core/document/PreIngestionAndFuzzyMatcherTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.document

import com.fenix.studio.core.document.guardian.DocumentIngestionVerdict
import com.fenix.studio.core.document.guardian.LowRamPageBitmapRenderer
import com.fenix.studio.core.document.guardian.PreIngestionGuardian
import com.fenix.studio.core.document.matching.BibliographicFuzzyMatcher
import com.fenix.studio.core.document.matching.HispanicAuthorLexemeExtractor
import com.fenix.studio.core.document.matching.MatchConfidence
import com.fenix.studio.core.document.syllabus.CurricularSyllabusParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class PreIngestionAndFuzzyMatcherTest {

    @Test
    fun `PreIngestionGuardian computes streaming SHA-256 flags scanned PDFs below 45 chars per page and extracts TOC in first 15 pages`() {
        val guardian = PreIngestionGuardian()
        val rawPdfBytes = "Contenido binario de documento de prueba.".toByteArray(Charsets.UTF_8)

        // 1. Documento escaneado sin capa de texto (< 45 chars/pág) -> OCR_REQUIRED_WARNING
        val scannedResult = guardian.inspectDocument(
            documentName = "escaneado_largo.pdf",
            inputStream = ByteArrayInputStream(rawPdfBytes),
            totalPages = 80,
            sampledPagesText = listOf("   ", "1", "  "),
        )
        assertEquals(DocumentIngestionVerdict.OCR_REQUIRED_WARNING, scannedResult.verdict)
        assertEquals(64, scannedResult.sha256Hex.length)
        assertTrue(scannedResult.warningMessage!!.contains("RGB_565"))

        // 2. Documento con capa de texto nativa e índice TOC en las primeras 15 páginas -> TEXT_LAYER_NATIVE
        val nativePages = listOf(
            """
            ÍNDICE GENERAL DEL DOCUMENTO
            1. Introducción a los Sistemas Modulares ............... 5
            1.1. Estructura de Acoplamiento Débil .................. 12
            2. Modelado de Estados Deterministas ................... 24
            """.trimIndent(),
            "Texto de desarrollo amplio que supera holgadamente el umbral mínimo de cuarenta y cinco caracteres por página.".repeat(3),
        )
        val nativeResult = guardian.inspectDocument(
            documentName = "manual_base.pdf",
            inputStream = ByteArrayInputStream(rawPdfBytes),
            totalPages = 45,
            sampledPagesText = nativePages,
        )
        assertEquals(DocumentIngestionVerdict.TEXT_LAYER_NATIVE, nativeResult.verdict)
        assertEquals(3, nativeResult.detectedTocEntries.size)
        assertEquals(5, nativeResult.detectedTocEntries[0].pageNumber)
        assertEquals(1, nativeResult.detectedTocEntries[0].level)
        assertEquals(12, nativeResult.detectedTocEntries[1].pageNumber)
        assertEquals(2, nativeResult.detectedTocEntries[1].level)

        // 3. Cálculo de offset de páginas (RN-09) y plan RGB_565 (2 bytes/px)
        assertEquals(14, guardian.calculatePageOffsetDelta(physicalPdfPage = 26, printedPageNumber = 12))
        val rgb565Plan = LowRamPageBitmapRenderer.calculateSafeRgb565Allocation(1_000, 1_500)
        assertEquals(2, rgb565Plan.bytesPerPixel)
        assertEquals(3_000_000L, rgb565Plan.estimatedAllocationBytes)
    }

    @Test
    fun `HispanicAuthorLexemeExtractor and BibliographicFuzzyMatcher classify HIGH_GREEN MEDIUM_AMBER and LOW_RED_ORPHAN`() {
        val extracted = HispanicAuthorLexemeExtractor.extractSignificantLexemes(
            "12_Capitulo_3_AutorAlfa_y_AutorBeta_2021_ed2_scan.pdf",
        )
        assertTrue(extracted.contains("autor") || extracted.contains("alfa"))
        assertEquals(listOf("autor", "alfa", "beta"), extracted)

        val matcher = BibliographicFuzzyMatcher()

        // Caso 1: Alta coincidencia (>= 0.85 -> HIGH_GREEN)
        val highMatch = matcher.evaluateMatch(
            candidateFileName = "Cap_04_AutorAlfa_AutorBeta_Sistemas_Dinamicos.pdf",
            targetSyllabusReference = "AutorAlfa y AutorBeta (2022). Sistemas Dinámicos.",
            targetUnitNumber = 1,
        )
        assertTrue(highMatch.score >= 0.85, "Expected >= 0.85, got ${highMatch.score}")
        assertEquals(MatchConfidence.HIGH_GREEN, highMatch.confidence)

        // Caso 2: Coincidencia parcial intermedia (0.60..0.84 -> MEDIUM_AMBER)
        val mediumMatch = matcher.evaluateMatch(
            candidateFileName = "AutorGamma_Introduccion_Metodologia.pdf",
            targetSyllabusReference = "AutorGamma, AutorDelta y AutorEpsilon: Metodología Estructural Avanzada y Aplicaciones",
            targetUnitNumber = 2,
        )
        assertTrue(mediumMatch.score in 0.60..0.8499, "Expected in 0.60..0.8499, got ${mediumMatch.score}")
        assertEquals(MatchConfidence.MEDIUM_AMBER, mediumMatch.confidence)

        // Caso 3: Material huérfano sin relación (< 0.60 -> LOW_RED_ORPHAN, habilita RN-26)
        val lowMatch = matcher.evaluateMatch(
            candidateFileName = "apunte_suelto_topologia_algebraica.pdf",
            targetSyllabusReference = "AutorOmega (2019). Metabolismo Celular Comparado.",
            targetUnitNumber = 3,
        )
        assertTrue(lowMatch.score < 0.60, "Expected < 0.60, got ${lowMatch.score}")
        assertEquals(MatchConfidence.LOW_RED_ORPHAN, lowMatch.confidence)
    }

    @Test
    fun `CurricularSyllabusParser parses Roman and Arabic units preserving exact official point numbering RN-03`() {
        val rawSyllabus = """
            UNIDAD I: Conceptos Base de Sistemas
            1.1 Definición axiomática y propiedades fundamentales
            1.2 Clasificación estructural de modelos
            a) Modelos deterministas
            b) Modelos estocásticos
            Bibliografía:
            - AutorAlfa (2020). Fundamentos de Sistemas.

            Unidad 2: Dinámica de Transición
            2.1.1 Ecuaciones de estado y estabilidad asintótica
            2.1.2 Condiciones de contorno y bifurcación
        """.trimIndent()

        val parser = CurricularSyllabusParser()
        val units = parser.parseSyllabusText(rawSyllabus)

        assertEquals(2, units.size)
        assertEquals(1, units[0].unitSequenceNumber)
        assertEquals("Conceptos Base de Sistemas", units[0].unitTitle)
        assertEquals(4, units[0].points.size)
        assertEquals("1.1", units[0].points[0].officialNumber)
        assertEquals("a)", units[0].points[2].officialNumber)
        assertEquals(1, units[0].citedBibliographyLines.size)

        assertEquals(2, units[1].unitSequenceNumber)
        assertEquals("2.1.1", units[1].points[0].officialNumber)
        assertEquals("2.1.2", units[1].points[1].officialNumber)
    }
}
