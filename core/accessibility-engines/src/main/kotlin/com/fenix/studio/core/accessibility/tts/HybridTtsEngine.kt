/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-02 03:28:00 -03:00
 * ARCHIVO: core/accessibility-engines/src/main/kotlin/com/fenix/studio/core/accessibility/tts/HybridTtsEngine.kt
 * ================================================================================
 */

package com.fenix.studio.core.accessibility.tts

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Estado de reproducción del motor TTS sincronizado por párrafos.
 */
enum class TtsPlaybackStatus {
    IDLE,
    PLAYING,
    PAUSED,
    FINISHED,
    ERROR,
}

/**
 * Perfil de síntesis de voz configurado en el motor TTS (`MF-D.6`).
 */
enum class TtsVoiceProfile {
    EDGE_NATURAL_NEURAL,
    LOCAL_DEVICE_STANDARD,
}

/**
 * Estado inmutable de reproducción TTS en tiempo real.
 */
data class TtsPlaybackState(
    val status: TtsPlaybackStatus = TtsPlaybackStatus.IDLE,
    val currentParagraphIndex: Int = 0,
    val totalParagraphs: Int = 0,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val currentUtteranceId: String? = null,
    val errorMessage: String? = null,
    val activeVoiceProfile: TtsVoiceProfile = TtsVoiceProfile.EDGE_NATURAL_NEURAL,
    val activeVoiceName: String? = "Piper VITS (Neuronal Nativo)",
    val selectedNarratorVoice: FenixNarratorVoice = FenixNarratorVoice.MALE_MATURE_RIOPLATENSE,
)

/**
 * Motor TTS Soberano y Nativo a Costo $0 (`MF-8.2`, `MF-D.6`).
 * Integra el motor de red neuronal Piper VITS (sherpa-onnx) para síntesis humana en el chip local,
 * con fallback instantáneo a TextToSpeech de Android en caso de contingencia.
 */
