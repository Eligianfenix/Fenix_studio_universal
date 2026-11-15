/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-15 01:50:00 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/ingestion/CatedraPreorderedMappingProposal.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.ingestion

import com.fenix.studio.core.database.model.MaterialRoleType
import com.fenix.studio.core.document.matching.AcademicTextNormalizer
import com.fenix.studio.core.document.matching.AuthorCanonical
import com.fenix.studio.core.document.matching.BibliographicFuzzyMatcher
import com.fenix.studio.core.document.matching.CitationMatchResult
import com.fenix.studio.core.document.matching.MatchConfidence
import com.fenix.studio.core.document.matching.SyllabusCitationEntry
import com.fenix.studio.domain.protocolv3.curriculum.SyllabusGraph
import kotlinx.serialization.Serializable
import kotlin.math.min

/**
 * Asignación propuesta por el motor difuso entre un archivo del lote y un punto del
 * programa. Es una **propuesta**, no una verdad: la persona usuaria la confirma o la
 * corrige antes de ingerir.
 */
@Serializable
data class MatchedDocumentAssignment(
    val documentId: String,
    val originalFileName: String,
    val sha256Hash: String,
    val totalPages: Int,
    val isScannedOnly: Boolean,
    val detectedAuthorCanonical: AuthorCanonical?,
    val detectedTitle: String,
    val assignedUnitNumber: Int?,
    val assignedPointOfficialCode: String?,
    val assignedMaterialType: MaterialRoleType,
    val matchedCitationId: String?,
    val targetPhysicalPageStart: Int?,
    val targetPhysicalPageEnd: Int?,
    val pageOffsetDelta: Int,
    val combinedConfidenceScore: Double,
    val matchJustification: String,
)

/**
 * Propuesta completa de mapeo pre-ordenado de una cátedra, lista para revisión táctil.
 *
 * [assignmentsByPointCode] agrupa por código oficial de punto (o `UNIDAD_n` si la cita
 * no tiene punto). [globalOrUnassignedDocuments] contiene lo que el motor no pudo
 * anclar: material complementario, programa oficial y huérfanos.
 */
@Serializable
data class CatedraPreorderedMappingProposal(
    val catedraId: String,
    val subjectTitle: String,
    val syllabusGraph: SyllabusGraph? = null,
    val assignmentsByPointCode: Map<String, List<MatchedDocumentAssignment>> = emptyMap(),
    val globalOrUnassignedDocuments: List<MatchedDocumentAssignment> = emptyList(),
    val consolidatedAuthors: List<AuthorCanonical> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    val totalAssigned: Int
        get() = assignmentsByPointCode.values.sumOf { it.size }

    val totalDocuments: Int
        get() = totalAssigned + globalOrUnassignedDocuments.size
}

/**
 * Motor de mapeo difuso archivo → punto curricular (`F4b`).
 *
 * No modifica el parser curricular ni la base de datos: produce una propuesta 
 * serializable que después la UI revisa y persiste.
 *
 * Reglas de decisión, en orden:
 * 1. Si el archivo se anuncia como programa analítico, se marca `SYLLABUS_RECTOR`.
 * 2. Si coincide con una cita por encima del umbral (`≥ 0.60`), se asigna a su punto
 *    con el rol de la cita. Entre `0.60` y `0.72` queda como sugerencia a confirmar.
 * 3. Si no hay cita, se clasifica por heurística determinista (extensión, páginas y
 *    encabezados) y queda fuera de los puntos, para que el estudiante lo ubique.
 */
