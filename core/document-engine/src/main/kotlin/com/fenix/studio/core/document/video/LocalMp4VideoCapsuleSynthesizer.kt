/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:06:10 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/video/LocalMp4VideoCapsuleSynthesizer.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.video

import kotlin.math.ceil

/**
 * Fotograma clave (keyframe) de una Video-Cápsula Educativa de repaso rápido (`MF-8.3`).
 */
data class VideoCapsuleSceneFrame(
    val sceneIndex: Int,
    val headlineText: String,
    val narrationScriptText: String,
    val epistemicBadge: String,
    val startTimeMs: Long,
    val durationMs: Long,
    val startFrameIndex: Int,
    val endFrameIndex: Int,
)

/**
 * Plan de síntesis local de Video-Cápsula `.mp4` (720p a 15 fps, < 218 MB PSS RAM).
 */
data class VideoCapsuleSynthesisPlan(
    val capsuleTitle: String,
    val widthPx: Int = LocalMp4VideoCapsuleSynthesizer.TARGET_WIDTH_720P,
    val heightPx: Int = LocalMp4VideoCapsuleSynthesizer.TARGET_HEIGHT_720P,
    val framesPerSecond: Int = LocalMp4VideoCapsuleSynthesizer.SAFE_FPS_LOW_RAM,
    val totalDurationMs: Long,
    val totalFrames: Int,
    val scenes: List<VideoCapsuleSceneFrame>,
)

/**
 * Sintetizador Local de Video-Cápsulas Educativas `.mp4` (`LocalMp4VideoCapsuleSynthesizer`, `MF-8.3`).
 * - Opera a `1280x720` (720p) y `15 fps` para proteger la temperatura térmica y el techo de RAM
 *   del Xiaomi Redmi 14C.
 * - Calcula tiempos exactos de locución TTS local y cuadros por escena a partir de proposiciones auditadas.
 */
class LocalMp4VideoCapsuleSynthesizer {

    fun buildSynthesisPlan(
        capsuleTitle: String,
        auditedPropositions: List<Pair<String, String>>,
        wordsPerMinuteTtsRate: Int = DEFAULT_TTS_WORDS_PER_MINUTE,
    ): VideoCapsuleSynthesisPlan {
        require(auditedPropositions.isNotEmpty()) {
            "Se requiere al menos una proposición auditada para sintetizar la video-cápsula."
        }
        val safeWpm = wordsPerMinuteTtsRate.coerceIn(90, 260)
        val scenes = mutableListOf<VideoCapsuleSceneFrame>()
        var currentStartMs = 0L
        var currentFrameCursor = 0

        auditedPropositions.forEachIndexed { idx, (headline, narration) ->
            val wordCount = narration.trim().split(Regex("\\s+")).count { it.isNotEmpty() }.coerceAtLeast(1)
            val estimatedSec = ceil((wordCount.toDouble() / safeWpm.toDouble()) * 60.0)
                .toLong()
                .coerceAtLeast(MIN_SCENE_DURATION_SEC)
            val durationMs = estimatedSec * 1_000L
            val sceneFrames = (estimatedSec * SAFE_FPS_LOW_RAM).toInt()

            scenes += VideoCapsuleSceneFrame(
                sceneIndex = idx + 1,
                headlineText = headline.trim(),
                narrationScriptText = narration.trim(),
                epistemicBadge = "[FUENTE]",
                startTimeMs = currentStartMs,
                durationMs = durationMs,
                startFrameIndex = currentFrameCursor,
                endFrameIndex = currentFrameCursor + sceneFrames - 1,
            )

            currentStartMs += durationMs
            currentFrameCursor += sceneFrames
        }

        return VideoCapsuleSynthesisPlan(
            capsuleTitle = capsuleTitle,
            totalDurationMs = currentStartMs,
            totalFrames = currentFrameCursor,
            scenes = scenes,
        )
    }

    companion object {
        const val TARGET_WIDTH_720P: Int = 1_280
        const val TARGET_HEIGHT_720P: Int = 720
        const val SAFE_FPS_LOW_RAM: Int = 15
        const val DEFAULT_TTS_WORDS_PER_MINUTE: Int = 150
        private const val MIN_SCENE_DURATION_SEC: Long = 3L
    }
}
