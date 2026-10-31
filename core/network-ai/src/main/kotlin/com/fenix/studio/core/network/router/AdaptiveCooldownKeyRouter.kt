/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:25:45 -03:00
 * ARCHIVO: core/network-ai/src/main/kotlin/com/fenix/studio/core/network/router/AdaptiveCooldownKeyRouter.kt
 * ================================================================================
 */

package com.fenix.studio.core.network.router

import com.fenix.studio.core.network.model.RemoteRouterPolicyConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import kotlin.math.ceil
import kotlin.random.Random

/**
 * Estado interno de salud y Token Bucket de una ranura del pool.
 */
data class KeySlotHealthState(
    val slotIndex: Int,
    val remainingBurstTokens: Int = AdaptiveCooldownKeyRouter.MAX_CONSECUTIVE_BURST_PER_CATEDRA,
    val cooldownUntilEpochMs: Long = 0L,
    val consecutiveFailures: Int = 0,
) {
    fun isHealthyAt(nowEpochMs: Long): Boolean =
        nowEpochMs >= cooldownUntilEpochMs && remainingBurstTokens > 0
}

/**
 * Resultado de la adquisición de una ranura del pool.
 */
sealed interface SlotLeaseDecision {
    data class Acquired(
        val slotIndex: Int,
        val wasStickyHit: Boolean,
        val appliedJitterMs: Long,
        val remainingTokensInSlot: Int,
    ) : SlotLeaseDecision

    data class AllSlotsInCooldown(
        val minimumWaitSeconds: Int,
        val earliestAvailableEpochMs: Long,
    ) : SlotLeaseDecision
}

/**
 * Coordinador Multiclave con Cooldown Adaptativo, Token Bucket, Jitter Anti-WAF y Gobernanza Dinámica
 * desde **`Fenix Admin`** (`DT-MEDIA-01`, `RN-07`).
 *
 * Todos los parámetros operativos (`activeSlotCount`, `maxConsecutiveBurstPerSlot`, `minJitterMs`,
 * `maxJitterMs`, `defaultCooldownMs`, `burstReplenishCooldownMs`) son variables dinámicas actualizables
 * en caliente mediante `updateRouterPolicyFromAdmin(newPolicy)` desde la futura app `Fenix Admin`.
 */
