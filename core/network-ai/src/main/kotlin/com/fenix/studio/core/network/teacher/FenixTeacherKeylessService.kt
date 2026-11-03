/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-02 22:35:00 -03:00
 * ARCHIVO: core/network-ai/src/main/kotlin/com/fenix/studio/core/network/teacher/FenixTeacherKeylessService.kt
 * ================================================================================
 */

package com.fenix.studio.core.network.teacher

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Turno conversacional previo para preservar contexto y memoria multi-turno en el chat.
 */
data class TeacherChatHistoryTurn(
    val role: String, // "user" o "assistant"
    val content: String,
)

/**
 * Definición de un proveedor Zero-Config / Keyless en el pool de "Fenix Teacher".
 * Cumple estrictamente con RN-08 (Cero Hardcodeo de Modelos): solo declara las URLs de
 * autodescubrimiento (`modelsDiscoveryUrl`) y de inferencia (`chatCompletionsUrl`).
 * Los modelos activos se descubren en tiempo real consultando `/models`.
 */
data class FenixKeylessProviderGateway(
    val providerId: String,
    val providerName: String,
    val modelsDiscoveryUrl: String,
    val chatCompletionsUrl: String,
    val publicAuthToken: String? = null,
)

/**
 * Ruta dinámica descubierta en vivo (`GET /models`) para un proveedor Zero-Config.
 */
data class DiscoveredKeylessRoute(
    val gateway: FenixKeylessProviderGateway,
    val discoveredModelId: String,
) {
    val routeKey: String get() = "${gateway.providerId}::$discoveredModelId"
}

/**
 * Servicio autónomo "Fenix Teacher" con Autodescubrimiento Dinámico de Modelos (`RN-08`)
 * y Enrutamiento Paralelo Blindado (Hedged Requests).
 *
 * - Cero modelos hardcodeados: interroga dinámicamente `GET /models` en cada gateway Zero-Config.
 * - Filtra automáticamente modelos que no sean de chat/texto (como embeddings, TTS, STT, difusión o guardias)
 *   y descarta modelos marcados como de pago.
 * - Ejecuta carreras paralelas (Hedged Requests de a 2 rutas simultáneas con timeout corto) para responder
 *   en ~1-2 segundos y penalizar automáticamente cualquier nodo lento o caído.
 */
class FenixTeacherKeylessService {

    private val json = Json { ignoreUnknownKeys = true }
    private val discoveryMutex = Mutex()

    @Volatile
    private var cachedRoutes: List<DiscoveredKeylessRoute> = emptyList()

    @Volatile
    private var lastDiscoveryEpochMs: Long = 0L

    private val routePenaltyScore = ConcurrentHashMap<String, Int>()
    private val routeSuccessLatencyMs = ConcurrentHashMap<String, Long>()

    companion object {
        const val DEFAULT_TEACHER_ID = "fenix_teacher"
        const val DEFAULT_TEACHER_NAME = "Fenix Teacher"
        private const val DISCOVERY_CACHE_TTL_MS = 15 * 60 * 1_000L
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        /**
         * Gateways Zero-Config (sin ningún modelo hardcodeado).
         * Cada gateway expone su endpoint estándar `/models` para autodescubrimiento en vivo.
         */
        val KEYLESS_GATEWAYS: List<FenixKeylessProviderGateway> = listOf(
            FenixKeylessProviderGateway(
                providerId = "ovhcloud_ai_keyless",
                providerName = "OVHcloud AI Endpoints",
                modelsDiscoveryUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/models",
                chatCompletionsUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions",
            ),
            FenixKeylessProviderGateway(
                providerId = "pollinations_keyless",
                providerName = "Pollinations AI",
                modelsDiscoveryUrl = "https://text.pollinations.ai/models",
                chatCompletionsUrl = "https://text.pollinations.ai/openai",
            ),
            FenixKeylessProviderGateway(
                providerId = "llm7_keyless",
                providerName = "LLM7 Public Gateway",
                modelsDiscoveryUrl = "https://api.llm7.io/v1/models",
                chatCompletionsUrl = "https://api.llm7.io/v1/chat/completions",
                publicAuthToken = "unused",
            ),
            FenixKeylessProviderGateway(
                providerId = "openapis_keyless",
                providerName = "OpenAPIs Public Proxy",
                modelsDiscoveryUrl = "https://api.openapis.online/openai/v1/models",
                chatCompletionsUrl = "https://api.openapis.online/openai/v1/chat/completions",
                publicAuthToken = "admin",
            ),
        )

        private val NON_CHAT_MODEL_KEYWORDS = listOf(
            "embed",
            "bge-",
            "whisper",
            "tts",
            "diffusion",
            "guard",
            "moderation",
            "rerank",
            "audio",
            "speech",
            "image",
            "flux",
        )
    }

