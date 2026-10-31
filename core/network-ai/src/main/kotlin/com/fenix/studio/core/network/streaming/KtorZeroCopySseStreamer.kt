/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/network-ai/src/main/kotlin/com/fenix/studio/core/network/streaming/KtorZeroCopySseStreamer.kt
 * ================================================================================
 */

package com.fenix.studio.core.network.streaming

import com.fenix.studio.core.database.entity.ProtocolBlockEntity
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.charset.StandardCharsets

/**
 * Cliente de Streaming SSE Zero-Copy con Ktor (`KtorZeroCopySseStreamer`, `MF-2.2`).
 * - Lee del `ByteReadChannel` mediante un único buffer reutilizable de 8 KB (`SSE_BUFFER_SIZE_BYTES = 8_192`).
 * - Vuelca bloques atómicos hacia Room invocando `onAtomicFlushReady` antes de alcanzar el techo
 *   de 12 KB (`ProtocolBlockEntity.MAX_BLOCK_CHARS = 12_288`) para proteger `CursorWindow`.
 */
class KtorZeroCopySseStreamer(
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val atomicBlockFlushThresholdChars: Int = DEFAULT_ATOMIC_FLUSH_THRESHOLD_CHARS,
) {
    init {
        require(atomicBlockFlushThresholdChars in 256..ProtocolBlockEntity.MAX_BLOCK_CHARS) {
            "El umbral de volcado atómico debe no superar ProtocolBlockEntity.MAX_BLOCK_CHARS (${ProtocolBlockEntity.MAX_BLOCK_CHARS})."
        }
    }

    /**
     * Consume un `ByteReadChannel` SSE en trozos de 8 KB y emite deltas y bloques atómicos listos para SQLite.
     */
    suspend fun consumeSseChannel(
        channel: ByteReadChannel,
        onTokenDelta: suspend (String) -> Unit = {},
        onAtomicFlushReady: suspend (blockSequence: Int, atomicBlockText: String) -> Unit,
    ): Int {
        val reusableBuffer = ByteArray(SSE_BUFFER_SIZE_BYTES)
        val lineAccumulator = StringBuilder()
        val blockAccumulator = StringBuilder()
        var blockSequence = 1
        var totalCharsEmitted = 0

        suspend fun flushBlockIfNeeded(force: Boolean) {
            while (blockAccumulator.length >= atomicBlockFlushThresholdChars ||
                (force && blockAccumulator.isNotEmpty())
            ) {
                val sliceLength = minOf(blockAccumulator.length, atomicBlockFlushThresholdChars)
                val chunkText = blockAccumulator.substring(0, sliceLength)
                blockAccumulator.delete(0, sliceLength)
                if (chunkText.isNotBlank()) {
                    onAtomicFlushReady(blockSequence++, chunkText)
                }
                if (!force) break
            }
        }

        while (!channel.isClosedForRead) {
            val bytesRead = channel.readAvailable(reusableBuffer, 0, reusableBuffer.size)
            if (bytesRead <= 0) break

            val decodedChunk = String(reusableBuffer, 0, bytesRead, StandardCharsets.UTF_8)
            lineAccumulator.append(decodedChunk)

            var newlineIndex = lineAccumulator.indexOf('\n')
            while (newlineIndex != -1) {
                val rawLine = lineAccumulator.substring(0, newlineIndex).trim()
                lineAccumulator.delete(0, newlineIndex + 1)

                val tokenDelta = parseSseDataLine(rawLine)
                if (!tokenDelta.isNullOrEmpty()) {
                    totalCharsEmitted += tokenDelta.length
                    onTokenDelta(tokenDelta)
                    blockAccumulator.append(tokenDelta)
                    flushBlockIfNeeded(force = false)
                }

                newlineIndex = lineAccumulator.indexOf('\n')
            }
        }

        // Procesar última línea residual si no terminó en '\n'
        val trailingLine = lineAccumulator.toString().trim()
        if (trailingLine.isNotEmpty()) {
            val tokenDelta = parseSseDataLine(trailingLine)
            if (!tokenDelta.isNullOrEmpty()) {
                totalCharsEmitted += tokenDelta.length
                onTokenDelta(tokenDelta)
                blockAccumulator.append(tokenDelta)
            }
        }

        flushBlockIfNeeded(force = true)
        return totalCharsEmitted
    }

    private fun parseSseDataLine(line: String): String? {
        if (!line.startsWith("data:")) return null
        val payload = line.removePrefix("data:").trim()
        if (payload.isEmpty() || payload == "[DONE]") return null

        return try {
            val root = json.parseToJsonElement(payload).jsonObject
            // Formato estándar choices[0].delta.content o token directo
            val choices = root["choices"]?.jsonArray
            if (choices != null && choices.isNotEmpty()) {
                val firstChoice = choices[0].jsonObject
                firstChoice["delta"]?.jsonObject?.get("content")?.jsonPrimitive?.content
                    ?: firstChoice["text"]?.jsonPrimitive?.content
            } else {
                root["delta"]?.jsonPrimitive?.content
                    ?: root["token"]?.jsonPrimitive?.content
            }
        } catch (_: Exception) {
            payload
        }
    }

    companion object {
        const val SSE_BUFFER_SIZE_BYTES: Int = 8_192
        const val DEFAULT_ATOMIC_FLUSH_THRESHOLD_CHARS: Int = 4_096
    }
}
