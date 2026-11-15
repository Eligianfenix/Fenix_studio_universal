/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:16:30 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/matching/BibliographicFuzzyMatcher.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.matching

import com.fenix.studio.core.document.ingestion.DocumentCandidateMetadata
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Los 3 niveles canónicos de confianza del Conciliador Bibliográfico Difuso (`MF-3.2`, `RN-26`).
 */
enum class MatchConfidence(val badgeLabel: String) {
    /** Score >= 0.85: Auto-asignación confirmada (`[ 🟢 >= 85% ]`). */
    HIGH_GREEN("[ \uD83D\uDFE2 >= 85% ]"),

    /** 0.60 <= Score < 0.85: Sugerencia pendiente de confirmación (`[ 🟡 60%–84% ]`). */
    MEDIUM_AMBER("[ \uD83D\uDFE1 60%–84% ]"),

    /** Score < 0.60: Material huérfano que habilita Ingesta Directa en 1 clic (`[ 🔴 < 60% ]`, `RN-26`). */
    LOW_RED_ORPHAN("[ \uD83D\uDD34 < 60% ]"),
}

/**
 * Resultado de conciliación entre un archivo suelto del estudiante y una referencia o unidad del programa.
 */
data class FuzzyBibliographicMatchResult(
    val candidateFileName: String,
    val targetSyllabusReference: String,
    val targetUnitNumber: Int,
    val score: Double,
    val confidence: MatchConfidence,
    val matchedLexemes: Set<String>,
)

/**
 * Conciliador Difuso Bibliográfico (`BibliographicFuzzyMatcher`, `MF-3.2`).
 * Combina:
 * - Similitud **Jaro-Winkler** ($0.55$)
 * - Coeficiente **Dice-Sørensen** sobre bigramas ($0.45$)
 * - F1 simétrico de lexemas + Boost Jaccard de intersección exacta de lexemas autorales extraídos por `HispanicAuthorLexemeExtractor`
 * Clasificando el resultado determinísticamente en `HIGH_GREEN` ($\ge 0.85$),
 * `MEDIUM_AMBER` ($0.60..0.84$) o `LOW_RED_ORPHAN` ($< 0.60$).
 */
class BibliographicFuzzyMatcher {

    fun evaluateMatch(
        candidateFileName: String,
        targetSyllabusReference: String,
        targetUnitNumber: Int = 1,
    ): FuzzyBibliographicMatchResult {
        val fileLexemes = HispanicAuthorLexemeExtractor.extractSignificantLexemes(candidateFileName)
        val refLexemes = HispanicAuthorLexemeExtractor.extractSignificantLexemes(targetSyllabusReference)

        if (fileLexemes.isEmpty() || refLexemes.isEmpty()) {
            return FuzzyBibliographicMatchResult(
                candidateFileName = candidateFileName,
                targetSyllabusReference = targetSyllabusReference,
                targetUnitNumber = targetUnitNumber,
                score = 0.0,
                confidence = MatchConfidence.LOW_RED_ORPHAN,
                matchedLexemes = emptySet(),
            )
        }

        val normalizedFileJoined = fileLexemes.joinToString(" ")
        val normalizedRefJoined = refLexemes.joinToString(" ")

        val fullStringHybrid = (WEIGHT_JARO_WINKLER * computeJaroWinklerSimilarity(normalizedFileJoined, normalizedRefJoined)) +
            (WEIGHT_DICE_SORENSEN * computeDiceBigramSimilarity(normalizedFileJoined, normalizedRefJoined))

        val forwardLexemeAvg = fileLexemes.map { fLex ->
            refLexemes.maxOf { rLex ->
                (WEIGHT_JARO_WINKLER * computeJaroWinklerSimilarity(fLex, rLex)) +
                    (WEIGHT_DICE_SORENSEN * computeDiceBigramSimilarity(fLex, rLex))
            }
        }.average()

        val backwardLexemeAvg = refLexemes.map { rLex ->
            fileLexemes.maxOf { fLex ->
                (WEIGHT_JARO_WINKLER * computeJaroWinklerSimilarity(rLex, fLex)) +
                    (WEIGHT_DICE_SORENSEN * computeDiceBigramSimilarity(rLex, fLex))
            }
        }.average()

        val symmetricLexemeF1 = if (forwardLexemeAvg + backwardLexemeAvg <= 1e-9) {
            0.0
        } else {
            (2.0 * forwardLexemeAvg * backwardLexemeAvg) / (forwardLexemeAvg + backwardLexemeAvg)
        }

        val baseHybrid = max(fullStringHybrid, symmetricLexemeF1)

        val exactIntersection = fileLexemes.intersect(refLexemes.toSet())
        val unionSize = fileLexemes.union(refLexemes.toSet()).size.coerceAtLeast(1)
        val jaccardCoverage = exactIntersection.size.toDouble() / unionSize.toDouble()

        val authorBoost = if (exactIntersection.isNotEmpty()) {
            0.20 * jaccardCoverage
        } else {
            0.0
        }

        val finalScore = (baseHybrid + authorBoost).coerceIn(0.0, 1.0)
        val confidence = classifyConfidence(finalScore)

        return FuzzyBibliographicMatchResult(
            candidateFileName = candidateFileName,
            targetSyllabusReference = targetSyllabusReference,
            targetUnitNumber = targetUnitNumber,
            score = finalScore,
            confidence = confidence,
            matchedLexemes = exactIntersection,
        )
    }

