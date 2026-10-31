/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/database/src/main/kotlin/com/fenix/studio/core/database/converter/FenixRoomConverters.kt
 * ================================================================================
 */

package com.fenix.studio.core.database.converter

import androidx.room.TypeConverter
import com.fenix.studio.core.database.model.ClassroomLifecycleState
import com.fenix.studio.core.database.model.ClassroomModality
import com.fenix.studio.core.database.model.EpistemicStateDb
import com.fenix.studio.core.database.model.MaterialRoleType
import com.fenix.studio.core.database.model.OntologicalCategoryDb
import com.fenix.studio.core.database.model.ProcessingStatus
import com.fenix.studio.core.database.model.ProtocolSectionType
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Conversores deterministas de tipos enumerados y vectores float32 (`ByteArray` Little-Endian) para Room.
 */
class FenixRoomConverters {

    @TypeConverter
    fun fromClassroomModality(value: ClassroomModality): String = value.name

    @TypeConverter
    fun toClassroomModality(value: String): ClassroomModality = ClassroomModality.valueOf(value)

    @TypeConverter
    fun fromClassroomLifecycleState(value: ClassroomLifecycleState): String = value.name

    @TypeConverter
    fun toClassroomLifecycleState(value: String): ClassroomLifecycleState = ClassroomLifecycleState.valueOf(value)

    @TypeConverter
    fun fromProcessingStatus(value: ProcessingStatus): String = value.name

    @TypeConverter
    fun toProcessingStatus(value: String): ProcessingStatus = ProcessingStatus.valueOf(value)

    @TypeConverter
    fun fromMaterialRoleType(value: MaterialRoleType): String = value.name

    @TypeConverter
    fun toMaterialRoleType(value: String): MaterialRoleType = MaterialRoleType.valueOf(value)

    @TypeConverter
    fun fromProtocolSectionType(value: ProtocolSectionType): String = value.name

    @TypeConverter
    fun toProtocolSectionType(value: String): ProtocolSectionType = ProtocolSectionType.valueOf(value)

    @TypeConverter
    fun fromEpistemicStateDb(value: EpistemicStateDb): String = value.name

    @TypeConverter
    fun toEpistemicStateDb(value: String): EpistemicStateDb = EpistemicStateDb.valueOf(value)

    @TypeConverter
    fun fromOntologicalCategoryDb(value: OntologicalCategoryDb): String = value.name

    @TypeConverter
    fun toOntologicalCategoryDb(value: String): OntologicalCategoryDb = OntologicalCategoryDb.valueOf(value)

    companion object {
        /**
         * Serializa un `FloatArray` en un `ByteArray` IEEE-754 Little-Endian sin overhead de JSON.
         */
        fun floatArrayToBlob(vector: FloatArray): ByteArray {
            val buffer = ByteBuffer.allocate(vector.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            for (v in vector) {
                buffer.putFloat(v)
            }
            return buffer.array()
        }

        /**
         * Deserializa un `ByteArray` IEEE-754 Little-Endian a `FloatArray`.
         */
        fun blobToFloatArray(blob: ByteArray): FloatArray {
            if (blob.isEmpty()) return FloatArray(0)
            val floatCount = blob.size / Float.SIZE_BYTES
            val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(floatCount) { buffer.getFloat() }
        }
    }
}
