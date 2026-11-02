/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-02 02:37:00 -03:00
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
    val speechRate: Float = 0.92f,
    val pitch: Float = 0.82f,
    val currentUtteranceId: String? = null,
    val errorMessage: String? = null,
    val activeVoiceProfile: TtsVoiceProfile = TtsVoiceProfile.LOCAL_DEVICE_STANDARD,
    val activeVoiceName: String? = null,
    val selectedNarratorVoice: FenixNarratorVoice = FenixNarratorVoice.MALE_MATURE_RIOPLATENSE,
)

/**
 * Motor TTS Híbrido Soberano a Costo 0 (`MF-8.2`, `MF-D.6`).
 * Prioriza la síntesis de voz neuronal de alta fidelidad (Narrador Profesional - es-AR-TomasNeural)
 * y conmuta transparentemente al motor nativo offline de Android en ausencia de red o clave.
 */
class HybridTtsEngine(
    private val context: Context? = null,
    private val customTtsProvider: (() -> TextToSpeech?)? = null,
) : TextToSpeech.OnInitListener {

    val preferences: TtsPreferences? = context?.let { TtsPreferences(it) }
    private val azureClient = AzureSpeechTtsClient()
    private val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isInitialized = false

    private val initialVoice = preferences?.getSelectedVoice() ?: FenixNarratorVoice.MALE_MATURE_RIOPLATENSE
    private val initialPitch = preferences?.getPitch() ?: initialVoice.defaultPitch
    private val initialRate = preferences?.getSpeechRate() ?: initialVoice.defaultRate

    private val _playbackState = MutableStateFlow(
        TtsPlaybackState(
            pitch = initialPitch,
            speechRate = initialRate,
            selectedNarratorVoice = initialVoice,
            activeVoiceProfile = if (preferences?.getAzureKey() != null) TtsVoiceProfile.EDGE_NATURAL_NEURAL else TtsVoiceProfile.LOCAL_DEVICE_STANDARD,
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
        preferences?.setPitch(narratorVoice.defaultPitch)
        preferences?.setSpeechRate(narratorVoice.defaultRate)

        val targetLocale = Locale.forLanguageTag(narratorVoice.targetLocaleTag)
        try {
            tts?.language = targetLocale
            tts?.setPitch(narratorVoice.defaultPitch)
            tts?.setSpeechRate(narratorVoice.defaultRate)
        } catch (_: Throwable) {
            // Manejo de resiliencia
        }

        _playbackState.update {
            it.copy(
                selectedNarratorVoice = narratorVoice,
                pitch = narratorVoice.defaultPitch,
                speechRate = narratorVoice.defaultRate,
                activeVoiceName = narratorVoice.displayName,
                activeVoiceProfile = if (preferences?.getAzureKey() != null) TtsVoiceProfile.EDGE_NATURAL_NEURAL else TtsVoiceProfile.LOCAL_DEVICE_STANDARD,
            )
        }
    }

    /**
     * Sintetiza y exporta directamente el texto a un archivo de audio WAV en almacenamiento local.
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

    fun configureNaturalVoiceOrFallback() {
        configureVoiceByNarratorProfile(_playbackState.value.selectedNarratorVoice)
    }

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
     * Sintetiza y reproduce el texto en voz alta.
     * Si la clave de Azure Speech está presente, sintetiza voz neuronal de alta fidelidad.
     * Si no hay clave o falla, usa el motor nativo de Android.
     */
    fun speak(text: String) {
        if (text.isBlank()) return
        stopReading()

        val azureKey = preferences?.getAzureKey()
        val isDefaultOnly = preferences?.isUseDeviceDefaultOnly() == true

        if (!azureKey.isNullOrBlank() && !isDefaultOnly && context != null) {
            val region = preferences.getAzureRegion()
            val selectedVoice = _playbackState.value.selectedNarratorVoice
            val cacheFile = File(context.cacheDir, "fenix_tts_${System.currentTimeMillis()}.mp3")

            _playbackState.update {
                it.copy(
                    status = TtsPlaybackStatus.PLAYING,
                    activeVoiceProfile = TtsVoiceProfile.EDGE_NATURAL_NEURAL,
                    activeVoiceName = selectedVoice.displayName,
                    errorMessage = null,
                )
            }

            engineScope.launch {
                val pitchPercent = ((_playbackState.value.pitch - 1.0f) * 40).toInt()
                val result = azureClient.synthesizeSpeech(
                    apiKey = azureKey,
                    region = region,
                    text = text,
                    voiceName = selectedVoice.azureVoiceName,
                    outputFile = cacheFile,
                    rateModifier = _playbackState.value.speechRate,
                    pitchModifierPercent = pitchPercent,
                )

                result.onSuccess { audioFile ->
                    playAudioFile(audioFile) {
                        speakWithNativeFallback(text)
                    }
                }.onFailure {
                    // Fallback transparente al motor nativo si la red o Azure falla
                    speakWithNativeFallback(text)
                }
            }
        } else {
            speakWithNativeFallback(text)
        }
    }

    /**
     * Reproduce inmediatamente el texto con el motor nativo local de Android.
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
                errorMessage = null,
            )
        }

        speakParagraphNative(0)
    }

    private fun playAudioFile(audioFile: File, onFallbackNeeded: () -> Unit) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    _playbackState.update { it.copy(status = TtsPlaybackStatus.FINISHED) }
                    audioFile.delete()
                }
                setOnErrorListener { _, _, _ ->
                    audioFile.delete()
                    onFallbackNeeded()
                    true
                }
                start()
            }
        } catch (_: Exception) {
            audioFile.delete()
            onFallbackNeeded()
        }
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
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (_: Exception) {}
        tts?.stop()
        _playbackState.update { it.copy(status = TtsPlaybackStatus.PAUSED) }
    }

    fun resumeReading() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer?.start()
                _playbackState.update { it.copy(status = TtsPlaybackStatus.PLAYING) }
                return
            }
        } catch (_: Exception) {}

        val currentIndex = _playbackState.value.currentParagraphIndex
        if (currentIndex in activeParagraphs.indices) {
            _playbackState.update { it.copy(status = TtsPlaybackStatus.PLAYING) }
            speakParagraphNative(currentIndex)
        }
    }

    fun stopReading() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}

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
        tts?.setSpeechRate(clampedRate)
        preferences?.setSpeechRate(clampedRate)
        _playbackState.update { it.copy(speechRate = clampedRate) }
    }

    fun setPitch(pitch: Float) {
        val clampedPitch = pitch.coerceIn(0.5f, 1.5f)
        tts?.setPitch(clampedPitch)
        preferences?.setPitch(clampedPitch)
        _playbackState.update { it.copy(pitch = clampedPitch) }
    }

    fun shutdown() {
        stopReading()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
