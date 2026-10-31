/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: domain/src/test/kotlin/com/fenix/studio/domain/FsrsV6SchedulerTest.kt
 * ================================================================================
 */

package com.fenix.studio.domain

import com.fenix.studio.domain.fsrs.ActiveRecallQaPair
import com.fenix.studio.domain.fsrs.DeterministicFlashcardExtractor
import com.fenix.studio.domain.fsrs.FsrsRating
import com.fenix.studio.domain.fsrs.FsrsV6Scheduler
import com.fenix.studio.domain.fsrs.GlossaryTermEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FsrsV6SchedulerTest {

    @Test
    fun `DeterministicFlashcardExtractor transforms Part D and Part F into FSRS cards at zero AI cost`() {
        val partD = (1..15).map { idx ->
            ActiveRecallQaPair(
                questionIndex = idx,
                pointCode = "1.1",
                questionText = "¿Cuál es el invariante principal del punto $idx?",
                verifiedAnswerText = "Respuesta verificada del punto $idx.",
            )
        }
        val partF = listOf(
            GlossaryTermEntry(
                term = "Homeostasis",
                pointCode = "1.1",
                definitionText = "Equilibrio dinámico autorregulado del sistema.",
            ),
            GlossaryTermEntry(
                term = "Entropía",
                pointCode = "1.2",
                definitionText = "Magnitud termodinámica que mide la dispersión energética.",
            ),
        )

        val cards = DeterministicFlashcardExtractor.extractCardsFromUnit(
            catedraId = "cat-01",
            unitNumber = 1,
            partDPairs = partD,
            partFGlossary = partF,
            createdAtEpochMs = 1_700_000_000_000L,
        )

        assertEquals(17, cards.size)
    }

    @Test
    fun `FsrsV6Scheduler increases stability monotonically across GOOD and EASY reviews and resets interval on AGAIN`() {
        val baseEpochMs = 1_700_000_000_000L
        val initialCard = DeterministicFlashcardExtractor.extractCardsFromUnit(
            catedraId = "cat-01",
            unitNumber = 1,
            partDPairs = listOf(
                ActiveRecallQaPair(1, "1.1", "Pregunta 1", "Respuesta 1"),
            ),
            partFGlossary = emptyList(),
            createdAtEpochMs = baseEpochMs,
        ).single()

        // 1ra revisión: GOOD
        val afterFirstGood = FsrsV6Scheduler.scheduleNextReview(
            currentCard = initialCard,
            rating = FsrsRating.GOOD,
            reviewEpochMs = baseEpochMs,
        )
        assertTrue(afterFirstGood.stabilityDays > 0.0)
        assertEquals(1, afterFirstGood.repsCount)

        // 2da revisión 3 días después: EASY -> estabilidad debe crecer significativamente
        val threeDaysLater = afterFirstGood.nextReviewEpochMs
        val afterSecondEasy = FsrsV6Scheduler.scheduleNextReview(
            currentCard = afterFirstGood,
            rating = FsrsRating.EASY,
            reviewEpochMs = threeDaysLater,
        )
        assertTrue(afterSecondEasy.stabilityDays > afterFirstGood.stabilityDays)
        assertEquals(2, afterSecondEasy.repsCount)

        // 3ra revisión: olvido (AGAIN) -> incrementa lapsesCount y reduce estabilidad
        val afterLapse = FsrsV6Scheduler.scheduleNextReview(
            currentCard = afterSecondEasy,
            rating = FsrsRating.AGAIN,
            reviewEpochMs = afterSecondEasy.nextReviewEpochMs,
        )
        assertEquals(1, afterLapse.lapsesCount)
        assertTrue(afterLapse.stabilityDays < afterSecondEasy.stabilityDays)
    }
}
