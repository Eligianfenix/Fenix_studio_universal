/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-14 20:35:00 -03:00
 * ARCHIVO: core/security-vault/src/main/kotlin/com/fenix/studio/core/security/keystore/EncryptedSlotStore.kt
 * ================================================================================
 */

package com.fenix.studio.core.security.keystore

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistencia de las ranuras **ya cifradas** de la bóveda BYOK.
 *
 * La bóveda solo es útil si las claves sobreviven al cierre de la app: el texto plano nunca
 * se guarda, se guarda el sobre AES-256-GCM cuya llave vive en el `AndroidKeyStore`.
 */
interface EncryptedSlotStore {

    fun loadAll(): List<EncryptedApiKeySlot>

    fun upsert(slot: EncryptedApiKeySlot)

    fun delete(providerId: String, slotIndex: Int)
}

/** Implementación volátil para tests JVM y para ejecuciones sin contexto Android. */
class InMemoryEncryptedSlotStore : EncryptedSlotStore {

    private val slots = ConcurrentHashMap<String, EncryptedApiKeySlot>()

    override fun loadAll(): List<EncryptedApiKeySlot> = slots.values.toList()

    override fun upsert(slot: EncryptedApiKeySlot) {
        slots[storageKey(slot.providerId, slot.slotIndex)] = slot
    }

    override fun delete(providerId: String, slotIndex: Int) {
        slots.remove(storageKey(providerId, slotIndex))
    }

    private fun storageKey(providerId: String, slotIndex: Int): String =
        "${providerId.trim()}#$slotIndex"
}

/**
 * Persistencia real en `SharedPreferences`.
 *
 * Solo se serializan `providerId`, `slotIndex`, IV y ciphertext: sin la llave del
 * `AndroidKeyStore` esos datos no se pueden leer. La bóveda nunca guarda texto plano.
 */
class SharedPreferencesEncryptedSlotStore(
    context: Context,
    preferencesName: String = PREFERENCES_NAME,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : EncryptedSlotStore {

    private val preferences = context.applicationContext
        .getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    override fun loadAll(): List<EncryptedApiKeySlot> {
        val raw = preferences.getString(KEY_SLOTS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(EncryptedApiKeySlot.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    override fun upsert(slot: EncryptedApiKeySlot) {
        val all = loadAll().associateBy { storageKey(it.providerId, it.slotIndex) }.toMutableMap()
        all[storageKey(slot.providerId, slot.slotIndex)] = slot
        write(all.values.toList())
    }

    override fun delete(providerId: String, slotIndex: Int) {
        val all = loadAll().associateBy { storageKey(it.providerId, it.slotIndex) }.toMutableMap()
        all.remove(storageKey(providerId, slotIndex))
        write(all.values.toList())
    }

    private fun write(slots: List<EncryptedApiKeySlot>) {
        preferences.edit()
            .putString(KEY_SLOTS, json.encodeToString(ListSerializer(EncryptedApiKeySlot.serializer()), slots))
            .apply()
    }

    private fun storageKey(providerId: String, slotIndex: Int): String =
        "${providerId.trim()}#$slotIndex"

    private companion object {
        const val PREFERENCES_NAME = "fenix_byok_vault"
        const val KEY_SLOTS = "encrypted_slots_v1"
    }
}
