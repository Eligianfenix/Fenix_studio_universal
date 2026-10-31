/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: domain/src/main/kotlin/com/fenix/studio/domain/fsrs/DeterministicFlashcardExtractor.kt
 * ================================================================================
 */

package com.fenix.studio.domain.fsrs

/**
 * Par de pregunta y respuesta de la Parte D (15 preguntas de Active Recall).
 */
data class ActiveRecallQaPair(
    val questionIndex: Int,
    val pointCode: String,
    val questionText: String,
    val verifiedAnswerText: String,
)

/**
 * Entrada del Glosario Técnico-Académico de la Parte F.
 */
data class GlossaryTermEntry(
    val term: String,
    val pointCode: String,
    val definitionText: String,
)

/**
 * Extractor determinístico de tarjetas de memoria FSRS v6 a Costo 0 (`MF-6.2`).
 * Transforma automáticamente las preguntas de la Parte D y los términos del glosario de la Parte F
 * en tarjetas interactivas `FsrsCardMemoryState` sin realizar ninguna llamada adicional a los LLMs.
 */
object DeterministicFlashcardExtractor {

    fun extractCardsFromUnit(
        catedraId: String,
        unitNumber: Int,
        partDPairs: List<ActiveRecallQaPair>,
        partFGlossary: List<GlossaryTermEntry>,
        createdAtEpochMs: Long,
    ): List<FsrsCardMemoryState> {
        val cards = mutableListOf<FsrsCardMemoryState>()

        partDPairs.forEach { pair ->
            if (pair.questionText.isNotBlank() && pair.verifiedAnswerText.isNotBlank()) {
                cards += FsrsCardMemoryState(
                    cardId = "${catedraId}_u${unitNumber}_partD_q${pair.questionIndex}",
                    catedraId = catedraId,
                    unitNumber = unitNumber,
                    pointCode = pair.pointCode,
                    sourceSection = FlashcardSourceSection.PART_D_ACTIVE_RECALL,
                    frontPrompt = pair.questionText.trim(),
                    backAnswer = pair.verifiedAnswerText.trim(),
                    nextReviewEpochMs = createdAtEpochMs,
                )
            }
        }

        partFGlossary.forEachIndexed { idx, entry ->
            if (entry.term.isNotBlank() && entry.definitionText.isNotBlank()) {
                cards += FsrsCardMemoryState(
                    cardId = "${catedraId}_u${unitNumber}_partF_g${idx + 1}",
                    catedraId = catedraId,
                    unitNumber = unitNumber,
                    pointCode = entry.pointCode,
                    sourceSection = FlashcardSourceSection.PART_F_GLOSSARY,
                    frontPrompt = "Definir concepto técnico: ${entry.term.trim()}",
                    backAnswer = entry.definitionText.trim(),
                    nextReviewEpochMs = createdAtEpochMs,
                )
            }
        }

        return cards
    }
}
