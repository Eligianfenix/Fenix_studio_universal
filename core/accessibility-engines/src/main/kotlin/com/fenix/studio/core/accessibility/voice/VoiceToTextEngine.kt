/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-01 22:00:00 -03:00
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
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VoiceInputState(
    val isListening: Boolean = false,
    val partialText: String = "",
    val rmsDb: Float = 0f,
    val lastError: String? = null,
    val isAvailable: Boolean = true,
    val isWhisperActive: Boolean = false,
)

/**
 * Motor local soberano de reconocimiento de voz a texto (STT) a Costo 0 (`MF-8.2`).
 * Ejecuta Whisper Int8 local empaquetado en los assets del APK con lectura directa por AssetManager.
 */
class VoiceToTextEngine(
    private val context: Context,
) {
    private val _state = MutableStateFlow(VoiceInputState())
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var whisperRecognizer: OfflineRecognizer? = null
    private var isRecording = false
    private var recordingJob: Job? = null

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    init {
        scope.launch {
            initWhisperSafely()
        }
    }

    private suspend fun initWhisperSafely(): Boolean = withContext(Dispatchers.IO) {
        try {
            val whisperConfig = OfflineWhisperModelConfig(
                encoder = "models/whisper/tiny-encoder.int8.onnx",
                decoder = "models/whisper/tiny-decoder.int8.onnx",
                language = "es",
                task = "transcribe",
                tailPaddings = 1000,
            )
            val modelConfig = OfflineModelConfig(
                whisper = whisperConfig,
                tokens = "models/whisper/tiny-tokens.txt",
                numThreads = 4,
                debug = false,
                provider = "cpu",
            )
            val config = OfflineRecognizerConfig(
                modelConfig = modelConfig,
            )
            whisperRecognizer = OfflineRecognizer(
                assetManager = context.assets,
                config = config,
            )
            _state.update { it.copy(isWhisperActive = true, isAvailable = true) }
            true
        } catch (e: Exception) {
            _state.update { it.copy(isWhisperActive = false, lastError = e.localizedMessage) }
            false
        }
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

        val recordedSamples = mutableListOf<Float>()

        recordingJob = scope.launch(Dispatchers.IO) {
            try {
                audioRecord.startRecording()
                val buffer = ShortArray(1024)

                while (isActive && isRecording) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sumSquare = 0.0
                        for (i in 0 until read) {
                            val sample = buffer[i] / 32768.0f
                            recordedSamples.add(sample)
                            sumSquare += (sample * sample)
                        }
                        val rms = kotlin.math.sqrt(sumSquare / read).toFloat()
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

                // Si se canceló y no hay muestras, salir
                if (!isActive || recordedSamples.isEmpty()) return@launch

                // Inferencia offline con Whisper
                val recognizer = whisperRecognizer
                if (recognizer != null && recordedSamples.isNotEmpty()) {
                    mainHandler.post { onPartialResult?.invoke("Transcribiendo con Whisper local...") }
                    val stream = recognizer.createStream()
                    stream.acceptWaveform(recordedSamples.toFloatArray(), sampleRate)
                    recognizer.decode(stream)
                    val resultText = recognizer.getResult(stream).text.trim()
                    stream.release()

                    mainHandler.post {
                        _state.update { it.copy(isListening = false, rmsDb = 0f, partialText = resultText) }
                        onPartialResult?.invoke(resultText)
                        onResult(resultText)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) return@launch
                mainHandler.post {
                    _state.update { it.copy(isListening = false, rmsDb = 0f, lastError = e.localizedMessage) }
                    onError?.invoke("Error en Whisper local: ${e.localizedMessage}")
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
}
