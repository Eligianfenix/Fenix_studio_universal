/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/database/src/main/kotlin/com/fenix/studio/core/database/entity/ProviderModelEntity.kt
 * ================================================================================
 */

package com.fenix.studio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Modelo de lenguaje descubierto dinámicamente vía `GET /models` (`provider_models`, RN-08).
 * Suprime cualquier lista estática de modelos en el código fuente.
 */
@Entity(
    tableName = "provider_models",
    primaryKeys = ["provider_id", "model_id"],
    indices = [
        Index(value = ["provider_id", "is_selected"]),
    ],
)
data class ProviderModelEntity(
    @ColumnInfo(name = "provider_id")
    val providerId: String,
    @ColumnInfo(name = "model_id")
    val modelId: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "context_window_tokens")
    val contextWindowTokens: Int,
    @ColumnInfo(name = "supports_vision")
    val supportsVision: Boolean = false,
    @ColumnInfo(name = "supports_json_schema")
    val supportsJsonSchema: Boolean = false,
    @ColumnInfo(name = "is_selected")
    val isSelected: Boolean = false,
    @ColumnInfo(name = "discovered_at_epoch_ms")
    val discoveredAtEpochMs: Long,
)
