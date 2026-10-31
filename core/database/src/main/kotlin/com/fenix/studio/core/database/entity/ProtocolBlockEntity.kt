/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/database/src/main/kotlin/com/fenix/studio/core/database/entity/ProtocolBlockEntity.kt
 * ================================================================================
 */

package com.fenix.studio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fenix.studio.core.database.model.EpistemicStateDb
import com.fenix.studio.core.database.model.OntologicalCategoryDb
import com.fenix.studio.core.database.model.ProtocolSectionType

/**
 * Bloque atómico del Protocolo Fénix v2.1 / v3.0 (`protocol_blocks`).
 * Cada párrafo, cita, caso o término del glosario se persiste individualmente con techo estricto
 * de 12 KB (`MAX_BLOCK_CHARS = 12_288`) para erradicar el desbordamiento de `android.database.CursorWindow` (2 MB).
 */
@Entity(
    tableName = "protocol_blocks",
    foreignKeys = [
        ForeignKey(
            entity = SyllabusUnitEntity::class,
            parentColumns = ["unit_id"],
            childColumns = ["unit_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["unit_id", "section_type", "block_order"], unique = true),
        Index(value = ["catedra_id", "point_code"]),
    ],
)
data class ProtocolBlockEntity(
    @PrimaryKey
    @ColumnInfo(name = "block_id")
    val blockId: String,
    @ColumnInfo(name = "catedra_id")
    val catedraId: String,
    @ColumnInfo(name = "unit_id")
    val unitId: String,
    @ColumnInfo(name = "point_code")
    val pointCode: String,
    @ColumnInfo(name = "section_type")
    val sectionType: ProtocolSectionType,
    @ColumnInfo(name = "block_order")
    val blockOrder: Int,
    @ColumnInfo(name = "epistemic_state")
    val epistemicState: EpistemicStateDb = EpistemicStateDb.FUENTE,
    @ColumnInfo(name = "ontological_category")
    val ontologicalCategory: OntologicalCategoryDb = OntologicalCategoryDb.EMPIRICAL_FACT,
    @ColumnInfo(name = "raw_text")
    val rawText: String,
    @ColumnInfo(name = "source_citation")
    val sourceCitation: String? = null,
    @ColumnInfo(name = "is_read_by_student")
    val isReadByStudent: Boolean = false,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
) {
    init {
        require(rawText.isNotBlank()) {
            "El bloque atómico del protocolo no puede estar vacío."
        }
        require(rawText.length <= MAX_BLOCK_CHARS) {
            "Violación de atomicidad CursorWindow: el bloque tiene ${rawText.length} caracteres (máximo permitido: $MAX_BLOCK_CHARS)."
        }
    }

    companion object {
        const val MAX_BLOCK_CHARS: Int = 12_288
    }
}
