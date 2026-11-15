/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-14 22:10:00 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/ingestion/AcademicDocumentFormat.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.ingestion

/**
 * Formatos que el estudiante puede cargar (MF-1.5, matriz de soporte multiformato).
 *
 * La lista no promete lo mismo para todos: cada formato declara qué mecanismo de
 * extracción usa y si puede requerir OCR.
 */
enum class AcademicDocumentFormat(
    val displayLabel: String,
    val extensions: Set<String>,
    val usesOcr: Boolean = false,
) {
    PDF("PDF", setOf("pdf"), usesOcr = true),
    DOCX("Word (DOCX)", setOf("docx")),
    ODT("OpenDocument (ODT)", setOf("odt")),
    PPTX("Presentación (PPTX)", setOf("pptx")),
    EPUB("Libro electrónico (EPUB)", setOf("epub")),
    HTML("HTML", setOf("html", "htm", "xhtml")),
    MARKDOWN("Markdown", setOf("md", "markdown")),
    PLAIN_TEXT(
        "Texto plano",
        setOf("txt", "text", "csv", "tsv", "log", "json", "xml", "yaml", "yml", "rtf"),
    ),
    SOURCE_CODE(
        "Código fuente",
        setOf(
            "kt", "kts", "java", "py", "c", "h", "cpp", "hpp", "cc",
            "js", "ts", "tsx", "jsx", "sql", "sh", "bash", "css", "rb",
            "go", "rs", "php", "swift", "scala", "lua", "r", "m",
        ),
    ),
    IMAGE("Imagen", setOf("jpg", "jpeg", "png", "webp", "bmp", "heic", "heif"), usesOcr = true),
    UNKNOWN("Desconocido", emptySet()),
    ;

    companion object {
        fun fromExtension(fileName: String): AcademicDocumentFormat {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            if (extension.isEmpty()) return UNKNOWN
            return entries.firstOrNull { extension in it.extensions } ?: UNKNOWN
        }

        fun fromMimeType(mimeType: String?): AcademicDocumentFormat? {
            val clean = mimeType?.substringBefore(';')?.trim()?.lowercase() ?: return null
            return when {
                clean == "application/pdf" -> PDF
                clean.contains("wordprocessingml") -> DOCX
                clean.contains("opendocument.text") -> ODT
                clean.contains("presentationml") -> PPTX
                clean.contains("epub") -> EPUB
                clean == "text/html" || clean == "application/xhtml+xml" -> HTML
                clean == "text/markdown" -> MARKDOWN
                clean.startsWith("text/") -> PLAIN_TEXT
                clean.startsWith("image/") -> IMAGE
                else -> null
            }
        }
    }
}

/** Cómo se obtuvo (o no) el texto de un documento. */
enum class ExtractionMode {
    /** Texto nativo extraído sin OCR. */
    NATIVE_TEXT,

    /** El documento parece escaneado o es una imagen: necesita OCR local. */
    OCR_REQUIRED,

    /** Formato no soportado por el motor. */
    UNSUPPORTED,
}

/**
 * Detección de formato por extensión, MIME y —cuando ayuda— bytes de cabecera.
 *
 * La extensión manda porque es lo que el estudiante ve; el MIME y la firma solo corrigen
 * casos evidentes (un `.pdf` que no empieza con `%PDF`, un `.docx` que no es ZIP).
 */
object AcademicDocumentFormatDetector {

    fun detect(
        fileName: String,
        mimeType: String? = null,
        headerBytes: ByteArray? = null,
    ): AcademicDocumentFormat {
        val byExtension = AcademicDocumentFormat.fromExtension(fileName)
        if (byExtension != AcademicDocumentFormat.UNKNOWN) {
            return if (matchesMagic(byExtension, headerBytes)) byExtension else byExtension
        }
        return AcademicDocumentFormat.fromMimeType(mimeType) ?: AcademicDocumentFormat.UNKNOWN
    }

    fun looksLikePdf(headerBytes: ByteArray?): Boolean =
        headerBytes != null && headerBytes.size >= 4 &&
            headerBytes[0] == '%'.code.toByte() &&
            headerBytes[1] == 'P'.code.toByte() &&
            headerBytes[2] == 'D'.code.toByte() &&
            headerBytes[3] == 'F'.code.toByte()

    fun looksLikeZip(headerBytes: ByteArray?): Boolean =
        headerBytes != null && headerBytes.size >= 2 &&
            headerBytes[0] == 'P'.code.toByte() &&
            headerBytes[1] == 'K'.code.toByte()

    private fun matchesMagic(
        format: AcademicDocumentFormat,
        headerBytes: ByteArray?,
    ): Boolean {
        if (headerBytes == null) return true
        return when (format) {
            AcademicDocumentFormat.PDF -> looksLikePdf(headerBytes)
            AcademicDocumentFormat.DOCX,
            AcademicDocumentFormat.ODT,
            AcademicDocumentFormat.PPTX,
            AcademicDocumentFormat.EPUB,
            -> looksLikeZip(headerBytes)
            else -> true
        }
    }
}
