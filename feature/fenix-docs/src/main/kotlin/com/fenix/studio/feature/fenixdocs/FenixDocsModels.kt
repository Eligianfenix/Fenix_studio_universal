/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:04:15 -03:00
 * ARCHIVO: feature/fenix-docs/src/main/kotlin/com/fenix/studio/feature/fenixdocs/FenixDocsModels.kt
 * ================================================================================
 */

package com.fenix.studio.feature.fenixdocs

import androidx.compose.runtime.Immutable
import com.fenix.studio.core.document.openxml.Apa7ParagraphStyle
import com.fenix.studio.core.document.openxml.DocxParagraphNode

/**
 * Catálogo completo de las **20 Herramientas APA 7.ª Edición** en Fenix Docs (`P-09`, `MF-7.1`).
 */
enum class Apa7DocumentTool(
    val toolNumber: Int,
    val displayLabel: String,
) {
    TOOL_01_STUDENT_COVER_PAGE(1, "1. Portada Estudiante APA 7ma"),
    TOOL_02_HEADING_LEVEL_1(2, "2. Encabezado Nivel 1 (Centrado Negrita)"),
    TOOL_03_HEADING_LEVEL_2(3, "3. Encabezado Nivel 2 (Izquierda Negrita)"),
    TOOL_04_HEADING_LEVEL_3(4, "4. Encabezado Nivel 3 (Izquierda Negrita Cursiva)"),
    TOOL_05_HEADING_LEVEL_4(5, "5. Encabezado Nivel 4 (Sangría Negrita.)"),
    TOOL_06_HEADING_LEVEL_5(6, "6. Encabezado Nivel 5 (Sangría Negrita Cursiva.)"),
    TOOL_07_SHORT_QUOTE_UNDER_40W(7, "7. Cita Textual Corta (< 40 palabras)"),
    TOOL_08_BLOCK_QUOTE_OVER_40W(8, "8. Cita en Bloque (> 40 palabras · Sangría 0.5\")"),
    TOOL_09_PARAPHRASED_CITATION(9, "9. Cita Parafraseada con Anclaje Autoral"),
    TOOL_10_HANGING_REFERENCE(10, "10. Referencia con Sangría Francesa (0.5\")"),
    TOOL_11_APA_TABLE(11, "11. Tabla Formato APA 7ma (Sin bordes verticales)"),
    TOOL_12_APA_FIGURE_NOTE(12, "12. Figura con Nota APA"),
    TOOL_13_ABBREVIATIONS_LIST(13, "13. Lista de Abreviaturas y Siglas"),
    TOOL_14_ONTOLOGICAL_GLOSSARY(14, "14. Glosario de Términos Ontológicos"),
    TOOL_15_ACTIVE_RECALL_QUESTION(15, "15. Pregunta de Active Recall Integrada"),
    TOOL_16_COMPARATIVE_MATRIX(16, "16. Matriz de Cuadro Comparativo"),
    TOOL_17_CORRELATIVE_FOOTNOTE(17, "17. Nota al Pie Correlativa"),
    TOOL_18_MARGIN_1_INCH_VALIDATOR(18, "18. Verificador Márgenes 1 Pulgada (1440 twips)"),
    TOOL_19_DOUBLE_SPACING_VALIDATOR(19, "19. Verificador Interlineado Doble (2.0)"),
    TOOL_20_AUTO_BIBLIOGRAPHY_BUILDER(20, "20. Generador Automático de Bibliografía"),
}

/**
 * Estado inmutable de la **Ventana Flotante Arrastrable del Asistente IA (`RN-21`)** en `P-09`.
 * Flota libremente sobre el lienzo sin bloquear la línea de edición activa del usuario.
 */
@Immutable
data class DraggableAiAssistantState(
    val offsetX: Float = 24f,
    val offsetY: Float = 120f,
    val isExpanded: Boolean = false,
    val queryPrompt: String = "",
    val lastAssistantResponse: String? = null,
    val doesNotObstructActiveLine: Boolean = true,
)

/**
 * Estado unificado del Editor Académico **Fenix Docs (`P-09`, `MF-7.1`)**.
 */
@Immutable
data class FenixDocsEditorState(
    val documentTitle: String = "Documento Académico sin Título",
    val paragraphs: List<DocxParagraphNode> = listOf(
        DocxParagraphNode(
            style = Apa7ParagraphStyle.APA_HEADING_1,
            text = "Título Principal del Manuscrito",
            isBold = true,
        ),
    ),
    val currentLazyPageWindowIndex: Int = 0,
    val maxParagraphsPerPageWindow: Int = 25,
    val marginsTwipsAllSides: Int = 1440,
    val lineSpacingTwips: Int = 480,
    val isMarginsCompliant: Boolean = true,
    val isDoubleSpacingCompliant: Boolean = true,
    val isDraftUnsaved: Boolean = false,
    val undoStackSize: Int = 0,
    val redoStackSize: Int = 0,
    val footnoteCounter: Int = 0,
    val assistantState: DraggableAiAssistantState = DraggableAiAssistantState(),
) {
    /**
     * Ventana perezosa (*lazy page window*) de párrafos visibles para evitar sobrecarga de memoria
     * al abrir documentos `.docx` masivos en dispositivos de 4 GB RAM.
     */
    val visibleWindowParagraphs: List<DocxParagraphNode>
        get() {
            if (paragraphs.isEmpty()) return emptyList()
            val start = (currentLazyPageWindowIndex * maxParagraphsPerPageWindow)
                .coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
            val endExclusive = (start + maxParagraphsPerPageWindow).coerceAtMost(paragraphs.size)
            return paragraphs.subList(start, endExclusive)
        }
}
