/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: domain/src/main/kotlin/com/fenix/studio/domain/protocolv3/validation/AcademicDeontologicalGuard.kt
 * ================================================================================
 */

package com.fenix.studio.domain.protocolv3.validation

/**
 * Decisión del Guardia Deontológico (RN-17).
 */
sealed interface DeontologicalDecision {
    /**
     * Consulta académica o estudio de material disciplinar (incluyendo Medicina, Psicología,
     * Veterinaria, Farmacología, Derecho, etc.): 100% permitido y estimulado.
     */
    data object AllowedAcademicProcessing : DeontologicalDecision

    /**
     * Solicitud de diagnóstico médico/psicológico personal en primera persona o patrocinio jurídico real:
     * bloqueado por ética deontológica (RN-17).
     */
    data class BlockedPersonalProfessionalPractice(
        val domainCategory: String,
        val safeRedirectionMessage: String,
    ) : DeontologicalDecision
}

/**
 * Guardia Deontológico de Fenix Studio (`RN-17`, `MF-3.3`).
 * Distingue con precisión entre:
 * - **Prohibido:** Ejercer como médico, psicólogo, terapeuta o abogado sobre el propio usuario
 *   (recetar medicación personal, diagnosticar síntomas propios del usuario, redactar demandas reales firmables).
 * - **Permitido y Estimulado:** Procesar y explicar casos clínicos académicos, fisiopatología, farmacología,
 *   psicopatología, derecho procesal o códigos normativos como objeto de estudio universitario.
 */
object AcademicDeontologicalGuard {

    private val PERSONAL_MEDICAL_OR_THERAPY_PATTERNS = listOf(
        Regex("\\b(recetame|recétame|qué medicamento tomo|que medicamento tomo|diagnosticame|diagnósticame)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(me duele el pecho y no sé qué tomar|hazme terapia psicológica|necesito que seas mi psicólogo)\\b", RegexOption.IGNORE_CASE),
    )

    private val PERSONAL_LEGAL_REPRESENTATION_PATTERNS = listOf(
        Regex("\\b(sé mi abogado defensor|representame en el juicio|redactame mi demanda personal para presentar hoy en el juzgado)\\b", RegexOption.IGNORE_CASE),
    )

    fun evaluateUserRequest(
        userPrompt: String,
        isAttachedToAcademicMaterial: Boolean = false,
    ): DeontologicalDecision {
        val normalized = userPrompt.trim()
        if (normalized.isEmpty()) return DeontologicalDecision.AllowedAcademicProcessing

        for (pattern in PERSONAL_MEDICAL_OR_THERAPY_PATTERNS) {
            if (pattern.containsMatchIn(normalized) && !isAttachedToAcademicMaterial) {
                return DeontologicalDecision.BlockedPersonalProfessionalPractice(
                    domainCategory = "SALUD_CLINICA_PERSONAL",
                    safeRedirectionMessage = "RN-17: Fenix Studio es un asistente de estudio académico y no reemplaza la consulta médica o psicológica profesional. Puedo ayudarte a analizar la fisiopatología, semiología o bibliografía académica de tu asignatura.",
                )
            }
        }

        for (pattern in PERSONAL_LEGAL_REPRESENTATION_PATTERNS) {
            if (pattern.containsMatchIn(normalized) && !isAttachedToAcademicMaterial) {
                return DeontologicalDecision.BlockedPersonalProfessionalPractice(
                    domainCategory = "PATROCINIO_JURIDICO_PERSONAL",
                    safeRedirectionMessage = "RN-17: Fenix Studio no ejerce patrocinio letrado personal. Puedo ayudarte a estudiar la doctrina jurídica, jurisprudencia y normas procesales de tu materia.",
                )
            }
        }

        return DeontologicalDecision.AllowedAcademicProcessing
    }
}
