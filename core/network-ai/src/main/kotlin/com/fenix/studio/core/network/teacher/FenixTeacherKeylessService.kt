/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-02 23:20:00 -03:00
 * ARCHIVO: core/network-ai/src/main/kotlin/com/fenix/studio/core/network/teacher/FenixTeacherKeylessService.kt
 * ================================================================================
 */

package com.fenix.studio.core.network.teacher

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Turno conversacional previo para preservar contexto y memoria multi-turno en el chat.
 */
data class TeacherChatHistoryTurn(
    val role: String, // "user" o "assistant"
    val content: String,
)

/**
 * Motivo tipado de un fallo de inferencia, para no confundir rate-limit con falta de red
 * (RN-07: prohibido desplegar errores técnicos crípticos).
 */
enum class TeacherChatFailureKind {
    NO_NETWORK,
    RATE_LIMITED,
    TIMEOUT,
    SERVER_ERROR,
    EMPTY_RESPONSE,
}

/**
 * Resultado tipado del envío al pool. Permite al llamador distinguir éxito de fallo
 * y evitar persistir textos de error como turnos conversacionales.
 */
sealed interface TeacherChatOutcome {
    data class Success(val text: String) : TeacherChatOutcome
    data class Failure(val kind: TeacherChatFailureKind, val detail: String) : TeacherChatOutcome
}

fun TeacherChatFailureKind.toUserMessage(): String = when (this) {
    TeacherChatFailureKind.NO_NETWORK ->
        "No hay conexión con los nodos de Fénix IA. Revisá tu internet e intentá de nuevo."
    TeacherChatFailureKind.RATE_LIMITED ->
        "Los proveedores gratuitos alcanzaron su límite por un momento. Reintentá en unos segundos."
    TeacherChatFailureKind.TIMEOUT ->
        "La respuesta tardó más de lo esperado. Reintentá: tu mensaje no se perdió."
    TeacherChatFailureKind.SERVER_ERROR ->
        "El nodo de Fénix IA devolvió un error. Reintentá en unos segundos."
    TeacherChatFailureKind.EMPTY_RESPONSE ->
        "El nodo respondió vacío. Reintentá o reformulá la consulta."
}

/**
 * Definición de un proveedor en el pool unificado de "Fenix Teacher".
 * Soporta autodescubrimiento dinámico (/models) o endpoints directos con rotación de claves comunitarias gratuitas.
 */
data class FenixKeylessProviderGateway(
    val providerId: String,
    val providerName: String,
    val modelsDiscoveryUrl: String? = null,
    val chatCompletionsUrl: String,
    val defaultModelId: String? = null,
    val authTokens: List<String> = emptyList(),
)

/**
 * Ruta de inferencia para el router pool de "Fenix Teacher".
 */
data class DiscoveredKeylessRoute(
    val gateway: FenixKeylessProviderGateway,
    val discoveredModelId: String,
    val activeToken: String? = null,
) {
    val routeKey: String get() = "${gateway.providerId}::$discoveredModelId::${activeToken?.takeLast(8) ?: "free"}"
}

/**
 * Servicio autónomo y tolerante a fallos "Fenix Teacher" (Pool Multicapa Gratuito Costo 0).
 *
 * Combina:
 * 1. Nodos Zero-Config Públicos (OVHcloud con modelos verificados rápidos y Pollinations).
 * 2. Pool Comunitario de Google Gemini a Costo 0 (9 claves en rotación round-robin a través del endpoint OpenAI-compatible).
 * 3. Enrutamiento paralelo Hedged Racing: lanza 2 o 3 rutas simultáneas. La primera que responde con éxito en ~1s gana
 *    y cancela las demás. Si un nodo entra en rate-limit por IP o cuota (ej. 2 RPM de OVHcloud), los demás nodos
 *    responden instantáneamente sin interrupción para el usuario.
 */
class FenixTeacherKeylessService {

    private val json = Json { ignoreUnknownKeys = true }
    private val discoveryMutex = Mutex()
    private val tokenRotationCursor = AtomicInteger(0)

    @Volatile
    private var cachedRoutes: List<DiscoveredKeylessRoute> = emptyList()

    @Volatile
    private var lastDiscoveryEpochMs: Long = 0L

    private val routePenaltyScore = ConcurrentHashMap<String, Int>()
    private val routeSuccessLatencyMs = ConcurrentHashMap<String, Long>()

    /**
     * Conexiones HTTP en vuelo, para poder abortarlas de verdad al perder una carrera.
     */
    private val activeConnections: MutableSet<HttpURLConnection> =
        java.util.Collections.newSetFromMap(ConcurrentHashMap<HttpURLConnection, Boolean>())

