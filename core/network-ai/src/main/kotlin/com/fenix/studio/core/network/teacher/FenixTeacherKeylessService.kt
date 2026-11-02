/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-02 20:39:00 -03:00
 * ARCHIVO: core/network-ai/src/main/kotlin/com/fenix/studio/core/network/teacher/FenixTeacherKeylessService.kt
 * ================================================================================
 */

package com.fenix.studio.core.network.teacher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Definición interna de un nodo IA Zero-Config / Keyless integrado en el pool unificado "Fenix Teacher".
 * Estos 14 nodos operan de forma transparente detrás de la única opción "Fenix Teacher".
 */
data class FenixKeylessEndpoint(
    val id: String,
    val providerName: String,
    val endpointUrl: String,
    val modelId: String,
    val publicAuthToken: String? = null,
    val includePrivateFlag: Boolean = false,
)

/**
 * Servicio autónomo "Fenix Teacher" (Pool unificado de 14 IAs Zero-Config / Sin API Key).
 * En la interfaz aparece como un único modelo ("Fenix Teacher") y por debajo balancea y hace failover
 * automático entre los 14 proveedores gratuitos sin API.
 */
class FenixTeacherKeylessService {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val DEFAULT_TEACHER_ID = "fenix_teacher"
        const val DEFAULT_TEACHER_NAME = "Fenix Teacher"

