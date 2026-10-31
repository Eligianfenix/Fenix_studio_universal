/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/database/src/main/kotlin/com/fenix/studio/core/database/entity/UnitMaterialCrossRef.kt
 * ================================================================================
 */

package com.fenix.studio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Tabla puente N:M entre Unidades del Programa y Materiales Bibliográficos (`unit_material_cross_ref`, DT-CRIT-01).
 */
@Entity(
    tableName = "unit_material_cross_ref",
    primaryKeys = ["unit_id", "material_id"],
    foreignKeys = [
        ForeignKey(
            entity = SyllabusUnitEntity::class,
            parentColumns = ["unit_id"],
            childColumns = ["unit_id"],
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
        Index(value = ["unit_id"]),
        Index(value = ["material_id"]),
    ],
)
data class UnitMaterialCrossRef(
    @ColumnInfo(name = "unit_id")
    val unitId: String,
    @ColumnInfo(name = "material_id")
    val materialId: String,
    @ColumnInfo(name = "start_page")
    val startPage: Int = 1,
    @ColumnInfo(name = "end_page")
    val endPage: Int = 1,
    @ColumnInfo(name = "match_confidence")
    val matchConfidence: Float = 1.0f,
)
