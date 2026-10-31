/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:29:45 -03:00
 * ARCHIVO: feature/community/src/main/kotlin/com/fenix/studio/feature/community/CommunityModels.kt
 * ================================================================================
 */

package com.fenix.studio.feature.community

import androidx.compose.runtime.Immutable

/**
 * Áreas de conocimiento académico normalizadas según clasificación Biglan-Becher universal.
 * Cero hardcodeo de cátedras o instituciones (Regla Inviolable 2).
 */
enum class AcademicKnowledgeArea(val displayLabel: String) {
    SOCIAL_SCIENCES("Ciencias Sociales"),
    LEGAL_JURIDICAL("Ciencias Jurídicas"),
    MEDICAL_HEALTH("Ciencias Médicas y de la Salud"),
    EXACT_NATURAL("Ciencias Exactas y Naturales"),
    ENGINEERING_TECH("Ingeniería y Tecnología"),
    HUMANITIES_ARTS("Humanidades y Artes"),
}

/**
 * Representa una herramienta o repositorio académico de acceso abierto y costo cero.
 */
@Immutable
data class AcademicUsefulSite(
    val id: String,
    val title: String,
    val url: String,
    val area: AcademicKnowledgeArea,
    val description: String,
    val isFree: Boolean = true,
    val tags: List<String> = emptyList(),
)

/**
 * Paquete de cátedra compartida sanitizado en formato soberano `.fenix`.
 * No contiene jamás datos personales (PII) de estudiantes o docentes.
 */
@Immutable
data class CommunitySharedPackage(
    val packageId: String,
    val title: String,
    val knowledgeArea: AcademicKnowledgeArea,
    val createdAtEpochMs: Long,
    val sizeBytes: Long,
    val checksumSha256: String,
    val isSanitized: Boolean = true,
    val downloadCount: Int = 0,
    val authorAlias: String = "Comunidad Fénix",
)

/**
 * Petición solidaria de ayuda o búsqueda de apuntes colaborativos sin fines de lucro.
 */
@Immutable
data class SolidaryAcademicRequest(
    val requestId: String,
    val topicSummary: String,
    val knowledgeArea: AcademicKnowledgeArea,
    val timestampEpochMs: Long,
    val urgencyLevel: String = "Media",
    val isResolved: Boolean = false,
)

/**
 * Pestañas canónicas del Hub Comunitario `P-10`.
 */
enum class CommunityHubTab(val displayTitle: String) {
    USEFUL_SITES("70 Sitios Útiles"),
    SOLIDARY_PACKAGES("Repositorio Soberano (.fenix)"),
    SOLIDARY_REQUESTS("Peticiones Solidarias"),
}

/**
 * Estado inmutable de la pantalla `P-10` (`MF-8.1`).
 */
@Immutable
data class CommunityUiState(
    val activeTab: CommunityHubTab = CommunityHubTab.USEFUL_SITES,
    val selectedAreaFilter: AcademicKnowledgeArea? = null,
    val searchQuery: String = "",
    val usefulSites: List<AcademicUsefulSite> = AcademicUsefulSitesCatalog.ALL_70_SITES,
    val sharedPackages: List<CommunitySharedPackage> = emptyList(),
    val solidaryRequests: List<SolidaryAcademicRequest> = emptyList(),
    val isLoading: Boolean = false,
    val toastFeedbackMessage: String? = null,
)

/**
 * Catálogo canónico de los 70 Sitios Académicos Gratuitos verificados a Costo 0.
 */
