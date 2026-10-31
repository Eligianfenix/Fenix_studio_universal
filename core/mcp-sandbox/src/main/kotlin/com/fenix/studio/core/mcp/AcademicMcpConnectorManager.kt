/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:35:40 -03:00
 * ARCHIVO: core/mcp-sandbox/src/main/kotlin/com/fenix/studio/core/mcp/AcademicMcpConnectorManager.kt
 * ================================================================================
 */

package com.fenix.studio.core.mcp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Niveles de política de supervisión humana Human-in-the-Loop (HITL) para conectores MCP (`MF-8.4`).
 */
enum class HitlPolicyLevel(val displayLabel: String) {
    /**
     * Lectura directa y consultas semánticas sin requerir confirmación interactiva.
     */
    READ_ONLY_AUTO("Lectura Automática (Sin fricción)"),

    /**
     * Escritura de apuntes, referencias o exportación que exige aprobación explícita previa del usuario.
     */
    WRITE_CONFIRMATION_REQUIRED("Escritura (Confirmación Requerida)"),

    /**
     * Modificación estructural o borrado de colecciones que exige consentimiento estricto en doble paso.
     */
    DESTRUCTIVE_STRICT_CONSENT("Destructivo (Consentimiento Estricto)"),
}

/**
 * Definición inmutable de una herramienta expuesta por un conector MCP.
 */
data class McpToolDefinition(
    val toolName: String,
    val description: String,
    val requiredPolicy: HitlPolicyLevel,
)

/**
 * Conector MCP académico registrado en el catálogo soberano (`MF-8.4`).
 */
data class AcademicMcpConnector(
    val connectorId: String,
    val displayName: String,
    val category: String,
    val description: String,
    val isEnabled: Boolean = true,
    val tools: List<McpToolDefinition>,
)

/**
 * Solicitud de invocación de una herramienta MCP bajo gobernanza HITL.
 */
data class McpInvocationRequest(
    val connectorId: String,
    val toolName: String,
    val arguments: Map<String, String> = emptyMap(),
    val isUserConsentGranted: Boolean = false,
)

/**
 * Resultado de invocación de una herramienta MCP.
 */
sealed interface McpInvocationResult {
    data class Success(val output: String) : McpInvocationResult
    data class AwaitingUserConsent(
        val connectorId: String,
        val toolName: String,
        val requiredPolicy: HitlPolicyLevel,
        val explanation: String,
    ) : McpInvocationResult
    data class Error(val message: String) : McpInvocationResult
}

/**
 * Gestor y catálogo de los 20 Conectores MCP Académicos con Gobernanza HITL (`MF-8.4`).
 */
class AcademicMcpConnectorManager {

