/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:26:00 -03:00
 * ARCHIVO: core/network-ai/src/test/kotlin/com/fenix/studio/core/network/AdaptiveCooldownKeyRouterTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.network

import com.fenix.studio.core.network.model.RemoteRouterPolicyConfig
import com.fenix.studio.core.network.router.AdaptiveCooldownKeyRouter
import com.fenix.studio.core.network.router.EthicalDispatchOutcome
import com.fenix.studio.core.network.router.FenixEthicalRouter
import com.fenix.studio.core.network.router.HttpRateLimitOrServerException
import com.fenix.studio.core.network.router.SlotLeaseDecision
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class AdaptiveCooldownKeyRouterTest {

    @Test
    fun `Sticky Node strategy stays on the active healthy slot for all 10 burst requests without Round-Robin`() = runTest {
        val recordedJitters = mutableListOf<Long>()
        val router = AdaptiveCooldownKeyRouter(
            randomGenerator = Random(42),
            jitterSleeper = { ms -> recordedJitters += ms },
        )

        val usedSlots = mutableListOf<Int>()
        for (i in 1..AdaptiveCooldownKeyRouter.MAX_CONSECUTIVE_BURST_PER_CATEDRA) {
            val lease = router.acquireHealthySlot(nowEpochMs = 1_000L)
            assertTrue(lease is SlotLeaseDecision.Acquired)
            usedSlots += (lease as SlotLeaseDecision.Acquired).slotIndex
        }

        assertEquals(1, usedSlots.distinct().size)
        assertEquals(0, usedSlots.first())

        val eleventhLease = router.acquireHealthySlot(nowEpochMs = 1_000L) as SlotLeaseDecision.Acquired
        assertFalse(eleventhLease.wasStickyHit)
        assertTrue(eleventhLease.slotIndex != 0)
        assertTrue(eleventhLease.appliedJitterMs in 500L..1_250L)
        assertEquals(1, recordedJitters.size)
    }

    @Test
    fun `updateRouterPolicyFromAdmin updates burst limits active slots cooldown and jitter dynamically at runtime`() = runTest {
        val router = AdaptiveCooldownKeyRouter(
            randomGenerator = Random(99),
            jitterSleeper = { _ -> },
        )

        // Actualizamos en caliente la política desde Fenix Admin: ráfaga de 3 peticiones por slot y 4 slots activos
        val adminPolicy = RemoteRouterPolicyConfig(
            activeSlotCount = 4,
            maxConsecutiveBurstPerSlot = 3,
            minJitterMs = 200L,
            maxJitterMs = 300L,
            defaultCooldownMs = 8_000L,
            burstReplenishCooldownMs = 2_000L,
            defaultAssistedGatewayUrl = "https://global-edu-stream.workers.dev/v1",
            defaultAssistedModelId = "admin-dynamic-router-model",
        )
        router.updateRouterPolicyFromAdmin(adminPolicy)

        assertEquals(4, router.snapshotPoolState().size)
        assertEquals("admin-dynamic-router-model", router.getActiveRouterPolicy().defaultAssistedModelId)

        // Las primeras 3 peticiones usan el slot 0; la 4ta petición ya debe rotar por el nuevo límite dinámico de ráfaga (3)
        repeat(3) {
            val lease = router.acquireHealthySlot(nowEpochMs = 5_000L) as SlotLeaseDecision.Acquired
            assertEquals(0, lease.slotIndex)
        }
        val fourthLease = router.acquireHealthySlot(nowEpochMs = 5_000L) as SlotLeaseDecision.Acquired
        assertFalse(fourthLease.wasStickyHit)
        assertTrue(fourthLease.slotIndex in 1..3)
        assertTrue(fourthLease.appliedJitterMs in 200L..300L)
    }

    @Test
    fun `Gate F2 simulation handles 50 concurrent HTTP 429 bursts with thread-safe failover and zero prompt loss`() = runTest {
        val router = AdaptiveCooldownKeyRouter(
            randomGenerator = Random(2026),
            jitterSleeper = { _ -> },
        )
        val ethicalRouter = FenixEthicalRouter(keyRouter = router)
        val studentPrompt = "Desarrollar la síntesis expositiva de la Unidad 4 con mapeo 1:1 de conceptos."
        val simulated429Counter = AtomicInteger(0)

        val outcomes = (1..50).map { requestIdx ->
            async {
                ethicalRouter.executeWithEthicalFailover(
                    userPrompt = "$studentPrompt [req-$requestIdx]",
                    nowEpochMsProvider = { 10_000L },
                ) { _, slotIdx, prompt ->
                    val current429 = simulated429Counter.incrementAndGet()
                    if (current429 <= 50) {
                        throw HttpRateLimitOrServerException(
                            statusCode = 429,
                            retryAfterSeconds = 5,
                            message = "Simulated WAF HTTP 429 on slot $slotIdx",
                        )
                    }
                    "Respuesta exitosa para: $prompt"
                }
            }
        }.awaitAll()

        assertEquals(50, outcomes.size)
        outcomes.forEachIndexed { idx, outcome ->
            val expectedPrompt = "$studentPrompt [req-${idx + 1}]"
            when (outcome) {
                is EthicalDispatchOutcome.CooldownHold -> {
                    assertEquals(expectedPrompt, outcome.event.preservedUserPrompt)
                    assertTrue(outcome.event.waitSeconds >= 1)
                }
                is EthicalDispatchOutcome.Success -> {
                    assertTrue(outcome.responseText.contains(expectedPrompt))
                }
            }
        }

        val recoveredOutcome = ethicalRouter.executeWithEthicalFailover(
            userPrompt = studentPrompt,
            nowEpochMsProvider = { 16_000L },
        ) { _, slotIdx, _ ->
            "Síntesis completada en slot $slotIdx"
        }
        assertTrue(recoveredOutcome is EthicalDispatchOutcome.Success)
    }
}
