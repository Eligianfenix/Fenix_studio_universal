/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/database/src/test/kotlin/com/fenix/studio/core/database/FenixDatabaseSchemaAndBlocksTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.database

import com.fenix.studio.core.database.converter.FenixRoomConverters
import com.fenix.studio.core.database.entity.ClassroomEntity
import com.fenix.studio.core.database.entity.PointMaterialCrossRef
import com.fenix.studio.core.database.entity.ProtocolBlockEntity
import com.fenix.studio.core.database.model.ClassroomLifecycleState
import com.fenix.studio.core.database.model.ClassroomModality
import com.fenix.studio.core.database.model.EpistemicStateDb
import com.fenix.studio.core.database.model.OntologicalCategoryDb
import com.fenix.studio.core.database.model.ProtocolSectionType
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FenixDatabaseSchemaAndBlocksTest {

    @Test
    fun `ClassroomEntity enforces RN-18 RN-19 and RN-20 invariants across the 3 sovereign modalities`() {
        val validCatedra = ClassroomEntity(
            catedraId = "cat-01",
            displayTitle = "Asignatura Experimental I",
            normalizedTitle = "asignatura experimental i",
            modality = ClassroomModality.CATEDRA_PROGRAMA,
            programaYear = 2026,
            createdAtEpochMs = 1_000L,
            updatedAtEpochMs = 1_000L,
        )
        assertEquals(2026, validCatedra.programaYear)

        // RN-18: CATEDRA_PROGRAMA sin programaYear debe fallar
        assertThrows(IllegalArgumentException::class.java) {
            ClassroomEntity(
                catedraId = "cat-invalid",
                displayTitle = "Asignatura Sin Año",
                normalizedTitle = "asignatura sin ano",
                modality = ClassroomModality.CATEDRA_PROGRAMA,
                programaYear = null,
                createdAtEpochMs = 1_000L,
                updatedAtEpochMs = 1_000L,
            )
        }

        // RN-20: PROYECTO_GUIADO exige instruccionesPrompt
        val validProyecto = ClassroomEntity(
            catedraId = "proy-01",
            displayTitle = "Proyecto de Investigación Aplicada",
            normalizedTitle = "proyecto de investigacion aplicada",
            modality = ClassroomModality.PROYECTO_GUIADO,
            instruccionesPrompt = "Diseñar prototipo modular con validación empírica en 4 etapas.",
            createdAtEpochMs = 1_000L,
            updatedAtEpochMs = 1_000L,
        )
        assertEquals(ClassroomModality.PROYECTO_GUIADO, validProyecto.modality)

        // RN-19: DOCUMENTO_AISLADO exige isStandaloneDocument = true
        val validStandalone = ClassroomEntity(
            catedraId = "doc-01",
            displayTitle = "Artículo Monográfico Independiente",
            normalizedTitle = "articulo monografico independiente",
            modality = ClassroomModality.DOCUMENTO_AISLADO,
            lifecycleState = ClassroomLifecycleState.SEALED_EPHEMERAL,
            isStandaloneDocument = true,
            createdAtEpochMs = 1_000L,
            updatedAtEpochMs = 1_000L,
        )
        assertEquals(ClassroomLifecycleState.SEALED_EPHEMERAL, validStandalone.lifecycleState)
    }

    @Test
    fun `ProtocolBlockEntity enforces 12 KB CursorWindow safety ceiling`() {
        val safeText = "Párrafo académico auditado. ".repeat(200) // ~5.6 KB
        val validBlock = ProtocolBlockEntity(
            blockId = "blk-1",
            catedraId = "cat-01",
            unitId = "unit-01",
            pointCode = "1.1",
            sectionType = ProtocolSectionType.PART_A_EXPOSITORY_SYNTHESIS,
            blockOrder = 1,
            epistemicState = EpistemicStateDb.FUENTE,
            ontologicalCategory = OntologicalCategoryDb.EMPIRICAL_FACT,
            rawText = safeText,
            createdAtEpochMs = 1_000L,
        )
        assertEquals(1, validBlock.blockOrder)

        val oversizedText = "X".repeat(ProtocolBlockEntity.MAX_BLOCK_CHARS + 1)
        assertThrows(IllegalArgumentException::class.java) {
            ProtocolBlockEntity(
                blockId = "blk-oversized",
                catedraId = "cat-01",
                unitId = "unit-01",
                pointCode = "1.1",
                sectionType = ProtocolSectionType.PART_A_EXPOSITORY_SYNTHESIS,
                blockOrder = 2,
                rawText = oversizedText,
                createdAtEpochMs = 1_000L,
            )
        }
    }

    @Test
    fun `FenixRoomConverters round-trips float32 embedding vectors in IEEE-754 Little-Endian`() {
        val originalVector = floatArrayOf(0.125f, -0.875f, 0.0f, 0.99912f, -0.4321f)
        val blob = FenixRoomConverters.floatArrayToBlob(originalVector)
        assertEquals(originalVector.size * Float.SIZE_BYTES, blob.size)

        val restoredVector = FenixRoomConverters.blobToFloatArray(blob)
        assertArrayEquals(originalVector, restoredVector, 1e-6f)
    }

    @Test
    fun `PointMaterialCrossRef validates surgical page boundaries and fuzzy match score`() {
        val validCrossRef = PointMaterialCrossRef(
            pointId = "pt-1.2",
            materialId = "mat-01",
            startPage = 14,
            endPage = 28,
            matchScore = 0.92f,
        )
        assertEquals(14, validCrossRef.startPage)
        assertEquals(28, validCrossRef.endPage)

        assertThrows(IllegalArgumentException::class.java) {
            PointMaterialCrossRef(
                pointId = "pt-1.2",
                materialId = "mat-01",
                startPage = 30,
                endPage = 12,
                matchScore = 0.90f,
            )
        }
    }
}
