/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-02 10:40:00 -03:00
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Motor de Texto a Voz (TTS) Neuronal 100% Nativo y Soberano a Costo $0 (`MF-8.2`).
 * Ejecuta el modelo Piper VITS en el chip del dispositivo vía sherpa-onnx con
 * streaming por cláusulas en tiempo real y pausas gramaticales calibradas.
 */
class NativePiperTtsEngine(private val context: Context) {

    private data class SpeechSegment(
        val text: String,
        val pauseAfterMs: Int,
    )

    private var tts: OfflineTts? = null
    private var isInitialized = false
    @Volatile
    private var currentAudioTrack: AudioTrack? = null
    private val stopRequested = AtomicBoolean(false)
    private val synthesisMutex = Mutex()

    private fun ensureAssetsExtracted(destDir: File) {
        val marker = File(destDir, ".extracted_marker_davefx_v2")
        val modelFile = File(destDir, "es_ES-davefx-medium.onnx")
        val espeakDir = File(destDir, "espeak-ng-data")
        if (marker.exists() && modelFile.exists() && espeakDir.exists()) {
            return
        }
        destDir.mkdirs()
        copyAssetRecursively("models/piper_tts", destDir)
        try {
            marker.createNewFile()
        } catch (_: Exception) {}
    }

    private fun copyAssetRecursively(assetPath: String, destDir: File) {
        val list = context.assets.list(assetPath) ?: return
        destDir.mkdirs()
        for (item in list) {
            val subAssetPath = if (assetPath.isEmpty()) item else "$assetPath/$item"
            val subDest = File(destDir, item)
            val isDirectory = try {
                context.assets.open(subAssetPath).use { }
                false
            } catch (_: Exception) {
                true
            }
            if (isDirectory) {
                copyAssetRecursively(subAssetPath, subDest)
            } else {
                copySingleAsset(subAssetPath, subDest)
            }
        }
    }

    private fun copySingleAsset(assetPath: String, destFile: File) {
        if (destFile.exists() && destFile.length() > 0) return
        try {
            context.assets.open(assetPath).use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e("FenixPiperTts", "Error copiando $assetPath: ${e.message}")
        }
    }

    fun initialize(): Boolean {
        if (isInitialized && tts != null) return true
        return try {
            val modelDir = File(context.filesDir, "piper_tts")
            ensureAssetsExtracted(modelDir)

            val modelFile = File(modelDir, "es_ES-davefx-medium.onnx")
            val tokensFile = File(modelDir, "tokens.txt")
            val dataDir = File(modelDir, "espeak-ng-data")

            val vitsConfig = OfflineTtsVitsModelConfig(
                model = modelFile.absolutePath,
                lexicon = "",
                tokens = tokensFile.absolutePath,
                dataDir = dataDir.absolutePath,
                noiseScale = 0.667f,
                noiseScaleW = 0.8f,
                lengthScale = 1.0f,
            )
            val optimalThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            val modelConfig = OfflineTtsModelConfig(
                vits = vitsConfig,
                numThreads = optimalThreads,
                provider = "cpu",
            )
            val ttsConfig = OfflineTtsConfig(
                model = modelConfig,
            )
            tts = OfflineTts(config = ttsConfig)
            isInitialized = tts != null
            Log.i("FenixPiperTts", "Piper VITS inicializado exitosamente desde almacenamiento local (sampleRate: ${tts?.sampleRate()})")
            isInitialized
        } catch (e: Throwable) {
            Log.e("FenixPiperTts", "Error al inicializar Piper VITS: ${e.message}", e)
            isInitialized = false
            false
        }
    }

    private fun parseIntoGrammaticalSegments(rawText: String): List<SpeechSegment> {
        val cleaned = rawText
            .replace(Regex("[*_`~#]+"), "")
            .replace(Regex("(?m)^\\s*[-•]\\s+"), "")
            .trim()
        if (cleaned.isEmpty()) return emptyList()

        val result = mutableListOf<SpeechSegment>()
        val paragraphs = cleaned.split(Regex("\\n+")).map { it.trim() }.filter { it.isNotEmpty() }

        val clauseRegex = Regex("([^.,;:?!…]+([.,;:?!…]+|$))")

        for (pIndex in paragraphs.indices) {
            val paragraph = paragraphs[pIndex]
            val matches = clauseRegex.findAll(paragraph).map { it.value.trim() }.filter { it.isNotEmpty() }.toList()

            for (mIndex in matches.indices) {
                val clause = matches[mIndex]
                val isLastInParagraph = (mIndex == matches.lastIndex)
                val lastChar = clause.lastOrNull() ?: '.'

                val pauseMs = when {
                    isLastInParagraph && pIndex < paragraphs.lastIndex -> 950 // Punto aparte
                    isLastInParagraph -> 300 // Final del texto
                    lastChar == '.' || lastChar == '?' || lastChar == '!' || lastChar == '…' -> 620 // Punto seguido
                    lastChar == ';' || lastChar == ':' -> 420 // Punto y coma / Dos puntos
                    lastChar == ',' -> 260 // Coma
                    else -> 260
                }
                result.add(SpeechSegment(text = clause, pauseAfterMs = pauseMs))
            }
        }
        return result
    }

