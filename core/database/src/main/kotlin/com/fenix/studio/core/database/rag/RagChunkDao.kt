/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/database/src/main/kotlin/com/fenix/studio/core/database/rag/RagChunkDao.kt
 * ================================================================================
 */

package com.fenix.studio.core.database.rag

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RagChunkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<ChunkEntity>)

    /**
     * Búsqueda léxica FTS acotada estrictamente al anclaje curricular (`catedraId`, `unitNumber`, `pointCode`).
     */
    @Query(
        """
        SELECT chunks_local.*
        FROM chunks_local
        JOIN chunks_fts ON chunks_local.rowid = chunks_fts.rowid
        WHERE chunks_fts MATCH :ftsQuery
          AND chunks_local.catedra_id = :catedraId
          AND (:unitNumber IS NULL OR chunks_local.unit_number = :unitNumber)
          AND (:pointCode IS NULL OR chunks_local.point_code = :pointCode)
        """,
    )
    suspend fun searchChunksLexicalFts(
        ftsQuery: String,
        catedraId: String,
        unitNumber: Int?,
        pointCode: String?,
    ): List<ChunkEntity>

    /**
     * Recupera los fragmentos candidatos estrictamente anclados a la unidad y punto curricular
     * para el cálculo vectorial y fusión RRF sin contaminación cruzada.
     */
    @Query(
        """
        SELECT * FROM chunks_local
        WHERE catedra_id = :catedraId
          AND (:unitNumber IS NULL OR unit_number = :unitNumber)
          AND (:pointCode IS NULL OR point_code = :pointCode)
        """,
    )
    suspend fun getAnchoredCandidateChunks(
        catedraId: String,
        unitNumber: Int?,
        pointCode: String?,
    ): List<ChunkEntity>
}
