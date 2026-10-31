/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:05:40 -03:00
 * ARCHIVO: feature/fenix-docs/src/test/kotlin/com/fenix/studio/feature/fenixdocs/FenixDocsViewModelTest.kt
 * ================================================================================
 */

package com.fenix.studio.feature.fenixdocs

import com.fenix.studio.core.document.openxml.Apa7ParagraphStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class FenixDocsViewModelTest {

    @Test
    fun `all 20 APA 7th tools are present and quotes hanging references and undo redo work deterministically`() {
        assertEquals(20, Apa7DocumentTool.entries.size)

        val viewModel = FenixDocsViewModel()
        assertFalse(viewModel.uiState.value.isDraftUnsaved)

        // Aplicamos Cita Corta (< 40 palabras), Cita en Bloque (> 40 palabras) y Referencia con Sangría Francesa
        viewModel.onApplyApa7Tool(
            tool = Apa7DocumentTool.TOOL_07_SHORT_QUOTE_UNDER_40W,
            contentText = "El acoplamiento débil preserva la mantenibilidad",
            authorTag = "AutorSintetico",
            yearTag = "2025",
            pageTag = "p. 42",
        )
        assertTrue(viewModel.uiState.value.isDraftUnsaved)

        viewModel.onApplyApa7Tool(
            tool = Apa7DocumentTool.TOOL_08_BLOCK_QUOTE_OVER_40W,
            contentText = "Bloque extenso de más de cuarenta palabras verificando sangría izquierda de media pulgada sin comillas envolventes",
            authorTag = "AutorSintetico",
            yearTag = "2025",
            pageTag = "pp. 43-44",
        )

        viewModel.onApplyApa7Tool(
            tool = Apa7DocumentTool.TOOL_10_HANGING_REFERENCE,
            contentText = "Arquitectura de Sistemas Modulares",
            authorTag = "AutorSintetico, A.",
            yearTag = "2025",
        )

        val paragraphs = viewModel.uiState.value.paragraphs
        assertEquals(4, paragraphs.size)
        assertTrue(paragraphs[1].text.contains("\"El acoplamiento débil preserva la mantenibilidad\" (AutorSintetico, 2025, p. 42)."))
        assertEquals(Apa7ParagraphStyle.APA_BLOCK_QUOTE_40_WORDS, paragraphs[2].style)
        assertEquals(Apa7ParagraphStyle.APA_HANGING_REFERENCE, paragraphs[3].style)

        // Probamos Deshacer / Rehacer
        assertTrue(viewModel.onUndo())
        assertEquals(3, viewModel.uiState.value.paragraphs.size)
        assertTrue(viewModel.onRedo())
        assertEquals(4, viewModel.uiState.value.paragraphs.size)
    }

    @Test
    fun `draggable AI assistant window RN-21 updates coordinates without obstructing active line and docx round-trip resets isDraftUnsaved`() {
        val viewModel = FenixDocsViewModel()
        viewModel.onDragAiAssistantWindow(deltaX = 85f, deltaY = 140f)

        val assistant = viewModel.uiState.value.assistantState
        assertEquals(109f, assistant.offsetX)
        assertEquals(260f, assistant.offsetY)
        assertTrue(assistant.doesNotObstructActiveLine)

        viewModel.onApplyApa7Tool(
            tool = Apa7DocumentTool.TOOL_02_HEADING_LEVEL_1,
            contentText = "Sección Experimental",
        )
        assertTrue(viewModel.uiState.value.isDraftUnsaved)

        val baos = ByteArrayOutputStream()
        viewModel.onExportToDocxStream(baos)
        assertFalse(viewModel.uiState.value.isDraftUnsaved)

        val pages = viewModel.onExportToPaginatedPdfModel()
        assertTrue(pages.isNotEmpty())
        assertEquals(1440, pages.first().marginTwipsAllSides)

        val freshViewModel = FenixDocsViewModel()
        freshViewModel.onImportFromDocxStream(ByteArrayInputStream(baos.toByteArray()))
        assertEquals(2, freshViewModel.uiState.value.paragraphs.size)
        assertEquals("Sección Experimental", freshViewModel.uiState.value.paragraphs[1].text)
    }
}
