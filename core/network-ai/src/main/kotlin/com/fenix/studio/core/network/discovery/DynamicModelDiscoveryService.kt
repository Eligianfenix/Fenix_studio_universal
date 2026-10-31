/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/network-ai/src/main/kotlin/com/fenix/studio/core/network/discovery/DynamicModelDiscoveryService.kt
 * ================================================================================
 */

package com.fenix.studio.core.network.discovery

import com.fenix.studio.core.database.dao.ProviderModelDao
import com.fenix.studio.core.database.entity.ProviderModelEntity
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Servicio de Descubrimiento Dinámico de Modelos (`DynamicModelDiscoveryService`, `MF-2.2`, `RN-08`).
 * Realiza la introspección asíncrona `GET {baseUrl}/models` al registrar o verificar una clave,
 * almacenando el catálogo descubierto en `ProviderModelEntity` y eliminando por completo
 * cualquier lista estática o hardcodeada de modelos en el código fuente.
 */
class DynamicModelDiscoveryService(
    private val httpClient: HttpClient,
    private val providerModelDao: ProviderModelDao? = null,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    /**
     * Consulta `GET {baseUrl}/models`, parsea dinámicamente los modelos disponibles y los persiste en Room.
     */
    suspend fun discoverAndPersistModels(
        providerId: String,
        baseUrl: String,
        apiKey: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): List<ProviderModelEntity> {
        require(providerId.isNotBlank()) { "El providerId no puede estar vacío." }
        require(baseUrl.isNotBlank()) { "El baseUrl no puede estar vacío." }

        val cleanBaseUrl = baseUrl.trimEnd('/')
        val response = httpClient.get("$cleanBaseUrl/models") {
            if (apiKey.isNotBlank()) {
                header(HttpHeaders.Authorization, "Bearer ${apiKey.trim()}")
            }
        }

        if (!response.status.isSuccess()) {
            throw IllegalStateException("Error HTTP ${response.status.value} al descubrir modelos en $cleanBaseUrl/models")
        }

        val rawBody = response.bodyAsText()
        val discovered = parseModelsPayload(providerId, rawBody, nowEpochMs)

        if (discovered.isNotEmpty() && providerModelDao != null) {
            providerModelDao.replaceModelsForProvider(providerId, discovered)
        }
        return discovered
    }

    /**
     * Parsea catálogos JSON compatibles con el estándar `/v1/models` (`data` o `models`).
     */
    fun parseModelsPayload(
        providerId: String,
        rawJsonPayload: String,
        discoveredAtEpochMs: Long,
    ): List<ProviderModelEntity> {
        val rootObject = json.parseToJsonElement(rawJsonPayload).jsonObject
        val itemsArray = rootObject["data"]?.jsonArray
            ?: rootObject["models"]?.jsonArray
            ?: return emptyList()

        val parsedModels = itemsArray.mapIndexedNotNull { index, element ->
            val obj = element.jsonObject
            val rawId = obj["id"]?.jsonPrimitive?.content
                ?: obj["name"]?.jsonPrimitive?.content
                ?: return@mapIndexedNotNull null
            val displayName = obj["display_name"]?.jsonPrimitive?.content
                ?: obj["displayName"]?.jsonPrimitive?.content
                ?: rawId
            val contextWindow = obj["context_window"]?.jsonPrimitive?.intOrNull
                ?: obj["inputTokenLimit"]?.jsonPrimitive?.intOrNull
                ?: DEFAULT_CONTEXT_WINDOW_TOKENS
            val supportsVision = obj["supports_vision"]?.jsonPrimitive?.booleanOrNull ?: false
            val supportsJson = obj["supports_json_schema"]?.jsonPrimitive?.booleanOrNull ?: true

            ProviderModelEntity(
                providerId = providerId,
                modelId = rawId,
                displayName = displayName,
                contextWindowTokens = contextWindow,
                supportsVision = supportsVision,
                supportsJsonSchema = supportsJson,
                isSelected = (index == 0),
                discoveredAtEpochMs = discoveredAtEpochMs,
            )
        }

        return parsedModels
    }

    companion object {
        private const val DEFAULT_CONTEXT_WINDOW_TOKENS: Int = 32_768
    }
}
