/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/database/src/main/kotlin/com/fenix/studio/core/database/dao/ProviderModelDao.kt
 * ================================================================================
 */

package com.fenix.studio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.fenix.studio.core.database.entity.ProviderModelEntity

@Dao
interface ProviderModelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDiscoveredModels(models: List<ProviderModelEntity>)

    @Query("SELECT * FROM provider_models WHERE provider_id = :providerId ORDER BY display_name ASC")
    suspend fun getModelsForProvider(providerId: String): List<ProviderModelEntity>

    @Query("SELECT * FROM provider_models WHERE provider_id = :providerId AND is_selected = 1 LIMIT 1")
    suspend fun getSelectedModelForProvider(providerId: String): ProviderModelEntity?

    @Query("DELETE FROM provider_models WHERE provider_id = :providerId")
    suspend fun clearModelsForProvider(providerId: String)

    @Transaction
    suspend fun replaceModelsForProvider(
        providerId: String,
        models: List<ProviderModelEntity>,
    ) {
        clearModelsForProvider(providerId)
        upsertDiscoveredModels(models)
    }
}