        /**
         * Catálogo interno de 14 endpoints Zero-Config / Keyless auditados en el proyecto.
         */
        val KEYLESS_ENDPOINTS: List<FenixKeylessEndpoint> = listOf(
            FenixKeylessEndpoint(
                id = "pollinations_openai",
                providerName = "Pollinations OpenAI",
                endpointUrl = "https://text.pollinations.ai/openai",
                modelId = "openai",
                includePrivateFlag = true,
            ),
            FenixKeylessEndpoint(
                id = "pollinations_deepseek",
                providerName = "Pollinations DeepSeek",
                endpointUrl = "https://text.pollinations.ai/openai",
                modelId = "deepseek",
                includePrivateFlag = true,
            ),
            FenixKeylessEndpoint(
                id = "pollinations_gemini",
                providerName = "Pollinations Gemini",
                endpointUrl = "https://text.pollinations.ai/openai",
                modelId = "gemini",
                includePrivateFlag = true,
            ),
            FenixKeylessEndpoint(
                id = "pollinations_mistral",
                providerName = "Pollinations Mistral",
                endpointUrl = "https://text.pollinations.ai/openai",
                modelId = "mistral",
                includePrivateFlag = true,
            ),
            FenixKeylessEndpoint(
                id = "pollinations_qwen",
                providerName = "Pollinations Qwen",
                endpointUrl = "https://text.pollinations.ai/openai",
                modelId = "qwen-coder",
                includePrivateFlag = true,
            ),
            FenixKeylessEndpoint(
                id = "llm7_anon",
                providerName = "LLM7 Public Gateway",
                endpointUrl = "https://api.llm7.io/v1/chat/completions",
                modelId = "gemini-2.5-flash-lite",
                publicAuthToken = "unused",
            ),
            FenixKeylessEndpoint(
                id = "ovhcloud_keyless",
                providerName = "OVHcloud AI Keyless",
                endpointUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions",
                modelId = "Meta-Llama-3_3-70B-Instruct",
            ),
            FenixKeylessEndpoint(
                id = "gaianet_llama",
                providerName = "GaiaNet Llama Node",
                endpointUrl = "https://llama.gaia.domains/v1/chat/completions",
                modelId = "llama",
                publicAuthToken = "gaia",
            ),
            FenixKeylessEndpoint(
                id = "gaianet_qwen",
                providerName = "GaiaNet Qwen Node",
                endpointUrl = "https://qwen.gaia.domains/v1/chat/completions",
                modelId = "qwen",
                publicAuthToken = "gaia",
            ),
            FenixKeylessEndpoint(
                id = "openclaw_keyless",
                providerName = "OpenClaw Keyless Gateway",
                endpointUrl = "https://ai.trxenergy.vip/v1/chat/completions",
                modelId = "deepseek-r1",
            ),
            FenixKeylessEndpoint(
                id = "smanx_free_api",
                providerName = "Smanx Public Free-API",
                endpointUrl = "https://openai.good.hidns.vip/v1/chat/completions",
                modelId = "gpt-5.2",
                publicAuthToken = "https://github.com/smanx/free-api",
            ),
            FenixKeylessEndpoint(
                id = "openapis_beta",
                providerName = "OpenAPIs Public Proxy",
                endpointUrl = "https://api.openapis.online/openai/v1/chat/completions",
                modelId = "gpt-5.4-mini",
                publicAuthToken = "admin",
            ),
            FenixKeylessEndpoint(
                id = "nymph_gateway",
                providerName = "Nymph Auto Gateway",
                endpointUrl = "https://web-tech.tw/recv/openai/v1/chat/completions",
                modelId = "auto",
                publicAuthToken = "nymph_zr3Pjc68z4bOtw==",
            ),
            FenixKeylessEndpoint(
                id = "aihorde_community",
                providerName = "AI Horde Community",
                endpointUrl = "https://oai.aihorde.net/v1/chat/completions",
                modelId = "default",
                publicAuthToken = "0000000000",
            ),
        )
    }

    /**
     * Envía la consulta del estudiante recorriendo en cascada los 14 proveedores sin API del pool "Fenix Teacher"
     * hasta obtener una respuesta válida.
     */
    suspend fun sendChatToFenixTeacher(
        userPrompt: String,
        systemInstruction: String,
        startEndpointIndex: Int = 0,
    ): String = withContext(Dispatchers.IO) {
        val total = KEYLESS_ENDPOINTS.size
        for (offset in 0 until total) {
            val endpoint = KEYLESS_ENDPOINTS[(startEndpointIndex + offset) % total]
            val result = runCatching {
                executeOpenAiCompatiblePost(
                    endpoint = endpoint,
                    systemInstruction = systemInstruction,
                    userPrompt = userPrompt,
                )
            }.getOrNull()

            if (!result.isNullOrBlank()) {
                return@withContext result.trim()
            }
        }

        // Respaldo GET directo a Pollinations Text API en caso de bloqueo de payloads POST
        val getFallback = runCatching {
            executePollinationsGetFallback(
                systemInstruction = systemInstruction,
                userPrompt = userPrompt,
            )
        }.getOrNull()

        if (!getFallback.isNullOrBlank()) {
            return@withContext getFallback.trim()
        }

        "No se pudo conectar en este momento con el servicio Fenix Teacher. Verifica tu conexión a internet o intenta nuevamente en unos segundos."
    }

    private fun executeOpenAiCompatiblePost(
        endpoint: FenixKeylessEndpoint,
        systemInstruction: String,
        userPrompt: String,
    ): String? {
        val url = URL(endpoint.endpointUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 9000
            readTimeout = 14000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "FenixStudio-Android/1.0")
            if (!endpoint.publicAuthToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer ${endpoint.publicAuthToken}")
            }
        }

        return try {
            val payload = buildJsonObject {
                put("model", endpoint.modelId)
                if (endpoint.includePrivateFlag) {
                    put("private", true)
                }
                put(
                    "messages",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("role", "system")
                                put("content", systemInstruction)
                            },
                        )
                        add(
                            buildJsonObject {
                                put("role", "user")
                                put("content", userPrompt)
                            },
                        )
                    },
                )
            }.toString()

            connection.outputStream.use { os ->
                os.write(payload.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val code = connection.responseCode
            if (code !in 200..299) return null

            val responseBody = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }
            extractContentFromOpenAiResponse(responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun extractContentFromOpenAiResponse(rawBody: String): String? {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.startsWith("{")) {
            return trimmed
        }
        val root: JsonObject = json.parseToJsonElement(trimmed).jsonObject
        val choices = root["choices"]?.jsonArray ?: return null
        if (choices.isEmpty()) return null
        val firstChoice = choices[0].jsonObject
        val messageContent = firstChoice["message"]
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.content
        if (!messageContent.isNullOrBlank()) return messageContent

        val textContent = firstChoice["text"]?.jsonPrimitive?.content
        return textContent?.takeIf { it.isNotBlank() }
    }

    private fun executePollinationsGetFallback(
        systemInstruction: String,
        userPrompt: String,
    ): String? {
        val encodedPrompt = URLEncoder.encode(userPrompt, "UTF-8")
        val encodedSystem = URLEncoder.encode(systemInstruction, "UTF-8")
        val url = URL("https://text.pollinations.ai/$encodedPrompt?model=openai&system=$encodedSystem&private=true")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 9000
            readTimeout = 14000
            setRequestProperty("User-Agent", "FenixStudio-Android/1.0")
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