    private fun buildPcmWithSilence(samples: FloatArray, sampleRate: Int, pauseAfterMs: Int): ShortArray {
        var startIdx = 0
        val silenceThreshold = 0.004f
        while (startIdx < samples.size && abs(samples[startIdx]) < silenceThreshold) {
            startIdx++
        }
        var endIdx = samples.lastIndex
        while (endIdx > startIdx && abs(samples[endIdx]) < silenceThreshold) {
            endIdx--
        }
        if (startIdx >= endIdx) return ShortArray(0)

        val activeCount = endIdx - startIdx + 1
        val silenceSamples = (sampleRate * pauseAfterMs) / 1000
        val fadeSamples = minOf(activeCount / 10, (sampleRate * 12) / 1000).coerceAtLeast(1)

        val out = ShortArray(activeCount + silenceSamples)
        for (i in 0 until activeCount) {
            var s = samples[startIdx + i]
            if (i < fadeSamples) {
                s *= (i.toFloat() / fadeSamples)
            } else if (i >= activeCount - fadeSamples) {
                s *= ((activeCount - 1 - i).toFloat() / fadeSamples)
            }
            val pcm = (s * 32767.0f).coerceIn(-32768.0f, 32767.0f)
            out[i] = pcm.toInt().toShort()
        }
        return out
    }

    suspend fun speak(
        text: String,
        speed: Float = 1.0f,
        onPlaybackStart: () -> Unit = {},
        onComplete: (Boolean) -> Unit = {},
    ) = withContext(Dispatchers.Default) {
        stop()
        if (!isInitialized && !initialize()) {
            onComplete(false)
            return@withContext
        }
        val ttsInstance = tts ?: run {
            onComplete(false)
            return@withContext
        }

        val segments = parseIntoGrammaticalSegments(text)
        if (segments.isEmpty()) {
            onComplete(false)
            return@withContext
        }

        stopRequested.set(false)
        val sampleRate = ttsInstance.sampleRate().takeIf { it > 0 } ?: 22050
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val streamBufferSize = maxOf(minBuf * 2, sampleRate * 2)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
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
            .setBufferSizeInBytes(streamBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        currentAudioTrack = track
        var hasStartedPlayback = false

        try {
            coroutineScope {
                val pcmChannel = Channel<ShortArray>(capacity = 6)

                val producerJob = launch(Dispatchers.Default) {
                    for (segment in segments) {
                        if (!isActive || stopRequested.get()) break
                        val audio = synthesisMutex.withLock {
                            if (stopRequested.get()) null
                            else ttsInstance.generate(segment.text, sid = 0, speed = speed)
                        } ?: break

                        if (audio.samples.isNotEmpty() && !stopRequested.get()) {
                            val pcm = buildPcmWithSilence(audio.samples, audio.sampleRate, segment.pauseAfterMs)
                            if (pcm.isNotEmpty()) {
                                pcmChannel.send(pcm)
                            }
                        }
                    }
                    pcmChannel.close()
                }

                for (pcmChunk in pcmChannel) {
                    if (!isActive || stopRequested.get()) break
                    if (!hasStartedPlayback) {
                        hasStartedPlayback = true
                        track.play()
                        withContext(Dispatchers.Main) { onPlaybackStart() }
                    }
                    var offset = 0
                    while (offset < pcmChunk.size && !stopRequested.get() && isActive) {
                        val written = track.write(pcmChunk, offset, pcmChunk.size - offset, AudioTrack.WRITE_BLOCKING)
                        if (written <= 0) break
                        offset += written
                    }
                }

                producerJob.cancel()
            }

            if (!stopRequested.get()) {
                track.stop()
            }
            track.release()
            if (currentAudioTrack === track) {
                currentAudioTrack = null
            }
            onComplete(hasStartedPlayback)
        } catch (e: Throwable) {
            Log.e("FenixPiperTts", "Error durante síntesis en streaming: ${e.message}", e)
            try {
                track.release()
            } catch (_: Exception) {}
            if (currentAudioTrack === track) {
                currentAudioTrack = null
            }
            onComplete(false)
        }
    }

    fun stop() {
        stopRequested.set(true)
        try {
            currentAudioTrack?.pause()
            currentAudioTrack?.flush()
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
