/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-14 22:55:00 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/ingestion/ImageAndPdfExtractors.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.ingestion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileOutputStream

/** Imagen suelta (foto de apunte, pizarra): requiere OCR local. */
class ImageDocumentExtractor(
    private val ocrEngine: LocalOcrEngine?,
    private val maxBytes: Int = 16 * 1024 * 1024,
) : AcademicDocumentExtractor {

    override val formats: Set<AcademicDocumentFormat> = setOf(AcademicDocumentFormat.IMAGE)

    override suspend fun extract(source: DocumentSource, maxPages: Int): ExtractedContent {
        if (ocrEngine == null) {
            return ExtractedContent(
                pages = emptyList(),
                totalPages = 1,
                mode = ExtractionMode.OCR_REQUIRED,
                isScannedOnly = true,
                warnings = listOf(
                    "La imagen necesita OCR local y esta instalación no tiene motor OCR activo.",
                ),
            )
        }

        val bytes = source.openStream().use { stream ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8 * 1024)
            var total = 0
            while (total < maxBytes) {
                val read = stream.read(chunk)
                if (read <= 0) break
                buffer.write(chunk, 0, read)
                total += read
            }
            buffer.toByteArray()
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return ExtractedContent(
                pages = emptyList(),
                totalPages = 1,
                mode = ExtractionMode.UNSUPPORTED,
                isScannedOnly = true,
                warnings = listOf("No se pudo decodificar la imagen; puede estar dañada."),
            )

        return try {
            val text = ocrEngine.recognizeText(bitmap)
            ExtractedContent(
                pages = if (text.isBlank()) emptyList() else listOf(RawDocumentPage(1, text)),
                totalPages = 1,
                mode = if (text.isBlank()) ExtractionMode.OCR_REQUIRED else ExtractionMode.NATIVE_TEXT,
                isScannedOnly = true,
                warnings = if (text.isBlank()) {
                    listOf("El OCR no encontró texto legible en la imagen.")
                } else {
                    emptyList()
                },
            )
        } finally {
            bitmap.recycle()
        }
    }
}

/**
 * PDF: primero intenta la capa de texto nativa (PDFBox). Si el documento está escaneado,
 * rasteriza solo las primeras páginas a RGB_565 y las pasa por OCR local.
 *
 * El archivo se copia a un temporal en disco: nunca se carga un PDF completo en RAM.
 */
