/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-01 21:18:00 -03:00
 * ARCHIVO: core/accessibility-engines/src/main/kotlin/com/fenix/studio/core/accessibility/voice/VoiceToTextEngine.kt
 * ================================================================================
 */

package com.fenix.studio.core.accessibility.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * Estado inmutable del motor de Dictado por Voz (Voz a Texto / STT) a Costo 0.
 */
data class VoiceInputState(
    val isListening: Boolean = false,
    val partialText: String = "",
    val rmsDb: Float = 0f,
    val lastError: String? = null,
    val isAvailable: Boolean = true,
)

/**
 * Motor local soberano de reconocimiento de voz a texto (STT) a Costo 0 (`MF-8.2`, `RF-ControlVoz`).
 * Utiliza el reconocimiento nativo On-Device de Android (SDK 31+) o del sistema operativo
 * sin consumir APIs de pago ni red externa obligatoria.
 * Mantiene la sesión continua activa en hardware con latencia cercana a 0 ms entre frases,
 * consolidando resultados parciales de forma defensiva para no perder palabras ante dicción rápida,
 * canto o ruido ambiental.
 */
class VoiceToTextEngine(
    private val context: Context,
) {
    private val _state = MutableStateFlow(VoiceInputState(isAvailable = SpeechRecognizer.isRecognitionAvailable(context)))
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isUserRecording = false
    private val accumulatedText = StringBuilder()
    private var currentCyclePartial: String = ""

    private var activeOnResult: ((String) -> Unit)? = null
    private var activeOnPartialResult: ((String) -> Unit)? = null
    private var activeOnRmsChanged: ((Float) -> Unit)? = null

    private val recognitionIntent by lazy {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
        }
    }

    private fun initRecognizerInstance(): SpeechRecognizer {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }.apply {
            setRecognitionListener(createListener())
        }
    }

    private fun ensureListening() {
        if (!isUserRecording) return
        try {
            if (speechRecognizer == null) {
                speechRecognizer = initRecognizerInstance()
            }
            speechRecognizer?.startListening(recognitionIntent)
        } catch (_: Exception) {
            recreateRecognizerSafely()
        }
    }

    private fun recreateRecognizerSafely() {
        if (!isUserRecording) return
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null

        try {
            speechRecognizer = initRecognizerInstance()
            speechRecognizer?.startListening(recognitionIntent)
        } catch (_: Exception) {
            if (isUserRecording) {
                mainHandler.postDelayed({ ensureListening() }, 100L)
            }
        }
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.update { it.copy(isListening = true, lastError = null) }
        }

        override fun onBeginningOfSpeech() {
            _state.update { it.copy(isListening = true) }
        }

        override fun onRmsChanged(rmsdB: Float) {
            _state.update { it.copy(rmsDb = rmsdB) }
            activeOnRmsChanged?.invoke(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            // Si hubo ruido, canto o desfasaje acústico pero se había capturado texto parcial, lo consolidamos
            if (currentCyclePartial.isNotBlank()) {
                commitPhrase(currentCyclePartial)
                currentCyclePartial = ""
            }
            if (isUserRecording) {
                mainHandler.postDelayed({ ensureListening() }, 30L)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val phrase = matches?.firstOrNull { it.isNotBlank() }?.trim() ?: currentCyclePartial.trim()
            if (phrase.isNotBlank()) {
                commitPhrase(phrase)
            }
            currentCyclePartial = ""
            if (isUserRecording) {
                mainHandler.postDelayed({ ensureListening() }, 20L)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull { it.isNotBlank() }?.trim() ?: ""
            if (partial.isNotBlank()) {
                currentCyclePartial = partial
                val full = if (accumulatedText.isEmpty()) partial else "$accumulatedText $partial"
                _state.update { it.copy(partialText = full) }
                activeOnPartialResult?.invoke(full)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun commitPhrase(phrase: String) {
        val trimmed = phrase.trim()
        if (trimmed.isBlank()) return
        if (accumulatedText.isNotEmpty()) accumulatedText.append(" ")
        accumulatedText.append(trimmed)
        val full = accumulatedText.toString()
        _state.update { it.copy(partialText = full) }
        activeOnPartialResult?.invoke(full)
        activeOnResult?.invoke(full)
    }

    fun startListening(
        onResult: (String) -> Unit,
        onPartialResult: ((String) -> Unit)? = null,
        onRmsChanged: ((Float) -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.update { it.copy(isAvailable = false, lastError = "Reconocimiento de voz no disponible.") }
            onError?.invoke("Reconocimiento de voz no disponible.")
            return
        }

        stopListening()
        isUserRecording = true
        accumulatedText.clear()
        currentCyclePartial = ""

        activeOnResult = onResult
        activeOnPartialResult = onPartialResult
        activeOnRmsChanged = onRmsChanged

        ensureListening()
    }

    fun stopListening() {
        isUserRecording = false
        mainHandler.removeCallbacksAndMessages(null)
        accumulatedText.clear()
        currentCyclePartial = ""
        try {
            speechRecognizer?.apply {
                stopListening()
                cancel()
                destroy()
            }
        } catch (_: Exception) {}
        speechRecognizer = null
        _state.update { it.copy(isListening = false, rmsDb = 0f) }
    }
}
