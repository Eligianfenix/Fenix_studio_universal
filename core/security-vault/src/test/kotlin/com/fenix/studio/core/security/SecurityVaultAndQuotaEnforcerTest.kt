/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:39:10 -03:00
 * ARCHIVO: core/security-vault/src/test/kotlin/com/fenix/studio/core/security/SecurityVaultAndQuotaEnforcerTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.security

import com.fenix.studio.core.security.archive.FenixPackageManifest
import com.fenix.studio.core.security.archive.FenixSovereignArchiveManager
import com.fenix.studio.core.security.keystore.FenixKeystoreVault
import com.fenix.studio.core.security.keystore.JvmMemoryMasterKeyProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.AEADBadTagException

class SecurityVaultAndQuotaEnforcerTest {

    @Test
    fun `FenixKeystoreVault encrypts and decrypts 6 BYOK slots with AES-256-GCM and rejects AAD substitution`() {
        val vault = FenixKeystoreVault(JvmMemoryMasterKeyProvider())

        for (slotIdx in 0 until FenixKeystoreVault.MAX_SLOTS_PER_PROVIDER) {
            val rawSecret = "test_byok_secret_slot_$slotIdx"
            val encrypted = vault.storeApiKey("gemini", slotIdx, rawSecret)

            assertFalse(encrypted.toString().contains(rawSecret), "toString() must redact plaintext keys")
            assertFalse(encrypted.ciphertextBase64.contains(rawSecret))
            assertEquals(rawSecret, vault.getDecryptedApiKey("gemini", slotIdx))
        }

        assertEquals(6, vault.listConfiguredSlots("gemini").size)

        val slot0 = vault.listConfiguredSlots("gemini").first { it.slotIndex == 0 }
        assertThrows(AEADBadTagException::class.java) {
            vault.decryptApiKey(slot0, expectedProviderId = "gemini", expectedSlotIndex = 1)
        }
    }

    @Test
    fun `FenixSovereignArchiveManager streams encrypted fenix packages and sanitizes private notes for community`() {
        val keyProvider = JvmMemoryMasterKeyProvider()
        val archiveKey = keyProvider.getOrCreateAes256Key("archive_test_key")
        val manager = FenixSovereignArchiveManager()

        val rawManifest = FenixPackageManifest(
            catedraId = "cat-900",
            displayTitle = "Cátedra General de Prueba",
            modalityName = "CATEDRA_PROGRAMA",
            programaYear = 2026,
            simHash64 = 987654321L,
            isSanitizedForCommunity = true,
            ownerUserId = "student-private-id-42",
            privateUserNotesJson = """[{"note":"Apunte personal privado"}]""",
            protocolBlocksJson = """[{"blockId":"b1","text":"Síntesis verificada"}]""",
            exportedAtEpochMs = 1_700_000_000_000L,
        )

        val output = ByteArrayOutputStream()
        manager.exportArchiveToStream(rawManifest, output, optionalAesKey = archiveKey)

        val restored = manager.importArchiveFromStream(
            ByteArrayInputStream(output.toByteArray()),
            optionalAesKey = archiveKey,
        )

        assertEquals("cat-900", restored.catedraId)
        assertTrue(restored.isSanitizedForCommunity)
        assertNull(restored.ownerUserId)
        assertNull(restored.privateUserNotesJson)
        assertTrue(restored.protocolBlocksJson.contains("Síntesis verificada"))
    }
}
