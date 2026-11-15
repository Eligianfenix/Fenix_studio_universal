/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-15 01:35:00 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/matching/CitationMatchModels.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.matching

import kotlinx.serialization.Serializable

/**
 * Cita bibliográfica tal como la entrega cualquier fuente (hoy el parser curricular,
 * mañana un programa importado a mano). Es un contrato neutro: el conciliador no
 * necesita conocer al parser para trabajar.
 */
@Serializable
data class SyllabusCitationEntry(
    val citationId: String,
    val unitNumber: Int,
    val pointOfficialCode: String?,
    val rawCitation: String,
    val authors: List<String> = emptyList(),
    val title: String,
    val pageRangeStart: Int? = null,
    val pageRangeEnd: Int? = null,
) {
    /**
     * Apellido raíz normalizado. En los programas hispanos la forma dominante es
     * "Apellido, Nombre"; si no hay coma se toma el primer token.
     */
    val primaryAuthorSurnameNormalized: String
        get() {
            val raw = authors.firstOrNull().orEmpty()
            if (raw.isBlank()) return ""
            val normalized = AcademicTextNormalizer.normalize(raw)
            return if (raw.contains(',')) {
                normalized.substringBefore(' ').trim()
            } else {
                normalized.substringBefore(' ').trim()
            }
        }

    /** Nombre de pila o inicial, usado para validar que la persona coincida. */
    val primaryAuthorGivenNameNormalized: String
        get() {
            val raw = authors.firstOrNull().orEmpty()
            if (raw.isBlank()) return ""
            val normalized = AcademicTextNormalizer.normalize(raw)
            return if (raw.contains(',')) {
                normalized.substringAfter(' ', "").trim()
            } else {
                normalized.substringAfter(' ', "").trim()
            }
        }
}

/** Consolidación autoral (Resolución Forense 6): un autor, varios alias. */
@Serializable
data class AuthorCanonical(
    val canonicalId: String,
    val canonicalName: String,
    val rootPatronymicLexeme: String,
    val aliases: Set<String> = emptySet(),
)

/** Resultado detallado de comparar un archivo con una cita del programa. */
data class CitationMatchResult(
    val citation: SyllabusCitationEntry,
    val combinedScore: Double,
    val confidence: MatchConfidence,
    val isStrongMatch: Boolean,
    val authorScore: Double,
    val titleJaroWinkler: Double,
    val weightedLevenshtein: Double,
    val simHashHammingDistance: Int,
    val authorCanonical: AuthorCanonical?,
) {
    val justification: String
        get() = buildString {
            append("autor ${(authorScore * 100).toInt()}%")
            append(", título ${(titleJaroWinkler * 100).toInt()}%")
            append(", tokens ${(weightedLevenshtein * 100).toInt()}%")
            append(", SimHash Δ$simHashHammingDistance")
            append(" → ${(combinedScore * 100).toInt()}%")
        }
}

internal data class AuthorAffinityEvaluation(
    val authorScore: Double,
    val canonical: AuthorCanonical?,
)
