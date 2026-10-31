/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:03:45 -03:00
 * ARCHIVO: feature/classrooms/src/test/kotlin/com/fenix/studio/feature/classrooms/ClassroomsAndFsrsSessionTest.kt
 * ================================================================================
 */

package com.fenix.studio.feature.classrooms

import com.fenix.studio.core.database.model.ClassroomModality
import com.fenix.studio.domain.fsrs.FlashcardSourceSection
import com.fenix.studio.domain.fsrs.FsrsCardMemoryState
import com.fenix.studio.domain.fsrs.FsrsRating
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClassroomsAndFsrsSessionTest {

    @Test
    fun `two-level weighted progress RN-24 combines theoretical blocks and FSRS v6 retention mastery`() {
        val summary = ClassroomSummaryUiModel(
            catedraId = "cat-01",
            displayTitle = "Espacio Curricular Sintético",
            modality = ClassroomModality.CATEDRA_PROGRAMA,
            programaYear = 2026,
            completedBlocksCount = 8,
            totalExpectedBlocksCount = 10, // 80% teórico
            masteredFsrsCardsCount = 6,
            totalFsrsCardsCount = 10, // 60% FSRS v6
        )

        assertEquals(80, summary.theoreticalProgressPercent)
        assertEquals(60, summary.fsrsMasteryPercent)
        assertEquals(70, summary.weightedOverallProgressPercent)
    }

    @Test
    fun `FSRS v6 interactive session updates card stability and classroom mastery at zero AI token cost`() {
        val viewModel = ClassroomsViewModel()
        viewModel.setClassroomsCatalog(
            summaries = listOf(
                ClassroomSummaryUiModel(
                    catedraId = "cat-01",
                    displayTitle = "Espacio Curricular Sintético",
                    modality = ClassroomModality.CATEDRA_PROGRAMA,
                    programaYear = 2026,
                    completedBlocksCount = 10,
                    totalExpectedBlocksCount = 10,
                    masteredFsrsCardsCount = 0,
                    totalFsrsCardsCount = 1,
                ),
            ),
        )

        val initialCard = FsrsCardMemoryState(
            cardId = "card-1",
            catedraId = "cat-01",
            unitNumber = 1,
            pointCode = "1.1",
            sourceSection = FlashcardSourceSection.PART_D_ACTIVE_RECALL,
            frontPrompt = "¿Cuál es el invariante estructural del módulo?",
            backAnswer = "El desacoplamiento determinístico entre capas.",
        )

        viewModel.onStartFsrsReviewSession(listOf(initialCard))
        assertFalse(viewModel.uiState.value.fsrsSession.isBackAnswerRevealed)

        viewModel.onRevealActiveFlashcardAnswer()
        assertTrue(viewModel.uiState.value.fsrsSession.isBackAnswerRevealed)

        val updated = viewModel.onRateActiveFlashcard(
            rating = FsrsRating.GOOD,
            reviewEpochMs = 1_730_000_000_000L,
        )
        assertNotNull(updated)
        assertTrue(updated!!.stabilityDays >= 3.0)
        assertEquals(1, viewModel.uiState.value.classrooms.first().masteredFsrsCardsCount)
        assertEquals(100, viewModel.uiState.value.classrooms.first().weightedOverallProgressPercent)
    }

    @Test
    fun `incremental ingestion RN-22 attaches material to target point without reprocessing closed points`() {
        val viewModel = ClassroomsViewModel()
        val unit1 = SyllabusUnitUiModel(
            unitId = "u-1",
            unitNumber = 1,
            unitTitle = "Unidad I",
            points = listOf(
                SyllabusPointUiModel(
                    pointId = "p-1.1",
                    pointCode = "1.1",
                    title = "Punto Cerrado Previo",
                    synthesizedMarkdownBlocks = listOf("Bloque Consolidado 1.1"),
                    linkedMaterialNames = listOf("fuente_original.pdf"),
                ),
                SyllabusPointUiModel(
                    pointId = "p-1.2",
                    pointCode = "1.2.a ✦",
                    title = "Subpunto por Densidad Teórica",
                    isSyntheticDensityNode = true,
                    synthesizedMarkdownBlocks = listOf("Bloque Consolidado 1.2.a"),
                    linkedMaterialNames = listOf("fuente_original.pdf"),
                ),
            ),
        )

        viewModel.setClassroomsCatalog(
            summaries = listOf(
                ClassroomSummaryUiModel(
                    catedraId = "cat-01",
                    displayTitle = "Aula Incremental",
                    modality = ClassroomModality.CATEDRA_PROGRAMA,
                    programaYear = 2026,
                ),
            ),
            unitsMap = mapOf("cat-01" to listOf(unit1)),
        )
        viewModel.onSelectClassroomForDetail("cat-01")

        val applied = viewModel.onSubmitIncrementalIngestion(
            IncrementalIngestionDraftModel(
                targetCatedraId = "cat-01",
                targetUnitId = "u-1",
                targetPointCode = "1.2.a ✦",
                newMaterialFileName = "anexo_actualizado.pdf",
                startPhysicalPage = 1,
                endPhysicalPage = 12,
            ),
        )
        assertTrue(applied)

        val pointsAfter = viewModel.uiState.value.selectedClassroomUnits.first().points
        assertFalse(pointsAfter[0].isPendingIncrementalProcessing)
        assertEquals(listOf("Bloque Consolidado 1.1"), pointsAfter[0].synthesizedMarkdownBlocks)

        assertTrue(pointsAfter[1].isPendingIncrementalProcessing)
        assertTrue(pointsAfter[1].linkedMaterialNames.contains("anexo_actualizado.pdf"))
    }
}
