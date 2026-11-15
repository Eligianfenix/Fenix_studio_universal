/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-15 01:10:00 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/matching/AcademicTextNormalizer.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.matching

/**
 * Normalización y huellas de texto académico (`MF-3.2`, Resolución Forense Q10).
 *
 * Es la base compartida del conciliador difuso: si dos textos se escriben distinto
 * ("Aquín, Nora" / "AQUIN NORA") deben caer en la misma forma antes de compararlos.
 *
 * El parser curricular **no** se toca: estas funciones viven acá y el parser podrá
 * adoptarlas cuando se recalibre con cátedras reales.
 */
object AcademicTextNormalizer {

    private val NON_ALPHANUMERIC = Regex("""[^a-z0-9ñ ]+""")
    private val MULTI_SPACE = Regex("""\s+""")

    /** Minúsculas, sin acentos, sin puntuación y con espacios simples. */
    fun normalize(raw: String): String {
        val stripped = buildString(raw.length) {
            for (character in raw.lowercase()) {
                append(
                    when (character) {
                        'á', 'à', 'ä', 'â' -> 'a'
                        'é', 'è', 'ë', 'ê' -> 'e'
                        'í', 'ì', 'ï', 'î' -> 'i'
                        'ó', 'ò', 'ö', 'ô' -> 'o'
                        'ú', 'ù', 'ü', 'û' -> 'u'
                        'ñ' -> 'n'
                        else -> character
                    },
                )
            }
        }
        return stripped
            .replace(NON_ALPHANUMERIC, " ")
            .replace(MULTI_SPACE, " ")
            .trim()
    }

    /**
     * Palabras con peso semántico: 4 caracteres o más, sin stopwords académicas ni
     * editoriales. Se deduplican porque el mapeo compara conjuntos, no repeticiones.
     */
    fun tokenizeSignificant(normalizedText: String): Set<String> =
        normalizedText
            .split(' ')
            .asSequence()
            .map { it.trim() }
            .filter { it.length >= 4 && it !in SPANISH_STOPWORDS }
            .toSet()

    fun tokenizeSignificantFromRaw(raw: String): Set<String> = tokenizeSignificant(normalize(raw))

    /**
     * SimHash de 64 bits sobre 3-gramas y 4-gramas de caracteres con FNV-1a
     * (Resolución Forense Q10). O(N) y sin matrices: apto para 4 GB de RAM.
     */
    fun simHash64(normalizedText: String): Long {
        val clean = normalizedText.replace(" ", "")
        if (clean.isEmpty()) return 0L
        if (clean.length < 3) return fnv1a64(clean, 0, clean.length)

        val bitWeights = IntArray(64)
        for (gramSize in 3..4) {
            if (clean.length < gramSize) continue
            for (start in 0..(clean.length - gramSize)) {
                val gramHash = fnv1a64(clean, start, start + gramSize)
                for (bit in 0 until 64) {
                    if (((gramHash ushr bit) and 1L) == 1L) {
                        bitWeights[bit]++
                    } else {
                        bitWeights[bit]--
                    }
                }
            }
        }

        var fingerprint = 0L
        for (bit in 0 until 64) {
            if (bitWeights[bit] >= 0) {
                fingerprint = fingerprint or (1L shl bit)
            }
        }
        return fingerprint
    }

    fun hammingDistance(a: Long, b: Long): Int = (a xor b).countOneBits()

    private fun fnv1a64(text: CharSequence, startIndex: Int, endIndex: Int): Long {
        var hash = FNV_OFFSET_BASIS
        for (index in startIndex until endIndex) {
            hash = hash xor text[index].code.toLong()
            hash *= FNV_PRIME
        }
        return hash
    }

    private const val FNV_OFFSET_BASIS = -3750763034362895579L // 0xCBF29CE484222325
    private const val FNV_PRIME = 1099511628211L

    /** Stopwords académicas, editoriales y de ciudades (normalizadas, sin acentos). */
    val SPANISH_STOPWORDS: Set<String> = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "al", "a", "en",
        "con", "por", "para", "sin", "sobre", "entre", "hacia", "hasta", "desde", "y", "e",
        "o", "u", "ni", "que", "como", "cuando", "donde", "cual", "quien", "cuyo", "su", "sus",
        "este", "esta", "estos", "estas", "ese", "esa", "esos", "esas", "aquel", "aquella",
        "introduccion", "concepto", "conceptos", "nocion", "nociones", "teoria", "analisis",
        "estudio", "general", "generales", "parte", "unidad", "bolilla", "modulo", "tema",
        "punto", "capitulo", "edicion", "editorial", "buenos", "aires", "madrid", "mexico",
        "barcelona", "siglo", "xxi", "paidos", "amorrortu", "gedisa", "alianza", "fondo", "cultura",
    )
}
