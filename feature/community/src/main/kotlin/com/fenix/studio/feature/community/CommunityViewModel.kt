/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:31:10 -03:00
 * ARCHIVO: feature/community/src/main/kotlin/com/fenix/studio/feature/community/CommunityViewModel.kt
 * ================================================================================
 */

package com.fenix.studio.feature.community

import androidx.lifecycle.ViewModel
import com.fenix.studio.core.security.archive.FenixSovereignArchiveManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel del Hub Comunitario Soberano `P-10` (`MF-8.1`).
 * Administra el catálogo de 70 sitios académicos gratuitos y el repositorio solidario de paquetes `.fenix`.
 */
class CommunityViewModel(
    private val archiveManager: FenixSovereignArchiveManager = FenixSovereignArchiveManager(),
) : ViewModel() {

    private val allSites = AcademicUsefulSitesCatalog.ALL_70_SITES
    private val initialPackages = listOf(
        CommunitySharedPackage(
            packageId = "pkg-sample-01",
            title = "Compendio Metodológico de Investigación Científica",
            knowledgeArea = AcademicKnowledgeArea.SOCIAL_SCIENCES,
            createdAtEpochMs = 1774880000000L,
            sizeBytes = 1420000L,
            checksumSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            isSanitized = true,
            downloadCount = 42,
            authorAlias = "Comunidad Soberana Fénix",
        ),
        CommunitySharedPackage(
            packageId = "pkg-sample-02",
            title = "Glosario y Jurisprudencia Comparada de Derechos Fundamentales",
            knowledgeArea = AcademicKnowledgeArea.LEGAL_JURIDICAL,
            createdAtEpochMs = 1774900000000L,
            sizeBytes = 2300000L,
            checksumSha256 = "4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945",
            isSanitized = true,
            downloadCount = 18,
            authorAlias = "Estudiantes Solidarios",
        ),
    )

    private val initialRequests = listOf(
        SolidaryAcademicRequest(
            requestId = "req-01",
            topicSummary = "Búsqueda de apuntes complementarios sobre Álgebra Lineal y Matrices",
            knowledgeArea = AcademicKnowledgeArea.EXACT_NATURAL,
            timestampEpochMs = 1774910000000L,
            urgencyLevel = "Alta",
            isResolved = false,
        ),
        SolidaryAcademicRequest(
            requestId = "req-02",
            topicSummary = "Material de consulta abierta sobre Anatomía y Fisiología Celular",
            knowledgeArea = AcademicKnowledgeArea.MEDICAL_HEALTH,
            timestampEpochMs = 1774920000000L,
            urgencyLevel = "Media",
            isResolved = false,
        ),
    )

    private val _uiState = MutableStateFlow(
        CommunityUiState(
            usefulSites = allSites,
            sharedPackages = initialPackages,
            solidaryRequests = initialRequests,
        ),
    )
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    fun onSelectTab(tab: CommunityHubTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                usefulSites = filterSites(allSites, query, current.selectedAreaFilter),
            )
        }
    }

    fun onSelectAreaFilter(area: AcademicKnowledgeArea?) {
        _uiState.update { current ->
            val updatedArea = if (current.selectedAreaFilter == area) null else area
            current.copy(
                selectedAreaFilter = updatedArea,
                usefulSites = filterSites(allSites, current.searchQuery, updatedArea),
            )
        }
    }

    fun onSubmitSolidaryRequest(topicSummary: String, area: AcademicKnowledgeArea): Boolean {
        if (topicSummary.isBlank()) return false
        val newRequest = SolidaryAcademicRequest(
            requestId = "req-${System.currentTimeMillis()}",
            topicSummary = topicSummary.trim(),
            knowledgeArea = area,
            timestampEpochMs = System.currentTimeMillis(),
            urgencyLevel = "Media",
            isResolved = false,
        )
        _uiState.update { current ->
            current.copy(
                solidaryRequests = listOf(newRequest) + current.solidaryRequests,
                toastFeedbackMessage = "Petición solidaria publicada correctamente en la comunidad.",
            )
        }
        return true
    }

    fun onSanitizeAndExportPackage(packageItem: CommunitySharedPackage): String {
        _uiState.update {
            it.copy(
                toastFeedbackMessage = "Paquete sanitizado '${packageItem.title}' preparado para compartir vía Sharesheet.",
            )
        }
        return "fenix://${packageItem.packageId}?checksum=${packageItem.checksumSha256}"
    }

    fun onDismissToastFeedback() {
        _uiState.update { it.copy(toastFeedbackMessage = null) }
    }

    private fun filterSites(
        sites: List<AcademicUsefulSite>,
        query: String,
        area: AcademicKnowledgeArea?,
    ): List<AcademicUsefulSite> {
        val normalizedQuery = query.trim().lowercase()
        return sites.filter { site ->
            val matchesArea = area == null || site.area == area
            val matchesQuery = normalizedQuery.isEmpty() ||
                site.title.lowercase().contains(normalizedQuery) ||
                site.description.lowercase().contains(normalizedQuery) ||
                site.tags.any { it.lowercase().contains(normalizedQuery) }
            matchesArea && matchesQuery
        }
    }
}