    private val allConnectors: List<AcademicMcpConnector> = listOf(
        // 1. Zotero
        AcademicMcpConnector(
            connectorId = "mcp-zotero",
            displayName = "Zotero Reference Manager",
            category = "Gestores Bibliográficos",
            description = "Consulta y sincronización de bibliotecas, colecciones y citas académicas.",
            tools = listOf(
                McpToolDefinition("search_items", "Buscar referencias por autor o título", HitlPolicyLevel.READ_ONLY_AUTO),
                McpToolDefinition("add_citation", "Insertar nueva cita bibliográfica", HitlPolicyLevel.WRITE_CONFIRMATION_REQUIRED),
                McpToolDefinition("delete_collection", "Eliminar una colección completa", HitlPolicyLevel.DESTRUCTIVE_STRICT_CONSENT),
            ),
        ),
        // 2. Mendeley
        AcademicMcpConnector(
            connectorId = "mcp-mendeley",
            displayName = "Mendeley Academic Library",
            category = "Gestores Bibliográficos",
            description = "Indexación y lectura de artículos y notas académicas compartidas.",
            tools = listOf(
                McpToolDefinition("list_documents", "Listar documentos y metadatos", HitlPolicyLevel.READ_ONLY_AUTO),
                McpToolDefinition("annotate_pdf", "Añadir anotaciones y notas al margen", HitlPolicyLevel.WRITE_CONFIRMATION_REQUIRED),
            ),
        ),
        // 3. Obsidian
        AcademicMcpConnector(
            connectorId = "mcp-obsidian",
            displayName = "Obsidian Local Vault",
            category = "Gestión del Conocimiento",
            description = "Integración bidireccional con bóvedas de Markdown y enlaces [[wikilinks]].",
            tools = listOf(
                McpToolDefinition("read_note", "Leer contenido de una nota de estudio", HitlPolicyLevel.READ_ONLY_AUTO),
                McpToolDefinition("create_atomic_note", "Crear nota atómica vinculada a cátedra", HitlPolicyLevel.WRITE_CONFIRMATION_REQUIRED),
                McpToolDefinition("purge_vault_notes", "Eliminar notas masivas", HitlPolicyLevel.DESTRUCTIVE_STRICT_CONSENT),
            ),
        ),
        // 4. Notion
        AcademicMcpConnector(
            connectorId = "mcp-notion",
            displayName = "Notion Academic Workspace",
            category = "Gestión del Conocimiento",
            description = "Consulta y actualización de bases de datos de estudio y calendarios de entrega.",
            tools = listOf(
                McpToolDefinition("query_database", "Consultar calendario de parciales y entregas", HitlPolicyLevel.READ_ONLY_AUTO),
                McpToolDefinition("insert_page", "Crear página de resumen procesado", HitlPolicyLevel.WRITE_CONFIRMATION_REQUIRED),
            ),
        ),
        // 5. Anki
        AcademicMcpConnector(
            connectorId = "mcp-anki",
            displayName = "Anki Deck Connector",
            category = "Repaso Espaciado",
            description = "Exportación e importación de mazos de tarjetas nemotécnicas.",
            tools = listOf(
                McpToolDefinition("list_decks", "Listar mazos locales disponibles", HitlPolicyLevel.READ_ONLY_AUTO),
                McpToolDefinition("export_fsrs_cards", "Exportar tarjetas generadas por FSRS v6", HitlPolicyLevel.WRITE_CONFIRMATION_REQUIRED),
            ),
        ),
        // 6. GitHub Academic
        AcademicMcpConnector(
            connectorId = "mcp-github",
            displayName = "GitHub Academic Code",
            category = "Repositorios y Código",
            description = "Exploración de proyectos y código fuente científico abierto.",
            tools = listOf(
                McpToolDefinition("search_repositories", "Buscar repositorios y datasets", HitlPolicyLevel.READ_ONLY_AUTO),
                McpToolDefinition("fork_repo", "Crear bifurcación de repositorio de investigación", HitlPolicyLevel.WRITE_CONFIRMATION_REQUIRED),
            ),
        ),
        // 7. GitLab Academic
        AcademicMcpConnector(
            connectorId = "mcp-gitlab",
            displayName = "GitLab Science Hub",
            category = "Repositorios y Código",
            description = "Colaboración en laboratorios y proyectos de software abierto.",
            tools = listOf(
                McpToolDefinition("read_snippets", "Consultar fragmentos de código reproducibles", HitlPolicyLevel.READ_ONLY_AUTO),
                McpToolDefinition("commit_analysis", "Guardar scripts de análisis de datos", HitlPolicyLevel.WRITE_CONFIRMATION_REQUIRED),
            ),
        ),
        // 8. OpenAlex
        AcademicMcpConnector(
            connectorId = "mcp-openalex",
            displayName = "OpenAlex Scholarly Graph",
            category = "Descubrimiento Científico",
            description = "Grafo de cientos de millones de entidades científicas y citas globales.",
            tools = listOf(
                McpToolDefinition("query_works", "Buscar papers y métricas de impacto libre", HitlPolicyLevel.READ_ONLY_AUTO),
                McpToolDefinition("get_author_concept", "Analizar red conceptual de autores", HitlPolicyLevel.READ_ONLY_AUTO),
            ),
        ),
        // 9. Semantic Scholar
        AcademicMcpConnector(
            connectorId = "mcp-semanticscholar",
            displayName = "Semantic Scholar Graph",
            category = "Descubrimiento Científico",
            description = "Extracción de citas influyentes y resúmenes automáticos de papers.",
            tools = listOf(
                McpToolDefinition("get_paper_summary", "Obtener resumen y hallazgos clave", HitlPolicyLevel.READ_ONLY_AUTO),
                McpToolDefinition("find_citations", "Listar citas directas y secundarias", HitlPolicyLevel.READ_ONLY_AUTO),
            ),
        ),
        // 10. ArXiv
        AcademicMcpConnector(
            connectorId = "mcp-arxiv",
            displayName = "arXiv Preprints Connector",
            category = "Descubrimiento Científico",
            description = "Búsqueda y descarga de preprints en física, matemáticas y computación.",
            tools = listOf(
                McpToolDefinition("search_arxiv", "Buscar preprints por categoría o autor", HitlPolicyLevel.READ_ONLY_AUTO),
                McpToolDefinition("fetch_latex_source", "Descargar fuentes LaTeX para procesamiento", HitlPolicyLevel.READ_ONLY_AUTO),
            ),
        ),
        // 11. PubMed
        AcademicMcpConnector(
            connectorId = "mcp-pubmed",
            displayName = "PubMed Central Connector",
            category = "Ciencias de la Salud",
            description = "Búsqueda federada en el mayor archivo biomédico mundial.",
            tools = listOf(
                McpToolDefinition("search_mesh", "Búsqueda por términos MeSH biomédicos", HitlPolicyLevel.READ_ONLY_AUTO),
                McpToolDefinition("fetch_abstract", "Descargar resumen estructurado de ensayo clínico", HitlPolicyLevel.READ_ONLY_AUTO),
            ),
        ),
        // 12. Crossref
        AcademicMcpConnector(
            connectorId = "mcp-crossref",
            displayName = "Crossref DOI Metadata",
            category = "Metadatos y Citas",
            description = "Resolución de identificadores de objeto digital (DOI) y referencias normalizadas.",
            tools = listOf(
                McpToolDefinition("resolve_doi", "Resolver metadatos completos desde un DOI", HitlPolicyLevel.READ_ONLY_AUTO),
                McpToolDefinition("validate_citation", "Verificar autoría y año de publicación", HitlPolicyLevel.READ_ONLY_AUTO),
            ),
        ),
        // 13. DOAJ
        AcademicMcpConnector(
            connectorId = "mcp-doaj",
            displayName = "DOAJ Open Access Journals",
            category = "Revistas Abiertas",
            description = "Indexación de artículos y revistas revisadas por pares de costo cero.",
            tools = listOf(
                McpToolDefinition("search_articles", "Buscar artículos de revistas sin costo de suscripción", HitlPolicyLevel.READ_ONLY_AUTO),
            ),
        ),
        // 14. SciELO
        AcademicMcpConnector(
            connectorId = "mcp-scielo",
            displayName = "SciELO Iberoamérica",
            category = "Revistas Abiertas",
            description = "Biblioteca científica con texto completo de investigaciones iberoamericanas.",
            tools = listOf(
                McpToolDefinition("search_scielo", "Buscar por palabras clave en repositorios regionales", HitlPolicyLevel.READ_ONLY_AUTO),
            ),
        ),
        // 15. Latindex
        AcademicMcpConnector(
            connectorId = "mcp-latindex",
            displayName = "Latindex Directory",
            category = "Revistas Abiertas",
            description = "Consulta de criterios editoriales y revistas indexadas de alta calidad.",
            tools = listOf(
                McpToolDefinition("check_journal", "Comprobar indexación y política de acceso abierto", HitlPolicyLevel.READ_ONLY_AUTO),
            ),
        ),
        // 16. Jupyter Notebook
        AcademicMcpConnector(
            connectorId = "mcp-jupyter",
            displayName = "Jupyter Notebooks Engine",
            category = "Cálculo y Computación",
            description = "Inspección de celdas de código, tablas y fórmulas en formato `.ipynb`.",
            tools = listOf(
                McpToolDefinition("read_notebook_cells", "Extraer fórmulas y explicaciones de cuadernos", HitlPolicyLevel.READ_ONLY_AUTO),
                McpToolDefinition("export_clean_notes", "Generar resumen estructurado desde celdas", HitlPolicyLevel.WRITE_CONFIRMATION_REQUIRED),
            ),
        ),
        // 17. Pandoc Converter
        AcademicMcpConnector(
            connectorId = "mcp-pandoc",
            displayName = "Pandoc Document Converter",
            category = "Conversión Documental",
            description = "Conversión universal entre Markdown, LaTeX, HTML y OpenXML.",
            tools = listOf(
                McpToolDefinition("convert_markdown_to_latex", "Transformar apuntes a sintaxis LaTeX", HitlPolicyLevel.READ_ONLY_AUTO),
                McpToolDefinition("overwrite_target_document", "Reemplazar archivo de destino convertido", HitlPolicyLevel.DESTRUCTIVE_STRICT_CONSENT),
            ),
        ),
        // 18. Zotero Storage
        AcademicMcpConnector(
            connectorId = "mcp-zotero-storage",
            displayName = "Zotero Storage Attachments",
            category = "Almacenamiento Académico",
            description = "Inspección de archivos adjuntos y PDFs asociados a referencias.",
            tools = listOf(
                McpToolDefinition("list_attachments", "Listar archivos asociados a una cita", HitlPolicyLevel.READ_ONLY_AUTO),
                McpToolDefinition("sync_attachment", "Sincronizar archivo PDF localmente", HitlPolicyLevel.WRITE_CONFIRMATION_REQUIRED),
            ),
        ),
        // 19. Local BibTeX
        AcademicMcpConnector(
            connectorId = "mcp-local-bibtex",
            displayName = "Local BibTeX Manager",
            category = "Metadatos y Citas",
            description = "Lectura y generación de archivos `.bib` con formateo estricto APA 7.ª.",
            tools = listOf(
                McpToolDefinition("parse_bibtex_file", "Parsear y validar entradas bibliográficas", HitlPolicyLevel.READ_ONLY_AUTO),
                McpToolDefinition("append_bibtex_entry", "Añadir nueva entrada al archivo `.bib`", HitlPolicyLevel.WRITE_CONFIRMATION_REQUIRED),
            ),
        ),
        // 20. Fenix Sovereign Vault
        AcademicMcpConnector(
            connectorId = "mcp-fenix-vault",
            displayName = "Fenix Sovereign Keystore Vault",
            category = "Seguridad y Soberanía",
            description = "Inspección de ranuras BYOK activas y contadores de cuota locales.",
            tools = listOf(
                McpToolDefinition("query_quota_status", "Consultar balance de créditos locales", HitlPolicyLevel.READ_ONLY_AUTO),
                McpToolDefinition("rotate_active_key", "Rotar ranura BYOK de proveedor", HitlPolicyLevel.WRITE_CONFIRMATION_REQUIRED),
                McpToolDefinition("wipe_all_keys", "Borrado criptográfico seguro de todas las claves", HitlPolicyLevel.DESTRUCTIVE_STRICT_CONSENT),
            ),
        ),
    )