class CatedraPreorderedMapper(
    private val matcher: BibliographicFuzzyMatcher = BibliographicFuzzyMatcher(),
    private val candidateExtractor: DocumentCandidateExtractor = DocumentCandidateExtractor(),
) {

    fun propose(
        catedraId: String,
        subjectTitle: String,
        documents: List<RawAcademicDocument>,
        citations: List<SyllabusCitationEntry>,
        syllabusGraph: SyllabusGraph? = null,
    ): CatedraPreorderedMappingProposal {
        val warnings = mutableListOf<String>()
        val seenHashes = mutableSetOf<String>()
        val assigned = linkedMapOf<String, MutableList<MatchedDocumentAssignment>>()
        val unassigned = mutableListOf<MatchedDocumentAssignment>()
        val authors = linkedMapOf<String, AuthorCanonical>()

        documents.forEach { document ->
            if (!seenHashes.add(document.sha256Hex)) {
                warnings += "'${document.fileName}' está repetido en el lote (SHA-256 idéntico); se procesó una sola vez."
                return@forEach
            }

            val candidate = candidateExtractor.extract(document)

            if (isAnnouncedProgram(candidate)) {
                unassigned += baseAssignment(
                    candidate = candidate,
                    role = MaterialRoleType.SYLLABUS_RECTOR,
                    score = 1.0,
                    justification = "Detectado como programa oficial de cátedra.",
                )
                return@forEach
            }

            val best = matcher.bestCitationMatch(candidate, citations)
            if (best != null && best.confidence != MatchConfidence.LOW_RED_ORPHAN && best.combinedScore >= ASSIGNMENT_FLOOR) {
                val assignment = toAssignment(candidate, best)
                val groupKey = assignment.assignedPointOfficialCode
                    ?: "UNIDAD_${assignment.assignedUnitNumber ?: 0}"
                assigned.getOrPut(groupKey) { mutableListOf() } += assignment
                best.authorCanonical?.let { canonical ->
                    val existing = authors[canonical.rootPatronymicLexeme]
                    authors[canonical.rootPatronymicLexeme] = if (existing != null) {
                        existing.copy(aliases = existing.aliases + canonical.aliases)
                    } else {
                        canonical
                    }
                }
                return@forEach
            }

            unassigned += baseAssignment(
                candidate = candidate,
                role = classifyUnassigned(candidate),
                score = best?.combinedScore ?: 0.0,
                justification = best?.let {
                    "Sin coincidencia suficiente (${it.justification}). Queda para asignación manual."
                } ?: "Sin citas comparables en el programa. Queda para asignación manual.",
            )
        }

        return CatedraPreorderedMappingProposal(
            catedraId = catedraId,
            subjectTitle = subjectTitle,
            syllabusGraph = syllabusGraph,
            assignmentsByPointCode = assigned,
            globalOrUnassignedDocuments = unassigned,
            consolidatedAuthors = authors.values.toList(),
            warnings = warnings,
        )
    }

    private fun toAssignment(
        candidate: DocumentCandidateMetadata,
        match: CitationMatchResult,
    ): MatchedDocumentAssignment {
        val citation = match.citation
        val offset = candidate.calculatedPageOffset
        val lastPage = candidate.totalPages.coerceAtLeast(1)
        val physicalStart = citation.pageRangeStart
            ?.let { printed -> (printed + offset).coerceIn(1, lastPage) }
        val physicalEnd = citation.pageRangeEnd
            ?.let { printed -> (printed + offset).coerceIn(physicalStart ?: 1, lastPage) }

        return MatchedDocumentAssignment(
            documentId = candidate.documentId,
            originalFileName = candidate.originalFileName,
            sha256Hash = candidate.sha256Hash,
            totalPages = candidate.totalPages,
            isScannedOnly = candidate.isScannedOnly,
            detectedAuthorCanonical = match.authorCanonical,
            detectedTitle = citation.title.ifBlank { candidate.candidateTitle },
            assignedUnitNumber = citation.unitNumber,
            assignedPointOfficialCode = citation.pointOfficialCode,
            assignedMaterialType = roleFor(citation),
            matchedCitationId = citation.citationId,
            targetPhysicalPageStart = physicalStart ?: 1,
            targetPhysicalPageEnd = physicalEnd ?: lastPage,
            pageOffsetDelta = offset,
            combinedConfidenceScore = match.combinedScore,
            matchJustification = if (match.isStrongMatch) {
                "Coincidencia fuerte: ${match.justification}."
            } else {
                "Sugerencia a confirmar: ${match.justification}."
            },
        )
    }

    private fun baseAssignment(
        candidate: DocumentCandidateMetadata,
        role: MaterialRoleType,
        score: Double,
        justification: String,
    ): MatchedDocumentAssignment = MatchedDocumentAssignment(
        documentId = candidate.documentId,
        originalFileName = candidate.originalFileName,
        sha256Hash = candidate.sha256Hash,
        totalPages = candidate.totalPages,
        isScannedOnly = candidate.isScannedOnly,
        detectedAuthorCanonical = null,
        detectedTitle = candidate.candidateTitle,
        assignedUnitNumber = null,
        assignedPointOfficialCode = null,
        assignedMaterialType = role,
        matchedCitationId = null,
        targetPhysicalPageStart = 1,
        targetPhysicalPageEnd = candidate.totalPages.coerceAtLeast(1),
        pageOffsetDelta = candidate.calculatedPageOffset,
        combinedConfidenceScore = min(1.0, score),
        matchJustification = justification,
    )

    private fun isAnnouncedProgram(candidate: DocumentCandidateMetadata): Boolean =
        PROGRAM_PATTERN.containsMatchIn(candidate.rawHeaderText) ||
            PROGRAM_PATTERN.containsMatchIn(candidate.originalFileName)

    /**
     * Cascada determinista de la Resolución Forense 24. No adivina contenido: usa
     * formato, cantidad de páginas y encabezados explícitos.
     */
    private fun classifyUnassigned(candidate: DocumentCandidateMetadata): MaterialRoleType {
        val extension = candidate.fileExtension.lowercase()
        if (extension in PRESENTATION_EXTENSIONS) return MaterialRoleType.COMPLEMENTARY_SUPPORT
        if (candidate.totalPages in 1..4 && COMPLEMENTARY_PATTERN.containsMatchIn(candidate.rawHeaderText)) {
            return MaterialRoleType.PRACTICAL_CASUISTRY
        }
        if (COMPLEMENTARY_PATTERN.containsMatchIn(candidate.originalFileName)) {
            return MaterialRoleType.PRACTICAL_CASUISTRY
        }
        if (candidate.totalPages > 25 && PROGRAM_PATTERN.containsMatchIn(candidate.rawHeaderText)) {
            return MaterialRoleType.SYLLABUS_RECTOR
        }
        return if (candidate.totalPages <= SHORT_DOCUMENT_PAGES) {
            MaterialRoleType.PRACTICAL_CASUISTRY
        } else {
            MaterialRoleType.PRIMARY_DOCTRINE
        }
    }

    private fun roleFor(citation: SyllabusCitationEntry): MaterialRoleType {
        val marker = AcademicTextNormalizer.normalize(citation.rawCitation)
        return when {
            COMPLEMENTARY_PATTERN.containsMatchIn(citation.rawCitation) -> MaterialRoleType.PRACTICAL_CASUISTRY
            marker.contains("anexo") || marker.contains("anexos") -> MaterialRoleType.COMPLEMENTARY_SUPPORT
            else -> MaterialRoleType.PRIMARY_DOCTRINE
        }
    }

    private companion object {
        const val ASSIGNMENT_FLOOR = 0.60
        const val SHORT_DOCUMENT_PAGES = 8

        val PRESENTATION_EXTENSIONS = setOf("pptx", "ppt", "odp")
        val PROGRAM_PATTERN = Regex(
            """\b(PROGRAMA\s+ANAL[IÍ]TICO|PROGRAMA\s+DE\s+EXAMEN|PROGRAMA\s+DE\s+LA\s+ASIGNATURA|PLANIFICACI[OÓ]N\s+DE\s+C[AÁ]TEDRA)\b""",
            RegexOption.IGNORE_CASE,
        )
        val COMPLEMENTARY_PATTERN = Regex(
            """\b(TRABAJO\s+PR[AÁ]CTICO|GU[IÍ]A\s+DE\s+LECTURA|GU[IÍ]A\s+DE\s+ESTUDIO|FALLO|SENTENCIA|JURISPRUDENCIA|NOTICIA|CR[OÓ]NICA|ART[IÍ]CULO\s+PERIOD[IÍ]STICO|CASO\s+PR[AÁ]CTICO)\b""",
            RegexOption.IGNORE_CASE,
        )
    }
}
