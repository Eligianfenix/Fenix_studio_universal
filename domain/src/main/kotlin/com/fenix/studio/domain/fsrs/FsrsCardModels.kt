/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: domain/src/main/kotlin/com/fenix/studio/domain/fsrs/FsrsCardModels.kt
 * ================================================================================
 */

package com.fenix.studio.domain.fsrs

/**
 * Calificaciones de respuesta del estudiante en el sistema de repaso espaciado FSRS v6 (`MF-6.2`).
 */
enum class FsrsRating(val gradeValue: Int) {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4),
}

/**
 * Origen determinístico de una tarjeta de estudio (Costo 0 de tokens de IA).
 */
enum class FlashcardSourceSection {
    PART_D_ACTIVE_RECALL,
    PART_F_GLOSSARY,
}

/**
 * Estado matemático inmutable de una tarjeta en el algoritmo FSRS v6.
 */
data class FsrsCardMemoryState(
    val cardId: String,
    val catedraId: String,
    val unitNumber: Int,
    val pointCode: String,
    val sourceSection: FlashcardSourceSection,
    val frontPrompt: String,
    val backAnswer: String,
    val stabilityDays: Double = 0.0,
    val difficulty: Double = 5.0,
    val repsCount: Int = 0,
    val lapsesCount: Int = 0,
    val lastReviewEpochMs: Long = 0L,
    val nextReviewEpochMs: Long = 0L,
)
