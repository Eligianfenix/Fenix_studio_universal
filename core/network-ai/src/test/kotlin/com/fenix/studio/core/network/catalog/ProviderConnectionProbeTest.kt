/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-14 19:58:00 -03:00
 * ARCHIVO: core/network-ai/src/test/kotlin/com/fenix/studio/core/network/catalog/ProviderConnectionProbeTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.network.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProviderConnectionProbeTest {

    private val probe = ProviderConnectionProbe()

    @Test
    fun `parses the OpenAI data envelope`() {
        val raw = """{"object":"list","data":[{"id":"llama-3.3-70b"},{"id":"gpt-oss-120b"}]}"""
        assertEquals(listOf("llama-3.3-70b", "gpt-oss-120b"), probe.parseModelIds(raw))
    }

    @Test
    fun `parses the models envelope and root arrays`() {
        assertEquals(
            listOf("gemini-2.5-flash"),
            probe.parseModelIds("""{"models":[{"name":"gemini-2.5-flash"}]}"""),
        )
        assertEquals(
            listOf("openai", "deepseek"),
            probe.parseModelIds("""[{"name":"openai"},{"name":"deepseek"}]"""),
        )
    }

    @Test
    fun `invalid payloads return an empty list instead of throwing`() {
        assertEquals(emptyList<String>(), probe.parseModelIds("no es json"))
        assertEquals(emptyList<String>(), probe.parseModelIds("""{"data":[]}"""))
        assertEquals(emptyList<String>(), probe.parseModelIds("""{"otra":"cosa"}"""))
    }
}