    fun classifyConfidence(score: Double): MatchConfidence = when {
        score >= THRESHOLD_HIGH_GREEN -> MatchConfidence.HIGH_GREEN
        score >= THRESHOLD_MEDIUM_AMBER -> MatchConfidence.MEDIUM_AMBER
        else -> MatchConfidence.LOW_RED_ORPHAN
    }

    fun computeJaroWinklerSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val matchDistance = max(0, (max(s1.length, s2.length) / 2) - 1)
        val s1Matches = BooleanArray(s1.length)
        val s2Matches = BooleanArray(s2.length)

        var matches = 0
        for (i in s1.indices) {
            val start = max(0, i - matchDistance)
            val end = min(i + matchDistance + 1, s2.length)
            for (j in start until end) {
                if (s2Matches[j]) continue
                if (s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }
        if (matches == 0) return 0.0

        var transpositions = 0
        var k = 0
        for (i in s1.indices) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        val m = matches.toDouble()
        val jaro = ((m / s1.length) + (m / s2.length) + ((m - transpositions / 2.0) / m)) / 3.0

        var prefix = 0
        val maxPrefix = min(4, min(s1.length, s2.length))
        while (prefix < maxPrefix && s1[prefix] == s2[prefix]) {
            prefix++
        }

        return (jaro + prefix * 0.1 * (1.0 - jaro)).coerceIn(0.0, 1.0)
    }

    fun computeDiceBigramSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.length < 2 || s2.length < 2) return 0.0

        val bigrams1 = (0 until s1.length - 1).map { s1.substring(it, it + 2) }
        val bigrams2 = (0 until s2.length - 1).map { s2.substring(it, it + 2) }.toMutableList()

