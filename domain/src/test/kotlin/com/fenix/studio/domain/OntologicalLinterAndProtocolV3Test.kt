/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: domain/src/test/kotlin/com/fenix/studio/domain/OntologicalLinterAndProtocolV3Test.kt
 * ================================================================================
 */

package com.fenix.studio.domain

import com.fenix.studio.domain.protocolv3.composition.CanonicalDisciplinaryProfile
import com.fenix.studio.domain.protocolv3.composition.InterdisciplinaryComposer
import com.fenix.studio.domain.protocolv3.curriculum.ConceptDescriptor
import com.fenix.studio.domain.protocolv3.curriculum.SyllabusPoint
import com.fenix.studio.domain.protocolv3.curriculum.TheoreticalDensityExpander
import com.fenix.studio.domain.protocolv3.validation.AcademicDeontologicalGuard
import com.fenix.studio.domain.protocolv3.validation.DeontologicalDecision
import com.fenix.studio.domain.protocolv3.validation.EpistemicStateTag
import com.fenix.studio.domain.protocolv3.validation.GeneratedUnitProtocolSubmission
import com.fenix.studio.domain.protocolv3.validation.OntologicalLinter
import com.fenix.studio.domain.protocolv3.validation.OntologicalPropositionTag
import com.fenix.studio.domain.protocolv3.validation.ProtocolChecklistValidator
import com.fenix.studio.domain.protocolv3.validation.TaggedPropositionCandidate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OntologicalLinterAndProtocolV3Test {

    @Test
    fun `TheoreticalDensityExpander generates synthetic subpoints with star glyph when D(p) exceeds 1_0 while keeping official pointCode immutable`() {
        val concepts = (1..6).map { idx ->
            ConceptDescriptor(
                conceptId = "c-$idx",
                canonicalName = "Concepto Primario $idx",
                propositionCount = 3,
                lexicalEntropyBits = 1.2,
            )
        }
        val densePoint = SyllabusPoint(
            pointCode = "2.3",
            officialTitle = "Dinámica de Sistemas Complejos",
            primaryConcepts = concepts,
            totalPropositions = 18,
            lexicalEntropy = 1.2,
        )

        val expanded = TheoreticalDensityExpander.expandPointIfDense(densePoint)

        // El código oficial jamás se altera
        assertEquals("2.3", expanded.pointCode)
        assertTrue(expanded.syntheticSubpoints.isNotEmpty())
        assertEquals("2.3.a", expanded.syntheticSubpoints.first().subpointCode)
        assertEquals("2.3.a \u2726", expanded.syntheticSubpoints.first().displayCodeWithStar)
        assertEquals(6, expanded.syntheticSubpoints.sumOf { it.assignedConcepts.size })
    }

    @Test
    fun `InterdisciplinaryComposer normalizes weights to 1_0 enforces dominance at 0_65 and injects secondary profiles at 0_25`() {
        val rawWeights = mapOf(
            CanonicalDisciplinaryProfile.P4_CLIN to 68.0,
            CanonicalDisciplinaryProfile.P5_NORM to 27.0,
            CanonicalDisciplinaryProfile.P2_EMP to 5.0,
        )

        val plan = InterdisciplinaryComposer.composePlan(rawWeights)

        val sum = plan.normalizedWeights.values.sum()
        assertEquals(1.0, sum, 1e-6)
        assertEquals(CanonicalDisciplinaryProfile.P4_CLIN, plan.primaryProfile)
        assertTrue(plan.isPrimaryDominant)
        assertEquals(listOf(CanonicalDisciplinaryProfile.P5_NORM), plan.injectedSecondaryProfiles)
        assertEquals(
            InterdisciplinaryComposer.TOTAL_ACTIVE_RECALL_QUESTIONS,
            plan.activeRecallQuestionDistribution.values.sum(),
        )
    }

    @Test
    fun `OntologicalLinter blocks causal verbs in CORRELATION_ASSOCIATION and enforces authorial and deontic markers`() {
        val invalidCorrelation = TaggedPropositionCandidate(
            id = "p-corr-bad",
            ontologicalTag = OntologicalPropositionTag.CORRELATION_ASSOCIATION,
            epistemicState = EpistemicStateTag.FUENTE,
            statementText = "El incremento de la variable X produce directamente el aumento de la variable Y.",
        )
        val reportBad = OntologicalLinter.lintProposition(invalidCorrelation)
        assertFalse(reportBad.isValid)
        assertTrue(reportBad.violations.first().contains("produce"))

        val validCorrelation = TaggedPropositionCandidate(
            id = "p-corr-ok",
            ontologicalTag = OntologicalPropositionTag.CORRELATION_ASSOCIATION,
            epistemicState = EpistemicStateTag.FUENTE,
            statementText = "El incremento de la variable X covaría con la tendencia observada en la variable Y.",
        )
        assertTrue(OntologicalLinter.lintProposition(validCorrelation).isValid)

        val validAuthorial = TaggedPropositionCandidate(
            id = "p-auth-ok",
            ontologicalTag = OntologicalPropositionTag.AUTHORIAL_INTERPRETATION,
            epistemicState = EpistemicStateTag.FUENTE,
            statementText = "Según el marco analítico expuesto (1998), la estructura institucional condiciona la acción.",
        )
        assertTrue(OntologicalLinter.lintProposition(validAuthorial).isValid)

        val validDeontic = TaggedPropositionCandidate(
            id = "p-norm-ok",
            ontologicalTag = OntologicalPropositionTag.DEONTIC_NORM_RULE,
            epistemicState = EpistemicStateTag.FUENTE,
            statementText = "Es obligatorio interponer el recurso administrativo dentro del plazo perentorio de cinco días.",
        )
        assertTrue(OntologicalLinter.lintProposition(validDeontic).isValid)
    }

    @Test
    fun `ProtocolChecklistValidator enforces 1 to 1 concept mapping 15 Active Recall questions and rejects EXTERNO in Part A`() {
        val validProp = TaggedPropositionCandidate(
            id = "pa-1",
            ontologicalTag = OntologicalPropositionTag.EMPIRICAL_FACT,
            epistemicState = EpistemicStateTag.FUENTE,
            statementText = "Proposición fiel a la fuente.",
        )
        val questions15 = (1..15).map { "Pregunta de recuperación activa #$it" }

        val validSubmission = GeneratedUnitProtocolSubmission(
            unitNumber = 1,
            part0IdentifiedConceptIds = setOf("c1", "c2", "c3"),
            partADevelopedConceptIds = setOf("c1", "c2", "c3"),
            partAPropositions = listOf(validProp),
            partBQuotePropositions = listOf(validProp),
            partCAppliedPropositions = emptyList(),
            partDActiveRecallQuestions = questions15,
            partEComparativeTablePropositions = listOf(validProp),
            partFGlossaryTerms = mapOf("Término A" to "Definición rigurosa A"),
        )

        val approvedResult = ProtocolChecklistValidator.validateUnitSubmission(validSubmission)
        assertTrue(approvedResult.isApproved)

        // Si se infiltra una proposición [EXTERNO] en la Parte A o falta un concepto de la Parte 0, debe rechazar
        val contaminatedSubmission = validSubmission.copy(
            partADevelopedConceptIds = setOf("c1", "c2"), // falta c3
            partAPropositions = listOf(
                validProp.copy(epistemicState = EpistemicStateTag.EXTERNO),
            ),
        )
        val rejectedResult = ProtocolChecklistValidator.validateUnitSubmission(contaminatedSubmission)
        assertFalse(rejectedResult.isApproved)
        assertEquals(setOf("c3"), rejectedResult.missingConceptIdsInPartA)
        assertTrue(rejectedResult.epistemicViolations.isNotEmpty())
    }

    @Test
    fun `AcademicDeontologicalGuard blocks personal prescriptions while allowing academic study of clinical and legal subjects`() {
        val personalMedicalRequest = AcademicDeontologicalGuard.evaluateUserRequest(
            userPrompt = "Me duele el pecho y no sé qué tomar, recetame un medicamento ahora.",
            isAttachedToAcademicMaterial = false,
        )
        assertTrue(personalMedicalRequest is DeontologicalDecision.BlockedPersonalProfessionalPractice)

        val academicClinicalStudy = AcademicDeontologicalGuard.evaluateUserRequest(
            userPrompt = "Explicar los criterios diagnósticos y mecanismos farmacológicos descritos en el capítulo 4 de la bibliografía.",
            isAttachedToAcademicMaterial = true,
        )
        assertEquals(DeontologicalDecision.AllowedAcademicProcessing, academicClinicalStudy)
    }
}
