/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-14 22:50:00 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/ingestion/LocalOcrEngine.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.ingestion

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Motor OCR local desacoplado. La pre-ingesta no lo exige: si no hay motor disponible,
 * el documento queda marcado `OCR_REQUIRED` en vez de fallar en silencio.
 */
fun interface LocalOcrEngine {
    suspend fun recognizeText(bitmap: Bitmap): String
}

/**
 * Implementación con ML Kit en modo **empaquetado**: reconoce texto sin conexión y sin
 * enviar imágenes a ningún servicio.
 */
class MlKitLocalOcrEngine(
    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
) : LocalOcrEngine {

    override suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text -> continuation.resume(text.text) }
            .addOnFailureListener { error -> continuation.resume("") }
    }

    fun close() {
        recognizer.close()
    }
}
