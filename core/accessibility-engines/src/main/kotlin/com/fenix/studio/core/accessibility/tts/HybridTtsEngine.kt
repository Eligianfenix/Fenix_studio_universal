/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 03:47:45 -03:00
 * ARCHIVO: core/accessibility-engines/src/main/kotlin/com/fenix/studio/core/accessibility/tts/HybridTtsEngine.kt
 * ================================================================================
 */

package com.fenix.studio.core.accessibility.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val activeVoiceProfile: TtsVoiceProfile = TtsVoiceProfile.LOCAL_DEVICE_STANDARD,
    val activeVoiceName: String? = null,
)

/**
 * Motor TTS Local Soberano a Costo 0 (`MF-8.2`, `MF-D.6`).
 * Prioriza la configuración de voces naturales / Edge-TTS antes del fallback a TTS mecánico local de Android.
 * Ofrece lectura sincronizada por párrafo y control de velocidad granular (`0.75x..2.0x`).
 */
class HybridTtsEngine(
    private val context: Context? = null,
    private val customTtsProvider: (() -> TextToSpeech?)? = null,
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _playbackState = MutableStateFlow(TtsPlaybackState())
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
                        errorMessage = "Fallo al inicializar TTS local: ${t.message}",
                    )
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            configureNaturalVoiceOrFallback()
            tts?.setSpeechRate(_playbackState.value.speechRate)
            tts?.setPitch(_playbackState.value.pitch)
            configureUtteranceListener()
        } else {
            isInitialized = false
            _playbackState.update {
                it.copy(
                    status = TtsPlaybackStatus.ERROR,
                    errorMessage = "El motor nativo de TextToSpeech no está disponible en este dispositivo.",
                )
            }
        }
    }

    /**
     * Prioriza la configuración de voces naturales / neurales (Edge-TTS / Neural Voices)
     * disponibles en el sistema antes de recurrir a la voz mecánica local por defecto (`MF-D.6`).
     */
    fun configureNaturalVoiceOrFallback() {
        val targetLocale = Locale.forLanguageTag("es-AR")
        try {
            val availableVoices = tts?.voices
            val naturalVoice = availableVoices?.firstOrNull { voice ->
                voice.locale.language.equals("es", ignoreCase = true) &&
                    (voice.name.contains("natural", ignoreCase = true) ||
                     voice.name.contains("neural", ignoreCase = true) ||
                     voice.name.contains("edge", ignoreCase = true) ||
                     voice.quality >= 400) // Voice.QUALITY_HIGH / VERY_HIGH
            }

            if (naturalVoice != null) {
                tts?.voice = naturalVoice
                _playbackState.update {
                    it.copy(
                        activeVoiceProfile = TtsVoiceProfile.EDGE_NATURAL_NEURAL,
                        activeVoiceName = naturalVoice.name,
                    )
                }
                return
            }
        } catch (_: Throwable) {
            // Fallback seguro si getVoices() no está disponible en entorno de pruebas
        }

        // Fallback a motor estándar local de Android
        tts?.language = targetLocale
        _playbackState.update {
            it.copy(
                activeVoiceProfile = TtsVoiceProfile.LOCAL_DEVICE_STANDARD,
                activeVoiceName = "Android Standard TTS (${targetLocale.toLanguageTag()})",
            )
        }
    }

    /**
     * Permite seleccionar explícitamente la preferencia de perfil de voz.
     */
    fun setVoiceProfilePreference(profile: TtsVoiceProfile) {
        _playbackState.update { it.copy(activeVoiceProfile = profile) }
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
                    speakParagraph(nextIndex)
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
     * Inicia la lectura en voz alta secuencial de una lista de párrafos con anclaje visual sincronizado.
     */
    fun startReading(paragraphs: List<String>, startIndex: Int = 0) {
        if (paragraphs.isEmpty()) return
        stopReading()

        activeParagraphs = paragraphs
        val safeIndex = startIndex.coerceIn(0, paragraphs.size - 1)

        _playbackState.update {
            it.copy(
                status = TtsPlaybackStatus.PLAYING,
                currentParagraphIndex = safeIndex,
                totalParagraphs = paragraphs.size,
                errorMessage = null,
            )
        }

        speakParagraph(safeIndex)
    }

    /**
     * Sintetiza y reproduce el texto en voz alta de forma inmediata (`MF-8.2`, `MF-D.6`).
     * Segmenta en párrafos naturales para sincronización visual y auditiva.
     */
    fun speak(text: String) {
        if (text.isBlank()) return
        val paragraphs = text.split("\n\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(text.trim()) }
        startReading(paragraphs)
    }

    private fun speakParagraph(index: Int) {
        if (index !in activeParagraphs.indices) return
        val text = activeParagraphs[index]
        val utteranceId = "fenix_p_$index"

        if (isInitialized && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            // Entorno de emulación o test
            _playbackState.update {
                it.copy(
                    status = TtsPlaybackStatus.PLAYING,
                    currentUtteranceId = utteranceId,
                )
            }
        }
    }

    /**
     * Pausa la locución activa preservando el índice del párrafo actual.
     */
    fun pauseReading() {
        tts?.stop()
        _playbackState.update { it.copy(status = TtsPlaybackStatus.PAUSED) }
    }

    /**
     * Reanuda la locución desde el párrafo actual.
     */
    fun resumeReading() {
        val currentIndex = _playbackState.value.currentParagraphIndex
        if (currentIndex in activeParagraphs.indices) {
            _playbackState.update { it.copy(status = TtsPlaybackStatus.PLAYING) }
            speakParagraph(currentIndex)
        }
    }

    /**
     * Detiene por completo la lectura y resetea el cursor.
     */
    fun stopReading() {
        tts?.stop()
        _playbackState.update {
            it.copy(
                status = TtsPlaybackStatus.IDLE,
                currentParagraphIndex = 0,
                currentUtteranceId = null,
            )
        }
    }

    /**
     * Ajusta la velocidad de habla delimitada estrictamente en `0.75x..2.0x`.
     */
    fun setSpeechRate(rate: Float) {
        val clampedRate = rate.coerceIn(0.75f, 2.0f)
        tts?.setSpeechRate(clampedRate)
        _playbackState.update { it.copy(speechRate = clampedRate) }
    }

    /**
     * Ajusta el tono de voz entre `0.5x..1.5x`.
     */
    fun setPitch(pitch: Float) {
        val clampedPitch = pitch.coerceIn(0.5f, 1.5f)
        tts?.setPitch(clampedPitch)
        _playbackState.update { it.copy(pitch = clampedPitch) }
    }

    /**
     * Libera los recursos del motor TTS local.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
