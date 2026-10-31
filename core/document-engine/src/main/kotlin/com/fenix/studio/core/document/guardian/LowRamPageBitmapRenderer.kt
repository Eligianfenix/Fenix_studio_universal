/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:04:10 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/guardian/LowRamPageBitmapRenderer.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.guardian

import android.graphics.Bitmap

/**
 * Presupuesto de memoria para renderizado de páginas de PDF en `Bitmap.Config.RGB_565` (`MF-3.1`).
 * Consume exactamente 2 bytes por píxel (50% menos que `ARGB_8888`) para mantener el pico
 * por debajo de 218 MB PSS en dispositivos de 4 GB de RAM (Xiaomi Redmi 14C).
 */
data class Rgb565FrameAllocationPlan(
    val targetWidthPx: Int,
    val targetHeightPx: Int,
    val bytesPerPixel: Int = LowRamPageBitmapRenderer.BYTES_PER_PIXEL_RGB_565,
    val estimatedAllocationBytes: Long = targetWidthPx.toLong() * targetHeightPx.toLong() * bytesPerPixel.toLong(),
)

/**
 * Renderizador determinístico de bajo consumo de RAM (`RGB_565`) con reciclaje garantizado
 * en bloque `finally` (`bitmap.recycle()`).
 */
object LowRamPageBitmapRenderer {

    const val BYTES_PER_PIXEL_RGB_565: Int = 2
    const val MAX_SAFE_DIMENSION_PX: Int = 2_048
    const val MAX_SINGLE_BITMAP_BYTES: Long = 8L * 1_024L * 1_024L

    /**
     * Calcula el plan de asignación en `RGB_565` reescalando proporcionalmente si supera `MAX_SAFE_DIMENSION_PX`.
     */
    fun calculateSafeRgb565Allocation(
        sourceWidthPx: Int,
        sourceHeightPx: Int,
    ): Rgb565FrameAllocationPlan {
        require(sourceWidthPx > 0 && sourceHeightPx > 0) {
            "Las dimensiones de página deben ser positivas."
        }
        val maxSide = maxOf(sourceWidthPx, sourceHeightPx)
        val scale = if (maxSide > MAX_SAFE_DIMENSION_PX) {
            MAX_SAFE_DIMENSION_PX.toDouble() / maxSide.toDouble()
        } else {
            1.0
        }
        val safeWidth = (sourceWidthPx * scale).toInt().coerceAtLeast(1)
        val safeHeight = (sourceHeightPx * scale).toInt().coerceAtLeast(1)
        return Rgb565FrameAllocationPlan(
            targetWidthPx = safeWidth,
            targetHeightPx = safeHeight,
        )
    }

    /**
     * Ejecuta una operación de OCR o vista previa sobre un `Bitmap` codificado en `Bitmap.Config.RGB_565`
     * y garantiza su liberación inmediata (`bitmap.recycle()`) en el bloque `finally`.
     */
    inline fun <T> withScopedRgb565Bitmap(
        widthPx: Int,
        heightPx: Int,
        bitmapFactory: (Int, Int, Bitmap.Config) -> Bitmap = { w, h, cfg ->
            Bitmap.createBitmap(w, h, cfg)
        },
        block: (Bitmap) -> T,
    ): T {
        val plan = calculateSafeRgb565Allocation(widthPx, heightPx)
        val bitmap = bitmapFactory(plan.targetWidthPx, plan.targetHeightPx, Bitmap.Config.RGB_565)
        try {
            return block(bitmap)
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }
}
