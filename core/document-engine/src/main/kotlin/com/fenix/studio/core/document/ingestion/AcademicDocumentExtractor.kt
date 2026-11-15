/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-14 22:30:00 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/ingestion/AcademicDocumentExtractor.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.ingestion

/** Contenido extraído antes de asociarlo a un hash y a un nombre de archivo. */
data class ExtractedContent(
    val pages: List<RawDocumentPage>,
    val totalPages: Int,
    val mode: ExtractionMode,
    val isScannedOnly: Boolean = false,
    val warnings: List<String> = emptyList(),
)

/**
 * Extractor de un conjunto de formatos. Debe ser determinista y no depender de la red:
 * la pre-ingesta se ejecuta a costo cero, sin llamadas a modelos.
 */
interface AcademicDocumentExtractor {

    val formats: Set<AcademicDocumentFormat>

    /**
     * @param maxPages tope de páginas a leer en esta pasada de triaje. Los extractores
     *   pueden devolver menos; nunca deben cargar el documento completo en RAM.
     */
    suspend fun extract(source: DocumentSource, maxPages: Int): ExtractedContent
}

/** Límites de seguridad para archivos comprimidos (DOCX, ODT, PPTX, EPUB). */
internal object ZipSafetyLimits {
    const val MAX_ENTRY_BYTES: Long = 16L * 1024 * 1024
    const val MAX_TOTAL_BYTES: Long = 48L * 1024 * 1024
    const val MAX_ENTRIES: Int = 512
}

/**
 * Lector acotado de entradas ZIP. Evita zip bombs y mantiene el consumo de RAM bajo:
 * solo se materializan las entradas pedidas y con un tope de bytes.
 */
internal object BoundedZipReader {

    fun readSelectedEntries(
        source: DocumentSource,
        shouldRead: (entryName: String) -> Boolean,
    ): LinkedHashMap<String, String> {
        val result = LinkedHashMap<String, String>()
        var totalBytes = 0L
        var entries = 0

        source.openStream().buffered().use { raw ->
            java.util.zip.ZipInputStream(raw).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    if (entries > ZipSafetyLimits.MAX_ENTRIES) break
                    val name = entry.name
                    if (entry.isDirectory || !shouldRead(name)) {
                        zip.closeEntry()
                        continue
                    }
                    val bytes = readBounded(zip, ZipSafetyLimits.MAX_ENTRY_BYTES)
                    totalBytes += bytes.size
                    if (totalBytes > ZipSafetyLimits.MAX_TOTAL_BYTES) break
                    result[name] = bytes.toString(Charsets.UTF_8)
                    zip.closeEntry()
                }
            }
        }
        return result
    }

    private fun readBounded(input: java.io.InputStream, maxBytes: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 64L * 1024).toInt())
        val buffer = ByteArray(8 * 1024)
        var readTotal = 0L
        while (readTotal < maxBytes) {
            val read = input.read(buffer)
            if (read <= 0) break
            val allowed = minOf(read.toLong(), maxBytes - readTotal).toInt()
            output.write(buffer, 0, allowed)
            readTotal += allowed
        }
        return output.toByteArray()
    }
}
