/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:04:30 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/matching/HispanicAuthorLexemeExtractor.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.matching

import java.text.Normalizer

/**
 * Extractor Patronímico y Lexemático Hispano (`HispanicAuthorLexemeExtractor`, `MF-3.2`).
 * Normaliza diacríticos, descompone `snake_case`, `camelCase` y guiones, y filtra stopwords
 * documentales para aislar apellidos de autores y términos clave de archivos de WhatsApp o Drive.
 */
object HispanicAuthorLexemeExtractor {

    private val DOCUMENT_STOPWORDS = setOf(
        "capitulo", "cap", "unidad", "modulo", "bloque", "tomo", "vol", "volumen",
        "edicion", "ed", "pdf", "docx", "doc", "txt", "scan", "escaneado", "ocr",
        "texto", "lectura", "apunte", "resumen", "ficha", "catedra", "programa",
        "parte", "pag", "paginas", "pp", "de", "del", "la", "las", "el", "los",
        "en", "por", "para", "con", "sin", "sobre", "entre", "hacia", "segun",
        "et", "al", "comp", "coord", "dir", "final", "version", "corregido",
    )

    private val CAMEL_CASE_BOUNDARY_REGEX = Regex("([a-z0-9])([A-Z])")
    private val NON_ALPHANUMERIC_SPLIT_REGEX = Regex("[^a-z0-9]+")
    private val EDITION_OR_PAGE_TOKEN_REGEX = Regex("^(?:ed\\d+|vol\\d+|t\\d+|p\\d+|pp\\d+|cap\\d+|u\\d+)$")

    /**
     * Extrae la lista ordenada y sin duplicados de lexemas significativos (apellidos y núcleos temáticos).
     */
    fun extractSignificantLexemes(rawInput: String): List<String> {
        if (rawInput.isBlank()) return emptyList()

        // 1. Quitar extensión final conocida (.pdf, .docx) y separar camelCase (ej. AutorAlfa -> Autor Alfa)
        val withoutExtension = rawInput.replace(Regex("\\.(pdf|docx|doc|txt)$", RegexOption.IGNORE_CASE), " ")
        val decamelized = CAMEL_CASE_BOUNDARY_REGEX.replace(withoutExtension, "$1 $2")

        // 2. Normalizar diacríticos (á -> a, ñ -> n, ü -> u) y pasar a minúsculas
        val normalizedAscii = stripDiacritics(decamelized).lowercase()

        // 3. Tokenizar y filtrar números puros, stopwords y tokens de 1-2 caracteres
        return normalizedAscii
            .split(NON_ALPHANUMERIC_SPLIT_REGEX)
            .map { it.trim() }
            .filter { token ->
                token.length >= 3 &&
                    token.any { ch -> ch in 'a'..'z' } &&
                    !token.all { ch -> ch.isDigit() } &&
                    !DOCUMENT_STOPWORDS.contains(token) &&
                    !EDITION_OR_PAGE_TOKEN_REGEX.matches(token)
            }
            .distinct()
    }

    /**
     * Elimina tildes y diacríticos manteniendo caracteres alfanuméricos base.
     */
    fun stripDiacritics(input: String): String {
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }
}
