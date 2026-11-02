/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-02 01:50:00 -03:00
 * ARCHIVO: core/accessibility-engines/src/main/kotlin/com/fenix/studio/core/accessibility/tts/TtsPreferences.kt
 * ================================================================================
 */

package com.fenix.studio.core.accessibility.tts

import android.content.Context
import android.content.SharedPreferences

enum class FenixNarratorVoice(
    val id: String,
    val displayName: String,
    val defaultPitch: Float,
    val defaultRate: Float,
    val targetLocaleTag: String,
) {
    MALE_MATURE_RIOPLATENSE(
        id = "male_mature_rioplatense",
        displayName = "Hombre Maduro (AMMA Audiolibros - Rioplatense)",
        defaultPitch = 0.82f,
        defaultRate = 0.92f,
        targetLocaleTag = "es-AR",
    ),
    MALE_CLASSIC_NEUTRAL(
        id = "male_classic_neutral",
        displayName = "Hombre Clásico (Narrador Neutro)",
        defaultPitch = 0.88f,
        defaultRate = 0.95f,
        targetLocaleTag = "es-MX",
    ),
    FEMALE_RIOPLATENSE(
        id = "female_rioplatense",
        displayName = "Mujer Rioplatense (Cálida y Natural)",
        defaultPitch = 1.0f,
        defaultRate = 1.0f,
        targetLocaleTag = "es-AR",
    ),
    FEMALE_NEUTRAL(
        id = "female_neutral",
        displayName = "Mujer Neutra (Locución Académica)",
        defaultPitch = 1.0f,
        defaultRate = 1.0f,
        targetLocaleTag = "es-ES",
    ),
    DEVICE_DEFAULT(
        id = "device_default",
        displayName = "Voz por Defecto del Dispositivo",
        defaultPitch = 1.0f,
        defaultRate = 1.0f,
        targetLocaleTag = "es",
    ),
}

class TtsPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("fenix_tts_prefs", Context.MODE_PRIVATE)

    fun getSelectedVoice(): FenixNarratorVoice {
        val raw = prefs.getString(KEY_SELECTED_VOICE, FenixNarratorVoice.MALE_MATURE_RIOPLATENSE.id)
        return FenixNarratorVoice.entries.firstOrNull { it.id == raw } ?: FenixNarratorVoice.MALE_MATURE_RIOPLATENSE
    }

    fun setSelectedVoice(voice: FenixNarratorVoice) {
        prefs.edit().putString(KEY_SELECTED_VOICE, voice.id).apply()
    }

    fun getPitch(): Float = prefs.getFloat(KEY_PITCH, FenixNarratorVoice.MALE_MATURE_RIOPLATENSE.defaultPitch)

    fun setPitch(pitch: Float) {
        prefs.edit().putFloat(KEY_PITCH, pitch.coerceIn(0.5f, 1.5f)).apply()
    }

    fun getSpeechRate(): Float = prefs.getFloat(KEY_SPEECH_RATE, FenixNarratorVoice.MALE_MATURE_RIOPLATENSE.defaultRate)

    fun setSpeechRate(rate: Float) {
        prefs.edit().putFloat(KEY_SPEECH_RATE, rate.coerceIn(0.75f, 2.0f)).apply()
    }

    companion object {
        private const val KEY_SELECTED_VOICE = "selected_tts_voice"
        private const val KEY_PITCH = "tts_pitch"
        private const val KEY_SPEECH_RATE = "tts_speech_rate"
    }
}

