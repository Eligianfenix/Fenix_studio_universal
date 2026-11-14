/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-14 19:55:00 -03:00
 * ARCHIVO: core/network-ai/src/test/kotlin/com/fenix/studio/core/network/catalog/AiProviderCatalogTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.network.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiProviderCatalogTest {

    @Test
    fun `every provider has a unique id and the required endpoints`() {
        val ids = AiProviderCatalog.providers.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Hay ids duplicados en el catálogo")

        AiProviderCatalog.providers.forEach { spec ->
            assertTrue(spec.chatCompletionsUrl.startsWith("https://"), "${spec.id} no usa HTTPS")
            spec.modelsUrl?.let {
                assertTrue(it.startsWith("https://"), "${spec.id} tiene modelsUrl sin HTTPS")
            }
        }
    }

    @Test
    fun `keyless providers need no key and BYOK providers use bearer auth`() {
        AiProviderCatalog.keylessProviders().forEach { spec ->
            assertEquals(ProviderAuthStyle.NONE, spec.authStyle, "${spec.id} es keyless pero pide auth")
            assertTrue(spec.keyless)
        }
        AiProviderCatalog.byokProviders().forEach { spec ->
            assertEquals(ProviderAuthStyle.BEARER, spec.authStyle, "${spec.id} no usa Bearer")
            assertTrue(!spec.keyless)
        }
    }

    @Test
    fun `catalog keeps the zero-config providers available out of the box`() {
        val keyless = AiProviderCatalog.keylessProviders().map { it.id }.toSet()
        assertTrue(keyless.containsAll(setOf("pollinations", "llm7", "ovh_ai", "publicai")))
    }

    @Test
    fun `catalog includes the providers whose keys are already on the machine`() {
        val ids = AiProviderCatalog.providers.map { it.id }.toSet()
        val expected = setOf(
            "gemini", "groq", "sambanova", "siliconflow", "cohere", "nvidia",
            "openrouter", "fastrouter", "unorouter", "routeway", "tokenharbor",
            "requesty", "plugsky", "agnes", "airforce", "baseten", "naga",
            "kilocode", "mancer", "ollama_cloud", "anyapi",
        )
        expected.forEach { id ->
            assertTrue(ids.contains(id), "Falta el proveedor $id en el catálogo")
        }
    }

    @Test
    fun `byok providers are ordered by ring`() {
        val orders = AiProviderCatalog.byokProviders().map { it.ring.order }
        assertEquals(orders.sorted(), orders)
    }
}
