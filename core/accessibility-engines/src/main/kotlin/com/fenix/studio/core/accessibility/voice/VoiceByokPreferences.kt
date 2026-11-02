/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-02 00:42:00 -03:00
 * ARCHIVO: core/accessibility-engines/src/main/kotlin/com/fenix/studio/core/accessibility/voice/VoiceByokPreferences.kt
 * ================================================================================
 */

package com.fenix.studio.core.accessibility.voice

import android.content.Context
import android.content.SharedPreferences

class VoiceByokPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("fenix_voice_byok_prefs", Context.MODE_PRIVATE)

    fun getApiKey(): String = prefs.getString(KEY_GROQ_API_KEY, "").orEmpty()

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_GROQ_API_KEY, key.trim()).apply()
    }

    fun hasApiKey(): Boolean = getApiKey().isNotBlank()

    fun getBaseUrl(): String = prefs.getString(KEY_BASE_URL, DEFAULT_GROQ_BASE_URL) ?: DEFAULT_GROQ_BASE_URL

    fun setBaseUrl(url: String) {
        prefs.edit().putString(KEY_BASE_URL, url.trim()).apply()
    }

    fun getSelectedModel(): String = prefs.getString(KEY_SELECTED_MODEL, "") ?: ""

    fun setSelectedModel(modelId: String) {
        prefs.edit().putString(KEY_SELECTED_MODEL, modelId.trim()).apply()
    }

    fun clearKey() {
        prefs.edit().remove(KEY_GROQ_API_KEY).apply()
    }

    companion object {
        private const val KEY_GROQ_API_KEY = "groq_api_key"
        private const val KEY_BASE_URL = "groq_base_url"
        private const val KEY_SELECTED_MODEL = "groq_selected_model"
        const val DEFAULT_GROQ_BASE_URL = "https://api.groq.com/openai/v1"
    }
}

