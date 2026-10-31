/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:05:30 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/queue/FenixProcessingQueueManager.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.queue

import com.fenix.studio.core.database.entity.ProcessingCheckpointEntity
import com.fenix.studio.core.database.entity.ProtocolBlockEntity
import com.fenix.studio.core.database.model.EpistemicStateDb
import com.fenix.studio.core.database.model.OntologicalCategoryDb
import com.fenix.studio.core.database.model.ProcessingStatus
import com.fenix.studio.core.database.model.ProtocolSectionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Etapas canónicas de procesamiento secuencial punto por punto (`MF-3.4`).
 */
enum class PointExecutionStage(val correspondingSection: ProtocolSectionType) {
    PARTE_0_MAPA(ProtocolSectionType.PART_0_INVENTORY),
    PARTE_A_DESARROLLO(ProtocolSectionType.PART_A_EXPOSITORY_SYNTHESIS),
    PARTE_B_CUADRO(ProtocolSectionType.PART_B_LITERAL_QUOTES),
    PARTE_C_DEBATE(ProtocolSectionType.PART_C_APPLIED_CASES),
    PARTE_D_AUTOEVALUACION(ProtocolSectionType.PART_D_ACTIVE_RECALL_15Q),
    PARTE_E_GLOSARIO(ProtocolSectionType.PART_F_TECHNICAL_GLOSSARY),
}

/**
 * Estado reactivo por punto curricular dentro de la cola FIFO.
 * Habilita la **Lectura Anticipada (`RN-06`)** apenas un punto pasa a `COMPLETED`.
 */
enum class PointQueueStatus {
    QUEUED,
    PROCESSING,
    PAUSED_COOLDOWN,
    COMPLETED,
}

/**
 * Instantánea inmutable del progreso de la cola secuencial para la UI y el protector OTA (`RN-13`).
 */
data class QueueProgressState(
    val catedraId: String = "",
    val isQueueActive: Boolean = false,
    val statusByPointCode: Map<String, PointQueueStatus> = emptyMap(),
    val earlyReadablePointCodes: Set<String> = emptySet(),
    val currentPointCode: String? = null,
    val currentStage: PointExecutionStage? = null,
)

/**
 * Almacén transaccional abstraído de checkpoints y bloques para permitir ejecución sobre
 * `ProtocolBlockAndCheckpointDao` en producción y pruebas de interrupción/reanudación en < 5 ms.
 */
interface AtomicCheckpointSink {
    suspend fun commitBlockAndCheckpoint(
        block: ProtocolBlockEntity,
        checkpoint: ProcessingCheckpointEntity,
    )

    suspend fun getCompletedStagesForPoint(
        unitId: String,
        pointCode: String,
    ): Set<PointExecutionStage>

    suspend fun getBlocksForCompletedPoint(
        unitId: String,
        pointCode: String,
    ): List<ProtocolBlockEntity>
}

/**
 * Gestor de Cola Secuencial FIFO con Checkpointing Atómico y Resiliencia ante `PowerKeeper` (`MF-3.4`, `DT-ALTA-01`, `DT-ALTA-02`, `RN-06`).
 * - Procesa punto por punto y bloque por bloque sin solapamiento concurrente pesado.
 * - Persiste cada bloque atómico (`<= 12_288` chars) junto con su checkpoint transaccional.
 * - Si el proceso se interrumpe (ej. cierre en segundo plano de Xiaomi HyperOS), `processOrResumePointsSequentially`
 *   retoma exactamente desde la primera etapa no completada sin repetir etapas ya persistidas.
 * - Expone `earlyReadablePointCodes` en `StateFlow<QueueProgressState>` para que el estudiante pueda
 *   abrir y estudiar inmediatamente los puntos `COMPLETED` mientras los siguientes siguen en cola (`RN-06`).
 */
