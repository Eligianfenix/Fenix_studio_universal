/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-02 03:27:00 -03:00
 * ARCHIVO: core/accessibility-engines/src/main/kotlin/com/fenix/studio/core/accessibility/tts/NativePiperTtsEngine.kt
 * ================================================================================
 */

package com.fenix.studio.core.accessibility.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Motor de Texto a Voz (TTS) Neuronal 100% Nativo y Soberano a Costo $0 (`MF-8.2`).
 * Ejecuta el modelo Piper VITS en el chip del dispositivo vía sherpa-onnx.
 * Cero cuentas, cero servidores, cero APIs, cero internet y timbre humano natural.
 */
class NativePiperTtsEngine(private val context: Context) {

    private var tts: OfflineTts? = null
    private var isInitialized = false
    private var currentAudioTrack: AudioTrack? = null

    fun initialize(): Boolean {
        if (isInitialized && tts != null) return true
        return try {
            val assetManager = context.assets
            val modelPath = "models/piper_tts/es_ES-carlfm-x_low.onnx"
            val tokensPath = "models/piper_tts/tokens.txt"
            val dataDir = "models/piper_tts/espeak-ng-data"

            val vitsConfig = OfflineTtsVitsModelConfig(
                model = modelPath,
                tokens = tokensPath,
                dataDir = dataDir,
                noiseScale = 0.667f,
                noiseScaleW = 0.8f,
                lengthScale = 1.0f,
            )
            val modelConfig = OfflineTtsModelConfig(
                vits = vitsConfig,
                numThreads = 2,
                provider = "cpu",
            )
            val ttsConfig = OfflineTtsConfig(
                model = modelConfig,
            )
            tts = OfflineTts(assetManager, ttsConfig)
            isInitialized = tts != null
            Log.i("FenixPiperTts", "Piper VITS inicializado exitosamente (sampleRate: ${tts?.sampleRate()})")
            isInitialized
        } catch (e: Throwable) {
            Log.e("FenixPiperTts", "Error al inicializar Piper VITS: ${e.message}", e)
            isInitialized = false
            false
        }
    }

    suspend fun speak(
        text: String,
        speed: Float = 1.0f,
        onComplete: () -> Unit = {},
    ) = withContext(Dispatchers.Default) {
        if (!isInitialized && !initialize()) {
            onComplete()
            return@withContext
        }
        val ttsInstance = tts ?: run {
            onComplete()
            return@withContext
        }

        try {
            stop()
            val audio = ttsInstance.generate(text, sid = 0, speed = speed)
            if (audio.samples.isEmpty()) {
                onComplete()
                return@withContext
            }

            val shortArray = ShortArray(audio.samples.size)
            for (i in audio.samples.indices) {
                val sample = (audio.samples[i] * 32767.0f).coerceIn(-32768.0f, 32767.0f)
                shortArray[i] = sample.toInt().toShort()
            }

            val sampleRate = audio.sampleRate
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBuf, shortArray.size * 2)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            currentAudioTrack = track
            track.write(shortArray, 0, shortArray.size)
            track.play()

            val durationMs = (shortArray.size * 1000L) / sampleRate
            delay(durationMs + 80)
            track.stop()
            track.release()
            currentAudioTrack = null
            onComplete()
        } catch (e: Throwable) {
            Log.e("FenixPiperTts", "Error durante la síntesis: ${e.message}", e)
            onComplete()
        }
    }

    fun stop() {
        try {
            currentAudioTrack?.stop()
            currentAudioTrack?.release()
            currentAudioTrack = null
        } catch (_: Exception) {}
    }

    fun release() {
        stop()
        tts?.release()
        tts = null
        isInitialized = false
    }
}

