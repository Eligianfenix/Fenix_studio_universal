/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/database/src/test/kotlin/com/fenix/studio/core/database/HybridSearchRrfTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.database

import com.fenix.studio.core.database.converter.FenixRoomConverters
import com.fenix.studio.core.database.model.MaterialRoleType
import com.fenix.studio.core.database.rag.ChunkEntity
import com.fenix.studio.core.database.rag.HybridSearchRrfEngine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HybridSearchRrfTest {

    @Test
    fun `HybridSearchRrfEngine prioritizes PRIMARY_DOCTRINE 1_0x over COMPLEMENTARY_SUPPORT 0_75x under RRF k60`() {
        val queryVector = floatArrayOf(1.0f, 0.0f, 0.0f)
        val identicalBlob = FenixRoomConverters.floatArrayToBlob(floatArrayOf(1.0f, 0.0f, 0.0f))

        val complementaryChunk = ChunkEntity(
            rowId = 1L,
            chunkId = "chunk-comp",
            catedraId = "cat-100",
            materialId = "mat-comp",
            unitNumber = 2,
            pointCode = "2.1",
            roleType = MaterialRoleType.COMPLEMENTARY_SUPPORT,
            priorityWeight = MaterialRoleType.COMPLEMENTARY_SUPPORT.defaultPriorityWeight, // 0.75f
            pageNumber = 10,
            sectionHeading = "Termodinámica de sistemas abiertos",
            chunkText = "El equilibrio estacionario conserva el flujo de entropía en el volumen de control.",
            embeddingBlob = identicalBlob,
        )

        val primaryDoctrineChunk = ChunkEntity(
            rowId = 2L,
            chunkId = "chunk-primary",
            catedraId = "cat-100",
            materialId = "mat-prim",
            unitNumber = 2,
            pointCode = "2.1",
            roleType = MaterialRoleType.PRIMARY_DOCTRINE,
            priorityWeight = MaterialRoleType.PRIMARY_DOCTRINE.defaultPriorityWeight, // 1.0f
            pageNumber = 4,
            sectionHeading = "Termodinámica de sistemas abiertos",
            chunkText = "El equilibrio estacionario conserva el flujo de entropía en el volumen de control.",
            embeddingBlob = identicalBlob,
        )

        val results = HybridSearchRrfEngine.rankCandidates(
            candidates = listOf(complementaryChunk, primaryDoctrineChunk),
            queryText = "equilibrio estacionario flujo entropía",
            queryEmbedding = queryVector,
            catedraId = "cat-100",
            unitNumber = 2,
            pointCode = "2.1",
            rrfK = 60,
        )

        assertEquals(2, results.size)
        assertEquals("chunk-primary", results[0].chunk.chunkId)
        assertEquals("chunk-comp", results[1].chunk.chunkId)
        assertTrue(results[0].weightedFinalScore > results[1].weightedFinalScore)
    }

    @Test
    fun `HybridSearchRrfEngine strictly filters out chunks from other units or syllabus points`() {
        val queryVector = floatArrayOf(0.8f, 0.6f, 0.0f)
        val matchingBlob = FenixRoomConverters.floatArrayToBlob(queryVector)

        val targetPointChunk = ChunkEntity(
            rowId = 10L,
            chunkId = "chunk-unit2-pt2.1",
            catedraId = "cat-100",
            materialId = "mat-1",
            unitNumber = 2,
            pointCode = "2.1",
            roleType = MaterialRoleType.PRIMARY_DOCTRINE,
            pageNumber = 15,
            sectionHeading = "Sección 2.1",
            chunkText = "Análisis cuantitativo de variables instrumentales y convergencia asintótica.",
            embeddingBlob = matchingBlob,
        )

        val foreignUnitChunk = ChunkEntity(
            rowId = 11L,
            chunkId = "chunk-unit5-pt5.3",
            catedraId = "cat-100",
            materialId = "mat-1",
            unitNumber = 5,
            pointCode = "5.3",
            roleType = MaterialRoleType.PRIMARY_DOCTRINE,
            pageNumber = 88,
            sectionHeading = "Sección 5.3",
            chunkText = "Análisis cuantitativo de variables instrumentales y convergencia asintótica.",
            embeddingBlob = matchingBlob,
        )

        val results = HybridSearchRrfEngine.rankCandidates(
            candidates = listOf(targetPointChunk, foreignUnitChunk),
            queryText = "variables instrumentales convergencia asintótica",
            queryEmbedding = queryVector,
            catedraId = "cat-100",
            unitNumber = 2,
            pointCode = "2.1",
        )

        assertEquals(1, results.size)
        assertEquals("chunk-unit2-pt2.1", results.single().chunk.chunkId)
    }
}