        var intersection = 0
        for (bg in bigrams1) {
            val idx = bigrams2.indexOf(bg)
            if (idx != -1) {
                intersection++
                bigrams2.removeAt(idx)
            }
        }
        return ((2.0 * intersection.toDouble()) / (bigrams1.size + (s2.length - 1)).toDouble())
            .coerceIn(0.0, 1.0)
    }

    /**
     * Conciliación real entre un archivo del lote y una cita del programa analítico.
     *
     * Fórmula del deep research (`Fase 0`):
     * `0.45 · (0.55·autor + 0.45·Jaro-Winkler de título) + 0.35 · Levenshtein ponderado + 0.20 · SimHash + bono patronímico`
     *
     * Es **determinista**: no participa ningún modelo de lenguaje. Si no hay evidencia
     * suficiente, la cita no se asigna y el archivo queda huérfano para revisión humana.
     */
    fun evaluateCandidateAgainstCitation(
        candidate: DocumentCandidateMetadata,
        citation: SyllabusCitationEntry,
    ): CitationMatchResult {
        val candidateFileNameNorm = AcademicTextNormalizer.normalize(
            candidate.originalFileName.substringBeforeLast('.'),
        )

        val authorEvaluation = evaluateHispanicAuthorAffinity(
            citationSurnameNorm = citation.primaryAuthorSurnameNormalized,
            citationGivenNorm = citation.primaryAuthorGivenNameNormalized,
            citationRawAuthor = citation.authors.firstOrNull().orEmpty(),
            candidateHeaderNorm = candidate.normalizedHeaderText,
            candidateAuthors = candidate.candidateAuthors,
            candidateFileNameNorm = candidateFileNameNorm,
        )

        val citationTitleNorm = AcademicTextNormalizer.normalize(citation.title)
        val candidateTitleNorm = AcademicTextNormalizer.normalize(
            "${candidate.candidateTitle} ${candidate.originalFileName.substringBeforeLast('.')}",
        )

        val titleJaroWinkler = computeJaroWinklerSimilarity(citationTitleNorm, candidateTitleNorm)
        val combinedJaroWinkler = (WEIGHT_AUTHOR_VS_TITLE * authorEvaluation.authorScore) +
            (WEIGHT_TITLE_VS_AUTHOR * titleJaroWinkler)

        val weightedLevenshtein = computeTokenWeightedLevenshteinSimilarity(
            tokensA = AcademicTextNormalizer.tokenizeSignificant(citationTitleNorm),
            tokensB = candidate.significantTokens,
        )

        val hammingDistance = AcademicTextNormalizer.hammingDistance(
            AcademicTextNormalizer.simHash64(citationTitleNorm),
            candidate.headerSimHash64,
        )
        val simHashScore = if (hammingDistance <= SIMHASH_HAMMING_TOLERANCE) {
            1.0
        } else {
            ((64 - hammingDistance).coerceAtLeast(0)) / 64.0
        }

        // Solo se premia al autor si además hay evidencia en el título: evita que un
        // apellido común ("García") asigne cualquier archivo a cualquier cita.
        val patronymicBonus = if (authorEvaluation.authorScore >= AUTHOR_CONSOLIDATION_THRESHOLD &&
            (weightedLevenshtein >= 0.35 || titleJaroWinkler >= 0.68)
        ) {
            0.18
        } else {
            0.0
        }

        val total = min(
            1.0,
            (WEIGHT_COMBINED_LEXICAL * combinedJaroWinkler) +
                (WEIGHT_LEVENSHTEIN * weightedLevenshtein) +
                (WEIGHT_SIMHASH * simHashScore) +
                patronymicBonus,
        )

        return CitationMatchResult(
            citation = citation,
            combinedScore = total,
            confidence = classifyConfidence(total),
            isStrongMatch = total >= STRONG_CITATION_MATCH_THRESHOLD,
            authorScore = authorEvaluation.authorScore,
            titleJaroWinkler = titleJaroWinkler,
            weightedLevenshtein = weightedLevenshtein,
            simHashHammingDistance = hammingDistance,
            authorCanonical = authorEvaluation.canonical,
        )
    }

    /** Mejor cita para un archivo, o `null` si no hay citas. */
    fun bestCitationMatch(
        candidate: DocumentCandidateMetadata,
        citations: List<SyllabusCitationEntry>,
    ): CitationMatchResult? = citations
        .asSequence()
        .map { citation -> evaluateCandidateAgainstCitation(candidate, citation) }
        .maxByOrNull { it.combinedScore }

    /**
     * Similitud de Levenshtein **ponderada por longitud de token** con fila rodante
     * (`O(min(|A|,|B|))` de memoria): los tokens largos pesan más porque suelen ser
     * apellidos y sustantivos distintivos.
     */
    fun computeTokenWeightedLevenshteinSimilarity(
        tokensA: Set<String>,
        tokensB: Set<String>,
    ): Double {
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0

        var weightedSimilaritySum = 0.0
        var totalWeight = 0.0

        for (tokenA in tokensA) {
            val tokenWeight = tokenA.length.coerceAtLeast(3).toDouble()
            totalWeight += tokenWeight

            var bestTokenSimilarity = 0.0
            for (tokenB in tokensB) {
                if (abs(tokenA.length - tokenB.length) > MAX_TOKEN_LENGTH_DELTA) continue
                val distance = computeCharLevenshteinDistance(tokenA, tokenB)
                val maxLength = max(tokenA.length, tokenB.length).toDouble()
                val similarity = (maxLength - distance) / maxLength
                if (similarity > bestTokenSimilarity) bestTokenSimilarity = similarity
                if (bestTokenSimilarity == 1.0) break
            }
            if (bestTokenSimilarity >= TOKEN_SIMILARITY_FLOOR) {
                weightedSimilaritySum += bestTokenSimilarity * tokenWeight
            }
        }

        return if (totalWeight > 0.0) (weightedSimilaritySum / totalWeight).coerceIn(0.0, 1.0) else 0.0
    }

    /**
     * Levenshtein de caracteres con dos filas primitivas, sin matrices bidimensionales.
     */
    fun computeCharLevenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previousRow = IntArray(b.length + 1) { it }
        var currentRow = IntArray(b.length + 1)

        for (i in a.indices) {
            currentRow[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                currentRow[j + 1] = min(
                    min(currentRow[j] + 1, previousRow[j + 1] + 1),
                    previousRow[j] + cost,
                )
            }
            val swap = previousRow
            previousRow = currentRow
            currentRow = swap
        }
        return previousRow[b.length]
    }

    /**
     * Resolución patronímica hispana: aísla el apellido raíz ("Aquín" en "Nora Aquín de
     * Brignone"), tolera variantes con Jaro-Winkler y consolida alias en un
     * `AuthorCanonical` cuando la coincidencia supera el umbral.
     */
    private fun evaluateHispanicAuthorAffinity(
        citationSurnameNorm: String,
        citationGivenNorm: String,
        citationRawAuthor: String,
        candidateHeaderNorm: String,
        candidateAuthors: List<String>,
        candidateFileNameNorm: String,
    ): AuthorAffinityEvaluation {
        if (citationSurnameNorm.isBlank()) return AuthorAffinityEvaluation(0.0, null)

        val surnameInHeader = candidateHeaderNorm.split(' ').any { token ->
            computeJaroWinklerSimilarity(citationSurnameNorm, token) >= SURNAME_IN_TEXT_THRESHOLD
        }
        val surnameInFileName = candidateFileNameNorm.contains(citationSurnameNorm)

        var bestCandidateFullAuthor = citationRawAuthor
        var bestScore = if (surnameInHeader || surnameInFileName) 0.90 else 0.0

        val citationInitial = citationGivenNorm.firstOrNull()

        for (candidateAuthor in candidateAuthors) {
            val normalizedAuthor = AcademicTextNormalizer.normalize(candidateAuthor)
            val authorTokens = normalizedAuthor.split(' ').filter { it.isNotBlank() }

            for (token in authorTokens) {
                val surnameSimilarity = computeJaroWinklerSimilarity(citationSurnameNorm, token)
                if (surnameSimilarity < AUTHOR_CONSOLIDATION_THRESHOLD) continue

                val initialMatches = citationInitial != null && authorTokens.any { other ->
                    other != token && other.firstOrNull() == citationInitial
                }
                val computed = if (initialMatches) {
                    min(1.0, surnameSimilarity * 0.95 + 0.05)
                } else {
                    surnameSimilarity * 0.90
                }
                if (computed > bestScore) {
                    bestScore = computed
                    bestCandidateFullAuthor = candidateAuthor
                }
            }
        }

        val canonical = if (bestScore >= AUTHOR_CONSOLIDATION_THRESHOLD) {
            AuthorCanonical(
                canonicalId = "AUTH-${citationSurnameNorm.uppercase()}",
                canonicalName = citationRawAuthor.ifBlank { bestCandidateFullAuthor },
                rootPatronymicLexeme = citationSurnameNorm,
                aliases = setOf(citationRawAuthor, bestCandidateFullAuthor)
                    .filter { it.isNotBlank() }
                    .toSet(),
            )
        } else {
            null
        }

        return AuthorAffinityEvaluation(bestScore, canonical)
    }

    companion object {
        const val THRESHOLD_HIGH_GREEN: Double = 0.85
        const val THRESHOLD_MEDIUM_AMBER: Double = 0.60
        private const val WEIGHT_JARO_WINKLER: Double = 0.55
        private const val WEIGHT_DICE_SORENSEN: Double = 0.45

        /** Pesos del conciliador archivo ↔ cita (deep research `Fase 0`). */
        private const val WEIGHT_AUTHOR_VS_TITLE: Double = 0.55
        private const val WEIGHT_TITLE_VS_AUTHOR: Double = 0.45
        private const val WEIGHT_COMBINED_LEXICAL: Double = 0.45
        private const val WEIGHT_LEVENSHTEIN: Double = 0.35
        private const val WEIGHT_SIMHASH: Double = 0.20

        const val AUTHOR_CONSOLIDATION_THRESHOLD: Double = 0.88
        const val STRONG_CITATION_MATCH_THRESHOLD: Double = 0.72
        private const val SIMHASH_HAMMING_TOLERANCE: Int = 4
        private const val SURNAME_IN_TEXT_THRESHOLD: Double = 0.92
        private const val TOKEN_SIMILARITY_FLOOR: Double = 0.72
        private const val MAX_TOKEN_LENGTH_DELTA: Int = 5
    }
}
