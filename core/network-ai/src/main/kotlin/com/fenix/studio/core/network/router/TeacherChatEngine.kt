/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-04 00:20:00 -03:00
 * ARCHIVO: core/network-ai/src/main/kotlin/com/fenix/studio/core/network/router/TeacherChatEngine.kt
 * ================================================================================
 */

package com.fenix.studio.core.network.router

import com.fenix.studio.core.network.teacher.TeacherChatHistoryTurn
import com.fenix.studio.core.network.teacher.TeacherChatOutcome

/**
 * Puerto de inferencia conversacional del chat académico.
 *
 * La UI no debe conocer de dónde sale la respuesta. Hoy lo implementa el pool de
 * proveedores keyless; en F3 lo implementará el router interno con las claves BYOK
 * del usuario. Cambiar el motor no debe tocar `feature:home-chat`.
 */
interface TeacherChatEngine {

    /**
     * Envía un turno al motor y devuelve un resultado tipado.
     *
     * @param startEndpointIndex permite rotar el punto de entrada entre turnos.
     */
    suspend fun sendChatToFenixTeacherResult(
        userPrompt: String,
        systemInstruction: String,
        conversationHistory: List<TeacherChatHistoryTurn> = emptyList(),
        startEndpointIndex: Int = 0,
    ): TeacherChatOutcome
}
