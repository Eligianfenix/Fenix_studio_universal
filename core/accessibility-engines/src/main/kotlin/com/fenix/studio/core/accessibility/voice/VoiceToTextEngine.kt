/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-02 00:44:00 -03:00
 * ARCHIVO: core/accessibility-engines/src/main/kotlin/com/fenix/studio/core/accessibility/voice/VoiceToTextEngine.kt
 * ================================================================================
 */

package com.fenix.studio.core.accessibility.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class VoiceInputState(
    val isListening: Boolean = false,
    val partialText: String = "",
    val rmsDb: Float = 0f,
    val lastError: String? = null,
    val hasApiKey: Boolean = false,
)

/**
 * Motor soberano de reconocimiento de voz a texto (STT) a Costo 0 (`MF-8.2`).
 * Captura audio PCM 16-bit 16kHz en memoria y lo transcribe mediante la API en la nube
 * (Groq Cloud Whisper Large v3 / compatible OpenAI) sin requerir modelos locales pesados.
 */
class VoiceToTextEngine(
    private val context: Context,
) {
    val preferences = VoiceByokPreferences(context)
    val client = GroqCloudTranscriptionClient(preferences)

    private val _state = MutableStateFlow(VoiceInputState(hasApiKey = preferences.hasApiKey()))
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isRecording = false
    private var recordingJob: Job? = null

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    fun hasApiKey(): Boolean = preferences.hasApiKey()

    fun setApiKey(key: String) {
        preferences.setApiKey(key)
        _state.update { it.copy(hasApiKey = preferences.hasApiKey()) }
    }

    fun startListening(
        onResult: (String) -> Unit,
        onPartialResult: ((String) -> Unit)? = null,
        onRmsChanged: ((Float) -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
    ) {
        stopListening()
        isRecording = true
        _state.update { it.copy(isListening = true, lastError = null) }

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize * 2,
            )
        } catch (e: Exception) {
            _state.update { it.copy(isListening = false, lastError = e.localizedMessage) }
            onError?.invoke("No se pudo inicializar el micrófono: ${e.localizedMessage}")
            return
        }

        val pcmAudioBytes = ByteArrayOutputStream()

        recordingJob = scope.launch(Dispatchers.IO) {
            try {
                audioRecord.startRecording()
                val buffer = ByteArray(2048)

                while (isActive && isRecording) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        pcmAudioBytes.write(buffer, 0, read)

                        // Cálculo de volumen RMS para la UI
                        var sumSquare = 0.0
                        val shortCount = read / 2
                        for (i in 0 until shortCount) {
                            val shortVal = (buffer[i * 2 + 1].toInt() shl 8) or (buffer[i * 2].toInt() and 0xFF)
                            val normalized = shortVal / 32768.0
                            sumSquare += (normalized * normalized)
                        }
                        val rms = kotlin.math.sqrt(sumSquare / shortCount).toFloat()
                        val rmsDb = if (rms > 0.0001f) (20f * kotlin.math.log10(rms) + 60f).coerceIn(0f, 100f) else 0f
                        mainHandler.post {
                            _state.update { it.copy(rmsDb = rmsDb) }
                            onRmsChanged?.invoke(rmsDb)
                        }
                    }
                }

                try {
                    audioRecord.stop()
                    audioRecord.release()
                } catch (_: Exception) {}

                if (!isActive || pcmAudioBytes.size() == 0) return@launch

                mainHandler.post { onPartialResult?.invoke("Transcribiendo con Groq...") }

                val rawPcm = pcmAudioBytes.toByteArray()
                val wavBytes = createWavByteArray(rawPcm, sampleRate, 1, 16)

                val resultText = client.transcribeAudio(wavBytes)
                android.util.Log.d("FenixVoice", "Groq transcribed text: '$resultText'")

                mainHandler.post {
                    _state.update { it.copy(isListening = false, rmsDb = 0f, partialText = resultText) }
                    onPartialResult?.invoke(resultText)
                    onResult(resultText)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) return@launch
                android.util.Log.e("FenixVoice", "Error en Groq Transcription: ${e.localizedMessage}", e)
                mainHandler.post {
                    _state.update { it.copy(isListening = false, rmsDb = 0f, lastError = e.localizedMessage) }
                    onError?.invoke(e.localizedMessage ?: "Error al transcribir")
                }
            }
        }
    }

    fun stopListening() {
        isRecording = false
    }

    fun cancel() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        _state.update { it.copy(isListening = false, rmsDb = 0f, partialText = "") }
    }

    private fun createWavByteArray(pcmData: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val totalAudioLen = pcmData.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()

        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalDataLen.toInt())
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // Subchunk1Size para PCM
        buffer.putShort(1) // AudioFormat PCM = 1
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate.toInt())
        buffer.putShort((channels * bitsPerSample / 8).toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray())
        buffer.putInt(totalAudioLen.toInt())

        val wavOut = ByteArrayOutputStream(44 + pcmData.size)
        wavOut.write(header)
        wavOut.write(pcmData)
        return wavOut.toByteArray()
    }
}
