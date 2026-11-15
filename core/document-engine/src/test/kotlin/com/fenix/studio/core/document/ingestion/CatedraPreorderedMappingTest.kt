/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-15 02:00:00 -03:00
 * ARCHIVO: core/document-engine/src/test/kotlin/com/fenix/studio/core/document/ingestion/CatedraPreorderedMappingTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.ingestion

import com.fenix.studio.core.database.model.MaterialRoleType
import com.fenix.studio.core.document.matching.AcademicTextNormalizer
import com.fenix.studio.core.document.matching.BibliographicFuzzyMatcher
import com.fenix.studio.core.document.matching.MatchConfidence
import com.fenix.studio.core.document.matching.SyllabusCitationEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CatedraPreorderedMappingTest {

    private val matcher = BibliographicFuzzyMatcher()
    private val extractor = DocumentCandidateExtractor()
    private val mapper = CatedraPreorderedMapper()

    @Test
    fun `normalizer strips accents and punctuation and drops academic stopwords`() {
        assertEquals("aquin nora", AcademicTextNormalizer.normalize("  AQUÍN,  Nora. "))
        assertEquals(
            setOf("intervencion", "social", "carballeda"),
            AcademicTextNormalizer.tokenizeSignificant(
                AcademicTextNormalizer.normalize("La intervención en lo social – Carballeda"),
            ),
        )
        assertTrue(
            "teoria" !in AcademicTextNormalizer.tokenizeSignificant("teoria del estado"),
        )
    }

    @Test
    fun `simhash is stable for the same text and distant for different texts`() {
        val first = AcademicTextNormalizer.simHash64("la intervencion en lo social")
        val second = AcademicTextNormalizer.simHash64("la intervencion en lo social")
        val other = AcademicTextNormalizer.simHash64("derecho constitucional comparado")

        assertEquals(first, second)
        assertEquals(0, AcademicTextNormalizer.hammingDistance(first, second))
        assertTrue(AcademicTextNormalizer.hammingDistance(first, other) > 4)
    }

    @Test
    fun `candidate metadata reads cover author, title and file extension`() {
        val document = documentOf(
            fileName = "AQUIN NORA - La intervencion en lo social.pdf",
            pages = listOf(
                "La intervención en lo social\nNora Aquín\nEditorial Paidós, Buenos Aires",
                "Índice\nIntroducción .......... 7",
            ),
        )

        val candidate = extractor.extract(document)

        assertEquals("pdf", candidate.fileExtension)
        assertEquals(document.sha256Hex, candidate.documentId)
        assertTrue(candidate.candidateAuthors.any { it.contains("Aquín") })
        assertTrue(candidate.candidateTitle.contains("intervención en lo social"))
        assertTrue(candidate.significantTokens.contains("intervencion"))
        assertTrue(candidate.calculatedPageOffset >= 0)
    }

    @Test
    fun `a scanned cover does not invent a page offset`() {
        val document = documentOf(
            fileName = "escaneo.pdf",
            pages = listOf("Portada sin numeración", "Texto sin ancla de página"),
            isScannedOnly = true,
        )

        val candidate = extractor.extract(document)

        assertEquals(0, candidate.calculatedPageOffset)
        assertEquals(0, candidate.romanPrelimEndPage)
        assertTrue(candidate.isScannedOnly)
    }

    @Test
    fun `matching a real citation gives a strong assignment`() {
        val candidate = extractor.extract(
            documentOf(
                fileName = "AQUIN NORA - La intervencion en lo social.pdf",
                pages = listOf(
                    "La intervención en lo social\nNora Aquín\nPaidós",
                    "Capítulo 1. La intervención profesional.",
                ),
            ),
        )
        val citation = citation(
            id = "CIT-1",
            unit = 2,
            point = "2.1",
            author = "Aquín, Nora",
            title = "La intervención en lo social",
            pageStart = 7,
            pageEnd = 24,
        )

        val result = matcher.evaluateCandidateAgainstCitation(candidate, citation)

        assertTrue(result.isStrongMatch, "score=${result.combinedScore} :: ${result.justification}")
        assertTrue(result.combinedScore >= BibliographicFuzzyMatcher.STRONG_CITATION_MATCH_THRESHOLD)
        assertNotNull(result.authorCanonical)
        assertEquals("aquin", result.authorCanonical!!.rootPatronymicLexeme)
        assertEquals(MatchConfidence.HIGH_GREEN, result.confidence)
    }

    @Test
    fun `an unrelated file stays below the assignment floor`() {
        val candidate = extractor.extract(
            documentOf(
                fileName = "notas de clase de estadistica.pptx",
                pages = listOf("Distribución normal y desvío estándar"),
            ),
        )
        val citation = citation(
            id = "CIT-2",
            unit = 1,
            point = "1.1",
            author = "Carballeda, Alfredo",
            title = "La intervención en lo social",
        )

        val result = matcher.evaluateCandidateAgainstCitation(candidate, citation)

        assertTrue(result.combinedScore < 0.60, "score=${result.combinedScore}")
        assertEquals(MatchConfidence.LOW_RED_ORPHAN, result.confidence)
    }

    @Test
    fun `the proposal groups matched files by point and leaves the rest for manual review`() {
        val matched = documentOf(
            fileName = "AQUIN NORA - La intervencion en lo social.pdf",
            pages = listOf("La intervención en lo social\nNora Aquín", "Desarrollo del capítulo."),
        )
        val presentation = documentOf(
            fileName = "clase 4.pptx",
            pages = listOf("Clase 4: políticas sociales"),
        )
        val programme = documentOf(
            fileName = "Programa Analítico 2026.docx",
            pages = listOf("PROGRAMA ANALÍTICO\nTrabajo Social I"),
        )

        val proposal = mapper.propose(
            catedraId = "catedra-1",
            subjectTitle = "Trabajo Social I",
            documents = listOf(matched, presentation, programme),
            citations = listOf(
                citation(id = "CIT-1", unit = 2, point = "2.1", author = "Aquín, Nora", title = "La intervención en lo social"),
            ),
        )

        assertEquals(1, proposal.totalAssigned)
        assertTrue(proposal.assignmentsByPointCode.containsKey("2.1"))
        val assignment = proposal.assignmentsByPointCode.getValue("2.1").single()
        assertEquals(2, assignment.assignedUnitNumber)
        assertEquals(MaterialRoleType.PRIMARY_DOCTRINE, assignment.assignedMaterialType)
        assertTrue(assignment.combinedConfidenceScore >= 0.60)

        assertEquals(2, proposal.globalOrUnassignedDocuments.size)
        assertTrue(
            proposal.globalOrUnassignedDocuments.any { it.assignedMaterialType == MaterialRoleType.SYLLABUS_RECTOR },
        )
        assertTrue(
            proposal.globalOrUnassignedDocuments.any { it.assignedMaterialType == MaterialRoleType.COMPLEMENTARY_SUPPORT },
        )
        assertEquals(1, proposal.consolidatedAuthors.size)
    }

    @Test
    fun `duplicated files in the same batch are reported and processed once`() {
        val document = documentOf(
            fileName = "apunte.pdf",
            pages = listOf("Contenido suficientemente distinto como para no coincidir con nada."),
        )

        val proposal = mapper.propose(
            catedraId = "catedra-1",
            subjectTitle = "Materia",
            documents = listOf(document, document.copy(fileName = "apunte (copia).pdf")),
            citations = emptyList(),
        )

        assertEquals(1, proposal.totalDocuments)
        assertTrue(proposal.warnings.any { it.contains("repetido") })
    }

    @Test
    fun `a medium score is kept as a suggestion instead of being silently accepted`() {
        val candidate = extractor.extract(
            documentOf(
                fileName = "carballeda intervencion social.pdf",
                pages = listOf("La intervención en lo social y sus desafíos"),
            ),
        )
        val citation = citation(
            id = "CIT-3",
            unit = 1,
            point = "1.2",
            author = "Carballeda, Alfredo",
            title = "La intervención en lo social",
        )

        val result = matcher.evaluateCandidateAgainstCitation(candidate, citation)

        if (!result.isStrongMatch) {
            assertEquals(MatchConfidence.MEDIUM_AMBER, result.confidence)
            assertFalse(result.justification.isBlank())
        } else {
            assertTrue(result.combinedScore >= 0.72)
        }
    }

    private fun citation(
        id: String,
        unit: Int,
        point: String,
        author: String,
        title: String,
        pageStart: Int? = null,
        pageEnd: Int? = null,
    ): SyllabusCitationEntry = SyllabusCitationEntry(
        citationId = id,
        unitNumber = unit,
        pointOfficialCode = point,
        rawCitation = "$author. $title.",
        authors = listOf(author),
        title = title,
        pageRangeStart = pageStart,
        pageRangeEnd = pageEnd,
    )

    private fun documentOf(
        fileName: String,
        pages: List<String>,
        isScannedOnly: Boolean = false,
    ): RawAcademicDocument = RawAcademicDocument(
        fileName = fileName,
        format = AcademicDocumentFormat.PDF,
        pages = pages.mapIndexed { index, text -> RawDocumentPage(index + 1, text) },
        totalPages = pages.size,
        sha256Hex = fileName.hashCode().toString().padStart(64, '0').takeLast(64),
        extractionMode = ExtractionMode.NATIVE_TEXT,
        isScannedOnly = isScannedOnly,
    )
}
