/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: domain/src/main/kotlin/com/fenix/studio/domain/protocolv3/curriculum/SyllabusGraphModels.kt
 * ================================================================================
 */

package com.fenix.studio.domain.protocolv3.curriculum

import kotlinx.serialization.Serializable

/**
 * Descriptor atómico de concepto primario identificado en la Parte 0 (Inventario Curricular).
 */
@Serializable
data class ConceptDescriptor(
    val conceptId: String,
    val canonicalName: String,
    val propositionCount: Int = 1,
    val lexicalEntropyBits: Double = 0.5,
)

/**
 * Subpunto generado por el árbol de decisión de Densidad Teórica (`N.N.a ✦`).
 * Conserva inmutable el código oficial `parentPointCode` del programa de estudios del alumno.
 */
@Serializable
data class SyllabusSubpoint(
    val subpointCode: String,
    val displayCodeWithStar: String,
    val parentPointCode: String,
    val title: String,
    val assignedConcepts: List<ConceptDescriptor>,
    val computedDensityScore: Double,
)

/**
 * Punto curricular oficial del programa del estudiante.
 */
@Serializable
data class SyllabusPoint(
    val pointCode: String,
    val officialTitle: String,
    val primaryConcepts: List<ConceptDescriptor>,
    val totalPropositions: Int,
    val lexicalEntropy: Double,
    val syntheticSubpoints: List<SyllabusSubpoint> = emptyList(),
)

/**
 * Unidad pedagógica o eje temático del programa o proyecto guiado.
 */
@Serializable
data class SyllabusUnit(
    val unitNumber: Int,
    val unitTitle: String,
    val points: List<SyllabusPoint>,
)

/**
 * Grafo curricular completo libre de hardcodeo institucional (Regla Inviolable 2 y 3).
 */
@Serializable
data class SyllabusGraph(
    val classroomId: String,
    val units: List<SyllabusUnit>,
)
