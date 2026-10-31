/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/database/src/main/kotlin/com/fenix/studio/core/database/rag/ChunkFtsEntity.kt
 * ================================================================================
 */

package com.fenix.studio.core.database.rag

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/**
 * Tabla virtual Full-Text Search (`chunks_fts`) sincronizada con `ChunkEntity` (`chunks_local`).
 */
@Fts4(contentEntity = ChunkEntity::class)
@Entity(tableName = "chunks_fts")
data class ChunkFtsEntity(
    @ColumnInfo(name = "section_heading")
    val sectionHeading: String,
    @ColumnInfo(name = "chunk_text")
    val chunkText: String,
)