object AcademicUsefulSitesCatalog {
    val ALL_70_SITES: List<AcademicUsefulSite> = listOf(
        // --- 1. CIENCIAS SOCIALES (12) ---
        AcademicUsefulSite(
            id = "soc-01",
            title = "SciELO Ciencias Sociales",
            url = "https://scielo.org",
            area = AcademicKnowledgeArea.SOCIAL_SCIENCES,
            description = "Biblioteca científica electrónica en línea con acceso abierto a revistas de ciencias sociales.",
            tags = listOf("revistas", "iberoamerica", "open-access"),
        ),
        AcademicUsefulSite(
            id = "soc-02",
            title = "Redalyc Red de Revistas Sociales",
            url = "https://www.redalyc.org",
            area = AcademicKnowledgeArea.SOCIAL_SCIENCES,
            description = "Ecosistema de comunicación científica sin fines de lucro para ciencias sociales.",
            tags = listOf("america-latina", "peer-reviewed", "articulos"),
        ),
        AcademicUsefulSite(
            id = "soc-03",
            title = "CLACSO Repositorio Digital Abierto",
            url = "https://biblioteca-repositorio.clacso.edu.ar",
            area = AcademicKnowledgeArea.SOCIAL_SCIENCES,
            description = "Consejo Latinoamericano de Ciencias Sociales con miles de libros y textos de acceso abierto.",
            tags = listOf("sociologia", "politica", "libros"),
        ),
        AcademicUsefulSite(
            id = "soc-04",
            title = "SSRN Social Science Research Network",
            url = "https://www.ssrn.com",
            area = AcademicKnowledgeArea.SOCIAL_SCIENCES,
            description = "Repositorio mundial líder de preprints e investigaciones en ciencias sociales y economía.",
            tags = listOf("preprints", "economia", "ciencias-sociales"),
        ),
        AcademicUsefulSite(
            id = "soc-05",
            title = "DOAJ Directorio de Revistas Abiertas",
            url = "https://doaj.org",
            area = AcademicKnowledgeArea.SOCIAL_SCIENCES,
            description = "Directorio global curado por pares de publicaciones académicas de acceso abierto gratuito.",
            tags = listOf("directorio", "acceso-abierto", "internacional"),
        ),
        AcademicUsefulSite(
            id = "soc-06",
            title = "Dialnet Difusión Científica",
            url = "https://dialnet.unirioja.es",
            area = AcademicKnowledgeArea.SOCIAL_SCIENCES,
            description = "Mayor portal de difusión de la producción científica hispana en ciencias humanas y sociales.",
            tags = listOf("tesis", "articulos", "hispano"),
        ),
        AcademicUsefulSite(
            id = "soc-07",
            title = "CORE Agregador Mundial de Papers",
            url = "https://core.ac.uk",
            area = AcademicKnowledgeArea.SOCIAL_SCIENCES,
            description = "El mayor agregador de artículos de investigación de acceso abierto del mundo.",
            tags = listOf("agregador", "global", "buscador"),
        ),
        AcademicUsefulSite(
            id = "soc-08",
            title = "BASE Motor Académico Multidisciplinar",
            url = "https://www.base-search.net",
            area = AcademicKnowledgeArea.SOCIAL_SCIENCES,
            description = "Bielefeld Academic Search Engine con más de 300 millones de documentos académicos indexados.",
            tags = listOf("buscador", "multidisciplinar", "academico"),
        ),
        AcademicUsefulSite(
            id = "soc-09",
            title = "OpenAIRE Explorador Científico",
            url = "https://explore.openaire.eu",
            area = AcademicKnowledgeArea.SOCIAL_SCIENCES,
            description = "Infraestructura paneuropea para ciencia abierta con millones de publicaciones y datasets libres.",
            tags = listOf("ciencia-abierta", "datos", "europa"),
        ),
        AcademicUsefulSite(
            id = "soc-10",
            title = "Latindex Catálogo Regional",
            url = "https://www.latindex.org",
            area = AcademicKnowledgeArea.SOCIAL_SCIENCES,
            description = "Sistema de información para publicaciones científicas seriadas de América Latina y España.",
            tags = listOf("indices", "calidad-editorial", "latinoamerica"),
        ),
        AcademicUsefulSite(
            id = "soc-11",
            title = "ERIC Centro de Información Educativa",
            url = "https://eric.ed.gov",
            area = AcademicKnowledgeArea.SOCIAL_SCIENCES,
            description = "Base de datos digital de investigación y literatura educativa con acceso abierto completo.",
            tags = listOf("educacion", "pedagogia", "articulos"),
        ),
        AcademicUsefulSite(
            id = "soc-12",
            title = "Semantic Scholar Investigación IA",
            url = "https://www.semanticscholar.org",
            area = AcademicKnowledgeArea.SOCIAL_SCIENCES,
            description = "Buscador científico que extrae citas clave, figuras y resúmenes de papers a costo cero.",
            tags = listOf("ia", "semantica", "papers"),
        ),

        // --- 2. CIENCIAS JURÍDICAS (11) ---
        AcademicUsefulSite(
            id = "jur-13",
            title = "SAIJ Sistema de Información Jurídica",
            url = "http://www.saij.gob.ar",
            area = AcademicKnowledgeArea.LEGAL_JURIDICAL,
            description = "Portal nacional público y gratuito de legislación, jurisprudencia y doctrina jurídica.",
            tags = listOf("legislacion", "fallos", "doctrina"),
        ),
        AcademicUsefulSite(
            id = "jur-14",
            title = "InfoLEG Base Legislativa Nacional",
            url = "https://servicios.infoleg.gob.ar",
            area = AcademicKnowledgeArea.LEGAL_JURIDICAL,
            description = "Centro de documentación legislativa oficial con leyes, decretos y resoluciones vigentes.",
            tags = listOf("leyes", "normativa", "oficial"),
        ),
        AcademicUsefulSite(
            id = "jur-15",
            title = "Corte IDH Jurisprudencia Abierta",
            url = "https://www.corteidh.or.cr",
            area = AcademicKnowledgeArea.LEGAL_JURIDICAL,
            description = "Sentencias completas, opiniones consultivas y medidas de la Corte Interamericana de DDHH.",
            tags = listOf("derechos-humanos", "fallos", "internacional"),
        ),
        AcademicUsefulSite(
            id = "jur-16",
            title = "Dialnet Derecho y Ciencias Jurídicas",
            url = "https://dialnet.unirioja.es",
            area = AcademicKnowledgeArea.LEGAL_JURIDICAL,
            description = "Colección exhaustiva de revistas y tesis doctorales de derecho en lengua española.",
            tags = listOf("derecho", "doctrina", "tesis"),
        ),
        AcademicUsefulSite(
            id = "jur-17",
            title = "Redalyc Ciencias Jurídicas",
            url = "https://www.redalyc.org",
            area = AcademicKnowledgeArea.LEGAL_JURIDICAL,
            description = "Revistas académicas arbitradas de derecho constitucional, penal, civil y administrativo.",
            tags = listOf("arbitradas", "derecho", "iberoamerica"),
        ),
        AcademicUsefulSite(
            id = "jur-18",
            title = "SciELO Jurídica Iberoamericana",
            url = "https://scielo.org",
            area = AcademicKnowledgeArea.LEGAL_JURIDICAL,
            description = "Investigación jurídica empírica y dogmática de alto impacto con acceso libre.",
            tags = listOf("investigacion", "derecho", "open-access"),
        ),
        AcademicUsefulSite(
            id = "jur-19",
            title = "WIPO Lex Legislación Internacional",
            url = "https://www.wipo.int/wipolex",
            area = AcademicKnowledgeArea.LEGAL_JURIDICAL,
            description = "Base de datos mundial de tratados y leyes sobre propiedad intelectual y derecho comparado.",
            tags = listOf("propiedad-intelectual", "tratados", "comparado"),
        ),
        AcademicUsefulSite(
            id = "jur-20",
            title = "Biblioteca Digital Jurídica Iberoamericana",
            url = "https://www.juridicas.unam.mx",
            area = AcademicKnowledgeArea.LEGAL_JURIDICAL,
            description = "Obras clásicas, enciclopedias y monografías jurídicas de acceso abierto libre.",
            tags = listOf("enciclopedias", "monografias", "clasicos"),
        ),
        AcademicUsefulSite(
            id = "jur-21",
            title = "EUR-Lex Legislación Comunitaria",
            url = "https://eur-lex.europa.eu",
            area = AcademicKnowledgeArea.LEGAL_JURIDICAL,
            description = "Acceso oficial abierto a la legislación, jurisprudencia y tratados de derecho internacional.",
            tags = listOf("derecho-internacional", "comunitario", "tratados"),
        ),
        AcademicUsefulSite(
            id = "jur-22",
            title = "WorldLII World Legal Information",
            url = "http://www.worldlii.org",
            area = AcademicKnowledgeArea.LEGAL_JURIDICAL,
            description = "Instituto global con más de 2000 bases de datos de jurisprudencia y leyes gratuitas.",
            tags = listOf("global", "jurisprudencia", "open-access"),
        ),
        AcademicUsefulSite(
            id = "jur-23",
            title = "DOAJ Revistas de Derecho",
            url = "https://doaj.org",
            area = AcademicKnowledgeArea.LEGAL_JURIDICAL,
            description = "Catálogo de revistas jurídicas indexadas sin cargos por publicación ni suscripción.",
            tags = listOf("revistas", "peer-reviewed", "derecho"),
        ),

        // --- 3. CIENCIAS MÉDICAS Y DE LA SALUD (12) ---
        AcademicUsefulSite(
            id = "med-24",
            title = "PubMed Central Archivo Biomédico",
            url = "https://www.ncbi.nlm.nih.gov/pmc/",
            area = AcademicKnowledgeArea.MEDICAL_HEALTH,
            description = "Archivo digital gratuito de texto completo de revistas biomédicas y de ciencias de la vida.",
            tags = listOf("medicina", "biologia", "ensayos-clinicos"),
        ),
        AcademicUsefulSite(
            id = "med-25",
            title = "SciELO Salud Pública y Medicina",
            url = "https://scielo.org",
            area = AcademicKnowledgeArea.MEDICAL_HEALTH,
            description = "Revistas médicas latinoamericanas con artículos de investigación epidemiológica y clínica.",
            tags = listOf("salud-publica", "clinica", "epidemiologia"),
        ),
        AcademicUsefulSite(
            id = "med-26",
            title = "Cochrane Library en Español",
            url = "https://www.cochranelibrary.com/es/",
            area = AcademicKnowledgeArea.MEDICAL_HEALTH,
            description = "Revisiones sistemáticas basadas en evidencia de alta calidad con resúmenes abiertos en español.",
            tags = listOf("medicina-basada-en-evidencia", "revisiones", "clinica"),
        ),
        AcademicUsefulSite(
            id = "med-27",
            title = "LILACS Literatura en Ciencias de la Salud",
            url = "https://lilacs.bvsalud.org/es/",
            area = AcademicKnowledgeArea.MEDICAL_HEALTH,
            description = "Índice de la literatura científica y técnica en salud de 27 países de América Latina y el Caribe.",
            tags = listOf("salud", "enfermeria", "latinoamerica"),
        ),
        AcademicUsefulSite(
            id = "med-28",
            title = "BioMed Central Revistas Abiertas",
            url = "https://www.biomedcentral.com/",
            area = AcademicKnowledgeArea.MEDICAL_HEALTH,
            description = "Pionero en publicación biomédica abierta con revisión rigurosa por pares.",
            tags = listOf("biomedicina", "genetica", "farmacologia"),
        ),
        AcademicUsefulSite(
            id = "med-29",
            title = "MedRxiv Preprints en Salud",
            url = "https://www.medrxiv.org/",
            area = AcademicKnowledgeArea.MEDICAL_HEALTH,
            description = "Servidor libre de preprints para investigación clínica, epidemiológica y servicios de salud.",
            tags = listOf("preprints", "salud", "reciente"),
        ),
        AcademicUsefulSite(
            id = "med-30",
            title = "PLOS ONE Ciencias de la Salud",
            url = "https://journals.plos.org/plosone/",
            area = AcademicKnowledgeArea.MEDICAL_HEALTH,
            description = "Revista multidisciplinar de acceso abierto con rigurosa verificación de reproducibilidad científica.",
            tags = listOf("reproducibilidad", "ciencia", "articulos"),
        ),
        AcademicUsefulSite(
            id = "med-31",
            title = "Europe PMC Biblioteca Médica",
            url = "https://europepmc.org/",
            area = AcademicKnowledgeArea.MEDICAL_HEALTH,
            description = "Repositorio abierto con decenas de millones de artículos y preprints biomédicos en texto completo.",
            tags = listOf("biomedicina", "europa", "texto-completo"),
        ),
        AcademicUsefulSite(
            id = "med-32",
            title = "BVS Biblioteca Virtual en Salud",
            url = "https://bvsalud.org/es/",
            area = AcademicKnowledgeArea.MEDICAL_HEALTH,
            description = "Modelo de gestión de información y conocimiento en salud coordinado por BIREME/OPS.",
            tags = listOf("bireme", "ops", "salud-integral"),
        ),
        AcademicUsefulSite(
            id = "med-33",
            title = "Portal OPS Organización Panamericana",
            url = "https://www.paho.org/es",
            area = AcademicKnowledgeArea.MEDICAL_HEALTH,
            description = "Pautas clínicas, guías terapéuticas, manuales de salud pública e informes epidemiológicos libres.",
            tags = listOf("guias", "manuales", "salud-publica"),
        ),
        AcademicUsefulSite(
            id = "med-34",
            title = "Epistemonikos Base de Evidencia Médica",
            url = "https://www.epistemonikos.org/es/",
            area = AcademicKnowledgeArea.MEDICAL_HEALTH,
            description = "Buscador de revisiones sistemáticas en salud en múltiples idiomas desarrollado en Iberoamérica.",
            tags = listOf("evidencia", "revisiones", "multilingue"),
        ),
        AcademicUsefulSite(
            id = "med-35",
            title = "Redalyc Medicina y Ciencias de la Salud",
            url = "https://www.redalyc.org",
            area = AcademicKnowledgeArea.MEDICAL_HEALTH,
            description = "Artículos completos en español de revistas de enfermería, odontología, medicina y nutrición.",
            tags = listOf("nutricion", "odontologia", "enfermeria"),
        ),

        // --- 4. CIENCIAS EXACTAS Y NATURALES (12) ---
        AcademicUsefulSite(
            id = "exc-36",
            title = "arXiv Servidor de Preprints Científicos",
            url = "https://arxiv.org/",
            area = AcademicKnowledgeArea.EXACT_NATURAL,
            description = "Archivo abierto de preprints en física, matemáticas, ciencias de la computación y biología.",
            tags = listOf("fisica", "matematicas", "preprints"),
        ),
        AcademicUsefulSite(
            id = "exc-37",
            title = "CERN Document Server Datos Abiertos",
            url = "https://cds.cern.ch/",
            area = AcademicKnowledgeArea.EXACT_NATURAL,
            description = "Artículos, reportes técnicos y datos experimentales de física de altas energías del CERN.",
            tags = listOf("fisica-cuantica", "cern", "datos"),
        ),
        AcademicUsefulSite(
            id = "exc-38",
            title = "NASA Open Data Portal",
            url = "https://data.nasa.gov/",
            area = AcademicKnowledgeArea.EXACT_NATURAL,
            description = "Conjuntos de datos, modelos climáticos y misiones astronómicas accesibles sin costo.",
            tags = listOf("astronomia", "clima", "datos-abiertos"),
        ),
        AcademicUsefulSite(
            id = "exc-39",
            title = "BioRxiv Preprints Biológicos",
            url = "https://www.biorxiv.org/",
            area = AcademicKnowledgeArea.EXACT_NATURAL,
            description = "Servidor libre para avances en ecología, evolución, biología molecular y biofísica.",
            tags = listOf("biologia", "biofisica", "preprints"),
        ),
        AcademicUsefulSite(
            id = "exc-40",
            title = "ChemRxiv Preprints Químicos",
            url = "https://chemrxiv.org/",
            area = AcademicKnowledgeArea.EXACT_NATURAL,
            description = "Plataforma de preprints de química pura, aplicada y ciencia de materiales.",
            tags = listOf("quimica", "materiales", "preprints"),
        ),
        AcademicUsefulSite(
            id = "exc-41",
            title = "Zenodo Repositorio Científico Abierto",
            url = "https://zenodo.org/",
            area = AcademicKnowledgeArea.EXACT_NATURAL,
            description = "Repositorio abierto respaldado por OpenAIRE y CERN para compartir datasets, código y papers.",
            tags = listOf("datasets", "doi", "ciencia-abierta"),
        ),
        AcademicUsefulSite(
            id = "exc-42",
            title = "Dryad Digital Repository",
            url = "https://datadryad.org/",
            area = AcademicKnowledgeArea.EXACT_NATURAL,
            description = "Repositorio internacional curado para publicación de datos científicos subyacentes a artículos.",
            tags = listOf("datos-crudos", "reproducibilidad", "ciencias"),
        ),
        AcademicUsefulSite(
            id = "exc-43",
            title = "OSF Open Science Framework",
            url = "https://osf.io/",
            area = AcademicKnowledgeArea.EXACT_NATURAL,
            description = "Plataforma para gestión integral y colaboración en proyectos de investigación abierta.",
            tags = listOf("proyectos", "metodologia", "ciencia-abierta"),
        ),
        AcademicUsefulSite(
            id = "exc-44",
            title = "Science.gov Portal de Ciencia Abierta",
            url = "https://www.science.gov/",
            area = AcademicKnowledgeArea.EXACT_NATURAL,
            description = "Búsqueda federada en más de 60 bases de datos científicas e investigación gubernamental abierta.",
            tags = listOf("gobierno", "ciencia", "busqueda-federada"),
        ),
        AcademicUsefulSite(
            id = "exc-45",
            title = "WorldWideScience Portal Global",
            url = "https://worldwidescience.org/",
            area = AcademicKnowledgeArea.EXACT_NATURAL,
            description = "Puerta de enlace científica global que proporciona búsqueda y traducción en tiempo real de papers.",
            tags = listOf("global", "traduccion", "multilingue"),
        ),
        AcademicUsefulSite(
            id = "exc-46",
            title = "Biodiversity Heritage Library",
            url = "https://www.biodiversitylibrary.org/",
            area = AcademicKnowledgeArea.EXACT_NATURAL,
            description = "Consorcio digital con millones de páginas de literatura científica sobre biodiversidad.",
            tags = listOf("biodiversidad", "botanica", "zoologia"),
        ),
        AcademicUsefulSite(
            id = "exc-47",
            title = "PubChem Base Química de Acceso Libre",
            url = "https://pubchem.ncbi.nlm.nih.gov/",
            area = AcademicKnowledgeArea.EXACT_NATURAL,
            description = "La mayor colección gratuita del mundo de estructuras químicas, bioensayos y propiedades moleculares.",
            tags = listOf("quimica", "moleculas", "bioensayos"),
        ),

        // --- 5. INGENIERÍA Y TECNOLOGÍA (12) ---
        AcademicUsefulSite(
            id = "ing-48",
            title = "IEEE Open Ingeniería y Electrónica",
            url = "https://open.ieee.org/",
            area = AcademicKnowledgeArea.ENGINEERING_TECH,
            description = "Revistas de acceso abierto de la sociedad técnica profesional más grande del mundo.",
            tags = listOf("electronica", "telecomunicaciones", "ingenieria"),
        ),
        AcademicUsefulSite(
            id = "ing-49",
            title = "ACM Open Computación y Sistemas",
            url = "https://www.acm.org/publications/openaccess",
            area = AcademicKnowledgeArea.ENGINEERING_TECH,
            description = "Publicaciones y conferencias de ciencias de la computación bajo modelo abierto.",
            tags = listOf("computacion", "algoritmos", "software"),
        ),
        AcademicUsefulSite(
            id = "ing-50",
            title = "Software Heritage Archivo Universal",
            url = "https://www.softwareheritage.org/",
            area = AcademicKnowledgeArea.ENGINEERING_TECH,
            description = "La mayor biblioteca del mundo para preservación del código fuente de software científico.",
            tags = listOf("codigo-fuente", "preservacion", "open-source"),
        ),
        AcademicUsefulSite(
            id = "ing-51",
            title = "GitHub Explore Repositorios Académicos",
            url = "https://github.com/explore",
            area = AcademicKnowledgeArea.ENGINEERING_TECH,
            description = "Catálogo de implementaciones abiertas de algoritmos, papers y herramientas de ingeniería.",
            tags = listOf("repositorios", "ingenieria", "herramientas"),
        ),
        AcademicUsefulSite(
            id = "ing-52",
            title = "Kaggle Datasets Públicos para Investigación",
            url = "https://www.kaggle.com/datasets",
            area = AcademicKnowledgeArea.ENGINEERING_TECH,
            description = "Miles de conjuntos de datos y cuadernos listos para análisis en ciencia de datos e ingeniería.",
            tags = listOf("datasets", "ciencia-de-datos", "machine-learning"),
        ),
        AcademicUsefulSite(
            id = "ing-53",
            title = "Papers with Code Estado del Arte",
            url = "https://paperswithcode.com/",
            area = AcademicKnowledgeArea.ENGINEERING_TECH,
            description = "Recurso gratuito para seguir los últimos avances de machine learning junto con su código fuente.",
            tags = listOf("benchmarks", "machine-learning", "codigo"),
        ),
        AcademicUsefulSite(
            id = "ing-54",
            title = "HuggingFace Modelos y Datasets Libres",
            url = "https://huggingface.co/",
            area = AcademicKnowledgeArea.ENGINEERING_TECH,
            description = "Comunidad abierta de inteligencia artificial con modelos, datasets y demos reproducibles.",
            tags = listOf("ia", "modelos", "datasets"),
        ),
        AcademicUsefulSite(
            id = "ing-55",
            title = "TechRxiv Preprints Eléctricos e Informáticos",
            url = "https://www.techrxiv.org/",
            area = AcademicKnowledgeArea.ENGINEERING_TECH,
            description = "Preprints abiertos en ingeniería eléctrica, computación, bioingeniería y robótica.",
            tags = listOf("robotica", "control", "electronica"),
        ),
        AcademicUsefulSite(
            id = "ing-56",
            title = "DBLP Computer Science Bibliography",
            url = "https://dblp.org/",
            area = AcademicKnowledgeArea.ENGINEERING_TECH,
            description = "Servicio bibliográfico abierto con millones de publicaciones indexadas en computación.",
            tags = listOf("bibliografia", "sistemas", "conferencias"),
        ),
        AcademicUsefulSite(
            id = "ing-57",
            title = "CiteSeerX Biblioteca Digital de Computación",
            url = "https://citeseerx.ist.psu.edu/",
            area = AcademicKnowledgeArea.ENGINEERING_TECH,
            description = "Motor de búsqueda y biblioteca digital que extrae citas y papers en informática.",
            tags = listOf("citas", "indexacion", "informatica"),
        ),
        AcademicUsefulSite(
            id = "ing-58",
            title = "IntechOpen Libros de Ingeniería",
            url = "https://www.intechopen.com/",
            area = AcademicKnowledgeArea.ENGINEERING_TECH,
            description = "Libros y monografías científicas revisadas por pares de acceso abierto en ingeniería mecánica y civil.",
            tags = listOf("libros", "ingenieria-civil", "mecanica"),
        ),
        AcademicUsefulSite(
            id = "ing-59",
            title = "Engineering Village Open Access",
            url = "https://www.engineeringvillage.com/",
            area = AcademicKnowledgeArea.ENGINEERING_TECH,
            description = "Base de datos bibliográfica de ingeniería con filtros para publicaciones abiertas y patentes.",
            tags = listOf("patentes", "tecnologia", "normas"),
        ),

        // --- 6. HUMANIDADES Y ARTES (11) ---
        AcademicUsefulSite(
            id = "hum-60",
            title = "Internet Archive Biblioteca Digital Universal",
            url = "https://archive.org/",
            area = AcademicKnowledgeArea.HUMANITIES_ARTS,
            description = "Biblioteca digital sin fines de lucro con millones de libros, películas, música y páginas web.",
            tags = listOf("libros", "historia", "archivo-universal"),
        ),
        AcademicUsefulSite(
            id = "hum-61",
            title = "Project Gutenberg Literatura Abierta",
            url = "https://www.gutenberg.org/",
            area = AcademicKnowledgeArea.HUMANITIES_ARTS,
            description = "Más de 70.000 libros electrónicos gratuitos de literatura clásica y filosofía universal.",
            tags = listOf("literatura", "clasicos", "ebooks"),
        ),
        AcademicUsefulSite(
            id = "hum-62",
            title = "Europeana Patrimonio Cultural Digital",
            url = "https://www.europeana.eu/",
            area = AcademicKnowledgeArea.HUMANITIES_ARTS,
            description = "Acceso a obras de arte, libros, música y videos de galerías, bibliotecas y museos.",
            tags = listOf("arte", "museos", "patrimonio"),
        ),
        AcademicUsefulSite(
            id = "hum-63",
            title = "Redalyc Filosofía y Humanidades",
            url = "https://www.redalyc.org",
            area = AcademicKnowledgeArea.HUMANITIES_ARTS,
            description = "Revistas académicas arbitradas de historia, filosofía, lingüística y letras.",
            tags = listOf("filosofia", "historia", "linguistica"),
        ),
        AcademicUsefulSite(
            id = "hum-64",
            title = "SciELO Humanidades y Letras",
            url = "https://scielo.org",
            area = AcademicKnowledgeArea.HUMANITIES_ARTS,
            description = "Artículos completos en texto libre de estética, literatura comparada y artes visuales.",
            tags = listOf("letras", "estetica", "artes"),
        ),
        AcademicUsefulSite(
            id = "hum-65",
            title = "OpenEdition Humanidades y Ciencias Sociales",
            url = "https://www.openedition.org/",
            area = AcademicKnowledgeArea.HUMANITIES_ARTS,
            description = "Plataforma integral de libros y revistas académicas en humanidades y ciencias sociales.",
            tags = listOf("frances", "espanol", "humanidades"),
        ),
        AcademicUsefulSite(
            id = "hum-66",
            title = "Perseus Digital Library Textos Clásicos",
            url = "http://www.perseus.tufts.edu/hopper/",
            area = AcademicKnowledgeArea.HUMANITIES_ARTS,
            description = "Colección de fuentes primarias grecolatinas, análisis morfológicos y diccionarios clásicos.",
            tags = listOf("griego", "latin", "clasicos"),
        ),
        AcademicUsefulSite(
            id = "hum-67",
            title = "PhilPapers Índice Abierto de Filosofía",
            url = "https://philpapers.org/",
            area = AcademicKnowledgeArea.HUMANITIES_ARTS,
            description = "Directorio y archivo exhaustivo de artículos y libros de filosofía de acceso abierto.",
            tags = listOf("filosofia", "epistemologia", "etica"),
        ),
        AcademicUsefulSite(
            id = "hum-68",
            title = "HathiTrust Digital Library Obras Libres",
            url = "https://www.hathitrust.org/",
            area = AcademicKnowledgeArea.HUMANITIES_ARTS,
            description = "Millones de títulos digitalizados de dominio público para investigación académica.",
            tags = listOf("dominio-publico", "archivo", "textos"),
        ),
        AcademicUsefulSite(
            id = "hum-69",
            title = "Wikimedia Commons Medios Educativos",
            url = "https://commons.wikimedia.org/",
            area = AcademicKnowledgeArea.HUMANITIES_ARTS,
            description = "Base de datos con más de 100 millones de archivos multimedia de contenido educativo libre.",
            tags = listOf("imagenes", "diagramas", "creative-commons"),
        ),
        AcademicUsefulSite(
            id = "hum-70",
            title = "Wikiversity Aprendizaje Colaborativo Abierto",
            url = "https://es.wikiversity.org/",
            area = AcademicKnowledgeArea.HUMANITIES_ARTS,
            description = "Plataforma comunitaria de recursos didácticos libres y proyectos de aprendizaje abierto.",
            tags = listOf("recursos-didacticos", "colaborativo", "pedagogia"),
        ),
    )
}
