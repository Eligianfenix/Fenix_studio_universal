/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-15 04:05:00 -03:00
 * ARCHIVO: feature/processor/src/test/kotlin/com/fenix/studio/feature/processor/PreIngestionViewModelTest.kt
 * ================================================================================
 */

package com.fenix.studio.feature.processor

import com.fenix.studio.core.document.guardian.DocumentIngestionVerdict
import com.fenix.studio.core.document.ingestion.DocumentExtractionEngine
import com.fenix.studio.core.document.ingestion.DocumentSource
import com.fenix.studio.core.document.ingestion.PreIngestionEngine
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class PreIngestionViewModelTest {

    private fun viewModel(files: Map<String, ByteArray?>): PreIngestionViewModel =
        PreIngestionViewModel(
            engine = PreIngestionEngine(extractionEngine = DocumentExtractionEngine()),
            opener = FakeDocumentOpener(files),
        )

    @Test
    fun `TXT with enough text is ready for ingestion`() = runTest {
        val files = mapOf("file:///catedra/programa.txt" to longText().toByteArray())
        val vm = viewModel(files)

        vm.scan(listOf(PickedDocument(uri = "file:///catedra/programa.txt", fileName = "programa.txt")))

        val state = vm.uiState.value
        assertFalse(state.isScanning)
        assertEquals(1, state.items.size)
        val item = state.items.single()
        assertEquals(DocumentIngestionVerdict.TEXT_LAYER_NATIVE, item.verdict)
        assertTrue(item.isReady)
        assertEquals(1, state.readyCount)
        assertTrue(item.characterDensityPerPage >= 45.0)
        assertEquals(1, vm.readyDocuments().size)
        assertEquals("programa.txt", vm.readyDocuments().single().fileName)
    }

    @Test
    fun `identical files are marked DUPLICATE_SHA256 inside the same batch`() = runTest {
        val bytes = longText().toByteArray()
        val vm = viewModel(
            mapOf(
                "file:///a.txt" to bytes,
                "file:///b.txt" to bytes,
            ),
        )

        vm.scan(
            listOf(
                PickedDocument(uri = "file:///a.txt", fileName = "a.txt"),
                PickedDocument(uri = "file:///b.txt", fileName = "b.txt"),
            ),
        )

        val state = vm.uiState.value
        assertEquals(2, state.items.size)
        assertEquals(1, state.readyCount)
        assertEquals(1, state.duplicateCount)
        assertEquals(DocumentIngestionVerdict.TEXT_LAYER_NATIVE, state.items[0].verdict)
        assertEquals(DocumentIngestionVerdict.DUPLICATE_SHA256, state.items[1].verdict)
        assertFalse(state.items[1].isReady)
        assertEquals(1, vm.readyDocuments().size)
    }

    @Test
    fun `hash already ingested in the classroom is reported as duplicate`() = runTest {
        val bytes = longText().toByteArray()
        val vm = viewModel(mapOf("file:///material.txt" to bytes))
        val sha = DocumentExtractionEngine().computeSha256Hex(
            DocumentSource(fileName = "material.txt") { ByteArrayInputStream(bytes) },
        )
        assertNotNull(sha)

        vm.scan(
            pickedDocuments = listOf(PickedDocument(uri = "file:///material.txt", fileName = "material.txt")),
            alreadyIngestedHashes = setOf(sha!!),
        )

        assertEquals(DocumentIngestionVerdict.DUPLICATE_SHA256, vm.uiState.value.items.single().verdict)
        assertTrue(vm.readyDocuments().isEmpty())
    }

    @Test
    fun `unsupported extension is reported without aborting the batch`() = runTest {
        val vm = viewModel(
            mapOf(
                "file:///raro.xyz" to "contenido cualquiera".toByteArray(),
                "file:///bueno.txt" to longText().toByteArray(),
            ),
        )

        vm.scan(
            listOf(
                PickedDocument(uri = "file:///raro.xyz", fileName = "raro.xyz"),
                PickedDocument(uri = "file:///bueno.txt", fileName = "bueno.txt"),
            ),
        )

        val state = vm.uiState.value
        assertEquals(2, state.items.size)
        assertEquals(DocumentIngestionVerdict.UNSUPPORTED_FORMAT, state.items[0].verdict)
        assertEquals(1, state.failedCount)
        assertEquals(DocumentIngestionVerdict.TEXT_LAYER_NATIVE, state.items[1].verdict)
        assertEquals(1, state.readyCount)
    }

    @Test
    fun `empty file is reported as corrupt`() = runTest {
        val vm = viewModel(mapOf("file:///vacio.txt" to ByteArray(0)))

        vm.scan(listOf(PickedDocument(uri = "file:///vacio.txt", fileName = "vacio.txt")))

        val item = vm.uiState.value.items.single()
        assertEquals(DocumentIngestionVerdict.CORRUPT_OR_EMPTY, item.verdict)
        assertFalse(item.isReady)
        assertNotNull(item.warningMessage)
    }

    @Test
    fun `file that cannot be opened is reported and the batch continues`() = runTest {
        val vm = viewModel(
            mapOf(
                "file:///perdido.txt" to null,
                "file:///ok.txt" to longText().toByteArray(),
            ),
        )

        vm.scan(
            listOf(
                PickedDocument(uri = "file:///perdido.txt", fileName = "perdido.txt"),
                PickedDocument(uri = "file:///ok.txt", fileName = "ok.txt"),
            ),
        )

        val state = vm.uiState.value
        assertEquals(2, state.items.size)
        assertEquals(DocumentIngestionVerdict.CORRUPT_OR_EMPTY, state.items[0].verdict)
        assertTrue(state.items[0].warningMessage?.contains("No se pudo abrir") == true)
        assertEquals(DocumentIngestionVerdict.TEXT_LAYER_NATIVE, state.items[1].verdict)
    }

    @Test
    fun `remove and clear drop items and their prepared documents`() = runTest {
        val vm = viewModel(
            mapOf(
                "file:///uno.txt" to longText().toByteArray(),
                "file:///dos.txt" to (longText() + " segunda parte").toByteArray(),
            ),
        )

        vm.scan(
            listOf(
                PickedDocument(uri = "file:///uno.txt", fileName = "uno.txt"),
                PickedDocument(uri = "file:///dos.txt", fileName = "dos.txt"),
            ),
        )
        assertEquals(2, vm.readyDocuments().size)

        vm.onRemove("file:///uno.txt")
        assertEquals(1, vm.uiState.value.items.size)
        assertEquals(1, vm.readyDocuments().size)
        assertEquals("dos.txt", vm.readyDocuments().single().fileName)

        vm.onClearAll()
        assertTrue(vm.uiState.value.items.isEmpty())
        assertTrue(vm.readyDocuments().isEmpty())
        assertNull(vm.uiState.value.currentFileName)
    }

    private fun longText(): String = buildString {
        repeat(60) { index ->
            append("Unidad ")
            append(index + 1)
            append(": contenido académico de prueba con densidad suficiente para la pre-ingesta.\n")
        }
    }

    private class FakeDocumentOpener(
        private val files: Map<String, ByteArray?>,
    ) : DocumentOpener {

        override fun describe(uri: String): PickedDocument = PickedDocument(
            uri = uri,
            fileName = uri.substringAfterLast('/'),
            sizeBytes = files[uri]?.size?.toLong(),
            mimeType = "text/plain",
        )

        override fun open(picked: PickedDocument): DocumentSource? {
            val bytes = files[picked.uri] ?: return null
            return DocumentSource(
                fileName = picked.fileName,
                mimeType = picked.mimeType,
                sizeBytes = bytes.size.toLong(),
                streamProvider = { ByteArrayInputStream(bytes) },
            )
        }
    }
}
