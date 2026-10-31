/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/database/src/main/kotlin/com/fenix/studio/core/database/entity/SyllabusPointEntity.kt
 * ================================================================================
 */

package com.fenix.studio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Punto o subpunto por densidad teórica (`N.N.a ✦`) dentro de una unidad (`puntos_programa`, DT-CRIT-01).
 */
@Entity(
    tableName = "puntos_programa",
    foreignKeys = [
        ForeignKey(
            entity = SyllabusUnitEntity::class,
            parentColumns = ["unit_id"],
            childColumns = ["unit_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["unit_id", "point_code"], unique = true),
        Index(value = ["catedra_id", "point_code"]),
    ],
)
data class SyllabusPointEntity(
    @PrimaryKey
    @ColumnInfo(name = "point_id")
    val pointId: String,
    @ColumnInfo(name = "unit_id")
    val unitId: String,
    @ColumnInfo(name = "catedra_id")
    val catedraId: String,
    @ColumnInfo(name = "point_code")
    val pointCode: String,
    @ColumnInfo(name = "point_title")
    val pointTitle: String,
    @ColumnInfo(name = "sequence_order")
    val sequenceOrder: Int,
    @ColumnInfo(name = "is_synthetic_density_subpoint")
    val isSyntheticDensitySubpoint: Boolean = false,
    @ColumnInfo(name = "parent_point_id")
    val parentPointId: String? = null,
    @ColumnInfo(name = "is_developed_in_part_a")
    val isDevelopedInPartA: Boolean = false,
)
