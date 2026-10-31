/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/security-vault/src/main/kotlin/com/fenix/studio/core/security/keystore/FenixKeystoreVault.kt
 * ================================================================================
 */

package com.fenix.studio.core.security.keystore

import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Sobre cifrado inmutable para una ranura (slot) de clave BYOK.
 * Jamás expone la clave en texto plano en `toString()`.
 */
data class EncryptedApiKeySlot(
    val providerId: String,
    val slotIndex: Int,
    val initializationVectorBase64: String,
    val ciphertextBase64: String,
    val updatedAtEpochMs: Long,
) {
    override fun toString(): String =
        "EncryptedApiKeySlot(providerId=$providerId, slotIndex=$slotIndex, redacted=***)"
}

/**
 * Proveedor criptográfico abstraído para permitir ejecución real sobre `AndroidKeyStore` (TEE/StrongBox)
 * en dispositivos físicos y ejecución determinista AES-256-GCM + HMAC-SHA256 en pruebas JVM JUnit 5.
 */
interface MasterKeyProvider {
    fun getOrCreateAes256Key(alias: String): SecretKey
    fun getOrCreateHmacSha256Key(alias: String): SecretKey
}

/**
 * Implementación de producción respaldada por el hardware `AndroidKeyStore`.
 */
class AndroidHardwareKeyProvider : MasterKeyProvider {
    override fun getOrCreateAes256Key(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
        val parameterSpec = android.security.keystore.KeyGenParameterSpec.Builder(
            alias,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }

    override fun getOrCreateHmacSha256Key(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(
            android.security.keystore.KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
            ANDROID_KEYSTORE,
        )
        val parameterSpec = android.security.keystore.KeyGenParameterSpec.Builder(
            alias,
            android.security.keystore.KeyProperties.PURPOSE_SIGN or
                android.security.keystore.KeyProperties.PURPOSE_VERIFY,
        )
            .setKeySize(256)
            .build()
        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}

/**
 * Implementación JCA pura en memoria para pruebas unitarias deterministas en JVM.
 */
class JvmMemoryMasterKeyProvider : MasterKeyProvider {
    private val aesKeys = ConcurrentHashMap<String, SecretKey>()
    private val hmacKeys = ConcurrentHashMap<String, SecretKey>()
    private val secureRandom = SecureRandom()

    override fun getOrCreateAes256Key(alias: String): SecretKey =
        aesKeys.getOrPut(alias) {
            KeyGenerator.getInstance("AES").apply { init(256, secureRandom) }.generateKey()
        }

    override fun getOrCreateHmacSha256Key(alias: String): SecretKey =
        hmacKeys.getOrPut(alias) {
            KeyGenerator.getInstance("HmacSHA256").apply { init(256, secureRandom) }.generateKey()
        }
}

/**
 * Bóveda criptográfica soberana de Fenix Studio (`FenixKeystoreVault`, MF-1.3).
 * - Cifra hasta 6 claves BYOK por proveedor (`MAX_SLOTS_PER_PROVIDER = 6`) con `AES/GCM/NoPadding` (256 bits, tag 128 bits).
 * - Vincula criptográficamente el `AAD` (`Associated Authenticated Data`) a `"$providerId:slot_$slotIndex"`
 *   para impedir ataques de sustitución cruzada de slots o proveedores.
 * - Firma contadores locales con `HmacSHA256`.
 */
class FenixKeystoreVault(
    private val masterKeyProvider: MasterKeyProvider = JvmMemoryMasterKeyProvider(),
) {
    private val slotStorage = ConcurrentHashMap<String, EncryptedApiKeySlot>()
    private val secureRandom = SecureRandom()

    /**
     * Almacena y cifra una API Key en el slot indicado `[0..5]` del proveedor.
     */
    fun storeApiKey(
        providerId: String,
        slotIndex: Int,
        plaintextApiKey: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): EncryptedApiKeySlot {
        validateProviderAndSlot(providerId, slotIndex)
        require(plaintextApiKey.isNotBlank()) { "La API Key no puede estar vacía." }

        val secretKey = masterKeyProvider.getOrCreateAes256Key(KEY_ALIAS_BYOK_AES)
        val cipher = Cipher.getInstance(TRANSFORMATION_AES_GCM)
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val aadBytes = buildSlotAad(providerId, slotIndex)
        cipher.updateAAD(aadBytes)

        val ciphertext = cipher.doFinal(plaintextApiKey.trim().toByteArray(StandardCharsets.UTF_8))
        val effectiveIv = cipher.iv ?: iv

        val encryptedSlot = EncryptedApiKeySlot(
            providerId = providerId.trim(),
            slotIndex = slotIndex,
            initializationVectorBase64 = Base64.getEncoder().encodeToString(effectiveIv),
            ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext),
            updatedAtEpochMs = nowEpochMs,
        )
        slotStorage[storageKey(providerId, slotIndex)] = encryptedSlot
        return encryptedSlot
    }

    /**
     * Descifra una API Key verificando autenticidad GCM y vinculación exacta de `AAD`.
     */
    fun decryptApiKey(
        slot: EncryptedApiKeySlot,
        expectedProviderId: String = slot.providerId,
        expectedSlotIndex: Int = slot.slotIndex,
    ): String {
        validateProviderAndSlot(expectedProviderId, expectedSlotIndex)
        val secretKey = masterKeyProvider.getOrCreateAes256Key(KEY_ALIAS_BYOK_AES)
        val iv = Base64.getDecoder().decode(slot.initializationVectorBase64)
        val ciphertext = Base64.getDecoder().decode(slot.ciphertextBase64)

        val cipher = Cipher.getInstance(TRANSFORMATION_AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        cipher.updateAAD(buildSlotAad(expectedProviderId, expectedSlotIndex))

        val plaintextBytes = cipher.doFinal(ciphertext)
        return String(plaintextBytes, StandardCharsets.UTF_8)
    }

    /**
     * Recupera y descifra la clave de un slot registrado, o `null` si el slot está vacío.
     */
    fun getDecryptedApiKey(providerId: String, slotIndex: Int): String? {
        validateProviderAndSlot(providerId, slotIndex)
        val slot = slotStorage[storageKey(providerId, slotIndex)] ?: return null
        return decryptApiKey(slot, providerId, slotIndex)
    }

    /**
     * Lista los slots cifrados activos de un proveedor ordenados por `slotIndex`.
     */
    fun listConfiguredSlots(providerId: String): List<EncryptedApiKeySlot> {
        val cleanProvider = providerId.trim()
        return (0 until MAX_SLOTS_PER_PROVIDER).mapNotNull { idx ->
            slotStorage[storageKey(cleanProvider, idx)]
        }
    }

    fun removeApiKeySlot(providerId: String, slotIndex: Int) {
        validateProviderAndSlot(providerId, slotIndex)
        slotStorage.remove(storageKey(providerId, slotIndex))
    }

    /**
     * Genera una firma `HMAC-SHA256` en Base64 para proteger registros locales de cuota y créditos.
     */
    fun computeHmacSha256Base64(canonicalPayload: String): String {
        val hmacKey = masterKeyProvider.getOrCreateHmacSha256Key(KEY_ALIAS_QUOTA_HMAC)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey)
        val digest = mac.doFinal(canonicalPayload.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }

    /**
     * Verifica en tiempo constante si la firma `HMAC-SHA256` coincide con el payload canónico.
     */
    fun verifyHmacSha256(canonicalPayload: String, expectedSignatureBase64: String): Boolean {
        val computed = computeHmacSha256Base64(canonicalPayload)
        val a = computed.toByteArray(StandardCharsets.UTF_8)
        val b = expectedSignatureBase64.toByteArray(StandardCharsets.UTF_8)
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
        }
        return diff == 0
    }

    private fun validateProviderAndSlot(providerId: String, slotIndex: Int) {
        require(providerId.isNotBlank()) { "El identificador de proveedor no puede estar vacío." }
        require(slotIndex in 0 until MAX_SLOTS_PER_PROVIDER) {
            "El índice de slot debe estar entre 0 y ${MAX_SLOTS_PER_PROVIDER - 1} (recibido: $slotIndex)."
        }
    }

    private fun buildSlotAad(providerId: String, slotIndex: Int): ByteArray =
        "${providerId.trim()}:slot_$slotIndex".toByteArray(StandardCharsets.UTF_8)

    private fun storageKey(providerId: String, slotIndex: Int): String =
        "${providerId.trim()}#$slotIndex"

    companion object {
        const val MAX_SLOTS_PER_PROVIDER: Int = 6
        private const val TRANSFORMATION_AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val KEY_ALIAS_BYOK_AES = "fenix_studio_byok_aes256_master"
        private const val KEY_ALIAS_QUOTA_HMAC = "fenix_studio_quota_hmac_sha256"
    }
}
