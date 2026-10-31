/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:32:00 -03:00
 * ARCHIVO: feature/community/src/test/kotlin/com/fenix/studio/feature/community/CommunityViewModelTest.kt
 * ================================================================================
 */

package com.fenix.studio.feature.community

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommunityViewModelTest {

    @Test
    fun `academic useful sites catalog contains exactly 70 free sites covering all 6 knowledge areas`() {
        val sites = AcademicUsefulSitesCatalog.ALL_70_SITES
        assertEquals(70, sites.size, "El catálogo debe contener exactamente 70 sitios académicos auditados")

        // Verificar unicidad de IDs y URLs
        val uniqueIds = sites.map { it.id }.toSet()
        assertEquals(70, uniqueIds.size, "Todos los IDs de sitios deben ser únicos")

        // Verificar cobertura completa de las 6 áreas de conocimiento
        AcademicKnowledgeArea.entries.forEach { area ->
            val countForArea = sites.count { it.area == area }
            assertTrue(countForArea >= 10, "El área ${area.name} debe tener al menos 10 sitios auditados")
        }

        // Regla Inviolable 2: Cero nombres de universidades, cátedras específicas o profesores hardcodeados
        val forbiddenInstitutions = listOf("unsj", "uba", "unc", "utn", "profesor", "catedra de")
        sites.forEach { site ->
            val contentLower = "${site.title} ${site.description}".lowercase()
            forbiddenInstitutions.forEach { forbidden ->
                assertFalse(
                    contentLower.contains(forbidden),
                    "El sitio '${site.title}' contiene término hardcodeado prohibido: $forbidden",
                )
            }
            assertTrue(site.isFree, "Todos los sitios deben ser verificados a costo 0")
            assertTrue(site.url.startsWith("http://") || site.url.startsWith("https://"))
        }
    }

    @Test
    fun `community viewmodel filters sites by query and knowledge area deterministically`() {
        val viewModel = CommunityViewModel()
        assertEquals(70, viewModel.uiState.value.usefulSites.size)

        // Filtrar por área jurídica
        viewModel.onSelectAreaFilter(AcademicKnowledgeArea.LEGAL_JURIDICAL)
        val juridicalSites = viewModel.uiState.value.usefulSites
        assertEquals(11, juridicalSites.size)
        assertTrue(juridicalSites.all { it.area == AcademicKnowledgeArea.LEGAL_JURIDICAL })

        // Buscar término específico dentro del área
        viewModel.onSearchQueryChanged("InfoLEG")
        val searched = viewModel.uiState.value.usefulSites
        assertEquals(1, searched.size)
        assertEquals("jur-14", searched.first().id)

        // Limpiar búsqueda
        viewModel.onSearchQueryChanged("")
        assertEquals(11, viewModel.uiState.value.usefulSites.size)

        // Quitar filtro de área
        viewModel.onSelectAreaFilter(AcademicKnowledgeArea.LEGAL_JURIDICAL)
        assertEquals(70, viewModel.uiState.value.usefulSites.size)
    }

    @Test
    fun `solidary request submission and package export operate cleanly without PII`() {
        val viewModel = CommunityViewModel()

        val initialRequestCount = viewModel.uiState.value.solidaryRequests.size
        val success = viewModel.onSubmitSolidaryRequest(
            topicSummary = "Apuntes abiertos de Cálculo Multivariable",
            area = AcademicKnowledgeArea.EXACT_NATURAL,
        )
        assertTrue(success)
        assertEquals(initialRequestCount + 1, viewModel.uiState.value.solidaryRequests.size)
        assertNotNull(viewModel.uiState.value.toastFeedbackMessage)

        val pkg = viewModel.uiState.value.sharedPackages.first()
        val exportUri = viewModel.onSanitizeAndExportPackage(pkg)
        assertTrue(exportUri.startsWith("fenix://${pkg.packageId}"))
        assertTrue(exportUri.contains("checksum=${pkg.checksumSha256}"))
        assertTrue(pkg.isSanitized)

        viewModel.onDismissToastFeedback()
        assertEquals(null, viewModel.uiState.value.toastFeedbackMessage)
    }
}
