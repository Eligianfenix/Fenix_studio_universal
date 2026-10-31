/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/database/src/main/kotlin/com/fenix/studio/core/database/dao/ProtocolBlockAndCheckpointDao.kt
 * ================================================================================
 */

package com.fenix.studio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.fenix.studio.core.database.entity.FsrsCardStateEntity
import com.fenix.studio.core.database.entity.ProcessingCheckpointEntity
import com.fenix.studio.core.database.entity.ProtocolBlockEntity
import com.fenix.studio.core.database.model.ProtocolSectionType

@Dao
interface ProtocolBlockAndCheckpointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProtocolBlocks(blocks: List<ProtocolBlockEntity>)

    /**
     * Consulta paginada por ventana (`limit` y `offset`) para alimentar Paging 3 en P-08 y P-09,
     * manteniendo en RAM únicamente 12-15 párrafos visibles y jamás superando el límite de `CursorWindow` (2 MB).
     */
    @Query(
        """
        SELECT * FROM protocol_blocks
        WHERE unit_id = :unitId AND section_type = :sectionType
        ORDER BY block_order ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getBlocksPaged(
        unitId: String,
        sectionType: ProtocolSectionType,
        limit: Int,
        offset: Int,
    ): List<ProtocolBlockEntity>

    @Query(
        """
        SELECT COUNT(*) FROM protocol_blocks
        WHERE unit_id = :unitId AND section_type = :sectionType
        """,
    )
    suspend fun countBlocksForSection(
        unitId: String,
        sectionType: ProtocolSectionType,
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheckpoint(checkpoint: ProcessingCheckpointEntity)

    @Query(
        """
        SELECT * FROM processing_checkpoints
        WHERE unit_id = :unitId AND section_type = :sectionType
        LIMIT 1
        """,
    )
    suspend fun getCheckpoint(
        unitId: String,
        sectionType: ProtocolSectionType,
    ): ProcessingCheckpointEntity?

    @Query(
        """
        SELECT * FROM processing_checkpoints
        WHERE catedra_id = :catedraId
        ORDER BY updated_at_epoch_ms ASC
        """,
    )
    suspend fun getCheckpointsForClassroom(catedraId: String): List<ProcessingCheckpointEntity>

    /**
     * Transacción atómica idempotente (DT-ALTA-02): persiste los bloques generados de una sección
     * y actualiza el checkpoint correspondiente en una única operación ACID de SQLite.
     */
    @Transaction
    suspend fun commitSectionBlocksAndMarkCheckpoint(
        blocks: List<ProtocolBlockEntity>,
        checkpoint: ProcessingCheckpointEntity,
    ) {
        upsertProtocolBlocks(blocks)
        upsertCheckpoint(checkpoint)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFsrsCards(cards: List<FsrsCardStateEntity>)

    @Query(
        """
        SELECT * FROM fsrs_cards_state
        WHERE catedra_id = :catedraId AND next_review_epoch_ms <= :nowEpochMs
        ORDER BY next_review_epoch_ms ASC
        LIMIT :limit
        """,
    )
    suspend fun getDueFsrsCards(
        catedraId: String,
        nowEpochMs: Long,
        limit: Int = 50,
    ): List<FsrsCardStateEntity>
}