class HybridTtsEngine(
    private val context: Context? = null,
    private val customTtsProvider: (() -> TextToSpeech?)? = null,
) : TextToSpeech.OnInitListener {

    val preferences: TtsPreferences? = context?.let { TtsPreferences(it) }
    private val piperEngine: NativePiperTtsEngine? = context?.let { NativePiperTtsEngine(it) }
    private val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val initialVoice = preferences?.getSelectedVoice() ?: FenixNarratorVoice.MALE_MATURE_RIOPLATENSE
    private val initialPitch = preferences?.getPitch() ?: 1.0f
    private val initialRate = preferences?.getSpeechRate() ?: 1.0f

    private val _playbackState = MutableStateFlow(
        TtsPlaybackState(
            pitch = initialPitch,
            speechRate = initialRate,
            selectedNarratorVoice = initialVoice,
            activeVoiceProfile = TtsVoiceProfile.EDGE_NATURAL_NEURAL,
            activeVoiceName = "Piper VITS (Neuronal Nativo)",
        )
    )
    val playbackState: StateFlow<TtsPlaybackState> = _playbackState.asStateFlow()

    private var activeParagraphs: List<String> = emptyList()

    init {
        initEngine()
    }

    private fun initEngine() {
        if (customTtsProvider != null) {
            tts = customTtsProvider.invoke()
            isInitialized = tts != null
            configureUtteranceListener()
        } else if (context != null) {
            try {
                tts = TextToSpeech(context, this)
            } catch (t: Throwable) {
                _playbackState.update {
                    it.copy(
                        status = TtsPlaybackStatus.ERROR,
                        errorMessage = "Fallo al inicializar fallback TTS local: ${t.message}",
                    )
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            configureUtteranceListener()
        } else {
            isInitialized = false
        }
    }

    fun configureVoiceByNarratorProfile(narratorVoice: FenixNarratorVoice) {
        preferences?.setSelectedVoice(narratorVoice)
        _playbackState.update {
            it.copy(
                selectedNarratorVoice = narratorVoice,
                activeVoiceName = "Piper VITS (${narratorVoice.displayName})",
            )
        }
    }

    fun synthesizeToFile(
        text: String,
        outputFile: File,
        utteranceId: String = "fenix_audiobook_${System.currentTimeMillis()}",
        onComplete: (Boolean) -> Unit = {},
    ) {
        if (!isInitialized || tts == null) {
            onComplete(false)
            return
        }
        val result = tts?.synthesizeToFile(text, null, outputFile, utteranceId)
        onComplete(result == TextToSpeech.SUCCESS)
    }

    private fun configureUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _playbackState.update {
                    it.copy(
                        status = TtsPlaybackStatus.PLAYING,
                        currentUtteranceId = utteranceId,
                    )
                }
            }

            override fun onDone(utteranceId: String?) {
                val nextIndex = _playbackState.value.currentParagraphIndex + 1
                if (nextIndex < activeParagraphs.size) {
                    _playbackState.update { it.copy(currentParagraphIndex = nextIndex) }
                    speakParagraphNative(nextIndex)
                } else {
                    _playbackState.update {
                        it.copy(
                            status = TtsPlaybackStatus.FINISHED,
                            currentUtteranceId = null,
                        )
                    }
                }
            }

            @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, -1)"))
            override fun onError(utteranceId: String?) {
                onError(utteranceId, -1)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _playbackState.update {
                    it.copy(
                        status = TtsPlaybackStatus.ERROR,
                        errorMessage = "Error de síntesis ($errorCode) en el párrafo $utteranceId",
                    )
                }
            }
        })
    }

    /**
     * Sintetiza y reproduce el texto con el modelo neuronal nativo Piper VITS.
     * Cero configuración, cero internet y calidad de voz humana.
     */
    fun speak(text: String) {
        if (text.isBlank()) return
        stopReading()

        if (piperEngine != null) {
            _playbackState.update {
                it.copy(
                    status = TtsPlaybackStatus.PLAYING,
                    activeVoiceProfile = TtsVoiceProfile.EDGE_NATURAL_NEURAL,
                    activeVoiceName = "Piper VITS (Neuronal Nativo)",
                    errorMessage = null,
                )
            }

            engineScope.launch {
                piperEngine.speak(
                    text = text,
                    speed = _playbackState.value.speechRate,
                    onComplete = { success ->
                        if (success) {
                            _playbackState.update { it.copy(status = TtsPlaybackStatus.FINISHED) }
                        } else {
                            speakWithNativeFallback(text)
                        }
                    }
                )
            }
        } else {
            speakWithNativeFallback(text)
        }
    }

    /**
     * Fallback al motor local tradicional de Android en caso de emergencia.
     */
    fun speakWithNativeFallback(text: String) {
        if (text.isBlank()) return
        stopReading()

        val paragraphs = text.split("\n\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(text.trim()) }

        activeParagraphs = paragraphs
        _playbackState.update {
            it.copy(
                status = TtsPlaybackStatus.PLAYING,
                currentParagraphIndex = 0,
                totalParagraphs = paragraphs.size,
                activeVoiceProfile = TtsVoiceProfile.LOCAL_DEVICE_STANDARD,
                activeVoiceName = "Android Nativo",
                errorMessage = null,
            )
        }

        speakParagraphNative(0)
    }

    private fun speakParagraphNative(index: Int) {
        if (index !in activeParagraphs.indices) return
        val text = activeParagraphs[index]
        val utteranceId = "fenix_p_$index"

        if (isInitialized && tts != null) {
            tts?.setPitch(_playbackState.value.pitch)
            tts?.setSpeechRate(_playbackState.value.speechRate)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            _playbackState.update {
                it.copy(
                    status = TtsPlaybackStatus.PLAYING,
                    currentUtteranceId = utteranceId,
                )
            }
        }
    }

    fun pauseReading() {
        piperEngine?.stop()
        tts?.stop()
        _playbackState.update { it.copy(status = TtsPlaybackStatus.PAUSED) }
    }

    fun resumeReading() {
        // En síntesis en memoria se continúa con el siguiente bloque
        _playbackState.update { it.copy(status = TtsPlaybackStatus.IDLE) }
    }

    fun stopReading() {
        piperEngine?.stop()
        tts?.stop()
        _playbackState.update {
            it.copy(
                status = TtsPlaybackStatus.IDLE,
                currentParagraphIndex = 0,
                currentUtteranceId = null,
            )
        }
    }

    fun setSpeechRate(rate: Float) {
        val clampedRate = rate.coerceIn(0.75f, 2.0f)
        preferences?.setSpeechRate(clampedRate)
        _playbackState.update { it.copy(speechRate = clampedRate) }
    }

    fun setPitch(pitch: Float) {
        val clampedPitch = pitch.coerceIn(0.5f, 1.5f)
        preferences?.setPitch(clampedPitch)
        _playbackState.update { it.copy(pitch = clampedPitch) }
    }

    fun shutdown() {
        stopReading()
        piperEngine?.release()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