class PdfDocumentExtractor(
    private val ocrEngine: LocalOcrEngine?,
    private val maxFileBytes: Long = 160L * 1024 * 1024,
) : AcademicDocumentExtractor {

    override val formats: Set<AcademicDocumentFormat> = setOf(AcademicDocumentFormat.PDF)

    override suspend fun extract(source: DocumentSource, maxPages: Int): ExtractedContent {
        val tempFile = copyToTempFile(source, maxFileBytes)
            ?: return ExtractedContent(
                pages = emptyList(),
                totalPages = 0,
                mode = ExtractionMode.UNSUPPORTED,
                warnings = listOf("No se pudo preparar el PDF para lectura (archivo vacío o demasiado grande)."),
            )

        return try {
            val native = extractNativeText(tempFile, maxPages)
            if (native.mode == ExtractionMode.NATIVE_TEXT) {
                native
            } else if (ocrEngine != null) {
                extractWithOcr(tempFile, maxPages, native)
            } else {
                native.copy(
                    warnings = native.warnings + "El PDF no tiene capa de texto y no hay motor OCR activo.",
                )
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun extractNativeText(file: File, maxPages: Int): ExtractedContent {
        return try {
            PDDocument.load(file).use { document ->
                if (document.isEncrypted) {
                    return ExtractedContent(
                        pages = emptyList(),
                        totalPages = 0,
                        mode = ExtractionMode.UNSUPPORTED,
                        warnings = listOf("El PDF está protegido con contraseña."),
                    )
                }

                val totalPages = document.numberOfPages.coerceAtLeast(1)
                val pagesToRead = if (maxPages <= 0) totalPages else minOf(totalPages, maxPages)
                val stripper = PDFTextStripper()
                val pages = (1..pagesToRead).mapNotNull { pageNumber ->
                    runCatching {
                        stripper.startPage = pageNumber
                        stripper.endPage = pageNumber
                        RawDocumentPage(pageNumber, normalize(stripper.getText(document)))
                    }.getOrNull()
                }

                val density = if (pages.isEmpty()) {
                    0.0
                } else {
                    pages.sumOf { it.text.trim().length }.toDouble() / pages.size
                }
                val hasTextLayer = density >= MIN_NATIVE_TEXT_CHARS_PER_PAGE

                ExtractedContent(
                    pages = pages,
                    totalPages = totalPages,
                    mode = if (hasTextLayer) ExtractionMode.NATIVE_TEXT else ExtractionMode.OCR_REQUIRED,
                    isScannedOnly = !hasTextLayer,
                    warnings = if (hasTextLayer) {
                        emptyList()
                    } else {
                        listOf(
                            "El PDF no tiene capa de texto suficiente (${"%.1f".format(density)} chars/pág).",
                        )
                    },
                )
            }
        } catch (error: Exception) {
            ExtractedContent(
                pages = emptyList(),
                totalPages = 0,
                mode = ExtractionMode.UNSUPPORTED,
                warnings = listOf("No se pudo leer el PDF: ${error.message ?: "formato inválido"}."),
            )
        }
    }

    private suspend fun extractWithOcr(
        file: File,
        maxPages: Int,
        nativeAttempt: ExtractedContent,
    ): ExtractedContent {
        val descriptor = runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrNull() ?: return nativeAttempt

        return try {
            PdfRenderer(descriptor).use { renderer ->
                val totalPages = renderer.pageCount.coerceAtLeast(1)
                val pagesToRead = if (maxPages <= 0) totalPages else minOf(totalPages, maxPages)
                val pages = mutableListOf<RawDocumentPage>()
                val warnings = mutableListOf<String>()

                for (pageIndex in 0 until pagesToRead) {
                    renderer.openPage(pageIndex).use { page ->
                        val width = (page.width * 1.5f).toInt().coerceIn(600, 1_240)
                        val height = (page.height * 1.5f).toInt().coerceIn(800, 1_754)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                        try {
                            Canvas(bitmap).drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val text = ocrEngine?.recognizeText(bitmap).orEmpty()
                            if (text.isNotBlank()) {
                                pages += RawDocumentPage(pageIndex + 1, normalize(text))
                            }
                        } catch (error: Exception) {
                            warnings += "La página ${pageIndex + 1} no se pudo reconocer: ${error.message}."
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }

                ExtractedContent(
                    pages = pages,
                    totalPages = totalPages,
                    mode = if (pages.isEmpty()) ExtractionMode.OCR_REQUIRED else ExtractionMode.NATIVE_TEXT,
                    isScannedOnly = true,
                    warnings = warnings + if (pages.isEmpty()) {
                        listOf("El OCR no encontró texto legible en las páginas revisadas.")
                    } else {
                        emptyList()
                    },
                )
            }
        } finally {
            runCatching { descriptor.close() }
        }
    }

    private fun copyToTempFile(source: DocumentSource, maxBytes: Long): File? {
        val tempFile = runCatching { File.createTempFile("fenix_pdf_", ".pdf") }.getOrNull() ?: return null
        return try {
            source.openStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (total < maxBytes) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        total += read
                    }
                    if (total == 0L) {
                        tempFile.delete()
                        return null
                    }
                }
            }
            tempFile
        } catch (error: Exception) {
            tempFile.delete()
            null
        }
    }

    private fun normalize(raw: String): String = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .joinToString("\n") { it.trimEnd() }
        .trim()

    private companion object {
        const val MIN_NATIVE_TEXT_CHARS_PER_PAGE = 45.0
    }
}
