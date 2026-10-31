/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/database/src/main/kotlin/com/fenix/studio/core/database/entity/FsrsCardStateEntity.kt
 * ================================================================================
 */

package com.fenix.studio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Estado matemático local de repaso espaciado FSRS v6 (`fsrs_cards_state`).
 * Vinculado a un bloque atómico de la Parte D (15 preguntas Active Recall) o Parte F (Glosario).
 */
@Entity(
    tableName = "fsrs_cards_state",
    foreignKeys = [
        ForeignKey(
            entity = ProtocolBlockEntity::class,
            parentColumns = ["block_id"],
            childColumns = ["source_block_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["catedra_id", "next_review_epoch_ms"]),
        Index(value = ["source_block_id"], unique = true),
    ],
)
data class FsrsCardStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "card_id")
    val cardId: String,
    @ColumnInfo(name = "catedra_id")
    val catedraId: String,
    @ColumnInfo(name = "unit_id")
    val unitId: String,
    @ColumnInfo(name = "source_block_id")
    val sourceBlockId: String,
    @ColumnInfo(name = "stability")
    val stability: Double = 0.0,
    @ColumnInfo(name = "difficulty")
    val difficulty: Double = 5.0,
    @ColumnInfo(name = "elapsed_days")
    val elapsedDays: Int = 0,
    @ColumnInfo(name = "scheduled_days")
    val scheduledDays: Int = 0,
    @ColumnInfo(name = "reps_count")
    val repsCount: Int = 0,
    @ColumnInfo(name = "lapses_count")
    val lapsesCount: Int = 0,
    @ColumnInfo(name = "last_review_epoch_ms")
    val lastReviewEpochMs: Long = 0L,
    @ColumnInfo(name = "next_review_epoch_ms")
    val nextReviewEpochMs: Long = 0L,
)
