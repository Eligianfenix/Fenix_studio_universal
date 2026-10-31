/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:04:20 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/syllabus/CurricularSyllabusParser.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.syllabus

/**
 * Punto o subpunto extraído de un Programa Oficial de Cátedra (`MF-3.2`, `RN-03`).
 * Conserva 100% intacto el `officialNumber` original del documento rector.
 */
data class ParsedSyllabusPoint(
    val officialNumber: String,
    val title: String,
    val rawText: String,
)

/**
 * Unidad, Módulo, Eje o Bloque extraído de un Programa Oficial.
 */
data class ParsedSyllabusUnit(
    val unitSequenceNumber: Int,
    val officialHeaderLabel: String,
    val unitTitle: String,
    val points: List<ParsedSyllabusPoint>,
    val citedBibliographyLines: List<String> = emptyList(),
)

/**
 * Analizador morfosintáctico determinista de Programas de Estudio (`CurricularSyllabusParser`, `MF-3.2`).
 * - Independiente de cualquier universidad o disciplina (Regla Inviolable 2 y 3).
 * - Reconoce encabezados en numeración romana (`UNIDAD I`, `MÓDULO IV`) y arábiga (`Unidad 1`, `Bloque 2`, `Eje 3`).
 * - Reconoce subpuntos (`1.1`, `1.2.1`, `a)`, `b)`) preservando el `officialNumber` exacto (`RN-03`).
 */
class CurricularSyllabusParser {

    fun parseSyllabusText(rawSyllabusText: String): List<ParsedSyllabusUnit> {
        if (rawSyllabusText.isBlank()) return emptyList()

        val units = mutableListOf<ParsedSyllabusUnit>()
        var currentUnitSeq = 0
        var currentHeaderLabel = ""
        var currentUnitTitle = ""
        var currentPoints = mutableListOf<ParsedSyllabusPoint>()
        var currentBibliography = mutableListOf<String>()
        var inBibliographySection = false

        fun flushCurrentUnit() {
            if (currentUnitSeq > 0) {
                units += ParsedSyllabusUnit(
                    unitSequenceNumber = currentUnitSeq,
                    officialHeaderLabel = currentHeaderLabel,
                    unitTitle = currentUnitTitle,
                    points = currentPoints.toList(),
                    citedBibliographyLines = currentBibliography.toList(),
                )
            }
        }

        for (rawLine in rawSyllabusText.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val unitMatch = UNIT_HEADER_REGEX.matchEntire(line)
            if (unitMatch != null) {
                flushCurrentUnit()
                inBibliographySection = false
                val prefixType = unitMatch.groupValues[1].trim()
                val rawNumToken = unitMatch.groupValues[2].trim()
                val extractedTitle = unitMatch.groupValues[3].trim().ifEmpty { "$prefixType $rawNumToken" }

                currentUnitSeq = parseArabicOrRoman(rawNumToken) ?: (currentUnitSeq + 1)
                currentHeaderLabel = "$prefixType $rawNumToken"
                currentUnitTitle = extractedTitle
                currentPoints = mutableListOf()
                currentBibliography = mutableListOf()
                continue
            }

            if (BIBLIOGRAPHY_HEADER_REGEX.containsMatchIn(line)) {
                inBibliographySection = true
                continue
            }

            if (currentUnitSeq == 0) {
                continue
            }

            if (inBibliographySection) {
                currentBibliography += line
                continue
            }

            val numericPointMatch = NUMERIC_POINT_REGEX.matchEntire(line)
            if (numericPointMatch != null) {
                val officialCode = numericPointMatch.groupValues[1].trim().trimEnd('.')
                val pointTitle = numericPointMatch.groupValues[2].trim()
                currentPoints += ParsedSyllabusPoint(
                    officialNumber = officialCode,
                    title = pointTitle,
                    rawText = line,
                )
                continue
            }

            val letterPointMatch = LETTER_SUBPOINT_REGEX.matchEntire(line)
            if (letterPointMatch != null) {
                val officialCode = letterPointMatch.groupValues[1].trim()
                val pointTitle = letterPointMatch.groupValues[2].trim()
                currentPoints += ParsedSyllabusPoint(
                    officialNumber = officialCode,
                    title = pointTitle,
                    rawText = line,
                )
            }
        }

        flushCurrentUnit()
        return units
    }

    private fun parseArabicOrRoman(token: String): Int? {
        token.toIntOrNull()?.let { return it }
        val upper = token.uppercase()
        val romanValues = mapOf(
            'I' to 1,
            'V' to 5,
            'X' to 10,
            'L' to 50,
            'C' to 100,
        )
        var total = 0
        var prev = 0
        for (i in upper.length - 1 downTo 0) {
            val current = romanValues[upper[i]] ?: return null
            if (current < prev) {
                total -= current
            } else {
                total += current
                prev = current
            }
        }
        return if (total > 0) total else null
    }

    companion object {
        private val UNIT_HEADER_REGEX = Regex(
            "^(UNIDAD|MÓDULO|MODULO|BLOQUE|EJE(?:\\s+TEMÁTICO|\\s+TEMATICO)?)\\s+([0-9]+|[IVXLCDM]+)\\s*[:.\\-–]?\\s*(.*)$",
            RegexOption.IGNORE_CASE,
        )
        private val BIBLIOGRAPHY_HEADER_REGEX = Regex(
            "^(BIBLIOGRAF[ÍI]A|LECTURAS\\s+OBLIGATORIAS|REFERENCIAS\\s+BIBLIOGR[ÁA]FICAS)\\b",
            RegexOption.IGNORE_CASE,
        )
        private val NUMERIC_POINT_REGEX = Regex(
            "^(\\d+(?:\\.\\d+)+(?:\\.[a-z])?)\\.?\\s*[:\\-–]?\\s+(.+)$",
            RegexOption.IGNORE_CASE,
        )
        private val LETTER_SUBPOINT_REGEX = Regex(
            "^([a-z]\\))\\s+(.+)$",
            RegexOption.IGNORE_CASE,
        )
    }
}