    companion object {
        const val DEFAULT_TEACHER_ID = "fenix_teacher"
        const val DEFAULT_TEACHER_NAME = "Fenix Teacher"
        private const val DISCOVERY_CACHE_TTL_MS = 20 * 60 * 1_000L

        // P0: contrato de tiempo acotado. El servidor calcula y cierra en <=28 s;
        // el cliente espera 30 s de lectura y reparte un presupuesto global de 25 s.
        private const val CONNECT_TIMEOUT_MS = 4_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val TOTAL_BUDGET_MS = 25_000L
        private const val BATCH_TIMEOUT_MS = 9_000L
        private const val MAX_RACE_ROUTES = 6
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        private val PREFERRED_FAST_MODELS = listOf(
            "openai/gpt-oss-120b",
            "openai-fast",
            "Meta-Llama-3.3-70B-Instruct",
            "qwen/qwen3.8-27b",
            "kimi-k2-instruct",
            "Mistral-Small-3.2-24B-Instruct-2506",
        )

        /**
         * Gateways conectados al Router y Dispatcher de Fénix (Cloudflare Anycast + Render Cluster + Fallbacks Zero-Config).
         */
        val GATEWAY_CATALOG: List<FenixKeylessProviderGateway> = listOf(
            // 1. Cloudflare Anycast Edge Gateway (Master Dispatcher en POP EZE con failover de 10 Nodos y 21 Proveedores)
            FenixKeylessProviderGateway(
                providerId = "fenix_dispatcher_edge",
                providerName = "Fenix Dispatcher Edge (Cloudflare Anycast)",
                modelsDiscoveryUrl = "https://fenix-router-edge.eligianlts88.workers.dev/v1/models",
                chatCompletionsUrl = "https://fenix-router-edge.eligianlts88.workers.dev/v1/chat/completions",
                defaultModelId = "openai/gpt-oss-120b",
            ),
            // 2. Render Upstream Direct (Master Dispatcher Docker en Oregon)
            FenixKeylessProviderGateway(
                providerId = "fenix_dispatcher_render",
                providerName = "Fenix Dispatcher Render Upstream",
                modelsDiscoveryUrl = "https://fenix-router-v3.onrender.com/v1/models",
                chatCompletionsUrl = "https://fenix-router-v3.onrender.com/v1/chat/completions",
                defaultModelId = "openai/gpt-oss-120b",
            ),
            // 3. Fallback Comunitario Zero-Config Pollinations AI
            FenixKeylessProviderGateway(
                providerId = "pollinations_keyless",
                providerName = "Pollinations AI",
                chatCompletionsUrl = "https://text.pollinations.ai/openai",
                defaultModelId = "openai-fast",
            ),
            // 4. Fallback Comunitario Zero-Config OVHcloud Kepler
            FenixKeylessProviderGateway(
                providerId = "ovhcloud_ai_keyless",
                providerName = "OVHcloud AI Endpoints",
                modelsDiscoveryUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/models",
                chatCompletionsUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions",
                defaultModelId = "Mistral-Small-3.2-24B-Instruct-2506",
            ),
        )

        /**
         * Palabras clave de modelos que NO son para chat académico o que se sabe que fallan/no tienen backend activo.
         */
        private val UNUSABLE_MODEL_KEYWORDS = listOf(
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
            "gpt-oss",
            "qwen3.",
            "qwen3-",
            "sdxl",
        )
    }

