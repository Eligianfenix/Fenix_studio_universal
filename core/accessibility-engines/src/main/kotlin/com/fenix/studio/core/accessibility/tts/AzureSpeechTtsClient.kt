/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-02 02:39:00 -03:00
 * ARCHIVO: core/accessibility-engines/src/main/kotlin/com/fenix/studio/core/accessibility/tts/AzureSpeechTtsClient.kt
 * ================================================================================
 */

package com.fenix.studio.core.accessibility.tts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente REST soberano y ultraligero para síntesis de voz neuronal de Azure Cognitive Services (Costo $0 Tier F0).
 * Soporta síntesis directa de voz de narrador profesional (es-AR-TomasNeural) y exportación a MP3 sin dependencias pesadas.
 */
class AzureSpeechTtsClient {

    suspend fun synthesizeSpeech(
        apiKey: String,
        region: String,
        text: String,
        voiceName: String = "es-AR-TomasNeural",
        outputFile: File,
        rateModifier: Float = 0.92f,
        pitchModifierPercent: Int = -5,
    ): Result<File> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val endpoint = "https://${region.trim()}.tts.speech.microsoft.com/cognitiveservices/v1"
            val escapedText = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")

            val rateStr = String.format(java.util.Locale.US, "%.2f", rateModifier)
            val pitchStr = if (pitchModifierPercent >= 0) "+$pitchModifierPercent%" else "$pitchModifierPercent%"

            val ssml = """
                <speak version='1.0' xml:lang='es-AR'>
                  <voice xml:lang='es-AR' name='$voiceName'>
                    <prosody rate='$rateStr' pitch='$pitchStr'>
                      $escapedText
                    </prosody>
                  </voice>
                </speak>
            """.trimIndent()

            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Ocp-Apim-Subscription-Key", apiKey.trim())
                setRequestProperty("Content-Type", "application/ssml+xml")
                setRequestProperty("X-Microsoft-OutputFormat", "audio-16khz-128kbitrate-mono-mp3")
                setRequestProperty("User-Agent", "FenixStudioAcademicPWA")
                connectTimeout = 15000
                readTimeout = 30000
            }

            connection.outputStream.use { os ->
                os.write(ssml.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                return@withContext Result.failure(
                    IllegalStateException("Error en Azure Speech ($responseCode): $errorBody")
                )
            }

            FileOutputStream(outputFile).use { outputStream ->
                connection.inputStream.use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }
}
