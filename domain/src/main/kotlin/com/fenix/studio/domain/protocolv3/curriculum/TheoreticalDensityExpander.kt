/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: domain/src/main/kotlin/com/fenix/studio/domain/protocolv3/curriculum/TheoreticalDensityExpander.kt
 * ================================================================================
 */

package com.fenix.studio.domain.protocolv3.curriculum

import kotlin.math.ceil

/**
 * Motor determinista de expansión de Subpuntos por Densidad Teórica (`N.N.a ✦`, MF-3.3).
 *
 * Aplica la ecuación de densidad del Protocolo Fénix v3.0:
 * $$D(p) = 0.45 \cdot \frac{C_{\text{prim}}}{4} + 0.35 \cdot \frac{P_{\text{prop}}}{8} + 0.20 \cdot H_{\text{lex}}(p)$$
 *
 * Si $D(p) > 1.0$, particiona los conceptos primarios en subpuntos sintéticos (`1.1.a ✦`, `1.1.b ✦`, ...)
 * manteniendo intacta la numeración oficial `point.pointCode` del programa del estudiante.
 */
object TheoreticalDensityExpander {

    const val DENSITY_EXPANSION_THRESHOLD: Double = 1.0
    private const val WEIGHT_CONCEPTS: Double = 0.45
    private const val WEIGHT_PROPOSITIONS: Double = 0.35
    private const val WEIGHT_LEXICAL_ENTROPY: Double = 0.20
    private const val MAX_CONCEPTS_PER_SUBPOINT: Int = 3

    /**
     * Calcula el índice de densidad teórica $D(p)$ de un punto curricular.
     */
    fun calculateDensityScore(
        primaryConceptCount: Int,
        propositionCount: Int,
        lexicalEntropy: Double,
    ): Double {
        val cNorm = primaryConceptCount.coerceAtLeast(0).toDouble() / 4.0
        val pNorm = propositionCount.coerceAtLeast(0).toDouble() / 8.0
        val hClamped = lexicalEntropy.coerceIn(0.0, 2.0)
        return (WEIGHT_CONCEPTS * cNorm) +
            (WEIGHT_PROPOSITIONS * pNorm) +
            (WEIGHT_LEXICAL_ENTROPY * hClamped)
    }

    /**
     * Evalúa un `SyllabusPoint` y genera sus subpuntos `N.N.a ✦` si $D(p) > 1.0$.
     * El código oficial `point.pointCode` y `point.officialTitle` jamás se alteran.
     */
    fun expandPointIfDense(point: SyllabusPoint): SyllabusPoint {
        val density = calculateDensityScore(
            primaryConceptCount = point.primaryConcepts.size,
            propositionCount = point.totalPropositions,
            lexicalEntropy = point.lexicalEntropy,
        )
        if (density <= DENSITY_EXPANSION_THRESHOLD || point.primaryConcepts.size <= 1) {
            return point.copy(syntheticSubpoints = emptyList())
        }

        val chunkSize = ceil(
            point.primaryConcepts.size.toDouble() /
                ceil(density).coerceAtLeast(2.0),
        ).toInt().coerceIn(1, MAX_CONCEPTS_PER_SUBPOINT)

        val conceptGroups = point.primaryConcepts.chunked(chunkSize)
        val subpoints = conceptGroups.mapIndexed { index, group ->
            val letterSuffix = ('a'.code + index).toChar()
            val cleanCode = "${point.pointCode}.$letterSuffix"
            val displayCodeWithStar = "$cleanCode \u2726"
            val summaryTitle = group.joinToString(separator = "; ") { it.canonicalName }
            SyllabusSubpoint(
                subpointCode = cleanCode,
                displayCodeWithStar = displayCodeWithStar,
                parentPointCode = point.pointCode,
                title = summaryTitle,
                assignedConcepts = group,
                computedDensityScore = density,
            )
        }

        return point.copy(syntheticSubpoints = subpoints)
    }
}
