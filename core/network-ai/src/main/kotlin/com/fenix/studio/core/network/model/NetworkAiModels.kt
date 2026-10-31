/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:25:30 -03:00
 * ARCHIVO: core/network-ai/src/main/kotlin/com/fenix/studio/core/network/model/NetworkAiModels.kt
 * ================================================================================
 */

package com.fenix.studio.core.network.model

import kotlinx.serialization.Serializable

/**
 * Los 4 niveles del Enjambre Ético a Costo Cero de Fenix Studio (`RN-25`, `DT-MEDIA-01`).
 */
enum class RouterTier(val priorityOrder: Int) {
    TIER_1_STATIC_CDN(1),
    TIER_2_ZERO_CONFIG(2),
    TIER_3_CREATOR_FREE_POOL(3),
    TIER_4_STUDENT_BYOK(4),
}

/**
 * Política dinámica del enrutador multiclave gobernable en tiempo real desde la futura app **`Fenix Admin`**.
 * Todos los parámetros de ráfaga, cantidad de slots, enfriamiento, jitter y modelo/gateway asistido
 * pueden actualizarse en vivo desde `Fenix Admin` sin recompilar la APK.
 */
@Serializable
data class RemoteRouterPolicyConfig(
    val activeSlotCount: Int = 6,
    val maxConsecutiveBurstPerSlot: Int = 10,
    val minJitterMs: Long = 500L,
    val maxJitterMs: Long = 1_250L,
    val defaultCooldownMs: Long = 4_000L,
    val burstReplenishCooldownMs: Long = 1_500L,
    val defaultAssistedGatewayUrl: String = "https://academic-edge-proxy.workers.dev/v1",
    val defaultAssistedModelId: String? = null,
)

/**
 * Configuración inmutable de un proveedor de IA dentro del enrutador ético.
 */
@Serializable
data class AiProviderConfig(
    val providerId: String,
    val displayName: String,
    val tier: RouterTier,
    val baseUrl: String,
    val isDefaultAssistedPool: Boolean = false,
    val dynamicDefaultModelId: String? = null,
) {
    companion object {
        const val DEFAULT_FENIX_IA_PROVIDER_ID: String = "fenix_ia_assisted"
        const val DEFAULT_FENIX_IA_DISPLAY_NAME: String = "Fénix IA (Gratuito Asistido)"

        fun createDefaultFenixIaProvider(
            edgeGatewayBaseUrl: String = "https://academic-edge-proxy.workers.dev/v1",
            dynamicModelId: String? = null,
        ): AiProviderConfig = AiProviderConfig(
            providerId = DEFAULT_FENIX_IA_PROVIDER_ID,
            displayName = DEFAULT_FENIX_IA_DISPLAY_NAME,
            tier = RouterTier.TIER_3_CREATOR_FREE_POOL,
            baseUrl = edgeGatewayBaseUrl,
            isDefaultAssistedPool = true,
            dynamicDefaultModelId = dynamicModelId,
        )
    }
}

/**
 * Mensaje conversacional enviado al modelo de lenguaje.
 */
@Serializable
data class LlmMessage(
    val role: String,
    val content: String,
)

/**
 * Invocación de herramienta MCP estructurada.
 */
@Serializable
data class LlmToolCall(
    val callId: String,
    val toolName: String,
    val argumentsJson: String,
)

/**
 * Eventos reactivos emitidos por el enrutador y el streamer SSE hacia la UI y Room.
 */
sealed interface FenixStreamEvent {
    data class TokenDelta(
        val deltaText: String,
        val activeSlotIndex: Int,
        val providerId: String,
    ) : FenixStreamEvent

    data class AtomicBlockReady(
        val blockText: String,
        val sequenceIndex: Int,
    ) : FenixStreamEvent

    /**
     * Emitido cuando la totalidad del pool entra temporalmente en enfriamiento (RN-07).
     * Garantiza por contrato que `preservedUserPrompt` conserve 100% intacta la consulta del alumno.
     */
    data class PoolCooldownActive(
        val preservedUserPrompt: String,
        val waitSeconds: Int,
        val providerName: String,
    ) : FenixStreamEvent

    data class StreamCompleted(
        val totalCharacters: Int,
        val finalSlotIndex: Int,
    ) : FenixStreamEvent
}
