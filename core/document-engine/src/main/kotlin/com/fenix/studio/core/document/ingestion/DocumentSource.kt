/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-14 22:18:00 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/ingestion/DocumentSource.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.ingestion

import java.io.InputStream

/**
 * Archivo de entrada del estudiante.
 *
 * Se entrega un `openStream` en vez de bytes porque el motor lee cada archivo **dos veces**:
 * una pasada para SHA-256 en bloques de 8 KB y otra para extraer el texto. Así no se carga
 * un PDF de 150 MB completo en RAM (MF-1.5, estrategia contra OOM).
 */
class DocumentSource(
    val fileName: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    private val streamProvider: () -> InputStream,
) {
    fun openStream(): InputStream = streamProvider()

    /** Primeros bytes para detectar formato; no consume el stream principal. */
    fun readHeader(maxBytes: Int = 8): ByteArray = runCatching {
        openStream().use { stream ->
            val buffer = ByteArray(maxBytes)
            val read = stream.read(buffer)
            if (read <= 0) ByteArray(0) else buffer.copyOf(read)
        }
    }.getOrDefault(ByteArray(0))
}
