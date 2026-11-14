/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-14 20:55:00 -03:00
 * ARCHIVO: core/network-ai/src/main/kotlin/com/fenix/studio/core/network/router/ByokTeacherChatEngine.kt
 * ================================================================================
 */

package com.fenix.studio.core.network.router

import com.fenix.studio.core.network.catalog.AiProviderCatalog
import com.fenix.studio.core.network.catalog.AiProviderSpec
import com.fenix.studio.core.network.teacher.FenixTeacherKeylessService
import com.fenix.studio.core.network.teacher.TeacherChatHistoryTurn
import com.fenix.studio.core.network.teacher.TeacherChatOutcome
import com.fenix.studio.core.security.keystore.EncryptedApiKeySlot
import com.fenix.studio.core.security.keystore.FenixKeystoreVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Router interno del chat académico (`F3`).
 *
 * Orden de intentos:
 * 1. **Claves del usuario**, proveedor por proveedor en orden de anillo (1 → 4).
 * 2. Dentro de un proveedor, sus ranuras `0..5` en orden.
 * 3. Si no hay claves o todas fallan, delega en el motor keyless (red de contención).
 *
 * La decisión es explícita: la clave que la persona cargó se usa primero por calidad y
 * cuota previsible; la red comunitaria evita que el chat quede mudo.
 */
class ByokTeacherChatEngine(
    private val vault: FenixKeystoreVault?,
    private val transport: ChatCompletionTransport = JsonChatCompletionTransport(),
    private val keylessFallback: TeacherChatEngine = FenixTeacherKeylessService(),
    private val providers: List<AiProviderSpec> = AiProviderCatalog.byokProviders(),
) : TeacherChatEngine {

    override var lastRespondingProviderLabel: String? = null
        private set

    override suspend fun sendChatToFenixTeacherResult(
        userPrompt: String,
        systemInstruction: String,
        conversationHistory: List<TeacherChatHistoryTurn>,
        startEndpointIndex: Int,
    ): TeacherChatOutcome = withContext(Dispatchers.IO) {
        lastRespondingProviderLabel = null

        var lastFailure: TeacherChatOutcome.Failure? = null
        for (route in buildRoutes(startEndpointIndex)) {
            val result = transport.complete(
                ChatCompletionRequest(
                    spec = route.spec,
                    apiKey = route.apiKey,
                    modelId = route.modelId,
                    systemInstruction = systemInstruction,
                    userPrompt = userPrompt,
                    conversationHistory = conversationHistory,
                ),
            )
            when (result) {
                is ChatCompletionResult.Success -> {
                    lastRespondingProviderLabel = route.label
                    return@withContext TeacherChatOutcome.Success(result.text.trim())
                }
                is ChatCompletionResult.Failure -> {
                    lastFailure = TeacherChatOutcome.Failure(result.kind, result.detail)
                }
            }
        }

        val fallbackOutcome = keylessFallback.sendChatToFenixTeacherResult(
            userPrompt = userPrompt,
            systemInstruction = systemInstruction,
            conversationHistory = conversationHistory,
            startEndpointIndex = startEndpointIndex,
        )
        if (fallbackOutcome is TeacherChatOutcome.Success) {
            lastRespondingProviderLabel =
                keylessFallback.lastRespondingProviderLabel ?: "Red comunitaria sin clave"
            return@withContext fallbackOutcome
        }

        lastFailure ?: fallbackOutcome
    }

    /**
     * Arma las rutas BYOK disponibles. Un proveedor entra solo si tiene clave cifrada y
     * modelo por defecto conocido: no se adivina un modelo que el proveedor no publicó.
     */
    fun buildRoutes(startEndpointIndex: Int = 0): List<ByokRoute> {
        val vault = this.vault ?: return emptyList()

        val routes = providers
            .sortedBy { it.ring.order }
            .flatMap { spec ->
                val modelId = spec.defaultModelId?.takeIf { it.isNotBlank() } ?: return@flatMap emptyList()
                if (spec.chatCompletionsUrl.contains("{")) return@flatMap emptyList()

                val slots: List<EncryptedApiKeySlot> = runCatching {
                    vault.listConfiguredSlots(spec.id)
                }.getOrElse { emptyList() }

                slots.sortedBy { it.slotIndex }.mapNotNull { slot ->
                    val key = runCatching {
                        vault.getDecryptedApiKey(spec.id, slot.slotIndex)
                    }.getOrNull()
                    if (key.isNullOrBlank()) {
                        null
                    } else {
                        ByokRoute(
                            spec = spec,
                            slotIndex = slot.slotIndex,
                            apiKey = key,
                            modelId = modelId,
                            label = spec.displayName,
                        )
                    }
                }
            }

        if (routes.isEmpty() || startEndpointIndex <= 0) return routes
        val shift = startEndpointIndex % routes.size
        return routes.drop(shift) + routes.take(shift)
    }

    /** Una ruta concreta: proveedor + ranura + clave descifrada + modelo. */
    data class ByokRoute(
        val spec: AiProviderSpec,
        val slotIndex: Int,
        val apiKey: String,
        val modelId: String,
        val label: String,
    ) {
        override fun toString(): String =
            "ByokRoute(provider=${spec.id}, slot=$slotIndex, model=$modelId)"
    }
}