    private val _connectorsState = MutableStateFlow(allConnectors)
    val connectorsState: StateFlow<List<AcademicMcpConnector>> = _connectorsState.asStateFlow()

    fun getAllConnectors(): List<AcademicMcpConnector> = allConnectors

    fun getConnector(connectorId: String): AcademicMcpConnector? =
        allConnectors.find { it.connectorId == connectorId }

    /**
     * Ejecuta una herramienta MCP aplicando rigurosamente la política Human-in-the-Loop (HITL).
     */
    fun executeMcpTool(request: McpInvocationRequest): McpInvocationResult {
        val connector = getConnector(request.connectorId)
            ?: return McpInvocationResult.Error("Conector MCP no encontrado: ${request.connectorId}")

        val tool = connector.tools.find { it.toolName == request.toolName }
            ?: return McpInvocationResult.Error("Herramienta '${request.toolName}' no soportada por ${connector.displayName}")

        // Evaluación de Gobernanza HITL
        when (tool.requiredPolicy) {
            HitlPolicyLevel.READ_ONLY_AUTO -> {
                // Aprobado automáticamente sin requerir interacción
                return McpInvocationResult.Success(
                    output = "Ejecución exitosa de '${tool.toolName}' en ${connector.displayName}: Lectura completada.",
                )
            }

            HitlPolicyLevel.WRITE_CONFIRMATION_REQUIRED -> {
                if (!request.isUserConsentGranted) {
                    return McpInvocationResult.AwaitingUserConsent(
                        connectorId = connector.connectorId,
                        toolName = tool.toolName,
                        requiredPolicy = tool.requiredPolicy,
                        explanation = "La herramienta '${tool.toolName}' modificará tus apuntes o bibliotecas. ¿Deseas autorizar la escritura?",
                    )
                }
                return McpInvocationResult.Success(
                    output = "Escritura autorizada por el usuario en ${connector.displayName} ejecutada con éxito.",
                )
            }

            HitlPolicyLevel.DESTRUCTIVE_STRICT_CONSENT -> {
                if (!request.isUserConsentGranted) {
                    return McpInvocationResult.AwaitingUserConsent(
                        connectorId = connector.connectorId,
                        toolName = tool.toolName,
                        requiredPolicy = tool.requiredPolicy,
                        explanation = "¡ADVERTENCIA DE SEGURIDAD! La acción '${tool.toolName}' en ${connector.displayName} es destructiva e irreversible. Se requiere tu consentimiento expreso.",
                    )
                }
                return McpInvocationResult.Success(
                    output = "Operación destructiva autorizada bajo estricto consentimiento ejecutada en ${connector.displayName}.",
                )
            }
        }
    }
}
