/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/database/src/main/kotlin/com/fenix/studio/core/database/FenixDatabase.kt
 * ================================================================================
 */

package com.fenix.studio.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fenix.studio.core.database.converter.FenixRoomConverters
import com.fenix.studio.core.database.dao.ClassroomAndSyllabusDao
import com.fenix.studio.core.database.dao.ProtocolBlockAndCheckpointDao
import com.fenix.studio.core.database.dao.ProviderModelDao
import com.fenix.studio.core.database.entity.ClassroomEntity
import com.fenix.studio.core.database.entity.FsrsCardStateEntity
import com.fenix.studio.core.database.entity.MaterialDocumentEntity
import com.fenix.studio.core.database.entity.PointMaterialCrossRef
import com.fenix.studio.core.database.entity.ProcessingCheckpointEntity
import com.fenix.studio.core.database.entity.ProtocolBlockEntity
import com.fenix.studio.core.database.entity.ProviderModelEntity
import com.fenix.studio.core.database.entity.SyllabusPointEntity
import com.fenix.studio.core.database.entity.SyllabusUnitEntity
import com.fenix.studio.core.database.entity.UnitMaterialCrossRef
import com.fenix.studio.core.database.rag.ChunkEntity
import com.fenix.studio.core.database.rag.ChunkFtsEntity
import com.fenix.studio.core.database.rag.RagChunkDao

/**
 * Base de datos relacional normalizada y motor de búsqueda híbrida local de Fenix Studio (`FenixDatabase`).
 */
@Database(
    entities = [
        ClassroomEntity::class,
        SyllabusUnitEntity::class,
        SyllabusPointEntity::class,
        MaterialDocumentEntity::class,
        PointMaterialCrossRef::class,
        UnitMaterialCrossRef::class,
        ProtocolBlockEntity::class,
        ProcessingCheckpointEntity::class,
        ProviderModelEntity::class,
        FsrsCardStateEntity::class,
        ChunkEntity::class,
        ChunkFtsEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(FenixRoomConverters::class)
abstract class FenixDatabase : RoomDatabase() {
    abstract fun classroomAndSyllabusDao(): ClassroomAndSyllabusDao
    abstract fun protocolBlockAndCheckpointDao(): ProtocolBlockAndCheckpointDao
    abstract fun providerModelDao(): ProviderModelDao
    abstract fun ragChunkDao(): RagChunkDao
}
