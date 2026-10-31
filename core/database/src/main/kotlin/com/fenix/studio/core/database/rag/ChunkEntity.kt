/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/database/src/main/kotlin/com/fenix/studio/core/database/rag/ChunkEntity.kt
 * ================================================================================
 */

package com.fenix.studio.core.database.rag

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fenix.studio.core.database.entity.ClassroomEntity
import com.fenix.studio.core.database.entity.MaterialDocumentEntity
import com.fenix.studio.core.database.model.MaterialRoleType

/**
 * Fragmento documental indexado para el Motor de Búsqueda Híbrida Local (`chunks_local`, MF-1.2).
 * Incluye anclaje curricular estricto (`catedraId`, `unitNumber`, `pointCode`), ponderación jerárquica
 * (`1.0f` Doctrina Troncal vs `0.75f` Material Complementario) y vector `embeddingBlob` IEEE-754 Little-Endian.
 */
@Entity(
    tableName = "chunks_local",
    foreignKeys = [
        ForeignKey(
            entity = ClassroomEntity::class,
            parentColumns = ["catedra_id"],
            childColumns = ["catedra_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MaterialDocumentEntity::class,
            parentColumns = ["material_id"],
            childColumns = ["material_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["chunk_id"], unique = true),
        Index(value = ["catedra_id", "unit_number", "point_code"]),
        Index(value = ["material_id"]),
    ],
)
data class ChunkEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    val rowId: Long = 0L,
    @ColumnInfo(name = "chunk_id")
    val chunkId: String,
    @ColumnInfo(name = "catedra_id")
    val catedraId: String,
    @ColumnInfo(name = "material_id")
    val materialId: String,
    @ColumnInfo(name = "unit_number")
    val unitNumber: Int,
    @ColumnInfo(name = "point_code")
    val pointCode: String,
    @ColumnInfo(name = "role_type")
    val roleType: MaterialRoleType = MaterialRoleType.PRIMARY_DOCTRINE,
    @ColumnInfo(name = "priority_weight")
    val priorityWeight: Float = roleType.defaultPriorityWeight,
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    @ColumnInfo(name = "section_heading")
    val sectionHeading: String,
    @ColumnInfo(name = "chunk_text")
    val chunkText: String,
    @ColumnInfo(name = "embedding_blob", typeAffinity = ColumnInfo.BLOB)
    val embeddingBlob: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChunkEntity) return false
        return rowId == other.rowId &&
            chunkId == other.chunkId &&
            catedraId == other.catedraId &&
            materialId == other.materialId &&
            unitNumber == other.unitNumber &&
            pointCode == other.pointCode &&
            roleType == other.roleType &&
            priorityWeight == other.priorityWeight &&
            pageNumber == other.pageNumber &&
            sectionHeading == other.sectionHeading &&
            chunkText == other.chunkText &&
            embeddingBlob.contentEquals(other.embeddingBlob)
    }

    override fun hashCode(): Int {
        var result = rowId.hashCode()
        result = 31 * result + chunkId.hashCode()
        result = 31 * result + catedraId.hashCode()
        result = 31 * result + materialId.hashCode()
        result = 31 * result + unitNumber
        result = 31 * result + pointCode.hashCode()
        result = 31 * result + roleType.hashCode()
        result = 31 * result + priorityWeight.hashCode()
        result = 31 * result + pageNumber
        result = 31 * result + sectionHeading.hashCode()
        result = 31 * result + chunkText.hashCode()
        result = 31 * result + embeddingBlob.contentHashCode()
        return result
    }
}