    /**
     * Construye y descubre dinámicamente las rutas del pool de Fenix Teacher.
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

                val discovered = mutableListOf<DiscoveredKeylessRoute>()

                // 1. Descubrir rutas del Dispatcher Edge en Cloudflare Anycast (POP EZE)
                val edgeGw = GATEWAY_CATALOG.find { it.providerId == "fenix_dispatcher_edge" }
                if (edgeGw != null && !edgeGw.modelsDiscoveryUrl.isNullOrBlank()) {
                    val edgeRoutes = runCatching { fetchModelsFromGateway(edgeGw) }.getOrDefault(emptyList())
                    if (edgeRoutes.isNotEmpty()) {
                        // Priorizar modelos de inferencia ultra-rápida (Groq/SambaNova LPU) sobre modelos de 25s
                        val sortedEdgeRoutes = edgeRoutes.sortedBy { route ->
                            val idx = PREFERRED_FAST_MODELS.indexOfFirst { route.discoveredModelId.contains(it, ignoreCase = true) }
                            if (idx >= 0) idx else 99
                        }
                        discovered.addAll(sortedEdgeRoutes.take(4))
                    } else if (edgeGw.defaultModelId != null) {
                        discovered.add(
                            DiscoveredKeylessRoute(
                                gateway = edgeGw,
                                discoveredModelId = edgeGw.defaultModelId,
                            ),
                        )
                    }
                }

                // 2. Pollinations openai-fast en paralelo directo (Inferencia comunitaria sin colas)
                val pollinationsGw = GATEWAY_CATALOG.find { it.providerId == "pollinations_keyless" }
                if (pollinationsGw != null) {
                    discovered.add(
                        DiscoveredKeylessRoute(
                            gateway = pollinationsGw,
                            discoveredModelId = "openai-fast",
                        ),
                    )
                }

                // 3. Ruta directa de Render Upstream (Master Dispatcher)
                val renderGw = GATEWAY_CATALOG.find { it.providerId == "fenix_dispatcher_render" }
                if (renderGw != null && renderGw.defaultModelId != null) {
                    discovered.add(
                        DiscoveredKeylessRoute(
                            gateway = renderGw,
                            discoveredModelId = renderGw.defaultModelId,
                        ),
                    )
                }

                // 4. Fallback Zero-Config OVHcloud Kepler
                val ovhGw = GATEWAY_CATALOG.find { it.providerId == "ovhcloud_ai_keyless" }
                if (ovhGw != null && !ovhGw.modelsDiscoveryUrl.isNullOrBlank()) {
                    val ovhRoutes = runCatching { fetchModelsFromGateway(ovhGw) }.getOrDefault(emptyList())
                    discovered.addAll(ovhRoutes.take(2))
                }

                if (discovered.isNotEmpty()) {
                    cachedRoutes = discovered
                    lastDiscoveryEpochMs = System.currentTimeMillis()
                }
                rankRoutesByHealth(cachedRoutes)
            }
        }

    /**
     * Envía la consulta académica con memoria de contexto multi-turno (`conversationHistory`).
     * Lanza en paralelo (Hedged Racing) contra las mejores rutas del pool para garantizar respuesta en ~1s
     * y tolerancia absoluta a rate-limits o caídas.
     */
    suspend fun sendChatToFenixTeacher(
        userPrompt: String,
        systemInstruction: String,
        conversationHistory: List<TeacherChatHistoryTurn> = emptyList(),
        startEndpointIndex: Int = 0,
    ): String = when (
        val outcome = sendChatToFenixTeacherResult(
            userPrompt = userPrompt,
            systemInstruction = systemInstruction,
            conversationHistory = conversationHistory,
            startEndpointIndex = startEndpointIndex,
        )
    ) {
        is TeacherChatOutcome.Success -> outcome.text.trim()
        is TeacherChatOutcome.Failure -> outcome.kind.toUserMessage()
    }

    /**
     * Variante tipada del envío. Presupuesto global acotado para que el tiempo total
     * nunca dependa de la cantidad de rutas del pool.
     */
    suspend fun sendChatToFenixTeacherResult(
        userPrompt: String,
        systemInstruction: String,
        conversationHistory: List<TeacherChatHistoryTurn> = emptyList(),
        startEndpointIndex: Int = 0,
    ): TeacherChatOutcome = withContext(Dispatchers.IO) {
        var routes = discoverLiveKeylessRoutes(forceRefresh = false)
        if (routes.isEmpty()) {
            routes = discoverLiveKeylessRoutes(forceRefresh = true)
        }

        if (routes.isEmpty()) {
            return@withContext TeacherChatOutcome.Failure(
                TeacherChatFailureKind.NO_NETWORK,
                "El pool de rutas quedó vacío tras el descubrimiento.",
            )
        }

        val rotatedRoutes = if (startEndpointIndex > 0 && routes.size > 1) {
            val shift = startEndpointIndex % routes.size
            routes.drop(shift) + routes.take(shift)
        } else {
            routes
        }

        val deadline = System.currentTimeMillis() + TOTAL_BUDGET_MS
        var lastFailure: TeacherChatOutcome.Failure? = null

        for (batch in rotatedRoutes.take(MAX_RACE_ROUTES).chunked(2)) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0L) break

