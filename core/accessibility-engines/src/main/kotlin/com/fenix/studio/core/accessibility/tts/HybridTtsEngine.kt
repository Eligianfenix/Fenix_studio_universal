/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-02 01:52:00 -03:00
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
    val speechRate: Float = 0.92f,
    val pitch: Float = 0.82f,
    val currentUtteranceId: String? = null,
    val errorMessage: String? = null,
    val activeVoiceProfile: TtsVoiceProfile = TtsVoiceProfile.LOCAL_DEVICE_STANDARD,
    val activeVoiceName: String? = null,
    val selectedNarratorVoice: FenixNarratorVoice = FenixNarratorVoice.MALE_MATURE_RIOPLATENSE,
)

/**
 * Motor TTS Local Soberano a Costo 0 (`MF-8.2`, `MF-D.6`).
 * Configurado por defecto con voz profunda y madura de narrador de audiolibros (AMMA Audiolibros, Rioplatense).
 * Soporta síntesis continua por párrafo, exportación a archivos reproducibles y personalización en ajustes.
 */
class HybridTtsEngine(
    private val context: Context? = null,
    private val customTtsProvider: (() -> TextToSpeech?)? = null,
) : TextToSpeech.OnInitListener {

    val preferences: TtsPreferences? = context?.let { TtsPreferences(it) }

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val initialVoice = preferences?.getSelectedVoice() ?: FenixNarratorVoice.MALE_MATURE_RIOPLATENSE
    private val initialPitch = preferences?.getPitch() ?: initialVoice.defaultPitch
    private val initialRate = preferences?.getSpeechRate() ?: initialVoice.defaultRate

    private val _playbackState = MutableStateFlow(
        TtsPlaybackState(
            pitch = initialPitch,
            speechRate = initialRate,
            selectedNarratorVoice = initialVoice,
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
                        errorMessage = "Fallo al inicializar TTS local: ${t.message}",
                    )
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            val currentVoice = preferences?.getSelectedVoice() ?: FenixNarratorVoice.MALE_MATURE_RIOPLATENSE
            val currentPitch = preferences?.getPitch() ?: currentVoice.defaultPitch
            val currentRate = preferences?.getSpeechRate() ?: currentVoice.defaultRate

            _playbackState.update {
                it.copy(
                    pitch = currentPitch,
                    speechRate = currentRate,
                    selectedNarratorVoice = currentVoice,
                )
            }

            configureVoiceByNarratorProfile(currentVoice)
            tts?.setSpeechRate(currentRate)
            tts?.setPitch(currentPitch)
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
     * Configura el idioma, tono y características de la voz seleccionada por el estudiante.
     */
    fun configureVoiceByNarratorProfile(narratorVoice: FenixNarratorVoice) {
        preferences?.setSelectedVoice(narratorVoice)
        val targetLocale = Locale.forLanguageTag(narratorVoice.targetLocaleTag)
        try {
            val availableVoices = tts?.voices
            val isMaleTarget = narratorVoice == FenixNarratorVoice.MALE_MATURE_RIOPLATENSE ||
                narratorVoice == FenixNarratorVoice.MALE_CLASSIC_NEUTRAL

            val matchingVoice = availableVoices?.firstOrNull { voice ->
                val matchesLang = voice.locale.language.equals("es", ignoreCase = true)
                val matchesGender = if (isMaleTarget) {
                    voice.name.contains("male", ignoreCase = true) || !voice.name.contains("ana", ignoreCase = true)
                } else {
                    voice.name.contains("female", ignoreCase = true) || voice.name.contains("ana", ignoreCase = true)
                }
                matchesLang && matchesGender
            } ?: availableVoices?.firstOrNull { it.locale.language.equals("es", ignoreCase = true) }

            if (matchingVoice != null) {
                tts?.voice = matchingVoice
                _playbackState.update {
                    it.copy(
                        selectedNarratorVoice = narratorVoice,
                        activeVoiceName = matchingVoice.name,
                    )
                }
                return
            }
        } catch (_: Throwable) {
            // Fallback seguro si getVoices() falla en pruebas
        }

        tts?.language = targetLocale
        _playbackState.update {
            it.copy(
                selectedNarratorVoice = narratorVoice,
                activeVoiceName = "${narratorVoice.displayName} (${targetLocale.toLanguageTag()})",
            )
        }
    }

    /**
     * Sintetiza y exporta directamente el texto a un archivo de audio WAV en almacenamiento local.
     * Permite generar audiolibros completos y apuntes reproducibles offline a Costo $0.
     */
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

    /**
     * Prioriza la configuración de voces naturales disponibles en el sistema.
     */
    fun configureNaturalVoiceOrFallback() {
        configureVoiceByNarratorProfile(_playbackState.value.selectedNarratorVoice)
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
            _playbackState.update {
                it.copy(
                    status = TtsPlaybackStatus.PLAYING,
                    currentUtteranceId = utteranceId,
                )
            }
        }
    }

    fun pauseReading() {
        tts?.stop()
        _playbackState.update { it.copy(status = TtsPlaybackStatus.PAUSED) }
    }

    fun resumeReading() {
        val currentIndex = _playbackState.value.currentParagraphIndex
        if (currentIndex in activeParagraphs.indices) {
            _playbackState.update { it.copy(status = TtsPlaybackStatus.PLAYING) }
            speakParagraph(currentIndex)
        }
    }

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
        preferences?.setSpeechRate(clampedRate)
        _playbackState.update { it.copy(speechRate = clampedRate) }
    }

    /**
     * Ajusta el tono de voz entre `0.5x..1.5x`.
     */
    fun setPitch(pitch: Float) {
        val clampedPitch = pitch.coerceIn(0.5f, 1.5f)
        tts?.setPitch(clampedPitch)
        preferences?.setPitch(clampedPitch)
        _playbackState.update { it.copy(pitch = clampedPitch) }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
