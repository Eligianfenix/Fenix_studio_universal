/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-14 20:45:00 -03:00
 * ARCHIVO: core/network-ai/src/main/kotlin/com/fenix/studio/core/network/router/ChatCompletionTransport.kt
 * ================================================================================
 */

package com.fenix.studio.core.network.router

import com.fenix.studio.core.network.catalog.AiProviderSpec
import com.fenix.studio.core.network.catalog.ProviderAuthStyle
import com.fenix.studio.core.network.teacher.TeacherChatFailureKind
import com.fenix.studio.core.network.teacher.TeacherChatHistoryTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** Un turno de chat contra un proveedor concreto, con la clave ya resuelta. */
data class ChatCompletionRequest(
    val spec: AiProviderSpec,
    val apiKey: String,
    val modelId: String,
    val systemInstruction: String,
    val userPrompt: String,
    val conversationHistory: List<TeacherChatHistoryTurn>,
)

/** Resultado tipado del transporte. */
sealed interface ChatCompletionResult {
    data class Success(val text: String) : ChatCompletionResult
    data class Failure(val kind: TeacherChatFailureKind, val detail: String) : ChatCompletionResult
}

/**
 * Abstracción del cliente HTTP para poder testear el router sin red.
 */
interface ChatCompletionTransport {
    suspend fun complete(request: ChatCompletionRequest): ChatCompletionResult
}

/**
 * Cliente mínimo compatible con la API de OpenAI (`POST /chat/completions`).
 *
 * Usa `HttpURLConnection` a propósito: no agrega motores HTTP al APK y mantiene el mismo
 * criterio que la sonda de conexión. La clave nunca se registra ni se incluye en los errores.
 */
class JsonChatCompletionTransport(
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 30_000,
    private val maxTokens: Int = 1_200,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ChatCompletionTransport {

    override suspend fun complete(request: ChatCompletionRequest): ChatCompletionResult =
        withContext(Dispatchers.IO) {
            if (request.spec.chatCompletionsUrl.contains("{")) {
                return@withContext ChatCompletionResult.Failure(
                    kind = TeacherChatFailureKind.SERVER_ERROR,
                    detail = "La URL de ${request.spec.displayName} tiene datos sin completar.",
                )
            }

            val payload = buildPayload(request)
            val connection = try {
                (URL(request.spec.chatCompletionsUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "FenixStudio/1.0")
                    if (request.spec.authStyle == ProviderAuthStyle.BEARER) {
                        setRequestProperty("Authorization", "Bearer ${request.apiKey.trim()}")
                    }
                }
            } catch (io: IOException) {
                return@withContext ChatCompletionResult.Failure(
                    kind = TeacherChatFailureKind.NO_NETWORK,
                    detail = "No se pudo abrir la conexión con ${request.spec.displayName}.",
                )
            }

            try {
                connection.outputStream.use { it.write(payload.toByteArray()) }
                classify(connection, request.spec)
            } catch (timeout: SocketTimeoutException) {
                ChatCompletionResult.Failure(
                    kind = TeacherChatFailureKind.TIMEOUT,
                    detail = "${request.spec.displayName} no respondió a tiempo.",
                )
            } catch (io: IOException) {
                ChatCompletionResult.Failure(
                    kind = TeacherChatFailureKind.NO_NETWORK,
                    detail = "Error de red con ${request.spec.displayName}: ${io.message ?: "sin detalle"}.",
                )
            } finally {
                connection.disconnect()
            }
        }

    private fun buildPayload(request: ChatCompletionRequest): String {
        val messages = buildJsonArray {
            if (request.systemInstruction.isNotBlank()) {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", request.systemInstruction)
                    },
                )
            }
            request.conversationHistory.forEach { turn ->
                add(
                    buildJsonObject {
                        put("role", turn.role)
                        put("content", turn.content)
                    },
                )
            }
            add(
                buildJsonObject {
                    put("role", "user")
                    put("content", request.userPrompt)
                },
            )
        }

        return buildJsonObject {
            put("model", request.modelId)
            put("messages", messages)
            put("max_tokens", maxTokens)
            put("stream", false)
        }.toString()
    }

    private fun classify(
        connection: HttpURLConnection,
        spec: AiProviderSpec,
    ): ChatCompletionResult {
        val code = connection.responseCode
        if (code !in 200..299) {
            return when (code) {
                401, 403 -> ChatCompletionResult.Failure(
                    kind = TeacherChatFailureKind.SERVER_ERROR,
                    detail = "${spec.displayName} rechazó la clave (HTTP $code).",
                )
                429 -> ChatCompletionResult.Failure(
                    kind = TeacherChatFailureKind.RATE_LIMITED,
                    detail = "${spec.displayName} está limitando pedidos (HTTP 429).",
                )
                in 500..599 -> ChatCompletionResult.Failure(
                    kind = TeacherChatFailureKind.SERVER_ERROR,
                    detail = "${spec.displayName} respondió HTTP $code.",
                )
                else -> ChatCompletionResult.Failure(
                    kind = TeacherChatFailureKind.SERVER_ERROR,
                    detail = "${spec.displayName} respondió HTTP $code.",
                )
            }
        }

        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val text = parseContent(body)
        return if (text.isNullOrBlank()) {
            ChatCompletionResult.Failure(
                kind = TeacherChatFailureKind.EMPTY_RESPONSE,
                detail = "${spec.displayName} devolvió una respuesta vacía.",
            )
        } else {
            ChatCompletionResult.Success(text)
        }
    }

    /** Acepta `choices[0].message.content` y `choices[0].text`. */
    fun parseContent(raw: String): String? = runCatching {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return null
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        choice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            ?: choice["text"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()
}