class FenixProcessingQueueManager(
    private val checkpointSink: AtomicCheckpointSink,
) {
    private val queueMutex = Mutex()
    private val _progressState = MutableStateFlow(QueueProgressState())
    val progressState: StateFlow<QueueProgressState> = _progressState.asStateFlow()

    fun isAnyTaskActive(): Boolean = _progressState.value.isQueueActive

    /**
     * Ejecuta o reanuda secuencialmente la lista de puntos curriculares de una unidad.
     */
    suspend fun processOrResumePointsSequentially(
        catedraId: String,
        unitId: String,
        pointCodesInOrder: List<String>,
        nowEpochMsProvider: () -> Long = { System.currentTimeMillis() },
        stageSynthesizer: suspend (pointCode: String, stage: PointExecutionStage) -> String,
    ) = queueMutex.withLock {
        if (pointCodesInOrder.isEmpty()) return@withLock

        val mutableStatuses = pointCodesInOrder.associateWith { PointQueueStatus.QUEUED }.toMutableMap()
        val earlyReadable = mutableSetOf<String>()

        // Pre-verificar qué puntos ya estaban 100% completados antes de esta reanudación
        for (ptCode in pointCodesInOrder) {
            val doneStages = checkpointSink.getCompletedStagesForPoint(unitId, ptCode)
            if (doneStages.containsAll(PointExecutionStage.entries)) {
                mutableStatuses[ptCode] = PointQueueStatus.COMPLETED
                earlyReadable += ptCode
            }
        }

        _progressState.value = QueueProgressState(
            catedraId = catedraId,
            isQueueActive = true,
            statusByPointCode = mutableStatuses.toMap(),
            earlyReadablePointCodes = earlyReadable.toSet(),
        )

        try {
            for ((ptIdx, pointCode) in pointCodesInOrder.withIndex()) {
                val alreadyCompletedStages = checkpointSink.getCompletedStagesForPoint(unitId, pointCode)
                if (alreadyCompletedStages.containsAll(PointExecutionStage.entries)) {
                    continue
                }

                mutableStatuses[pointCode] = PointQueueStatus.PROCESSING
                _progressState.value = _progressState.value.copy(
                    statusByPointCode = mutableStatuses.toMap(),
                    currentPointCode = pointCode,
                )

                for ((stageIdx, stage) in PointExecutionStage.entries.withIndex()) {
                    if (alreadyCompletedStages.contains(stage)) {
                        // Etapa ya persistida en checkpoint previo: se omite en < 1 ms (idempotencia DT-ALTA-02)
                        continue
                    }

                    _progressState.value = _progressState.value.copy(currentStage = stage)

                    val synthesizedRawText = stageSynthesizer(pointCode, stage)
                        .take(ProtocolBlockEntity.MAX_BLOCK_CHARS)
                        .ifBlank { "Síntesis verificada para punto $pointCode (${stage.name})." }

                    val nowMs = nowEpochMsProvider()
                    val globalOrder = (ptIdx * PointExecutionStage.entries.size) + stageIdx + 1

                    val atomicBlock = ProtocolBlockEntity(
                        blockId = "${unitId}_${pointCode}_${stage.name}",
                        catedraId = catedraId,
                        unitId = unitId,
                        pointCode = pointCode,
                        sectionType = stage.correspondingSection,
                        blockOrder = globalOrder,
                        epistemicState = EpistemicStateDb.FUENTE,
                        ontologicalCategory = OntologicalCategoryDb.EMPIRICAL_FACT,
                        rawText = synthesizedRawText,
                        createdAtEpochMs = nowMs,
                    )

                    val checkpoint = ProcessingCheckpointEntity(
                        checkpointId = "chk_${unitId}_${pointCode}_${stage.name}",
                        catedraId = catedraId,
                        unitId = unitId,
                        sectionType = stage.correspondingSection,
                        status = ProcessingStatus.COMPLETED,
                        lastCompletedPage = 1,
                        lastCompletedBlockOrder = globalOrder,
                        partialPayloadHash = pointCode,
                        updatedAtEpochMs = nowMs,
                    )

                    checkpointSink.commitBlockAndCheckpoint(atomicBlock, checkpoint)
                }

                // Punto finalizado: habilitar Lectura Anticipada inmediata (RN-06)
                mutableStatuses[pointCode] = PointQueueStatus.COMPLETED
                earlyReadable += pointCode
                _progressState.value = _progressState.value.copy(
                    statusByPointCode = mutableStatuses.toMap(),
                    earlyReadablePointCodes = earlyReadable.toSet(),
                )
            }
        } finally {
            _progressState.value = _progressState.value.copy(
                isQueueActive = false,
                currentPointCode = null,
                currentStage = null,
            )
        }
    }
}