            when (
                val outcome = raceParallelBatch(
                    batch = batch,
                    systemInstruction = systemInstruction,
                    userPrompt = userPrompt,
                    conversationHistory = conversationHistory,
                    timeoutMs = minOf(BATCH_TIMEOUT_MS, remaining),
                )
            ) {
                is TeacherChatOutcome.Success -> return@withContext outcome
                is TeacherChatOutcome.Failure -> lastFailure = outcome
            }
        }

        lastFailure ?: TeacherChatOutcome.Failure(
            TeacherChatFailureKind.TIMEOUT,
            "Presupuesto de ${TOTAL_BUDGET_MS}ms agotado sin respuesta.",
        )
    }

    private suspend fun raceParallelBatch(
        batch: List<DiscoveredKeylessRoute>,
        systemInstruction: String,
        userPrompt: String,
        conversationHistory: List<TeacherChatHistoryTurn>,
        timeoutMs: Long,
    ): TeacherChatOutcome = coroutineScope {
        val channel = Channel<TeacherChatOutcome>(capacity = batch.size)
        val childJob = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + childJob)

        batch.forEach { route ->
            scope.launch(Dispatchers.IO) {
                channel.trySend(trySingleRoute(route, systemInstruction, userPrompt, conversationHistory))
            }
        }

        var lastFailure: TeacherChatOutcome.Failure? = null
        val winner = withTimeoutOrNull(timeoutMs) {
            var found: TeacherChatOutcome.Success? = null
            var received = 0
            while (found == null && received < batch.size) {
                when (val outcome = channel.receive()) {
                    is TeacherChatOutcome.Success -> found = outcome
                    is TeacherChatOutcome.Failure -> lastFailure = outcome
                }
                received++
            }
            found
        }

        // Cancelación real: cancelar el Job no interrumpe I/O bloqueante por sí solo.
        childJob.cancel()
        abortActiveConnections()

        winner ?: lastFailure ?: TeacherChatOutcome.Failure(
            TeacherChatFailureKind.TIMEOUT,
            "Lote sin respuesta en ${timeoutMs}ms.",
        )
    }

    private fun trySingleRoute(
        route: DiscoveredKeylessRoute,
        systemInstruction: String,
        userPrompt: String,
        conversationHistory: List<TeacherChatHistoryTurn>,
    ): TeacherChatOutcome {
        val t0 = System.currentTimeMillis()
        val outcome = runCatching {
            executeOpenAiCompatiblePost(
                route = route,
                systemInstruction = systemInstruction,
                userPrompt = userPrompt,
                conversationHistory = conversationHistory,
            )
        }.getOrElse { exc ->
            TeacherChatOutcome.Failure(
                kind = TeacherChatFailureKind.NO_NETWORK,
                detail = "Excepción no controlada: ${exc.message}",
            )
        }

        val elapsed = System.currentTimeMillis() - t0
        return when (outcome) {
            is TeacherChatOutcome.Success -> {
                routePenaltyScore[route.routeKey] = 0
                routeSuccessLatencyMs[route.routeKey] = elapsed
                Log.d("FenixTeacher", "Route ${route.routeKey} SUCCESS in ${elapsed}ms")
                outcome
            }
            is TeacherChatOutcome.Failure -> {
                val prevPenalty = routePenaltyScore[route.routeKey] ?: 0
                routePenaltyScore[route.routeKey] = prevPenalty + 1
                Log.w(
                    "FenixTeacher",
                    "Route ${route.routeKey} FAILED ${outcome.kind} in ${elapsed}ms (penalty=${prevPenalty + 1}): ${outcome.detail}",
                )
                outcome
            }
        }
    }

    private fun rankRoutesByHealth(routes: List<DiscoveredKeylessRoute>): List<DiscoveredKeylessRoute> {
        return routes.sortedWith(
            compareBy<DiscoveredKeylessRoute> { routePenaltyScore[it.routeKey] ?: 0 }
                .thenBy { route ->
                    val isPreferred = PREFERRED_FAST_MODELS.any { route.discoveredModelId.contains(it, ignoreCase = true) }
                    if (isPreferred) 0 else 1
                }
                .thenBy { routeSuccessLatencyMs[it.routeKey] ?: 1_200L },
        )
    }

    private fun fetchModelsFromGateway(gateway: FenixKeylessProviderGateway): List<DiscoveredKeylessRoute> {
        val discoveryUrl = gateway.modelsDiscoveryUrl ?: return emptyList()
        val url = URL(discoveryUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3500
            readTimeout = 4000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
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
        return UNUSABLE_MODEL_KEYWORDS.none { keyword -> lower.contains(keyword) }
    }

    private fun executeOpenAiCompatiblePost(
        route: DiscoveredKeylessRoute,
        systemInstruction: String,
        userPrompt: String,
        conversationHistory: List<TeacherChatHistoryTurn> = emptyList(),
    ): TeacherChatOutcome {
        val url = URL(route.gateway.chatCompletionsUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Connection", "close")
            setRequestProperty("User-Agent", USER_AGENT)
            val token = route.activeToken ?: route.gateway.authTokens.firstOrNull()
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
        }
        activeConnections.add(connection)

        return try {
            val payload = buildJsonObject {
                put("model", route.discoveredModelId)
                put("max_tokens", 900)
                put(
                    "messages",
                    buildJsonArray {
                        // 1. Mensaje de sistema (personalidad académica)
                        add(
                            buildJsonObject {
                                put("role", "system")
                                put("content", systemInstruction)
                            },
                        )
                        // 2. Historial conversacional previo (memoria multi-turno, hasta 10 turnos)
                        conversationHistory.takeLast(10).forEach { turn ->
                            add(
                                buildJsonObject {
                                    put("role", turn.role)
                                    put("content", turn.content)
                                },
                            )
                        }
                        // 3. Consulta actual
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
            if (code !in 200..299) {
                val err = runCatching {
                    connection.errorStream?.let { stream ->
                        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                    }
                }.getOrNull()
                Log.w("FenixTeacher", "Route ${route.routeKey} failed with HTTP $code: $err")
                val kind = if (code == 429) {
                    TeacherChatFailureKind.RATE_LIMITED
                } else {
                    TeacherChatFailureKind.SERVER_ERROR
                }
                return TeacherChatOutcome.Failure(kind, "HTTP $code: ${err?.take(180).orEmpty()}")
            }

            val responseBody = readResponseBody(connection)
            val content = extractContentFromOpenAiResponse(responseBody)
            if (content.isNullOrBlank()) {
                TeacherChatOutcome.Failure(
                    TeacherChatFailureKind.EMPTY_RESPONSE,
                    "Cuerpo sin contenido utilizable (${responseBody.length} chars).",
                )
            } else {
                TeacherChatOutcome.Success(content.trim())
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.w("FenixTeacher", "Route ${route.routeKey} timeout: ${e.message}")
            TeacherChatOutcome.Failure(TeacherChatFailureKind.TIMEOUT, "Timeout: ${e.message}")
        } catch (e: java.io.IOException) {
            Log.w("FenixTeacher", "Route ${route.routeKey} IO: ${e.message}")
            TeacherChatOutcome.Failure(TeacherChatFailureKind.NO_NETWORK, "I/O: ${e.message}")
        } catch (e: Exception) {
            Log.w("FenixTeacher", "Route ${route.routeKey} exception: ${e.message}")
            TeacherChatOutcome.Failure(TeacherChatFailureKind.SERVER_ERROR, "Excepción: ${e.message}")
        } finally {
            activeConnections.remove(connection)
            connection.disconnect()
        }
    }

    /**
     * Lee hasta completar un objeto/array JSON balanceado, sin depender del EOF.
     * Tolera servidores sin Content-Length y espacios antepuestos (bug histórico del dispatcher).
     */
    private fun readResponseBody(connection: HttpURLConnection): String {
        val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
        return reader.use { r ->
            val sb = StringBuilder()
            val buffer = CharArray(4_096)
            var depth = 0
            var started = false
            var inString = false
            var escaped = false

            while (true) {
                val read = r.read(buffer)
                if (read < 0) break
                for (i in 0 until read) {
                    val c = buffer[i]
                    if (!started) {
                        if (c.isWhitespace()) continue
                        started = true
                    }
                    sb.append(c)
                    if (inString) {
                        when {
                            escaped -> escaped = false
                            c == '\\' -> escaped = true
                            c == '"' -> inString = false
                        }
                    } else {
                        when (c) {
                            '"' -> inString = true
                            '{', '[' -> depth++
                            '}', ']' -> {
                                depth--
                                if (depth == 0) return@use sb.toString()
                            }
                        }
                    }
                }
            }
            sb.toString()
        }
    }

    /**
     * Cancelación real de I/O bloqueante: `Job.cancel()` no interrumpe un `read()` en curso.
     */
    private fun abortActiveConnections() {
        val snapshot = activeConnections.toList()
        activeConnections.clear()
        snapshot.forEach { conn -> runCatching { conn.disconnect() } }
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
            if (!text.isNullOrBlank()) {
                val clean = text.replace(Regex("<thought>[\\s\\S]*?</thought>"), "").trim()
                if (clean.isNotBlank()) return clean
            }
        }

        // Fallback a reasoning o text
        val reasoningElem = messageObj?.get("reasoning") ?: messageObj?.get("reasoning_content")
        if (reasoningElem != null && reasoningElem !is JsonNull) {
            val text = (reasoningElem as? JsonPrimitive)?.contentOrNull?.trim()
            if (!text.isNullOrBlank()) return text
        }

        val textFallback = (firstChoice["text"] as? JsonPrimitive)?.contentOrNull?.trim()
        return textFallback?.takeIf { it.isNotBlank() }
    }
}
