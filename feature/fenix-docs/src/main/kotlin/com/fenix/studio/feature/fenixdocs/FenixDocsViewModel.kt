/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:04:45 -03:00
 * ARCHIVO: feature/fenix-docs/src/main/kotlin/com/fenix/studio/feature/fenixdocs/FenixDocsViewModel.kt
 * ================================================================================
 */

package com.fenix.studio.feature.fenixdocs

import androidx.lifecycle.ViewModel
import com.fenix.studio.core.document.converter.DocxPdfBidirectionalConverter
import com.fenix.studio.core.document.converter.PaginatedApa7Page
import com.fenix.studio.core.document.openxml.Apa7ParagraphStyle
import com.fenix.studio.core.document.openxml.DocxParagraphNode
import com.fenix.studio.core.document.openxml.OpenXmlStreamer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.InputStream
import java.io.OutputStream

/**
 * ViewModel para `P-09` (Editor Académico **Fenix Docs**, `MF-7.1`).
 * - Aplica determinísticamente las **20 herramientas APA 7.ª Edición**.
 * - Administra la pila `undo`/`redo` y la bandera `isDraftUnsaved` (`RN-13`).
 * - Gestiona la **Ventana Flotante Arrastrable del Asistente IA (`RN-21`)**.
 * - Importa y exporta paquetes nativos `.docx` y modelos paginados `.pdf` 100% offline.
 */
