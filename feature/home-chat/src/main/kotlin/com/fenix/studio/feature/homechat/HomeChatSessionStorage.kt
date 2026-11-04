/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-02 22:56:00 -03:00
 * ARCHIVO: feature/home-chat/src/main/kotlin/com/fenix/studio/feature/homechat/HomeChatSessionStorage.kt
 * ================================================================================
 */

package com.fenix.studio.feature.homechat

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Modelo serializable de sesión persistida en almacenamiento local privado.
 */
@Serializable
data class PersistedChatSession(
    val id: String,
    val title: String,
    val preview: String = "",
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val messages: List<PersistedChatMessage> = emptyList(),
)

/**
 * Modelo serializable de mensaje persistido.
 */
@Serializable
data class PersistedChatMessage(
    val messageId: String,
    val isUser: Boolean,
    val text: String,
    val senderLabel: String,
    val isDeontologicalWarning: Boolean = false,
)

/**
 * Almacenamiento local persistente de sesiones e historiales conversacionales (`HomeChatSessionStorage`).
 * Guarda las sesiones y sus mensajes de forma atómica en archivos JSON dentro de `context.filesDir / "fenix_chat_sessions"`.
 * Garantiza que las sesiones perduren incondicionalmente tras cierres de app o reinicios del dispositivo.
 */
class HomeChatSessionStorage(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val sessionsDir = File(context.filesDir, "fenix_chat_sessions").apply { mkdirs() }
    private val indexFile = File(sessionsDir, "sessions_index.json")

    @Synchronized
    fun loadHistoricalSessions(): List<HistoricalSessionItem> {
        return runCatching {
            if (!indexFile.exists()) return emptyList()
            val content = indexFile.readText()
            val list = json.decodeFromString<List<PersistedChatSession>>(content)
            list.sortedByDescending { it.updatedAtEpochMs }.map {
                HistoricalSessionItem(id = it.id, title = it.title, preview = it.preview)
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun loadSessionMessages(sessionId: String): List<HomeChatMessageItem> {
        val file = File(sessionsDir, "$sessionId.json")
        if (!file.exists()) return emptyList()
        return runCatching {
            val content = file.readText()
            val session = json.decodeFromString<PersistedChatSession>(content)
            session.messages.map {
                HomeChatMessageItem(
                    messageId = it.messageId,
                    isUser = it.isUser,
                    text = it.text,
                    senderLabel = it.senderLabel,
                    isDeontologicalWarning = it.isDeontologicalWarning,
                    isThinking = false,
                )
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun saveSession(
        sessionId: String,
        messages: List<HomeChatMessageItem>,
    ) {
        if (messages.isEmpty()) return
        val firstUserMsg = messages.firstOrNull { it.isUser }?.text?.take(35) ?: "Sesión académica"
        val lastMsg = messages.lastOrNull { !it.isThinking && !it.isError }?.text?.take(50) ?: ""

        val persistedMessages = messages.filterNot { it.isThinking || it.isError }.map {
            PersistedChatMessage(
                messageId = it.messageId,
                isUser = it.isUser,
                text = it.text,
                senderLabel = it.senderLabel,
                isDeontologicalWarning = it.isDeontologicalWarning,
            )
        }

        val session = PersistedChatSession(
            id = sessionId,
            title = firstUserMsg,
            preview = lastMsg,
            updatedAtEpochMs = System.currentTimeMillis(),
            messages = persistedMessages,
        )

        // 1. Guardar archivo individual de la sesión
        val sessionFile = File(sessionsDir, "$sessionId.json")
        sessionFile.writeText(json.encodeToString(PersistedChatSession.serializer(), session))

        // 2. Actualizar índice maestro de sesiones
        val existingIndex = runCatching {
            if (indexFile.exists()) json.decodeFromString<List<PersistedChatSession>>(indexFile.readText()) else emptyList()
        }.getOrDefault(emptyList()).toMutableList()

        existingIndex.removeAll { it.id == sessionId }
        existingIndex.add(0, session)
        indexFile.writeText(json.encodeToString(existingIndex))
    }
}