class AdaptiveCooldownKeyRouter(
    initialPolicy: RemoteRouterPolicyConfig = RemoteRouterPolicyConfig(),
    configuredSlotIndicesOverride: Set<Int>? = null,
    private val randomGenerator: Random = Random(SecureRandom().nextLong()),
    private val jitterSleeper: suspend (Long) -> Unit = { ms -> delay(ms) },
) {
    private val mutex = Mutex()
    private val secureRandom = SecureRandom()

    private var currentPolicy: RemoteRouterPolicyConfig = initialPolicy
    private var configuredSlotIndices: Set<Int> =
        configuredSlotIndicesOverride ?: (0 until initialPolicy.activeSlotCount.coerceAtLeast(1)).toSet()

    private var slots: Array<KeySlotHealthState> = Array(
        maxOf(POOL_SIZE, initialPolicy.activeSlotCount, (configuredSlotIndices.maxOrNull() ?: 0) + 1),
    ) { index ->
        KeySlotHealthState(
            slotIndex = index,
            remainingBurstTokens = initialPolicy.maxConsecutiveBurstPerSlot,
        )
    }

    private var stickySlotIndex: Int = configuredSlotIndices.minOrNull() ?: 0

    init {
        require(configuredSlotIndices.isNotEmpty() && configuredSlotIndices.all { it >= 0 }) {
            "Los slots configurados deben ser un conjunto de índices no negativos."
        }
    }

    /**
     * Actualiza en tiempo real la política del enrutador multiclave desde **`Fenix Admin`**
     * (ráfaga máxima de llamadas por slot, cantidad de slots activos, tiempos de enfriamiento y jitter)
     * sin necesidad de recompilar ni reiniciar la aplicación.
     */
    suspend fun updateRouterPolicyFromAdmin(newPolicy: RemoteRouterPolicyConfig) {
        val safeSlotCount = newPolicy.activeSlotCount.coerceIn(1, 32)
        val safeBurst = newPolicy.maxConsecutiveBurstPerSlot.coerceIn(1, 500)
        mutex.withLock {
            currentPolicy = newPolicy.copy(
                activeSlotCount = safeSlotCount,
                maxConsecutiveBurstPerSlot = safeBurst,
            )
            configuredSlotIndices = (0 until safeSlotCount).toSet()
            val newSlots = Array(maxOf(slots.size, safeSlotCount)) { idx ->
                val previous = slots.getOrNull(idx)
                KeySlotHealthState(
                    slotIndex = idx,
                    remainingBurstTokens = previous?.remainingBurstTokens?.coerceAtMost(safeBurst) ?: safeBurst,
                    cooldownUntilEpochMs = previous?.cooldownUntilEpochMs ?: 0L,
                    consecutiveFailures = previous?.consecutiveFailures ?: 0,
                )
            }
            slots = newSlots
            if (stickySlotIndex !in configuredSlotIndices) {
                stickySlotIndex = configuredSlotIndices.minOrNull() ?: 0
            }
        }
    }

    suspend fun getActiveRouterPolicy(): RemoteRouterPolicyConfig = mutex.withLock { currentPolicy }

    /**
     * Adquiere una ranura saludable respetando la adherencia *Sticky Node* o conmutando pseudoaleatoriamente.
     */
    suspend fun acquireHealthySlot(nowEpochMs: Long): SlotLeaseDecision {
        var jitterToApplyMs = 0L
        val decision: SlotLeaseDecision = mutex.withLock {
            refillExpiredCooldownBucketsLocked(nowEpochMs)

            val currentSticky = slots[stickySlotIndex]
            if (stickySlotIndex in configuredSlotIndices && currentSticky.isHealthyAt(nowEpochMs)) {
                val updated = currentSticky.copy(
                    remainingBurstTokens = currentSticky.remainingBurstTokens - 1,
                )
                slots[stickySlotIndex] = updated
                SlotLeaseDecision.Acquired(
                    slotIndex = stickySlotIndex,
                    wasStickyHit = true,
                    appliedJitterMs = 0L,
                    remainingTokensInSlot = updated.remainingBurstTokens,
                )
            } else {
                val healthyAlternatives = configuredSlotIndices
                    .filter { idx -> idx != stickySlotIndex && slots[idx].isHealthyAt(nowEpochMs) }
                    .ifEmpty {
                        configuredSlotIndices.filter { idx -> slots[idx].isHealthyAt(nowEpochMs) }
                    }

                if (healthyAlternatives.isEmpty()) {
                    val earliestEpoch = configuredSlotIndices
                        .map { idx -> slots[idx].cooldownUntilEpochMs }
                        .filter { it > nowEpochMs }
                        .minOrNull() ?: (nowEpochMs + currentPolicy.defaultCooldownMs)
                    val waitSec = ceil((earliestEpoch - nowEpochMs).toDouble() / 1000.0)
                        .toInt()
                        .coerceAtLeast(1)
                    SlotLeaseDecision.AllSlotsInCooldown(
                        minimumWaitSeconds = waitSec,
                        earliestAvailableEpochMs = earliestEpoch,
                    )
                } else {
                    val chosenSlot = selectPseudoRandomAlternativeLocked(healthyAlternatives)
                    stickySlotIndex = chosenSlot
                    jitterToApplyMs = generateAntiWafJitterMsLocked()
                    val targetState = slots[chosenSlot]
                    val updated = targetState.copy(
                        remainingBurstTokens = targetState.remainingBurstTokens - 1,
                    )
                    slots[chosenSlot] = updated
                    SlotLeaseDecision.Acquired(
                        slotIndex = chosenSlot,
                        wasStickyHit = false,
                        appliedJitterMs = jitterToApplyMs,
                        remainingTokensInSlot = updated.remainingBurstTokens,
                    )
                }
            }
        }

        if (jitterToApplyMs > 0L) {
            jitterSleeper(jitterToApplyMs)
        }
        return decision
    }

    /**
     * Registra un error HTTP 429 o 5xx en una ranura y rota el puntero *Sticky Node* pseudoaleatoriamente.
     */
    suspend fun reportHttp429OrServerError(
        failedSlotIndex: Int,
        retryAfterHeaderSeconds: Int? = null,
        nowEpochMs: Long,
    ) {
        mutex.withLock {
            require(failedSlotIndex in slots.indices)
            val current = slots[failedSlotIndex]
            val cooldownMs = if (retryAfterHeaderSeconds != null && retryAfterHeaderSeconds > 0) {
                retryAfterHeaderSeconds * 1_000L
            } else {
                currentPolicy.defaultCooldownMs
            }
            val nextUntil = maxOf(current.cooldownUntilEpochMs, nowEpochMs + cooldownMs)
            slots[failedSlotIndex] = current.copy(
                remainingBurstTokens = 0,
                cooldownUntilEpochMs = nextUntil,
                consecutiveFailures = current.consecutiveFailures + 1,
            )

            if (stickySlotIndex == failedSlotIndex) {
                val healthyCandidates = configuredSlotIndices.filter { idx ->
                    idx != failedSlotIndex && slots[idx].isHealthyAt(nowEpochMs)
                }
                if (healthyCandidates.isNotEmpty()) {
                    stickySlotIndex = selectPseudoRandomAlternativeLocked(healthyCandidates)
                }
            }
        }
    }

    suspend fun snapshotPoolState(): List<KeySlotHealthState> = mutex.withLock {
        configuredSlotIndices.sorted().map { slots[it] }
    }

    private fun refillExpiredCooldownBucketsLocked(nowEpochMs: Long) {
        val burstLimit = currentPolicy.maxConsecutiveBurstPerSlot
        for (idx in configuredSlotIndices) {
            val state = slots[idx]
            if (state.cooldownUntilEpochMs in 1..nowEpochMs) {
                slots[idx] = state.copy(
                    remainingBurstTokens = burstLimit,
                    cooldownUntilEpochMs = 0L,
                    consecutiveFailures = 0,
                )
            } else if (state.cooldownUntilEpochMs == 0L && state.remainingBurstTokens <= 0) {
                slots[idx] = state.copy(
                    cooldownUntilEpochMs = nowEpochMs + currentPolicy.burstReplenishCooldownMs,
                )
            }
        }
    }

    private fun selectPseudoRandomAlternativeLocked(candidates: List<Int>): Int {
        if (candidates.size == 1) return candidates.first()
        val randomIdx = randomGenerator.nextInt(candidates.size)
        return candidates[randomIdx]
    }

    private fun generateAntiWafJitterMsLocked(): Long {
        val minJ = currentPolicy.minJitterMs.coerceAtLeast(0L)
        val maxJ = maxOf(minJ, currentPolicy.maxJitterMs)
        val span = (maxJ - minJ + 1L).toInt().coerceAtLeast(1)
        return minJ + secureRandom.nextInt(span).toLong()
    }

    companion object {
        const val POOL_SIZE: Int = 6
        const val MAX_CONSECUTIVE_BURST_PER_CATEDRA: Int = 10
        const val MIN_JITTER_MS: Long = 500L
        const val MAX_JITTER_MS: Long = 1_250L
        const val DEFAULT_COOLDOWN_MS: Long = 4_000L
        const val BURST_REPLENISH_COOLDOWN_MS: Long = 1_500L
    }
}