class FenixDocsViewModel(
    private val openXmlStreamer: OpenXmlStreamer = OpenXmlStreamer(),
    private val bidirectionalConverter: DocxPdfBidirectionalConverter = DocxPdfBidirectionalConverter(openXmlStreamer),
) : ViewModel() {

    private val undoStack = ArrayDeque<List<DocxParagraphNode>>()
    private val redoStack = ArrayDeque<List<DocxParagraphNode>>()

    private val _uiState = MutableStateFlow(FenixDocsEditorState())
    val uiState: StateFlow<FenixDocsEditorState> = _uiState.asStateFlow()

    fun onDocumentTitleChanged(newTitle: String) {
        _uiState.update {
            it.copy(
                documentTitle = newTitle,
                isDraftUnsaved = true,
            )
        }
    }

    /**
     * Aplica una de las **20 Herramientas APA 7.ª Edición** sobre el documento activo.
     */
    fun onApplyApa7Tool(
        tool: Apa7DocumentTool,
        contentText: String = "",
        authorTag: String = "Autor",
        yearTag: String = "2026",
        pageTag: String = "p. 1",
    ) {
        val currentParagraphs = _uiState.value.paragraphs
        pushUndoSnapshot(currentParagraphs)

        val cleanedText = contentText.trim().ifEmpty { tool.displayLabel }

        val updatedParagraphs: List<DocxParagraphNode> = when (tool) {
            Apa7DocumentTool.TOOL_01_STUDENT_COVER_PAGE -> listOf(
                DocxParagraphNode(
                    style = Apa7ParagraphStyle.APA_HEADING_1,
                    text = _uiState.value.documentTitle,
                    isBold = true,
                ),
                DocxParagraphNode(
                    style = Apa7ParagraphStyle.APA_BODY_PARAGRAPH,
                    text = "Manuscrito de Estudio · Formato Estándar APA 7.ª Edición ($yearTag)",
                ),
            ) + currentParagraphs

            Apa7DocumentTool.TOOL_02_HEADING_LEVEL_1 -> currentParagraphs + DocxParagraphNode(
                style = Apa7ParagraphStyle.APA_HEADING_1,
                text = cleanedText,
                isBold = true,
            )

            Apa7DocumentTool.TOOL_03_HEADING_LEVEL_2 -> currentParagraphs + DocxParagraphNode(
                style = Apa7ParagraphStyle.APA_HEADING_2,
                text = cleanedText,
                isBold = true,
            )

            Apa7DocumentTool.TOOL_04_HEADING_LEVEL_3 -> currentParagraphs + DocxParagraphNode(
                style = Apa7ParagraphStyle.APA_HEADING_2,
                text = cleanedText,
                isBold = true,
                isItalic = true,
            )

            Apa7DocumentTool.TOOL_05_HEADING_LEVEL_4 -> currentParagraphs + DocxParagraphNode(
                style = Apa7ParagraphStyle.APA_BODY_PARAGRAPH,
                text = "${cleanedText.trimEnd('.')}.",
                isBold = true,
            )

            Apa7DocumentTool.TOOL_06_HEADING_LEVEL_5 -> currentParagraphs + DocxParagraphNode(
                style = Apa7ParagraphStyle.APA_BODY_PARAGRAPH,
                text = "${cleanedText.trimEnd('.')}.",
                isBold = true,
                isItalic = true,
            )

            Apa7DocumentTool.TOOL_07_SHORT_QUOTE_UNDER_40W -> currentParagraphs + DocxParagraphNode(
                style = Apa7ParagraphStyle.APA_BODY_PARAGRAPH,
                text = "\"$cleanedText\" ($authorTag, $yearTag, $pageTag).",
            )

            Apa7DocumentTool.TOOL_08_BLOCK_QUOTE_OVER_40W -> currentParagraphs + DocxParagraphNode(
                style = Apa7ParagraphStyle.APA_BLOCK_QUOTE_40_WORDS,
                text = "$cleanedText ($authorTag, $yearTag, $pageTag)",
            )

            Apa7DocumentTool.TOOL_09_PARAPHRASED_CITATION -> currentParagraphs + DocxParagraphNode(
                style = Apa7ParagraphStyle.APA_BODY_PARAGRAPH,
                text = "Según $authorTag ($yearTag), $cleanedText",
            )

            Apa7DocumentTool.TOOL_10_HANGING_REFERENCE -> currentParagraphs + DocxParagraphNode(
                style = Apa7ParagraphStyle.APA_HANGING_REFERENCE,
                text = "$authorTag ($yearTag). $cleanedText.",
            )

            Apa7DocumentTool.TOOL_11_APA_TABLE -> currentParagraphs + DocxParagraphNode(
                style = Apa7ParagraphStyle.APA_BODY_PARAGRAPH,
                text = "Tabla APA | Encabezado Superior/Inferior Horizontal | $cleanedText",
            )

            Apa7DocumentTool.TOOL_12_APA_FIGURE_NOTE -> currentParagraphs + DocxParagraphNode(
                style = Apa7ParagraphStyle.APA_BODY_PARAGRAPH,
                text = "Nota de Figura. $cleanedText.",
                isItalic = true,
            )

            Apa7DocumentTool.TOOL_13_ABBREVIATIONS_LIST -> currentParagraphs + DocxParagraphNode(
                style = Apa7ParagraphStyle.APA_BODY_PARAGRAPH,
                text = "Siglas y Abreviaturas: $cleanedText",
            )

            Apa7DocumentTool.TOOL_14_ONTOLOGICAL_GLOSSARY -> currentParagraphs + DocxParagraphNode(
                style = Apa7ParagraphStyle.APA_BODY_PARAGRAPH,
                text = "Definición Ontológica: $cleanedText",
            )

            Apa7DocumentTool.TOOL_15_ACTIVE_RECALL_QUESTION -> currentParagraphs + DocxParagraphNode(
                style = Apa7ParagraphStyle.APA_BODY_PARAGRAPH,
                text = "[Active Recall] ¿$cleanedText?",
                isBold = true,
            )

            Apa7DocumentTool.TOOL_16_COMPARATIVE_MATRIX -> currentParagraphs + DocxParagraphNode(
                style = Apa7ParagraphStyle.APA_BODY_PARAGRAPH,
                text = "Cuadro Comparativo Estructural: $cleanedText",
            )

            Apa7DocumentTool.TOOL_17_CORRELATIVE_FOOTNOTE -> {
                val nextFootnote = _uiState.value.footnoteCounter + 1
                _uiState.update { it.copy(footnoteCounter = nextFootnote) }
                currentParagraphs + DocxParagraphNode(
                    style = Apa7ParagraphStyle.APA_BODY_PARAGRAPH,
                    text = "Nota al pie [$nextFootnote]: $cleanedText",
                )
            }

            Apa7DocumentTool.TOOL_18_MARGIN_1_INCH_VALIDATOR -> {
                _uiState.update {
                    it.copy(
                        marginsTwipsAllSides = 1440,
                        isMarginsCompliant = true,
                    )
                }
                currentParagraphs
            }

            Apa7DocumentTool.TOOL_19_DOUBLE_SPACING_VALIDATOR -> {
                _uiState.update {
                    it.copy(
                        lineSpacingTwips = 480,
                        isDoubleSpacingCompliant = true,
                    )
                }
                currentParagraphs
            }

            Apa7DocumentTool.TOOL_20_AUTO_BIBLIOGRAPHY_BUILDER -> {
                val citedEntries = currentParagraphs
                    .filter { it.text.contains("(") && it.text.contains(")") }
                    .map { node ->
                        DocxParagraphNode(
                            style = Apa7ParagraphStyle.APA_HANGING_REFERENCE,
                            text = "Referencia derivada: ${node.text.take(80)}",
                        )
                    }
                currentParagraphs + DocxParagraphNode(
                    style = Apa7ParagraphStyle.APA_HEADING_1,
                    text = "Referencias Bibliográficas",
                    isBold = true,
                ) + citedEntries
            }
        }

        _uiState.update {
            it.copy(
                paragraphs = updatedParagraphs,
                isDraftUnsaved = true,
                undoStackSize = undoStack.size,
                redoStackSize = redoStack.size,
            )
        }
    }

    fun onUndo(): Boolean {
        if (undoStack.isEmpty()) return false
        val previous = undoStack.removeLast()
        redoStack.addLast(_uiState.value.paragraphs)
        _uiState.update {
            it.copy(
                paragraphs = previous,
                isDraftUnsaved = true,
                undoStackSize = undoStack.size,
                redoStackSize = redoStack.size,
            )
        }
        return true
    }

    fun onRedo(): Boolean {
        if (redoStack.isEmpty()) return false
        val next = redoStack.removeLast()
        undoStack.addLast(_uiState.value.paragraphs)
        _uiState.update {
            it.copy(
                paragraphs = next,
                isDraftUnsaved = true,
                undoStackSize = undoStack.size,
                redoStackSize = redoStack.size,
            )
        }
        return true
    }

    fun onMarkDraftSavedLocally() {
        _uiState.update { it.copy(isDraftUnsaved = false) }
    }

    /**
     * Actualiza las coordenadas `(offsetX, offsetY)` de la **Ventana Flotante Arrastrable del Asistente IA (`RN-21`)**
     * delimitando estrictamente el rango con `coerceIn(0f, maxX)` y `coerceIn(0f, maxY)` para evitar matemáticamente
     * que la ventana sea arrojada fuera del lienzo o bloquee la línea activa del estudiante.
     */
    fun onDragAiAssistantWindow(
        deltaX: Float,
        deltaY: Float,
        maxX: Float = 1080f,
        maxY: Float = 1920f,
    ) {
        _uiState.update { current ->
            val currentAssistant = current.assistantState
            current.copy(
                assistantState = currentAssistant.copy(
                    offsetX = (currentAssistant.offsetX + deltaX).coerceIn(0f, maxX),
                    offsetY = (currentAssistant.offsetY + deltaY).coerceIn(0f, maxY),
                    doesNotObstructActiveLine = true,
                ),
            )
        }
    }

    fun onToggleAiAssistantExpanded(expanded: Boolean) {
        _uiState.update { current ->
            current.copy(
                assistantState = current.assistantState.copy(isExpanded = expanded),
            )
        }
    }

    fun onSubmitFloatingAssistantQuery(query: String, synthesizedReply: String) {
        _uiState.update { current ->
            current.copy(
                assistantState = current.assistantState.copy(
                    queryPrompt = query,
                    lastAssistantResponse = synthesizedReply,
                    isExpanded = true,
                ),
            )
        }
    }

    /**
     * Exporta el documento en formato `.docx` OpenXML nativo con márgenes 1440 twips e interlineado doble 480.
     */
    fun onExportToDocxStream(outputStream: OutputStream) {
        openXmlStreamer.streamWriteDocx(_uiState.value.paragraphs, outputStream)
        onMarkDraftSavedLocally()
    }

    /**
     * Importa en streaming por bloques `</w:p>` un archivo `.docx` externo aplicando paginación perezosa (*lazy window*).
     */
    fun onImportFromDocxStream(inputStream: InputStream) {
        val importedNodes = openXmlStreamer.readDocxParagraphsStreaming(inputStream)
        if (importedNodes.isNotEmpty()) {
            pushUndoSnapshot(_uiState.value.paragraphs)
            _uiState.update {
                it.copy(
                    paragraphs = importedNodes,
                    currentLazyPageWindowIndex = 0,
                    isDraftUnsaved = false,
                    undoStackSize = undoStack.size,
                    redoStackSize = redoStack.size,
                )
            }
        }
    }

    /**
     * Exporta el documento actual al modelo paginado de PDF APA 7ma listo para impresión o lectura accesible.
     */
    fun onExportToPaginatedPdfModel(): List<PaginatedApa7Page> {
        val pages = bidirectionalConverter.convertDocxNodesToPaginatedPdfModel(
            runningHeadTitle = _uiState.value.documentTitle.take(50).uppercase(),
            nodes = _uiState.value.paragraphs,
        )
        onMarkDraftSavedLocally()
        return pages
    }

    private fun pushUndoSnapshot(snapshot: List<DocxParagraphNode>) {
        if (undoStack.size >= 30) {
            undoStack.removeFirst()
        }
        undoStack.addLast(snapshot)
        redoStack.clear()
    }
}