    /**
     * Descubre en vivo todos los modelos de chat gratuitos ofrecidos por los gateways Zero-Config.
     */
    suspend fun discoverLiveKeylessRoutes(forceRefresh: Boolean = false): List<DiscoveredKeylessRoute> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (!forceRefresh && cachedRoutes.isNotEmpty() && (now - lastDiscoveryEpochMs) < DISCOVERY_CACHE_TTL_MS) {
                return@withContext rankRoutesByHealth(cachedRoutes)
            }

            discoveryMutex.withLock {
                val recheckNow = System.currentTimeMillis()
                if (!forceRefresh && cachedRoutes.isNotEmpty() && (recheckNow - lastDiscoveryEpochMs) < DISCOVERY_CACHE_TTL_MS) {
                    return@withLock rankRoutesByHealth(cachedRoutes)
                }

                val discovered = coroutineScope {
                    KEYLESS_GATEWAYS.map { gateway ->
                        async {
                            runCatching { fetchModelsFromGateway(gateway) }.getOrDefault(emptyList())
                        }
                    }.flatMap { it.await() }
                }

                if (discovered.isNotEmpty()) {
                    cachedRoutes = discovered
                    lastDiscoveryEpochMs = System.currentTimeMillis()
                }
                rankRoutesByHealth(cachedRoutes)
            }
        }

    /**
     * Envía la consulta del estudiante utilizando las rutas autodescubiertas en vivo.
     * Incluye memoria de conversación multi-turno (conversationHistory) para mantener coherencia contextual.
     * Lanza peticiones en paralelo (Hedged Racing de a 2 rutas) para garantizar respuesta rápida (~1.5s)
     * y tolerancia total a fallos sin esperas largas.
     */
    suspend fun sendChatToFenixTeacher(
        userPrompt: String,
        systemInstruction: String,
        conversationHistory: List<TeacherChatHistoryTurn> = emptyList(),
        startEndpointIndex: Int = 0,
    ): String = withContext(Dispatchers.IO) {
        var routes = discoverLiveKeylessRoutes(forceRefresh = false)
        if (routes.isEmpty()) {
            routes = discoverLiveKeylessRoutes(forceRefresh = true)
        }

        if (routes.isNotEmpty()) {
            val rotatedRoutes = if (startEndpointIndex > 0 && routes.size > 1) {
                val shift = startEndpointIndex % routes.size
                routes.drop(shift) + routes.take(shift)
            } else {
                routes
            }

            // Procesamos en olas paralelas de 2 rutas simultáneas: la primera que responda HTTP 200 gana
            val batches = rotatedRoutes.chunked(2)
            for (batch in batches.take(4)) {
                val winner = raceBatch(
                    batch = batch,
                    systemInstruction = systemInstruction,
                    userPrompt = userPrompt,
                    conversationHistory = conversationHistory,
                )
                if (!winner.isNullOrBlank()) {
                    return@withContext winner.trim()
                }
            }
        }

        "No se pudo obtener respuesta en este momento. Verifica tu conexión a internet o intenta nuevamente."
    }

    private suspend fun raceBatch(
        batch: List<DiscoveredKeylessRoute>,
        systemInstruction: String,
        userPrompt: String,
        conversationHistory: List<TeacherChatHistoryTurn>,
    ): String? = coroutineScope {
        if (batch.size == 1) {
            return@coroutineScope trySingleRoute(batch[0], systemInstruction, userPrompt, conversationHistory)
        }

        val childScope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val deferredList = batch.map { route ->
                childScope.async(Dispatchers.IO) {
                    trySingleRoute(route, systemInstruction, userPrompt, conversationHistory)
                }
            }

            val firstResult = select<String?> {
                deferredList.forEach { deferred ->
                    deferred.onAwait { result -> result }
                }
            }

            if (!firstResult.isNullOrBlank()) {
                childScope.coroutineContext.cancelChildren()
                return@coroutineScope firstResult
            }

            // Si el primero que terminó devolvió null, esperamos al segundo de la misma ola
            for (deferred in deferredList) {
                val fallback = runCatching { deferred.await() }.getOrNull()
                if (!fallback.isNullOrBlank()) {
                    childScope.coroutineContext.cancelChildren()
                    return@coroutineScope fallback
                }
            }
            null
        } finally {
            childScope.coroutineContext.cancelChildren()
        }
    }

    private fun trySingleRoute(
        route: DiscoveredKeylessRoute,
        systemInstruction: String,
        userPrompt: String,
        conversationHistory: List<TeacherChatHistoryTurn>,
    ): String? {
        val t0 = System.currentTimeMillis()
        val response = runCatching {
            executeOpenAiCompatiblePost(
                route = route,
                systemInstruction = systemInstruction,
                userPrompt = userPrompt,
                conversationHistory = conversationHistory,
            )
        }.getOrNull()

        return if (!response.isNullOrBlank()) {
            val elapsed = System.currentTimeMillis() - t0
            routePenaltyScore[route.routeKey] = 0
            routeSuccessLatencyMs[route.routeKey] = elapsed
            response
        } else {
            val prevPenalty = routePenaltyScore[route.routeKey] ?: 0
            routePenaltyScore[route.routeKey] = prevPenalty + 1
            null
        }
    }

    private fun rankRoutesByHealth(routes: List<DiscoveredKeylessRoute>): List<DiscoveredKeylessRoute> {
        return routes.sortedWith(
            compareBy<DiscoveredKeylessRoute> { routePenaltyScore[it.routeKey] ?: 0 }
                .thenBy { routeSuccessLatencyMs[it.routeKey] ?: 2_500L },
        )
    }

    /**
     * Interroga `GET /models` en el gateway y extrae dinámicamente los IDs de modelos de chat gratuitos.
     */
    private fun fetchModelsFromGateway(gateway: FenixKeylessProviderGateway): List<DiscoveredKeylessRoute> {
        val url = URL(gateway.modelsDiscoveryUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 6000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
            if (!gateway.publicAuthToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer ${gateway.publicAuthToken}")
            }
        }

        return try {
            if (conn.responseCode !in 200..299) return emptyList()
            val raw = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            parseDiscoveredModels(gateway, raw)
        } finally {
            conn.disconnect()
        }
    }

    internal fun parseDiscoveredModels(
        gateway: FenixKeylessProviderGateway,
        rawJson: String,
    ): List<DiscoveredKeylessRoute> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return emptyList()

        val element: JsonElement = runCatching { json.parseToJsonElement(trimmed) }.getOrNull() ?: return emptyList()
        val itemsArray: JsonArray = when (element) {
            is JsonArray -> element
            is JsonObject -> element["data"]?.jsonArray ?: element["models"]?.jsonArray ?: return emptyList()
            else -> return emptyList()
        }

        val routes = mutableListOf<DiscoveredKeylessRoute>()
        for (item in itemsArray) {
            val obj = item as? JsonObject ?: continue

            // Si el catálogo reporta tier (ej. Pollinations), filtramos únicamente el nivel gratuito/anónimo
            val tier = (obj["tier"] as? JsonPrimitive)?.contentOrNull?.lowercase()
            if (tier != null && tier != "anonymous" && tier != "free" && tier != "seed") {
                continue
            }

            val rawId = (obj["id"] as? JsonPrimitive)?.contentOrNull
                ?: (obj["name"] as? JsonPrimitive)?.contentOrNull
                ?: continue

            if (!isEligibleChatModelId(rawId)) continue

            routes.add(
                DiscoveredKeylessRoute(
                    gateway = gateway,
                    discoveredModelId = rawId,
                ),
            )
        }
        return routes
    }

    private fun isEligibleChatModelId(modelId: String): Boolean {
        val lower = modelId.lowercase()
        return NON_CHAT_MODEL_KEYWORDS.none { keyword -> lower.contains(keyword) }
    }

    private fun executeOpenAiCompatiblePost(
        route: DiscoveredKeylessRoute,
        systemInstruction: String,
        userPrompt: String,
        conversationHistory: List<TeacherChatHistoryTurn> = emptyList(),
    ): String? {
        val url = URL(route.gateway.chatCompletionsUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 7500
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
            if (!route.gateway.publicAuthToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer ${route.gateway.publicAuthToken}")
            }
        }

        return try {
            val payload = buildJsonObject {
                put("model", route.discoveredModelId)
                put(
                    "messages",
                    buildJsonArray {
                        // 1. System prompt (personalidad y encuadre pedagógico)
                        add(
                            buildJsonObject {
                                put("role", "system")
                                put("content", systemInstruction)
                            },
                        )
                        // 2. Historial conversacional previo (hasta 10 turnos para memoria multi-turno sin saturar tokens)
                        conversationHistory.takeLast(10).forEach { turn ->
                            add(
                                buildJsonObject {
                                    put("role", turn.role)
                                    put("content", turn.content)
                                },
                            )
                        }
                        // 3. Turno actual del usuario
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
        val root: JsonObject = runCatching { json.parseToJsonElement(trimmed).jsonObject }.getOrNull() ?: return null
        val choices = root["choices"]?.jsonArray ?: return null
        if (choices.isEmpty()) return null
        val firstChoice = choices[0] as? JsonObject ?: return null
        val messageObj = firstChoice["message"] as? JsonObject
        val contentElem = messageObj?.get("content")
        if (contentElem != null && contentElem !is JsonNull) {
            val text = (contentElem as? JsonPrimitive)?.contentOrNull?.trim()
            if (!text.isNullOrBlank()) return text
        }

        val textFallback = (firstChoice["text"] as? JsonPrimitive)?.contentOrNull?.trim()
        return textFallback?.takeIf { it.isNotBlank() }
    }
}
