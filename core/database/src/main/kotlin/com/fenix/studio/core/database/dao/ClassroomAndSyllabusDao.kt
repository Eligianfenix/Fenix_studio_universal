/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/database/src/main/kotlin/com/fenix/studio/core/database/dao/ClassroomAndSyllabusDao.kt
 * ================================================================================
 */

package com.fenix.studio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fenix.studio.core.database.entity.ClassroomEntity
import com.fenix.studio.core.database.entity.MaterialDocumentEntity
import com.fenix.studio.core.database.entity.PointMaterialCrossRef
import com.fenix.studio.core.database.entity.SyllabusPointEntity
import com.fenix.studio.core.database.entity.SyllabusUnitEntity
import com.fenix.studio.core.database.entity.UnitMaterialCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassroomAndSyllabusDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClassroom(classroom: ClassroomEntity)

    @Query("SELECT * FROM catedras_local ORDER BY updated_at_epoch_ms DESC")
    fun observeAllClassrooms(): Flow<List<ClassroomEntity>>

    @Query("SELECT * FROM catedras_local WHERE catedra_id = :catedraId LIMIT 1")
    suspend fun getClassroomById(catedraId: String): ClassroomEntity?

    @Query(
        """
        SELECT * FROM catedras_local
        WHERE normalized_title = :normalizedTitle
          AND (:programaYear IS NULL OR programa_year = :programaYear)
        """,
    )
    suspend fun findHomonymousClassrooms(
        normalizedTitle: String,
        programaYear: Int?,
    ): List<ClassroomEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUnits(units: List<SyllabusUnitEntity>)

    @Query("SELECT * FROM unidades_programa WHERE catedra_id = :catedraId ORDER BY unit_number ASC")
    suspend fun getUnitsForClassroom(catedraId: String): List<SyllabusUnitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPoints(points: List<SyllabusPointEntity>)

    @Query("SELECT * FROM puntos_programa WHERE unit_id = :unitId ORDER BY sequence_order ASC")
    suspend fun getPointsForUnit(unitId: String): List<SyllabusPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMaterial(material: MaterialDocumentEntity)

    @Query("SELECT * FROM materiales_bibliograficos WHERE catedra_id = :catedraId ORDER BY added_at_epoch_ms ASC")
    suspend fun getMaterialsForClassroom(catedraId: String): List<MaterialDocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPointMaterialCrossRefs(crossRefs: List<PointMaterialCrossRef>)

    @Query("SELECT * FROM point_material_cross_ref WHERE point_id = :pointId")
    suspend fun getMaterialCrossRefsForPoint(pointId: String): List<PointMaterialCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUnitMaterialCrossRefs(crossRefs: List<UnitMaterialCrossRef>)
}
