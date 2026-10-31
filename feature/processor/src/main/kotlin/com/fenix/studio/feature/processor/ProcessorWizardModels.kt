/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:53:40 -03:00
 * ARCHIVO: feature/processor/src/main/kotlin/com/fenix/studio/feature/processor/ProcessorWizardModels.kt
 * ================================================================================
 */

package com.fenix.studio.feature.processor

import androidx.compose.runtime.Immutable
import com.fenix.studio.core.database.model.ClassroomModality
import com.fenix.studio.core.document.matching.MatchConfidence
import com.fenix.studio.core.document.queue.PointQueueStatus

/**
 * Tarea de unidad en la cola del Monitor `P-06`.
 */
@Immutable
data class QueuedUnitMonitorItem(
    val unitId: String,
    val classroomId: String,
    val unitNumber: Int,
    val unitTitle: String,
    val status: PointQueueStatus = PointQueueStatus.QUEUED,
    val completedBlocksCount: Int = 0,
    val totalBlocksCount: Int = 6,
)

/**
 * Representación de una vinculación de unidad en la Matriz Curricular (`P-05b`).
 */
@Immutable
data class CurricularMatrixItemUi(
    val unitId: String,
    val unitNumber: Int,
    val unitTitle: String,
    val matchedFileId: String?,
    val matchedFileName: String?,
    val confidenceScore: Double,
    val confidenceBand: MatchConfidence,
    val startPhysicalPage: Int = 1,
    val endPhysicalPage: Int = 10,
    val printedPageOffset: Int = 0,
    val isDirectlyIngestedOrphan: Boolean = false,
) {
    /**
     * Calcula la página impresa real sumando el offset `RN-09`.
     */
    val effectivePrintedStartPage: Int
        get() = (startPhysicalPage + printedPageOffset).coerceAtLeast(1)
}

/**
 * Formatos de exportación soberana soportados en `P-06` (`MF-3.5` + `MF-8.3`).
 */
enum class SovereignExportFormat {
    DOCX_OPENXML,
    PDF_ACCESSIBLE,
    PPTX_SLIDES,
    MP4_VIDEO_CAPSULE,
}

/**
 * Estado unificado del flujo Procesador Académico:
 * - `P-05`: Configuración y 3 Modalidades Soberanas (`CATEDRA_PROGRAMA` con `programaYear` obligatorio `RN-18`,
 *   `DOCUMENTO_AISLADO` `RN-19` y `PROYECTO_GUIADO` `RN-20`).
 * - `P-05b`: Matriz de Vinculación Curricular con semáforo Verde/Ámbar/Rojo, Offset `RN-09` e Ingesta Directa `RN-26`.
 * - `P-06`: Monitor de Procesamiento por Unidad con Lectura Anticipada (`RN-06`) y exportación multiformato.
 */
@Immutable
data class ProcessorWizardUiState(
    val modality: ClassroomModality = ClassroomModality.CATEDRA_PROGRAMA,
    val subjectOrProjectTitle: String = "",
    val programaYearInput: String = "",
    val validationErrorMessage: String? = null,
    val isConfigurationConfirmed: Boolean = false,
    val matrixItems: List<CurricularMatrixItemUi> = emptyList(),
    val queueTasks: List<QueuedUnitMonitorItem> = emptyList(),
    val completedUnitOutputs: Map<String, String> = emptyMap(),
    val activeEarlyReadingUnitId: String? = null,
    val lastExportedFormat: SovereignExportFormat? = null,
) {
    /**
     * Verifica si la configuración actual cumple las reglas de negocio (`RN-18`, `RN-19`, `RN-20`).
     */
    val isConfigurationValid: Boolean
        get() {
            if (subjectOrProjectTitle.isBlank()) return false
            if (modality == ClassroomModality.CATEDRA_PROGRAMA) {
                val year = programaYearInput.trim().toIntOrNull() ?: return false
                if (year !in 1950..2100) return false
            }
            return true
        }
}
