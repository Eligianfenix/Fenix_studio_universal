/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/database/src/main/kotlin/com/fenix/studio/core/database/entity/ProcessingCheckpointEntity.kt
 * ================================================================================
 */

package com.fenix.studio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fenix.studio.core.database.model.ProcessingStatus
import com.fenix.studio.core.database.model.ProtocolSectionType

/**
 * Checkpoint transaccional idempotente (`processing_checkpoints`, DT-ALTA-02).
 * Permite reanudar el procesamiento en < 5 ms tras una suspensión de `PowerKeeper` en Xiaomi HyperOS
 * sin repetir OCR ni duplicar consumo de cuotas de IA.
 */
@Entity(
    tableName = "processing_checkpoints",
    foreignKeys = [
        ForeignKey(
            entity = SyllabusUnitEntity::class,
            parentColumns = ["unit_id"],
            childColumns = ["unit_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["unit_id", "section_type"], unique = true),
        Index(value = ["catedra_id", "status"]),
    ],
)
data class ProcessingCheckpointEntity(
    @PrimaryKey
    @ColumnInfo(name = "checkpoint_id")
    val checkpointId: String,
    @ColumnInfo(name = "catedra_id")
    val catedraId: String,
    @ColumnInfo(name = "unit_id")
    val unitId: String,
    @ColumnInfo(name = "section_type")
    val sectionType: ProtocolSectionType,
    @ColumnInfo(name = "status")
    val status: ProcessingStatus,
    @ColumnInfo(name = "last_completed_page")
    val lastCompletedPage: Int = 0,
    @ColumnInfo(name = "last_completed_block_order")
    val lastCompletedBlockOrder: Int = 0,
    @ColumnInfo(name = "partial_payload_hash")
    val partialPayloadHash: String = "",
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)
