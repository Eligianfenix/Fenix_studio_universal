/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:55:10 -03:00
 * ARCHIVO: feature/processor/src/test/kotlin/com/fenix/studio/feature/processor/ProcessorWorkflowTest.kt
 * ================================================================================
 */

package com.fenix.studio.feature.processor

import com.fenix.studio.core.database.model.ClassroomModality
import com.fenix.studio.core.document.matching.MatchConfidence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProcessorWorkflowTest {

    @Test
    fun `CATEDRA_PROGRAMA enforces mandatory programaYear RN-18 while DOCUMENTO_AISLADO and PROYECTO_GUIADO do not`() {
        val viewModel = ProcessorViewModel()
        viewModel.onSelectModality(ClassroomModality.CATEDRA_PROGRAMA)
        viewModel.onSubjectOrProjectTitleChanged("Espacio Curricular Genérico")

        assertFalse(viewModel.onConfirmConfiguration())
        assertTrue(viewModel.uiState.value.validationErrorMessage?.contains("RN-18") == true)

        viewModel.onProgramaYearChanged("2026")
        assertTrue(viewModel.onConfirmConfiguration())

        val freeViewModel = ProcessorViewModel()
        freeViewModel.onSelectModality(ClassroomModality.DOCUMENTO_AISLADO)
        freeViewModel.onSubjectOrProjectTitleChanged("Monografía Libre Sin Programa")
        assertTrue(freeViewModel.onConfirmConfiguration())
    }

    @Test
    fun `1-click orphan direct ingestion RN-26 and page offset RN-09 update curricular matrix cleanly`() {
        val viewModel = ProcessorViewModel()
        viewModel.setCurricularMatrixItems(
            listOf(
                CurricularMatrixItemUi(
                    unitId = "u-1",
                    unitNumber = 1,
                    unitTitle = "Introducción Metodológica",
                    matchedFileId = null,
                    matchedFileName = null,
                    confidenceScore = 0.25,
                    confidenceBand = MatchConfidence.LOW_RED_ORPHAN,
                    startPhysicalPage = 15,
                    printedPageOffset = 0,
                ),
            ),
        )

        viewModel.onUpdatePageOffset("u-1", -12)
        assertEquals(3, viewModel.uiState.value.matrixItems.first().effectivePrintedStartPage)

        viewModel.onConvertOrphanToDirectStudyUnit(
            unitId = "u-1",
            orphanFileId = "file-orphan-99",
            orphanFileName = "apunte_complementario.pdf",
        )
        val updated = viewModel.uiState.value.matrixItems.first()
        assertTrue(updated.isDirectlyIngestedOrphan)
        assertEquals(MatchConfidence.HIGH_GREEN, updated.confidenceBand)
        assertEquals("file-orphan-99", updated.matchedFileId)
    }

    @Test
    fun `early reading RN-06 opens Unit 1 immediately while Unit 2 remains queued in P-06`() {
        val viewModel = ProcessorViewModel()
        viewModel.setCurricularMatrixItems(
            listOf(
                CurricularMatrixItemUi(
                    unitId = "u-1",
                    unitNumber = 1,
                    unitTitle = "Módulo I",
                    matchedFileId = "f-1",
                    matchedFileName = "m1.pdf",
                    confidenceScore = 0.92,
                    confidenceBand = MatchConfidence.HIGH_GREEN,
                ),
                CurricularMatrixItemUi(
                    unitId = "u-2",
                    unitNumber = 2,
                    unitTitle = "Módulo II",
                    matchedFileId = "f-2",
                    matchedFileName = "m2.pdf",
                    confidenceScore = 0.89,
                    confidenceBand = MatchConfidence.HIGH_GREEN,
                ),
            ),
        )
        viewModel.onEnqueueAllUnitsForProcessing(classroomId = "cls-1")

        assertFalse(viewModel.onOpenEarlyReadingForCompletedUnit("u-1"))

        viewModel.onMarkUnitCompletedWithContent("u-1", "# Desarrollo Exhaustivo Módulo I")
        assertTrue(viewModel.onOpenEarlyReadingForCompletedUnit("u-1"))
        assertEquals("u-1", viewModel.uiState.value.activeEarlyReadingUnitId)
        assertFalse(viewModel.onOpenEarlyReadingForCompletedUnit("u-2"))
    }
}
