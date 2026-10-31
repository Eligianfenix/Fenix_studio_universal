/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/database/src/main/kotlin/com/fenix/studio/core/database/entity/SyllabusUnitEntity.kt
 * ================================================================================
 */

package com.fenix.studio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fenix.studio.core.database.model.ProcessingStatus

/**
 * Unidad temática o etapa de proyecto dentro de un aula (`unidades_programa`).
 */
@Entity(
    tableName = "unidades_programa",
    foreignKeys = [
        ForeignKey(
            entity = ClassroomEntity::class,
            parentColumns = ["catedra_id"],
            childColumns = ["catedra_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["catedra_id", "unit_number"], unique = true),
    ],
)
data class SyllabusUnitEntity(
    @PrimaryKey
    @ColumnInfo(name = "unit_id")
    val unitId: String,
    @ColumnInfo(name = "catedra_id")
    val catedraId: String,
    @ColumnInfo(name = "unit_number")
    val unitNumber: Int,
    @ColumnInfo(name = "unit_title")
    val unitTitle: String,
    @ColumnInfo(name = "processing_status")
    val processingStatus: ProcessingStatus = ProcessingStatus.PENDING,
    @ColumnInfo(name = "reading_progress_ratio")
    val readingProgressRatio: Float = 0.0f,
    @ColumnInfo(name = "mastery_score_ratio")
    val masteryScoreRatio: Float = 0.0f,
)
