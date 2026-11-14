/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-14 21:20:00 -03:00
 * ARCHIVO: core/security-vault/src/test/kotlin/com/fenix/studio/core/security/EncryptedSlotStoreTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.security

import com.fenix.studio.core.security.keystore.FenixKeystoreVault
import com.fenix.studio.core.security.keystore.InMemoryEncryptedSlotStore
import com.fenix.studio.core.security.keystore.JvmMemoryMasterKeyProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EncryptedSlotStoreTest {

    @Test
    fun `a new vault instance reads the encrypted slots already persisted`() {
        val store = InMemoryEncryptedSlotStore()
        val masterKeys = JvmMemoryMasterKeyProvider()

        val first = FenixKeystoreVault(masterKeys, store)
        first.storeApiKey("groq", 2, "gsk-persistida-9876")

        val second = FenixKeystoreVault(masterKeys, store)
        assertEquals("gsk-persistida-9876", second.getDecryptedApiKey("groq", 2))
        assertEquals(listOf(2), second.listConfiguredSlots("groq").map { it.slotIndex })
    }

    @Test
    fun `deleting a slot removes it from the persisted store too`() {
        val store = InMemoryEncryptedSlotStore()
        val masterKeys = JvmMemoryMasterKeyProvider()

        val first = FenixKeystoreVault(masterKeys, store)
        first.storeApiKey("groq", 0, "gsk-efimera")
        first.removeApiKeySlot("groq", 0)

        val second = FenixKeystoreVault(masterKeys, store)
        assertNull(second.getDecryptedApiKey("groq", 0))
        assertEquals(emptyList<Int>(), second.listConfiguredSlots("groq").map { it.slotIndex })
    }
}
