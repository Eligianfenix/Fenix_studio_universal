/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-14 22:22:00 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/ingestion/MarkupText.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.ingestion

/**
 * Conversión determinista de marcado (OOXML, ODF, XHTML) a texto plano.
 *
 * No usa un parser XML completo a propósito: los documentos académicos llegan con
 * declaraciones raras, namespaces y entidades sueltas, y acá solo interesa el texto de
 * lectura. Un parser estricto fallaría donde el estudiante necesita que igual se lea.
 */
internal object MarkupText {

    private val SCRIPT_OR_STYLE = Regex(
        pattern = """<(script|style)\b[^>]*>.*?</\1>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val XML_DECLARATION = Regex("""<\?.*?\?>""", RegexOption.DOT_MATCHES_ALL)
    private val TAG = Regex("""<(/?)([A-Za-z0-9:_-]+)[^>]*?(/?)>""")
    private val MULTI_BLANK_LINES = Regex("""\n{3,}""")
    private val TRAILING_SPACES = Regex("""[ \t]+\n""")
    private val NUMERIC_ENTITY = Regex("""&#(x?[0-9A-Fa-f]+);""")

    /**
     * @param raw marcado de origen.
     * @param blockTags etiquetas que cortan párrafo (p, h1..h6, li, br, w:p, a:p, text:p...).
     */
    fun toPlainText(raw: String, blockTags: Set<String>): String {
        val withoutScripts = SCRIPT_OR_STYLE.replace(raw, " ")
        val withoutDeclaration = XML_DECLARATION.replace(withoutScripts, "")
        val normalizedBlocks = blockTags.map { it.lowercase() }.toSet()

        val withBreaks = TAG.replace(withoutDeclaration) { match ->
            val closing = match.groupValues[1].isNotEmpty()
            val tagName = match.groupValues[2].lowercase()
            val localName = tagName.substringAfterLast(':')
            val isBlock = tagName in normalizedBlocks || localName in normalizedBlocks
            when {
                !isBlock -> ""
                closing -> "\n"
                else -> "\n"
            }
        }

        val withoutTags = withBreaks
            .replace('\u00A0', ' ')
            .replace('\u000B', '\n')

        return decodeEntities(withoutTags)
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .replace(TRAILING_SPACES, "\n")
            .replace(MULTI_BLANK_LINES, "\n\n")
            .trim()
    }

    fun decodeEntities(raw: String): String {
        var text = raw
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")

        text = NUMERIC_ENTITY.replace(text) { match ->
            val token = match.groupValues[1]
            val codePoint = runCatching {
                if (token.startsWith("x", ignoreCase = true)) {
                    token.drop(1).toInt(16)
                } else {
                    token.toInt()
                }
            }.getOrNull() ?: return@replace match.value
            runCatching { String(Character.toChars(codePoint)) }.getOrDefault(match.value)
        }
        return text
    }

    /** Parte texto largo en bloques de lectura de tamaño acotado (para formatos sin páginas). */
    fun chunkIntoPages(raw: String, pageCharacterLimit: Int = 3_000): List<String> {
        val clean = raw.trim()
        if (clean.isEmpty()) return emptyList()
        if (clean.length <= pageCharacterLimit) return listOf(clean)

        val pages = mutableListOf<String>()
        var cursor = 0
        while (cursor < clean.length) {
            var end = minOf(cursor + pageCharacterLimit, clean.length)
            if (end < clean.length) {
                val boundary = clean.lastIndexOf('\n', end).takeIf { it > cursor + pageCharacterLimit / 2 }
                if (boundary != null) end = boundary
            }
            pages += clean.substring(cursor, end).trim()
            cursor = end
        }
        return pages.filter { it.isNotEmpty() }
    }
}
