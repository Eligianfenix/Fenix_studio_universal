/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/database/src/main/kotlin/com/fenix/studio/core/database/entity/ClassroomEntity.kt
 * ================================================================================
 */

package com.fenix.studio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fenix.studio.core.database.model.ClassroomLifecycleState
import com.fenix.studio.core.database.model.ClassroomModality

/**
 * Entidad raíz de un Aula en Fenix Studio (`catedras_local`).
 * Soporta las 3 modalidades soberanas (RN-18, RN-19, RN-20) sin hardcodeo institucional.
 */
@Entity(
    tableName = "catedras_local",
    indices = [
        Index(value = ["normalized_title", "programa_year"]),
        Index(value = ["sim_hash_64"]),
    ],
)
data class ClassroomEntity(
    @PrimaryKey
    @ColumnInfo(name = "catedra_id")
    val catedraId: String,
    @ColumnInfo(name = "display_title")
    val displayTitle: String,
    @ColumnInfo(name = "normalized_title")
    val normalizedTitle: String,
    @ColumnInfo(name = "modality")
    val modality: ClassroomModality,
    @ColumnInfo(name = "lifecycle_state")
    val lifecycleState: ClassroomLifecycleState = ClassroomLifecycleState.ACTIVE,
    @ColumnInfo(name = "programa_year")
    val programaYear: Int? = null,
    @ColumnInfo(name = "instrucciones_prompt")
    val instruccionesPrompt: String? = null,
    @ColumnInfo(name = "is_standalone_document")
    val isStandaloneDocument: Boolean = false,
    @ColumnInfo(name = "dominant_profile_code")
    val dominantProfileCode: String = "P1-HERM",
    @ColumnInfo(name = "profile_weights_json")
    val profileWeightsJson: String = "{}",
    @ColumnInfo(name = "reading_progress_ratio")
    val readingProgressRatio: Float = 0.0f,
    @ColumnInfo(name = "mastery_score_ratio")
    val masteryScoreRatio: Float = 0.0f,
    @ColumnInfo(name = "sim_hash_64")
    val simHash64: Long = 0L,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
) {
    init {
        require(displayTitle.isNotBlank()) {
            "El título del aula no puede estar vacío."
        }
        when (modality) {
            ClassroomModality.CATEDRA_PROGRAMA -> {
                require(programaYear != null && programaYear in 1950..2100) {
                    "RN-18: La modalidad CATEDRA_PROGRAMA exige programaYear válido entre 1950 y 2100."
                }
                require(!isStandaloneDocument) {
                    "Una cátedra con programa no puede marcarse como documento aislado."
                }
            }
            ClassroomModality.PROYECTO_GUIADO -> {
                require(!instruccionesPrompt.isNullOrBlank()) {
                    "RN-20: La modalidad PROYECTO_GUIADO exige instruccionesPrompt no vacías."
                }
            }
            ClassroomModality.DOCUMENTO_AISLADO -> {
                require(isStandaloneDocument) {
                    "RN-19: La modalidad DOCUMENTO_AISLADO exige isStandaloneDocument = true."
                }
            }
        }
    }
}
