/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-14 21:15:00 -03:00
 * ARCHIVO: core/network-ai/src/test/kotlin/com/fenix/studio/core/network/router/ChatCompletionTransportTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.network.router

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ChatCompletionTransportTest {

    private val transport = JsonChatCompletionTransport()

    @Test
    fun `parses the OpenAI chat completion envelope`() {
        val raw = """
            {"id":"x","choices":[{"index":0,"message":{"role":"assistant","content":"Hola, Eli"}}]}
        """.trimIndent()
        assertEquals("Hola, Eli", transport.parseContent(raw))
    }

    @Test
    fun `parses legacy completion envelopes`() {
        val raw = """{"choices":[{"text":"respuesta vieja"}]}"""
        assertEquals("respuesta vieja", transport.parseContent(raw))
    }

    @Test
    fun `invalid or empty bodies return null instead of throwing`() {
        assertNull(transport.parseContent("no es json"))
        assertNull(transport.parseContent("""{"choices":[]}"""))
        assertNull(transport.parseContent("""{"choices":[{"message":{"content":null}}]}"""))
    }
}
