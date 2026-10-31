/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:02:00 -03:00
 * ARCHIVO: feature/classrooms/src/main/kotlin/com/fenix/studio/feature/classrooms/ClassroomsAndSyllabusModels.kt
 * ================================================================================
 */

package com.fenix.studio.feature.classrooms

import androidx.compose.runtime.Immutable
import com.fenix.studio.core.database.model.ClassroomModality
import com.fenix.studio.domain.fsrs.FsrsCardMemoryState
import kotlin.math.roundToInt

/**
 * Modelo de resumen de Aula en `P-07` con **Progreso Ponderado de Dos Niveles (`RN-24`)**:
 * - Nivel A (`theoreticalBlocksProgressRatio`): Progreso de procesamiento teórico y bloques completados (`0.0..1.0`).
 * - Nivel B (`fsrsRetentionMasteryRatio`): Progreso de retención y dominio mnemónico FSRS v6 (`0.0..1.0`).
 * - Progreso Global Ponderado (`weightedOverallProgressPercent`): $50\% \cdot \text{Nivel A} + 50\% \cdot \text{Nivel B}$.
 */
@Immutable
data class ClassroomSummaryUiModel(
    val catedraId: String,
    val displayTitle: String,
    val modality: ClassroomModality,
    val programaYear: Int? = null,
    val completedBlocksCount: Int = 0,
    val totalExpectedBlocksCount: Int = 1,
    val masteredFsrsCardsCount: Int = 0,
    val totalFsrsCardsCount: Int = 1,
) {
    val theoreticalBlocksProgressRatio: Float
        get() = if (totalExpectedBlocksCount <= 0) 0f
        else (completedBlocksCount.toFloat() / totalExpectedBlocksCount.toFloat()).coerceIn(0f, 1f)

    val fsrsRetentionMasteryRatio: Float
        get() = if (totalFsrsCardsCount <= 0) 0f
        else (masteredFsrsCardsCount.toFloat() / totalFsrsCardsCount.toFloat()).coerceIn(0f, 1f)

    val theoreticalProgressPercent: Int
        get() = (theoreticalBlocksProgressRatio * 100f).roundToInt()

    val fsrsMasteryPercent: Int
        get() = (fsrsRetentionMasteryRatio * 100f).roundToInt()

    /**
     * Progreso ponderado de dos niveles (`RN-24`): 50% avance teórico + 50% dominio mnemónico FSRS v6.
     */
    val weightedOverallProgressPercent: Int
        get() = (((theoreticalBlocksProgressRatio * 0.50f) + (fsrsRetentionMasteryRatio * 0.50f)) * 100f)
            .roundToInt()
            .coerceIn(0, 100)
}

/**
 * Punto o subpunto por densidad teórica (`N.N.a ✦`) dentro de una Unidad en `P-08`.
 */
@Immutable
data class SyllabusPointUiModel(
    val pointId: String,
    val pointCode: String,
    val title: String,
    val isSyntheticDensityNode: Boolean = false,
    val synthesizedMarkdownBlocks: List<String> = emptyList(),
    val linkedMaterialNames: List<String> = emptyList(),
    val isPendingIncrementalProcessing: Boolean = false,
)

/**
 * Unidad temática dentro de `P-08` (Aula en Detalle).
 */
@Immutable
data class SyllabusUnitUiModel(
    val unitId: String,
    val unitNumber: Int,
    val unitTitle: String,
    val points: List<SyllabusPointUiModel> = emptyList(),
)

/**
 * Borrador de **Ingesta Incremental (`RN-22`)** para añadir nuevos PDFs o apuntes a un punto
 * específico de un aula existente sin re-procesar las unidades ya cerradas.
 */
@Immutable
data class IncrementalIngestionDraftModel(
    val targetCatedraId: String,
    val targetUnitId: String,
    val targetPointCode: String,
    val newMaterialFileName: String,
    val startPhysicalPage: Int = 1,
    val endPhysicalPage: Int = 15,
)

/**
 * Estado de la sesión interactiva de repaso de flashcards FSRS v6 en `P-08` (`MF-6.2`).
 */
@Immutable
data class FsrsReviewSessionState(
    val deckCards: List<FsrsCardMemoryState> = emptyList(),
    val currentCardIndex: Int = 0,
    val isBackAnswerRevealed: Boolean = false,
    val reviewedCountInSession: Int = 0,
) {
    val activeCard: FsrsCardMemoryState?
        get() = deckCards.getOrNull(currentCardIndex)
}

/**
 * Estado unificado de `P-07` (Mis Aulas) y `P-08` (Aula en Detalle, Mazo FSRS v6 e Ingesta Incremental `RN-22`).
 */
@Immutable
data class ClassroomsUiState(
    val classrooms: List<ClassroomSummaryUiModel> = emptyList(),
    val selectedClassroomId: String? = null,
    val selectedClassroomUnits: List<SyllabusUnitUiModel> = emptyList(),
    val fsrsSession: FsrsReviewSessionState = FsrsReviewSessionState(),
    val lastIncrementalIngestionPointCode: String? = null,
)
