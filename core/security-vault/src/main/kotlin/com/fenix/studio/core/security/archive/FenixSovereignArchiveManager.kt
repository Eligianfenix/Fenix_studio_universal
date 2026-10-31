/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/security-vault/src/main/kotlin/com/fenix/studio/core/security/archive/FenixSovereignArchiveManager.kt
 * ================================================================================
 */

package com.fenix.studio.core.security.archive

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manifiesto soberano de un paquete `.fenix` (soberanía de datos del alumno en P-03 / P-07
 * e intercambio comunitario sanitizado en P-10).
 */
@Serializable
data class FenixPackageManifest(
    val formatVersion: String = "3.0.0",
    val catedraId: String,
    val displayTitle: String,
    val modalityName: String,
    val programaYear: Int? = null,
    val simHash64: Long = 0L,
    val isSanitizedForCommunity: Boolean = false,
    val ownerUserId: String? = null,
    val privateUserNotesJson: String? = null,
    val protocolBlocksJson: String = "[]",
    val exportedAtEpochMs: Long,
) {
    /**
     * Devuelve una copia sanitizada apta para publicación comunitaria (P-10),
     * purgando obligatoriamente `ownerUserId` y `privateUserNotesJson`.
     */
    fun sanitizeForCommunitySharing(): FenixPackageManifest = copy(
        isSanitizedForCommunity = true,
        ownerUserId = null,
        privateUserNotesJson = null,
    )
}

/**
 * Empaquetador y desempaquetador soberano `.fenix` en streaming ZIP con buffer estricto de 8 KB
 * (< 4 MB RAM) y cifrado autenticado `AES-256-GCM` opcional (`MF-1.3`).
 */
class FenixSovereignArchiveManager(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    private val secureRandom = SecureRandom()

    /**
     * Exporta un paquete `.fenix` en streaming ZIP (y opcionalmente cifrado AES-256-GCM).
     * Si `manifest.isSanitizedForCommunity == true`, purga de forma forzosa cualquier nota privada
     * o identificador de usuario antes de serializar.
     */
    fun exportArchiveToStream(
        manifest: FenixPackageManifest,
        outputStream: OutputStream,
        optionalAesKey: SecretKey? = null,
    ) {
        val effectiveManifest = if (manifest.isSanitizedForCommunity) {
            manifest.sanitizeForCommunitySharing()
        } else {
            manifest
        }

        val zipBytes = buildStreamingZipBytes(effectiveManifest)

        if (optionalAesKey == null) {
            outputStream.write(MAGIC_HEADER_PLAIN)
            streamCopy8Kb(ByteArrayInputStream(zipBytes), outputStream)
        } else {
            outputStream.write(MAGIC_HEADER_ENCRYPTED)
            val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
            val cipher = Cipher.getInstance(TRANSFORMATION_AES_GCM)
            cipher.init(Cipher.ENCRYPT_MODE, optionalAesKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.updateAAD(ARCHIVE_AAD)
            val encryptedBytes = cipher.doFinal(zipBytes)
            val effectiveIv = cipher.iv ?: iv
            outputStream.write(effectiveIv)
            streamCopy8Kb(ByteArrayInputStream(encryptedBytes), outputStream)
        }
        outputStream.flush()
    }

    /**
     * Importa y valida un paquete `.fenix` desde un flujo de entrada.
     */
    fun importArchiveFromStream(
        inputStream: InputStream,
        optionalAesKey: SecretKey? = null,
    ): FenixPackageManifest {
        val rawBytes = readAllWith8KbBuffer(inputStream)
        require(rawBytes.size > MAGIC_HEADER_PLAIN.size) {
            "Archivo .fenix inválido o truncado."
        }

        val header = rawBytes.copyOfRange(0, MAGIC_HEADER_PLAIN.size)
        val zipPayload: ByteArray = when {
            header.contentEquals(MAGIC_HEADER_PLAIN) -> {
                rawBytes.copyOfRange(MAGIC_HEADER_PLAIN.size, rawBytes.size)
            }
            header.contentEquals(MAGIC_HEADER_ENCRYPTED) -> {
                requireNotNull(optionalAesKey) {
                    "El paquete .fenix está cifrado y requiere clave AES-256 para su apertura."
                }
                val ivStart = MAGIC_HEADER_ENCRYPTED.size
                val ivEnd = ivStart + GCM_IV_LENGTH_BYTES
                require(rawBytes.size > ivEnd) { "Cabecera IV de paquete .fenix cifrado incompleta." }
                val iv = rawBytes.copyOfRange(ivStart, ivEnd)
                val ciphertext = rawBytes.copyOfRange(ivEnd, rawBytes.size)

                val cipher = Cipher.getInstance(TRANSFORMATION_AES_GCM)
                cipher.init(Cipher.DECRYPT_MODE, optionalAesKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                cipher.updateAAD(ARCHIVE_AAD)
                cipher.doFinal(ciphertext)
            }
            else -> throw IllegalArgumentException("Cabecera mágica .fenix no reconocida.")
        }

        return extractManifestFromZipBytes(zipPayload)
    }

    private fun buildStreamingZipBytes(manifest: FenixPackageManifest): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            val entry = ZipEntry(MANIFEST_ENTRY_NAME)
            zos.putNextEntry(entry)
            val manifestBytes = json.encodeToString(manifest).toByteArray(StandardCharsets.UTF_8)
            streamCopy8Kb(ByteArrayInputStream(manifestBytes), zos)
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun extractManifestFromZipBytes(zipBytes: ByteArray): FenixPackageManifest {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (entry.name == MANIFEST_ENTRY_NAME) {
                    val contentBytes = readAllWith8KbBuffer(zis)
                    val jsonString = String(contentBytes, StandardCharsets.UTF_8)
                    val parsed = json.decodeFromString<FenixPackageManifest>(jsonString)
                    return if (parsed.isSanitizedForCommunity) {
                        parsed.sanitizeForCommunitySharing()
                    } else {
                        parsed
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        throw IllegalStateException("El archivo .fenix no contiene el manifiesto $MANIFEST_ENTRY_NAME.")
    }

    private fun streamCopy8Kb(source: InputStream, sink: OutputStream) {
        val buffer = ByteArray(STREAM_BUFFER_SIZE_BYTES)
        var read: Int
        while (source.read(buffer).also { read = it } != -1) {
            sink.write(buffer, 0, read)
        }
    }

    private fun readAllWith8KbBuffer(source: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        streamCopy8Kb(source, out)
        return out.toByteArray()
    }

    companion object {
        const val STREAM_BUFFER_SIZE_BYTES: Int = 8_192
        private const val MANIFEST_ENTRY_NAME = "fenix_manifest_v3.json"
        private const val TRANSFORMATION_AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private val MAGIC_HEADER_PLAIN = "FNX3PLN\u0000".toByteArray(StandardCharsets.US_ASCII)
        private val MAGIC_HEADER_ENCRYPTED = "FNX3ENC\u0000".toByteArray(StandardCharsets.US_ASCII)
        private val ARCHIVE_AAD = "fenix_sovereign_archive_v3".toByteArray(StandardCharsets.UTF_8)
    }
}
