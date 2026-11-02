/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-02 00:43:00 -03:00
 * ARCHIVO: core/accessibility-engines/src/main/kotlin/com/fenix/studio/core/accessibility/voice/GroqCloudTranscriptionClient.kt
 * ================================================================================
 */

package com.fenix.studio.core.accessibility.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente soberano y ultraligero de transcripción de voz a texto a Costo $0 (`MF-8.2`).
 * Conecta con la API de Groq Cloud (o cualquier endpoint compatible con OpenAI) sin hardcodeo de modelos.
 */
class GroqCloudTranscriptionClient(
    private val preferences: VoiceByokPreferences,
) {
    /**
     * Descubre dinámicamente los modelos de audio en la API sin ningún hardcodeo.
     */
    suspend fun discoverAudioModels(apiKey: String = preferences.getApiKey()): List<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        val baseUrl = preferences.getBaseUrl().trimEnd('/')
        val url = URL("$baseUrl/models")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("User-Agent", "FenixStudio/1.0")
            connectTimeout = 10000
            readTimeout = 10000
        }

        try {
            if (conn.responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val data = json.optJSONArray("data") ?: return@withContext emptyList()
                val models = mutableListOf<String>()
                for (i in 0 until data.length()) {
                    val id = data.getJSONObject(i).optString("id")
                    if (id.contains("whisper", ignoreCase = true) || id.contains("audio", ignoreCase = true)) {
                        models.add(id)
                    }
                }
                models
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Envía el audio WAV grabado a la API de Groq en formato multipart/form-data.
     */
    suspend fun transcribeAudio(
        wavBytes: ByteArray,
        apiKey: String = preferences.getApiKey(),
        customModel: String? = null,
        language: String = "es",
    ): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "API Key de Groq no configurada." }
        require(wavBytes.isNotEmpty()) { "El audio grabado está vacío." }

        val baseUrl = preferences.getBaseUrl().trimEnd('/')
        val model = customModel?.takeIf { it.isNotBlank() }
            ?: preferences.getSelectedModel().takeIf { it.isNotBlank() }
            ?: "whisper-large-v3-turbo"

        val boundary = "----FenixGroqAudioBoundary${System.currentTimeMillis()}"
        val lineEnd = "\r\n"
        val twoHyphens = "--"

        val url = URL("$baseUrl/audio/transcriptions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doInput = true
            doOutput = true
            useCaches = false
            setRequestProperty("Connection", "Keep-Alive")
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("User-Agent", "FenixStudio/1.0")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connectTimeout = 15000
            readTimeout = 25000
        }

        val outputStream = DataOutputStream(conn.outputStream)

        // Campo 'model'
        outputStream.writeBytes(twoHyphens + boundary + lineEnd)
        outputStream.writeBytes("Content-Disposition: form-data; name=\"model\"$lineEnd$lineEnd")
        outputStream.writeBytes(model + lineEnd)

        // Campo 'language'
        outputStream.writeBytes(twoHyphens + boundary + lineEnd)
        outputStream.writeBytes("Content-Disposition: form-data; name=\"language\"$lineEnd$lineEnd")
        outputStream.writeBytes(language + lineEnd)

        // Campo 'response_format'
        outputStream.writeBytes(twoHyphens + boundary + lineEnd)
        outputStream.writeBytes("Content-Disposition: form-data; name=\"response_format\"$lineEnd$lineEnd")
        outputStream.writeBytes("json$lineEnd")

        // Archivo 'file' (audio.wav)
        outputStream.writeBytes(twoHyphens + boundary + lineEnd)
        outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"$lineEnd")
        outputStream.writeBytes("Content-Type: audio/wav$lineEnd$lineEnd")
        outputStream.write(wavBytes)
        outputStream.writeBytes(lineEnd)

        // Fin de multipart
        outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
        outputStream.flush()
        outputStream.close()

        val responseCode = conn.responseCode
        if (responseCode in 200..299) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            json.optString("text", "").trim()
        } else {
            val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            throw IllegalStateException("Error al transcribir en Groq ($responseCode): $errText")
        }
    }
}

