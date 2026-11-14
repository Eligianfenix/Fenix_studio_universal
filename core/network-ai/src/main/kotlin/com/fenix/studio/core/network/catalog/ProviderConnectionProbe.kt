/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-14 19:35:00 -03:00
 * ARCHIVO: core/network-ai/src/main/kotlin/com/fenix/studio/core/network/catalog/ProviderConnectionProbe.kt
 * ================================================================================
 */

package com.fenix.studio.core.network.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Resultado de "Probar conexión" contra un proveedor. */
enum class ProviderProbeStatus {
    OK,
    NO_KEY,
    NOT_CONFIGURED,
    UNAUTHORIZED,
    RATE_LIMITED,
    NOT_FOUND,
    NO_NETWORK,
    HTTP_ERROR,
}

data class ProviderProbeResult(
    val status: ProviderProbeStatus,
    val message: String,
    val modelsFound: Int = 0,
    val sampleModels: List<String> = emptyList(),
)

/**
 * Prueba real de conectividad contra un proveedor, sin gastar cuota de generación.
 *
 * Estrategia: `GET /models` (endpoint de listado, no consume tokens). Si el proveedor no
 * expone listado, hace una llamada mínima de chat con `max_tokens = 1`.
 *
 * Usa `HttpURLConnection` para no sumar un motor HTTP al APK: las claves nunca se registran
 * en logs ni se incluyen en los mensajes de error.
 */
class ProviderConnectionProbe(
    private val connectTimeoutMs: Int = 6_000,
    private val readTimeoutMs: Int = 8_000,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    suspend fun probe(
        spec: AiProviderSpec,
        apiKey: String,
    ): ProviderProbeResult = withContext(Dispatchers.IO) {
        if (!spec.keyless && apiKey.isBlank()) {
            return@withContext ProviderProbeResult(
                status = ProviderProbeStatus.NO_KEY,
                message = "Falta cargar una clave para ${spec.displayName}.",
            )
        }
        if (spec.chatCompletionsUrl.contains("{") || spec.modelsUrl?.contains("{") == true) {
            return@withContext ProviderProbeResult(
                status = ProviderProbeStatus.NOT_CONFIGURED,
                message = "Antes de probar, reemplazá los datos entre { } de la URL.",
            )
        }

        if (spec.modelsUrl != null) {
            probeModels(spec, apiKey)
        } else {
            probeMinimalChat(spec, apiKey)
        }
    }

    private fun probeModels(spec: AiProviderSpec, apiKey: String): ProviderProbeResult {
        val modelsUrl = spec.modelsUrl ?: return probeMinimalChat(spec, apiKey)
        val connection = open(modelsUrl, apiKey, spec)
            ?: return ProviderProbeResult(
                status = ProviderProbeStatus.NO_NETWORK,
                message = "No se pudo conectar con ${spec.displayName}.",
            )

        return try {
            val code = connection.responseCode
            when {
                code in 200..299 -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val models = parseModelIds(body)
                    ProviderProbeResult(
                        status = ProviderProbeStatus.OK,
                        message = if (models.isEmpty()) {
                            "Conexión correcta. El proveedor no devolvió catálogo de modelos."
                        } else {
                            "Conexión correcta: ${models.size} modelos disponibles."
                        },
                        modelsFound = models.size,
                        sampleModels = models.take(5),
                    )
                }
                code == 401 || code == 403 -> ProviderProbeResult(
                    status = ProviderProbeStatus.UNAUTHORIZED,
                    message = "La clave fue rechazada (HTTP $code).",
                )
                code == 429 -> ProviderProbeResult(
                    status = ProviderProbeStatus.RATE_LIMITED,
                    message = "El proveedor está limitando pedidos (HTTP 429). La clave parece válida.",
                )
                code == 404 -> ProviderProbeResult(
                    status = ProviderProbeStatus.NOT_FOUND,
                    message = "El endpoint de modelos no existe (HTTP 404). Revisá la URL base.",
                )
                else -> ProviderProbeResult(
                    status = ProviderProbeStatus.HTTP_ERROR,
                    message = "El proveedor respondió HTTP $code.",
                )
            }
        } catch (io: IOException) {
            ProviderProbeResult(
                status = ProviderProbeStatus.NO_NETWORK,
                message = "Error de red: ${io.message ?: "sin detalle"}.",
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun probeMinimalChat(spec: AiProviderSpec, apiKey: String): ProviderProbeResult {
        val model = spec.defaultModelId
            ?: return ProviderProbeResult(
                status = ProviderProbeStatus.NOT_CONFIGURED,
                message = "Elegí un modelo por defecto para probar este proveedor.",
            )

        val connection = open(spec.chatCompletionsUrl, apiKey, spec)
            ?: return ProviderProbeResult(
                status = ProviderProbeStatus.NO_NETWORK,
                message = "No se pudo conectar con ${spec.displayName}.",
            )

        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val payload = """
                {"model":"${escape(model)}","messages":[{"role":"user","content":"ping"}],"max_tokens":1,"stream":false}
            """.trimIndent()
            connection.outputStream.use { it.write(payload.toByteArray()) }

            val code = connection.responseCode
            when {
                code in 200..299 -> ProviderProbeResult(
                    status = ProviderProbeStatus.OK,
                    message = "Conexión correcta con ${spec.displayName}.",
                )
                code == 401 || code == 403 -> ProviderProbeResult(
                    status = ProviderProbeStatus.UNAUTHORIZED,
                    message = "La clave fue rechazada (HTTP $code).",
                )
                code == 429 -> ProviderProbeResult(
                    status = ProviderProbeStatus.RATE_LIMITED,
                    message = "El proveedor está limitando pedidos (HTTP 429). La clave parece válida.",
                )
                else -> ProviderProbeResult(
                    status = ProviderProbeStatus.HTTP_ERROR,
                    message = "El proveedor respondió HTTP $code.",
                )
            }
        } catch (io: IOException) {
            ProviderProbeResult(
                status = ProviderProbeStatus.NO_NETWORK,
                message = "Error de red: ${io.message ?: "sin detalle"}.",
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun open(
        url: String,
        apiKey: String,
        spec: AiProviderSpec,
    ): HttpURLConnection? = try {
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "FenixStudio/1.0")
            if (spec.authStyle == ProviderAuthStyle.BEARER && apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            }
        }
    } catch (io: IOException) {
        null
    }

    /** Acepta `{"data":[...]}`, `{"models":[...]}` o un array raíz. */
    fun parseModelIds(raw: String): List<String> = runCatching {
        val root = json.parseToJsonElement(raw)
        val array = when {
            root is kotlinx.serialization.json.JsonArray -> root
            root is kotlinx.serialization.json.JsonObject -> root["data"]?.jsonArray
                ?: root["models"]?.jsonArray
                ?: return emptyList()
            else -> return emptyList()
        }
        array.mapNotNull { element ->
            val obj = element.jsonObject
            obj["id"]?.jsonPrimitive?.content
                ?: obj["name"]?.jsonPrimitive?.content
        }
    }.getOrDefault(emptyList())

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
