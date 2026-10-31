/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: domain/src/main/kotlin/com/fenix/studio/domain/fsrs/FsrsV6Scheduler.kt
 * ================================================================================
 */

package com.fenix.studio.domain.fsrs

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Planificador matemático determinístico FSRS v6 (Free Spaced Repetition Scheduler v6, `MF-6.2`).
 * Opera 100% offline en < 1 ms y a Costo 0 absoluto de llamadas a IA.
 */
object FsrsV6Scheduler {

    private const val DESIRED_RETENTION: Double = 0.90
    private const val DECAY: Double = -0.5
    private const val FACTOR: Double = 19.0 / 81.0
    private const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L

    // Pesos base canónicos FSRS para inicialización de estabilidad por calificación (1..4)
    private val INITIAL_STABILITY_DAYS = doubleArrayOf(0.40, 1.20, 3.10, 8.50)
    private const val INITIAL_DIFFICULTY_BASE: Double = 5.20

    /**
     * Calcula la recuperabilidad $R(t, S) = \left(1 + \text{FACTOR} \cdot \frac{t}{S}\right)^{\text{DECAY}}$.
     */
    fun calculateRetrievability(
        elapsedDays: Double,
        stabilityDays: Double,
    ): Double {
        if (stabilityDays <= 0.0) return 0.0
        val safeElapsed = elapsedDays.coerceAtLeast(0.0)
        return (1.0 + FACTOR * (safeElapsed / stabilityDays)).pow(DECAY).coerceIn(0.0, 1.0)
    }

    /**
     * Actualiza el estado de memoria de una tarjeta tras la respuesta del estudiante (`AGAIN`, `HARD`, `GOOD`, `EASY`).
     */
    fun scheduleNextReview(
        currentCard: FsrsCardMemoryState,
        rating: FsrsRating,
        reviewEpochMs: Long,
    ): FsrsCardMemoryState {
        val isFirstReview = currentCard.repsCount == 0 || currentCard.stabilityDays <= 0.0
        val elapsedDays = if (currentCard.lastReviewEpochMs <= 0L) {
            0.0
        } else {
            ((reviewEpochMs - currentCard.lastReviewEpochMs).coerceAtLeast(0L).toDouble()) /
                MILLIS_PER_DAY.toDouble()
        }

        val nextDifficulty: Double
        val nextStability: Double
        val nextLapses: Int

        if (isFirstReview) {
            nextStability = INITIAL_STABILITY_DAYS[rating.gradeValue - 1]
            nextDifficulty = (INITIAL_DIFFICULTY_BASE - 0.8 * (rating.gradeValue - 3)).coerceIn(1.0, 10.0)
            nextLapses = if (rating == FsrsRating.AGAIN) 1 else 0
        } else {
            val r = calculateRetrievability(elapsedDays, currentCard.stabilityDays)
            val deltaD = -0.65 * (rating.gradeValue - 3).toDouble()
            // Reversión a la media en dificultad (FSRS v6)
            val rawD = currentCard.difficulty + deltaD * ((10.0 - currentCard.difficulty) / 9.0)
            nextDifficulty = (0.10 * INITIAL_DIFFICULTY_BASE + 0.90 * rawD).coerceIn(1.0, 10.0)

            if (rating == FsrsRating.AGAIN) {
                nextLapses = currentCard.lapsesCount + 1
                nextStability = max(
                    0.25,
                    currentCard.stabilityDays * 0.30 * exp(0.15 * (1.0 - r)),
                )
            } else {
                nextLapses = currentCard.lapsesCount
                val hardPenalty = if (rating == FsrsRating.HARD) 0.85 else 1.0
                val easyBonus = if (rating == FsrsRating.EASY) 1.35 else 1.0
                val difficultyFactor = (11.0 - nextDifficulty) / 10.0
                val retentionGain = exp(0.95 * (1.0 - r))
                val growth = 1.0 + (1.45 * difficultyFactor * retentionGain * hardPenalty * easyBonus)
                nextStability = (currentCard.stabilityDays * growth).coerceAtMost(3650.0)
            }
        }

        val intervalDays = calculateNextIntervalDays(nextStability, rating)
        val nextReviewMs = reviewEpochMs + intervalDays * MILLIS_PER_DAY

        return currentCard.copy(
            stabilityDays = nextStability,
            difficulty = nextDifficulty,
            repsCount = currentCard.repsCount + 1,
            lapsesCount = nextLapses,
            lastReviewEpochMs = reviewEpochMs,
            nextReviewEpochMs = nextReviewMs,
        )
    }

    private fun calculateNextIntervalDays(
        stabilityDays: Double,
        rating: FsrsRating,
    ): Long {
        if (rating == FsrsRating.AGAIN) return 1L
        val rawInterval = (stabilityDays / FACTOR) * (DESIRED_RETENTION.pow(1.0 / DECAY) - 1.0)
        return rawInterval.roundToInt().coerceAtLeast(1).toLong()
    }
}
