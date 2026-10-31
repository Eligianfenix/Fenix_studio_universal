/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/network-ai/src/main/kotlin/com/fenix/studio/core/network/router/FenixEthicalRouter.kt
 * ================================================================================
 */

package com.fenix.studio.core.network.router

import com.fenix.studio.core.network.model.AiProviderConfig
import com.fenix.studio.core.network.model.FenixStreamEvent

/**
 * Resultado del despacho de una consulta académica a través de `FenixEthicalRouter`.
 */
sealed interface EthicalDispatchOutcome {
    data class Success(
        val responseText: String,
        val usedProviderId: String,
        val usedSlotIndex: Int,
        val failoversPerformed: Int,
    ) : EthicalDispatchOutcome

    /**
     * Cuando las 6 ranuras se encuentran en enfriamiento simultáneo, el router retiene o notifica
     * a la UI emitiendo `PoolCooldownActive` y preservando intacto el `preservedUserPrompt` (RN-07).
     */
    data class CooldownHold(
        val event: FenixStreamEvent.PoolCooldownActive,
    ) : EthicalDispatchOutcome
}

/**
 * Excepción tipada para reportar códigos HTTP 429 o 5xx desde el transporte hacia el router.
 */
class HttpRateLimitOrServerException(
    val statusCode: Int,
    val retryAfterSeconds: Int? = null,
    override val message: String = "HTTP $statusCode",
) : Exception(message)

/**
 * Enrutador Ético de Alta Disponibilidad de 4 niveles (`FenixEthicalRouter`, `MF-2.1`, `RN-25`).
 * - Trae activo de fábrica el proveedor predeterminado **"Fénix IA (Gratuito Asistido)"** (`fenix_ia_assisted`).
 * - Ante errores HTTP 429 o 5xx en una clave, rota automáticamente a través de `AdaptiveCooldownKeyRouter`
 *   sin perder jamás el prompt del estudiante.
 */
class FenixEthicalRouter(
    private val keyRouter: AdaptiveCooldownKeyRouter,
    private val activeProvider: AiProviderConfig = AiProviderConfig.createDefaultFenixIaProvider(),
) {

    fun getActiveProviderConfig(): AiProviderConfig = activeProvider

    /**
     * Ejecuta una petición protegida con reintento automático multi-clave ante HTTP 429/5xx
     * y preservación garantizada del prompt del estudiante.
     */
    suspend fun executeWithEthicalFailover(
        userPrompt: String,
        nowEpochMsProvider: () -> Long = { System.currentTimeMillis() },
        maxAttempts: Int = AdaptiveCooldownKeyRouter.POOL_SIZE,
        networkCall: suspend (provider: AiProviderConfig, slotIndex: Int, prompt: String) -> String,
    ): EthicalDispatchOutcome {
        require(userPrompt.isNotBlank()) { "El prompt del estudiante no puede estar vacío." }

        var attempts = 0
        while (attempts < maxAttempts) {
            val nowMs = nowEpochMsProvider()
            when (val lease = keyRouter.acquireHealthySlot(nowMs)) {
                is SlotLeaseDecision.AllSlotsInCooldown -> {
                    return EthicalDispatchOutcome.CooldownHold(
                        event = FenixStreamEvent.PoolCooldownActive(
                            preservedUserPrompt = userPrompt,
                            waitSeconds = lease.minimumWaitSeconds,
                            providerName = activeProvider.displayName,
                        ),
                    )
                }
                is SlotLeaseDecision.Acquired -> {
                    try {
                        val response = networkCall(activeProvider, lease.slotIndex, userPrompt)
                        return EthicalDispatchOutcome.Success(
                            responseText = response,
                            usedProviderId = activeProvider.providerId,
                            usedSlotIndex = lease.slotIndex,
                            failoversPerformed = attempts,
                        )
                    } catch (rateEx: HttpRateLimitOrServerException) {
                        keyRouter.reportHttp429OrServerError(
                            failedSlotIndex = lease.slotIndex,
                            retryAfterHeaderSeconds = rateEx.retryAfterSeconds,
                            nowEpochMs = nowEpochMsProvider(),
                        )
                        attempts++
                    }
                }
            }
        }

        // Si tras rotar por los intentos máximos todo el pool quedó en cooldown, preservar el prompt intacto
        val finalCheck = keyRouter.acquireHealthySlot(nowEpochMsProvider())
        val waitSecs = (finalCheck as? SlotLeaseDecision.AllSlotsInCooldown)?.minimumWaitSeconds ?: 4
        return EthicalDispatchOutcome.CooldownHold(
            event = FenixStreamEvent.PoolCooldownActive(
                preservedUserPrompt = userPrompt,
                waitSeconds = waitSecs,
                providerName = activeProvider.displayName,
            ),
        )
    }
}
