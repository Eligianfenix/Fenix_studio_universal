/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-14 21:10:00 -03:00
 * ARCHIVO: core/network-ai/src/test/kotlin/com/fenix/studio/core/network/router/ByokTeacherChatEngineTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.network.router

import com.fenix.studio.core.network.teacher.TeacherChatFailureKind
import com.fenix.studio.core.network.teacher.TeacherChatHistoryTurn
import com.fenix.studio.core.network.teacher.TeacherChatOutcome
import com.fenix.studio.core.security.keystore.FenixKeystoreVault
import com.fenix.studio.core.security.keystore.JvmMemoryMasterKeyProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ByokTeacherChatEngineTest {

    private val vault = FenixKeystoreVault(JvmMemoryMasterKeyProvider())

    @Test
    fun `routes put user keys before the keyless fallback and order them by ring`() {
        vault.storeApiKey("groq", 0, "gsk-groq")
        vault.storeApiKey("gemini", 0, "gemini-key")

        val engine = ByokTeacherChatEngine(
            vault = vault,
            transport = RecordingTransport(mapOf()),
            keylessFallback = FakeFallback(TeacherChatOutcome.Success("nunca")),
        )

        val routes = engine.buildRoutes()
        assertEquals(listOf("gemini", "groq"), routes.map { it.spec.id })
    }

    @Test
    fun `providers without a stored key are not attempted`() {
        vault.storeApiKey("groq", 0, "gsk-groq")

        val transport = RecordingTransport(mapOf("groq" to ChatCompletionResult.Success("hola")))
        val engine = ByokTeacherChatEngine(
            vault = vault,
            transport = transport,
            keylessFallback = FakeFallback(TeacherChatOutcome.Success("nunca")),
        )

        val outcome = runBlocking { engine.sendChatToFenixTeacherResult("pregunta", "sistema") }

        assertEquals(TeacherChatOutcome.Success("hola"), outcome)
        assertEquals(listOf("groq"), transport.requests.map { it.spec.id })
        assertEquals("Groq Cloud", engine.lastRespondingProviderLabel)
    }

    @Test
    fun `a rate limited key falls through to the next provider`() {
        vault.storeApiKey("gemini", 0, "gemini-key")
        vault.storeApiKey("groq", 0, "gsk-groq")

        val transport = RecordingTransport(
            mapOf(
                "gemini" to ChatCompletionResult.Failure(TeacherChatFailureKind.RATE_LIMITED, "429"),
                "groq" to ChatCompletionResult.Success("respuesta de groq"),
            ),
        )
        val engine = ByokTeacherChatEngine(
            vault = vault,
            transport = transport,
            keylessFallback = FakeFallback(TeacherChatOutcome.Success("nunca")),
        )

        val outcome = runBlocking { engine.sendChatToFenixTeacherResult("pregunta", "sistema") }

        assertEquals(TeacherChatOutcome.Success("respuesta de groq"), outcome)
        assertEquals(listOf("gemini", "groq"), transport.requests.map { it.spec.id })
        assertEquals("Groq Cloud", engine.lastRespondingProviderLabel)
    }

    @Test
    fun `rotating slots tries the other key of the same provider`() {
        vault.storeApiKey("groq", 0, "slot-0")
        vault.storeApiKey("groq", 3, "slot-3")

        val engine = ByokTeacherChatEngine(
            vault = vault,
            transport = RecordingTransport(mapOf()),
            keylessFallback = FakeFallback(TeacherChatOutcome.Success("nunca")),
        )

        val routes = engine.buildRoutes(startEndpointIndex = 1)
        assertEquals(listOf(3, 0), routes.map { it.slotIndex })
        assertEquals("slot-3", routes.first().apiKey)
    }

    @Test
    fun `without user keys the keyless fallback answers and names its provider`() {
        val fallback = FakeFallback(
            outcome = TeacherChatOutcome.Success("respuesta comunitaria"),
            label = "Pollinations AI",
        )
        val engine = ByokTeacherChatEngine(
            vault = vault,
            transport = RecordingTransport(mapOf()),
            keylessFallback = fallback,
        )

        val outcome = runBlocking { engine.sendChatToFenixTeacherResult("pregunta", "sistema") }

        assertTrue(outcome is TeacherChatOutcome.Success)
        assertEquals(1, fallback.calls)
        assertEquals("Pollinations AI", engine.lastRespondingProviderLabel)
    }

    @Test
    fun `when every BYOK route fails the typed failure is preserved`() {
        vault.storeApiKey("groq", 0, "gsk-groq")

        val engine = ByokTeacherChatEngine(
            vault = vault,
            transport = RecordingTransport(
                mapOf("groq" to ChatCompletionResult.Failure(TeacherChatFailureKind.RATE_LIMITED, "429")),
            ),
            keylessFallback = FakeFallback(
                TeacherChatOutcome.Failure(TeacherChatFailureKind.NO_NETWORK, "sin red"),
            ),
        )

        val outcome = runBlocking { engine.sendChatToFenixTeacherResult("pregunta", "sistema") }

        assertEquals(TeacherChatOutcome.Failure(TeacherChatFailureKind.RATE_LIMITED, "429"), outcome)
    }

    @Test
    fun `history is forwarded to the transport`() {
        vault.storeApiKey("groq", 0, "gsk-groq")
        val transport = RecordingTransport(mapOf("groq" to ChatCompletionResult.Success("ok")))
        val engine = ByokTeacherChatEngine(
            vault = vault,
            transport = transport,
            keylessFallback = FakeFallback(TeacherChatOutcome.Success("nunca")),
        )
        val history = listOf(
            TeacherChatHistoryTurn("user", "turno previo"),
            TeacherChatHistoryTurn("assistant", "respuesta previa"),
        )

        runBlocking {
            engine.sendChatToFenixTeacherResult(
                userPrompt = "nueva pregunta",
                systemInstruction = "sistema",
                conversationHistory = history,
            )
        }

        assertEquals(history, transport.requests.single().conversationHistory)
        assertEquals("nueva pregunta", transport.requests.single().userPrompt)
    }

    private class RecordingTransport(
        private val behaviors: Map<String, ChatCompletionResult>,
    ) : ChatCompletionTransport {

        val requests = mutableListOf<ChatCompletionRequest>()

        override suspend fun complete(request: ChatCompletionRequest): ChatCompletionResult {
            requests += request
            return behaviors[request.spec.id]
                ?: ChatCompletionResult.Failure(TeacherChatFailureKind.NO_NETWORK, "sin ruta")
        }
    }

    private class FakeFallback(
        private val outcome: TeacherChatOutcome,
        private val label: String? = null,
    ) : TeacherChatEngine {

        var calls: Int = 0
            private set

        override val lastRespondingProviderLabel: String? = label

        override suspend fun sendChatToFenixTeacherResult(
            userPrompt: String,
            systemInstruction: String,
            conversationHistory: List<TeacherChatHistoryTurn>,
            startEndpointIndex: Int,
        ): TeacherChatOutcome {
            calls++
            return outcome
        }
    }
}
