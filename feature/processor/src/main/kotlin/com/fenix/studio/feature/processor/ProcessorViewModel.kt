/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:54:00 -03:00
 * ARCHIVO: feature/processor/src/main/kotlin/com/fenix/studio/feature/processor/ProcessorViewModel.kt
 * ================================================================================
 */

package com.fenix.studio.feature.processor

import androidx.lifecycle.ViewModel
import com.fenix.studio.core.database.model.ClassroomModality
import com.fenix.studio.core.document.matching.MatchConfidence
import com.fenix.studio.core.document.queue.PointQueueStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel para `P-05`, `P-05b` y `P-06` (`MF-5.1`, `MF-5.2`, `MF-5.3`).
 */
class ProcessorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ProcessorWizardUiState())
    val uiState: StateFlow<ProcessorWizardUiState> = _uiState.asStateFlow()

    fun onSelectModality(modality: ClassroomModality) {
        _uiState.update {
            it.copy(
                modality = modality,
                validationErrorMessage = null,
            )
        }
    }

    fun onSubjectOrProjectTitleChanged(title: String) {
        _uiState.update {
            it.copy(
                subjectOrProjectTitle = title,
                validationErrorMessage = null,
            )
        }
    }

    fun onProgramaYearChanged(yearText: String) {
        _uiState.update {
            it.copy(
                programaYearInput = yearText.filter { ch -> ch.isDigit() }.take(4),
                validationErrorMessage = null,
            )
        }
    }

    /**
     * Valida y confirma `P-05`. En modo `CATEDRA_PROGRAMA`, exige `programaYear` válido (`RN-18`).
     * En modos `DOCUMENTO_AISLADO` (`RN-19`) y `PROYECTO_GUIADO` (`RN-20`), no exige programa oficial.
     */
    fun onConfirmConfiguration(): Boolean {
        val current = _uiState.value
        if (current.subjectOrProjectTitle.isBlank()) {
            _uiState.update {
                it.copy(validationErrorMessage = "El título del espacio académico o documento es obligatorio.")
            }
            return false
        }
        if (current.modality == ClassroomModality.CATEDRA_PROGRAMA) {
            val parsedYear = current.programaYearInput.toIntOrNull()
            if (parsedYear == null || parsedYear !in 1950..2100) {
                _uiState.update {
                    it.copy(
                        validationErrorMessage = "RN-18: En modalidad Cátedra Oficial el año del programa (programa_year) es obligatorio.",
                    )
                }
                return false
            }
        }
        _uiState.update {
            it.copy(
                isConfigurationConfirmed = true,
                validationErrorMessage = null,
            )
        }
        return true
    }

    fun setCurricularMatrixItems(items: List<CurricularMatrixItemUi>) {
        _uiState.update { it.copy(matrixItems = items) }
    }

    /**
     * Ajusta el offset de página impresa vs página física del PDF (`RN-09`) en `P-05b`.
     */
    fun onUpdatePageOffset(unitId: String, newOffset: Int) {
        _uiState.update { current ->
            current.copy(
                matrixItems = current.matrixItems.map { item ->
                    if (item.unitId == unitId) item.copy(printedPageOffset = newOffset) else item
                },
            )
        }
    }

    /**
     * Acción 1-Clic `"Procesar como Apunte Directo"` (`RN-26`) para archivos huérfanos (`LOW_RED_ORPHAN` < 60%):
     * los convierte directamente en unidad procesable autónoma sin obligar al alumno a editar el programa.
     */
    fun onConvertOrphanToDirectStudyUnit(unitId: String, orphanFileId: String, orphanFileName: String) {
        _uiState.update { current ->
            current.copy(
                matrixItems = current.matrixItems.map { item ->
                    if (item.unitId == unitId) {
                        item.copy(
                            matchedFileId = orphanFileId,
                            matchedFileName = orphanFileName,
                            confidenceScore = 1.0,
                            confidenceBand = MatchConfidence.HIGH_GREEN,
                            isDirectlyIngestedOrphan = true,
                        )
                    } else {
                        item
                    }
                },
            )
        }
    }

    /**
     * Encola las unidades de la matriz para el Monitor `P-06`.
     */
    fun onEnqueueAllUnitsForProcessing(classroomId: String) {
        val tasks = _uiState.value.matrixItems.map { item ->
            QueuedUnitMonitorItem(
                unitId = item.unitId,
                classroomId = classroomId,
                unitNumber = item.unitNumber,
                unitTitle = item.unitTitle,
                status = PointQueueStatus.QUEUED,
            )
        }
        _uiState.update { it.copy(queueTasks = tasks) }
    }

    /**
     * Registra la finalización de una unidad en `P-06`, habilitando de inmediato la Lectura Anticipada (`RN-06`)
     * mientras el resto de unidades continúa procesándose en segundo plano.
     */
    fun onMarkUnitCompletedWithContent(unitId: String, generatedAcademicMarkdown: String) {
        val currentTasks = _uiState.value.queueTasks.map { task ->
            if (task.unitId == unitId) {
                task.copy(status = PointQueueStatus.COMPLETED, completedBlocksCount = task.totalBlocksCount)
            } else {
                task
            }
        }
        _uiState.update { current ->
            current.copy(
                queueTasks = currentTasks,
                completedUnitOutputs = current.completedUnitOutputs + (unitId to generatedAcademicMarkdown),
            )
        }
    }

    /**
     * Abre una unidad ya completada en modo Lectura Anticipada (`RN-06`) sin interrumpir la cola activa.
     */
    fun onOpenEarlyReadingForCompletedUnit(unitId: String): Boolean {
        if (!_uiState.value.completedUnitOutputs.containsKey(unitId)) return false
        _uiState.update { it.copy(activeEarlyReadingUnitId = unitId) }
        return true
    }

    fun onTriggerExportFormat(format: SovereignExportFormat) {
        _uiState.update { it.copy(lastExportedFormat = format) }
    }
}
