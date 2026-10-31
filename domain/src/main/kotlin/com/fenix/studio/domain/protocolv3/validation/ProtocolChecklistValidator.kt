/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: domain/src/main/kotlin/com/fenix/studio/domain/protocolv3/validation/ProtocolChecklistValidator.kt
 * ================================================================================
 */

package com.fenix.studio.domain.protocolv3.validation

/**
 * Representa una unidad procesada bajo el Protocolo Fénix v2.1 / v3.0 (Partes 0 a F)
 * para su verificación algorítmica 1:1 antes de persistir o exportar a `.docx`.
 */
data class GeneratedUnitProtocolSubmission(
    val unitNumber: Int,
    val part0IdentifiedConceptIds: Set<String>,
    val partADevelopedConceptIds: Set<String>,
    val partAPropositions: List<TaggedPropositionCandidate>,
    val partBQuotePropositions: List<TaggedPropositionCandidate>,
    val partCAppliedPropositions: List<TaggedPropositionCandidate>,
    val partDActiveRecallQuestions: List<String>,
    val partEComparativeTablePropositions: List<TaggedPropositionCandidate>,
    val partFGlossaryTerms: Map<String, String>,
)

/**
 * Resultado de la auditoría algorítmica 1:1 del Protocolo Fénix v3.0.
 */
data class ProtocolChecklistAuditResult(
    val isApproved: Boolean,
    val missingConceptIdsInPartA: Set<String>,
    val epistemicViolations: List<String>,
    val structuralViolations: List<String>,
)

/**
 * Verificador Algorítmico del Mapeo 1:1 y de la Política de Tres Estados Epistémicos (`MF-3.3`):
 * 1. Verifica que el 100% de los conceptos primarios identificados en la Parte 0 estén desarrollados en la Parte A.
 * 2. Verifica que las Partes A, B y E contengan exclusivamente estado `[FUENTE]` (prohibido `[EXTERNO]` o `[INFERENCIA]` en la doctrina troncal y citas literales).
 * 3. Verifica que la Parte D contenga exactamente 15 preguntas de recuperación activa (Active Recall) y la Parte F glosario no vacío.
 */
object ProtocolChecklistValidator {

    const val REQUIRED_ACTIVE_RECALL_COUNT: Int = 15

    fun validateUnitSubmission(submission: GeneratedUnitProtocolSubmission): ProtocolChecklistAuditResult {
        val missingInPartA = submission.part0IdentifiedConceptIds - submission.partADevelopedConceptIds
        val epistemicViolations = mutableListOf<String>()
        val structuralViolations = mutableListOf<String>()

        if (submission.part0IdentifiedConceptIds.isEmpty()) {
            structuralViolations += "Parte 0 inválida: el inventario curricular no identificó ningún concepto primario."
        }

        if (missingInPartA.isNotEmpty()) {
            structuralViolations += "Fallo de Mapeo 1:1: los conceptos ${missingInPartA.joinToString()} de la Parte 0 fueron omitidos en la Parte A."
        }

        // Política de 3 Estados Epistémicos: Parte A, B y E solo admiten [FUENTE]
        submission.partAPropositions.forEach { prop ->
            if (prop.epistemicState != EpistemicStateTag.FUENTE) {
                epistemicViolations += "Violación epistémica en Parte A (${prop.id}): se encontró ${prop.epistemicState.wireLabel}; la síntesis troncal exige exclusivamente [FUENTE]."
            }
        }

        submission.partBQuotePropositions.forEach { prop ->
            if (prop.epistemicState != EpistemicStateTag.FUENTE) {
                epistemicViolations += "Violación epistémica en Parte B (${prop.id}): las citas textuales exigen exclusivamente [FUENTE]."
            }
        }

        submission.partEComparativeTablePropositions.forEach { prop ->
            if (prop.epistemicState != EpistemicStateTag.FUENTE) {
                epistemicViolations += "Violación epistémica en Parte E (${prop.id}): las tablas comparativas exigen exclusivamente [FUENTE]."
            }
        }

        if (submission.partDActiveRecallQuestions.size != REQUIRED_ACTIVE_RECALL_COUNT) {
            structuralViolations += "Parte D inválida: se requieren exactamente $REQUIRED_ACTIVE_RECALL_COUNT preguntas de Active Recall (recibidas: ${submission.partDActiveRecallQuestions.size})."
        }

        if (submission.partFGlossaryTerms.isEmpty()) {
            structuralViolations += "Parte F inválida: el glosario técnico-académico no puede estar vacío."
        }

        return ProtocolChecklistAuditResult(
            isApproved = missingInPartA.isEmpty() &&
                epistemicViolations.isEmpty() &&
                structuralViolations.isEmpty(),
            missingConceptIdsInPartA = missingInPartA,
            epistemicViolations = epistemicViolations,
            structuralViolations = structuralViolations,
        )
    }
}
