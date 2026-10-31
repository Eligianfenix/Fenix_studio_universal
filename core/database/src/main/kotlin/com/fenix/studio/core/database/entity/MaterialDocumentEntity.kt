/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/database/src/main/kotlin/com/fenix/studio/core/database/entity/MaterialDocumentEntity.kt
 * ================================================================================
 */

package com.fenix.studio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fenix.studio.core.database.model.MaterialRoleType
import com.fenix.studio.core.database.model.ProcessingStatus

/**
 * Documento bibliográfico o archivo cargado en el aula (`materiales_bibliograficos`).
 * Incluye `sha256Hash` para deduplicación, `pageOffsetDelta` para offset romano/arábigo (RN-09)
 * e indicador `isScannedOnly` para activar el Guardián Pre-Ingesta (RN-05).
 */
@Entity(
    tableName = "materiales_bibliograficos",
    foreignKeys = [
        ForeignKey(
            entity = ClassroomEntity::class,
            parentColumns = ["catedra_id"],
            childColumns = ["catedra_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["catedra_id"]),
        Index(value = ["sha256_hash"]),
    ],
)
data class MaterialDocumentEntity(
    @PrimaryKey
    @ColumnInfo(name = "material_id")
    val materialId: String,
    @ColumnInfo(name = "catedra_id")
    val catedraId: String,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "local_uri")
    val localUri: String,
    @ColumnInfo(name = "sha256_hash")
    val sha256Hash: String,
    @ColumnInfo(name = "role_type")
    val roleType: MaterialRoleType = MaterialRoleType.PRIMARY_DOCTRINE,
    @ColumnInfo(name = "priority_weight")
    val priorityWeight: Float = roleType.defaultPriorityWeight,
    @ColumnInfo(name = "total_pages")
    val totalPages: Int = 1,
    @ColumnInfo(name = "page_offset_delta")
    val pageOffsetDelta: Int = 0,
    @ColumnInfo(name = "is_scanned_only")
    val isScannedOnly: Boolean = false,
    @ColumnInfo(name = "extracted_authors")
    val extractedAuthors: String = "",
    @ColumnInfo(name = "processing_status")
    val processingStatus: ProcessingStatus = ProcessingStatus.PENDING,
    @ColumnInfo(name = "added_at_epoch_ms")
    val addedAtEpochMs: Long,
)
