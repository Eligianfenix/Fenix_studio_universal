/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/network-ai/src/test/kotlin/com/fenix/studio/core/network/teacher/FenixTeacherKeylessServiceTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.network.teacher

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FenixTeacherKeylessServiceTest {

    @Test
    fun `discoverLiveKeylessRoutes dynamically discovers models from gateways without hardcoding RN-08`() = runBlocking {
        val service = FenixTeacherKeylessService()
        val routes = service.discoverLiveKeylessRoutes(forceRefresh = true)
        println("Discovered live keyless routes (${routes.size}): ${routes.map { it.routeKey }}")
        assertTrue(routes.isNotEmpty(), "Debe descubrir dinámicamente al menos una ruta desde /models")
    }

    @Test
    fun `sendChatToFenixTeacher returns real live AI response via hedged dynamic routes`() = runBlocking {
        val service = FenixTeacherKeylessService()
        val reply = service.sendChatToFenixTeacher(
            userPrompt = "Hola, responde brevemente en una oración en español.",
            systemInstruction = "Eres Fenix Teacher, profesor académico en español.",
        )
        println("Live Fenix Teacher reply: $reply")
        assertTrue(reply.isNotBlank())
        assertFalse(reply.startsWith("No se pudo obtener respuesta"), "No debe devolver mensaje de error: $reply")
    }
}

