/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/database/src/main/kotlin/com/fenix/studio/core/database/entity/PointMaterialCrossRef.kt
 * ================================================================================
 */

package com.fenix.studio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Tabla puente N:M entre Puntos del Programa y Materiales Bibliográficos (`point_material_cross_ref`, DT-CRIT-01).
 * Almacena el rango quirúrgico de páginas (`startPage`..`endPage`) y el puntaje de conciliación difusa (`matchScore`).
 */
@Entity(
    tableName = "point_material_cross_ref",
    primaryKeys = ["point_id", "material_id"],
    foreignKeys = [
        ForeignKey(
            entity = SyllabusPointEntity::class,
            parentColumns = ["point_id"],
            childColumns = ["point_id"],
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
        Index(value = ["point_id"]),
        Index(value = ["material_id"]),
    ],
)
data class PointMaterialCrossRef(
    @ColumnInfo(name = "point_id")
    val pointId: String,
    @ColumnInfo(name = "material_id")
    val materialId: String,
    @ColumnInfo(name = "start_page")
    val startPage: Int = 1,
    @ColumnInfo(name = "end_page")
    val endPage: Int = 1,
    @ColumnInfo(name = "match_score")
    val matchScore: Float = 1.0f,
    @ColumnInfo(name = "is_incremental_addition")
    val isIncrementalAddition: Boolean = false,
) {
    init {
        require(startPage >= 1 && endPage >= startPage) {
            "El rango de páginas quirúrgico debe cumplir 1 <= startPage <= endPage."
        }
        require(matchScore in 0.0f..1.0f) {
            "El puntaje de conciliación difusa debe estar en [0.0, 1.0]."
        }
    }
}
