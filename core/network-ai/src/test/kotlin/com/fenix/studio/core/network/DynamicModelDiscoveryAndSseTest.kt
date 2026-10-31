/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/network-ai/src/test/kotlin/com/fenix/studio/core/network/DynamicModelDiscoveryAndSseTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.network

import com.fenix.studio.core.database.entity.ProtocolBlockEntity
import com.fenix.studio.core.network.discovery.DynamicModelDiscoveryService
import com.fenix.studio.core.network.streaming.KtorZeroCopySseStreamer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DynamicModelDiscoveryAndSseTest {

    @Test
    fun `DynamicModelDiscoveryService introspects GET models endpoint without hardcoded model lists`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("https://api.example-llm.org/v1/models", request.url.toString())
            assertEquals("Bearer test-byok-token", request.headers[HttpHeaders.Authorization])

            respond(
                content = """
                    {
                      "data": [
                        {
                          "id": "dynamic-academic-model-pro",
                          "display_name": "Dynamic Academic Pro",
                          "context_window": 131072,
                          "supports_vision": true,
                          "supports_json_schema": true
                        },
                        {
                          "id": "dynamic-fast-synthesizer",
                          "display_name": "Dynamic Fast Synthesizer",
                          "context_window": 65536,
                          "supports_vision": false,
                          "supports_json_schema": true
                        }
                      ]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = HttpClient(mockEngine)
        val discoveryService = DynamicModelDiscoveryService(httpClient = client)

        val models = discoveryService.discoverAndPersistModels(
            providerId = "custom_provider",
            baseUrl = "https://api.example-llm.org/v1/",
            apiKey = "test-byok-token",
            nowEpochMs = 1_730_000_000_000L,
        )

        assertEquals(2, models.size)
        assertEquals("dynamic-academic-model-pro", models[0].modelId)
        assertEquals(131072, models[0].contextWindowTokens)
        assertTrue(models[0].supportsVision)
        assertTrue(models[0].isSelected)
        assertEquals("dynamic-fast-synthesizer", models[1].modelId)
    }

    @Test
    fun `KtorZeroCopySseStreamer processes 8 KB SSE stream and flushes atomic blocks under 12 KB ceiling`() = runTest {
        val streamer = KtorZeroCopySseStreamer(atomicBlockFlushThresholdChars = 512)
        val paragraphSegment = "Desarrollo riguroso basado en la fuente documental provista. "

        val sseLines = buildString {
            repeat(25) {
                appendLine("""data: {"choices":[{"delta":{"content":"$paragraphSegment"}}]}""")
                appendLine()
            }
            appendLine("data: [DONE]")
        }

        val flushedBlocks = mutableListOf<Pair<Int, String>>()
        val totalChars = streamer.consumeSseChannel(
            channel = ByteReadChannel(sseLines.toByteArray(Charsets.UTF_8)),
            onAtomicFlushReady = { seq, blockText ->
                flushedBlocks += seq to blockText
            },
        )

        assertEquals(paragraphSegment.length * 25, totalChars)
        assertTrue(flushedBlocks.size >= 2)
        flushedBlocks.forEach { (_, text) ->
            assertTrue(text.length <= 512)
            assertTrue(text.length <= ProtocolBlockEntity.MAX_BLOCK_CHARS)
        }
        assertEquals(totalChars, flushedBlocks.sumOf { it.second.length })
    }
}
