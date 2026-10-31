/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: domain/src/main/kotlin/com/fenix/studio/domain/protocolv3/validation/OntologicalLinter.kt
 * ================================================================================
 */

package com.fenix.studio.domain.protocolv3.validation

/**
 * Las 6 etiquetas del Desambiguador Ontológico de Proposiciones del Motor Fénix v3.0.
 */
enum class OntologicalPropositionTag(val wireLabel: String) {
    EMPIRICAL_FACT("[HECHO / DATO EMPÍRICO]"),
    CORRELATION_ASSOCIATION("[CORRELACIÓN / ASOCIACIÓN]"),
    THEORETICAL_HYPOTHESIS("[HIPÓTESIS / MODELO TEÓRICO]"),
    AUTHORIAL_INTERPRETATION("[INTERPRETACIÓN / POSTURA AUTORAL]"),
    FORMAL_AXIOM_PROOF("[DEMOSTRACIÓN / AXIOMA FORMAL]"),
    DEONTIC_NORM_RULE("[NORMA / REGLA DEÓNTICA]"),
}

/**
 *Los 3 Estados Epistémicos de Blindaje Anti-Alucinación del Protocolo Fénix v3.0.
 */
enum class EpistemicStateTag(val wireLabel: String) {
    FUENTE("[FUENTE]"),
    INFERENCIA("[INFERENCIA]"),
    EXTERNO("[EXTERNO]"),
}

/**
 * Proposición etiquetada sujeta a verificación estática por el Linter Ontológico.
 */
data class TaggedPropositionCandidate(
    val id: String,
    val ontologicalTag: OntologicalPropositionTag,
    val epistemicState: EpistemicStateTag,
    val statementText: String,
)

/**
 * Resultado de validación del Linter Ontológico.
 */
data class OntologicalLintReport(
    val propositionId: String,
    val isValid: Boolean,
    val violations: List<String>,
)

/**
 * Linter Ontológico determinista de 6 etiquetas (`MF-3.3`).
 * Previene la ceguera epistemológica de los LLMs:
 * 1. En `[CORRELACIÓN / ASOCIACIÓN]`: bloquea estrictamente verbos causales directos
 *    (*produce, determina, causa, ocasiona, provoca, genera causalmente*).
 * 2. En `[INTERPRETACIÓN / POSTURA AUTORAL]`: exige marcador explícito de atribución autoral
 *    (*según, sostiene, plantea, postula, para, conforme a* o año entre paréntesis).
 * 3. En `[NORMA / REGLA DEÓNTICA]`: exige operador deóntico explícito
 *    (*obligatorio, prohibido, facultativo, debe, deberá, no podrá, plazo*).
 */
object OntologicalLinter {

    private val FORBIDDEN_CAUSAL_VERBS_IN_CORRELATION = listOf(
        Regex("\\bproduce(n)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\bdetermina(n)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\bcausa(n)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\bocasiona(n)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\bprovoca(n)?\\b", RegexOption.IGNORE_CASE),
    )

    private val AUTHORIAL_ANCHOR_PATTERNS = listOf(
        Regex("\\bsegún\\b", RegexOption.IGNORE_CASE),
        Regex("\\bsostiene(n)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\bplantea(n)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\bpostula(n)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\bconforme a\\b", RegexOption.IGNORE_CASE),
        Regex("\\(\\d{4}[a-z]?\\)"),
    )

    private val DEONTIC_OPERATOR_PATTERNS = listOf(
        Regex("\\bobligatori[oa](s)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\bprohibid[oa](s)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\bfacultativ[oa](s)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\bdebe(rá|n|rán)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\bno podrá(n)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\bplazo\\b", RegexOption.IGNORE_CASE),
    )

    fun lintProposition(candidate: TaggedPropositionCandidate): OntologicalLintReport {
        val violations = mutableListOf<String>()
        val text = candidate.statementText.trim()

        if (text.isEmpty()) {
            violations += "La proposición no puede estar vacía."
        }

        when (candidate.ontologicalTag) {
            OntologicalPropositionTag.CORRELATION_ASSOCIATION -> {
                for (causalRegex in FORBIDDEN_CAUSAL_VERBS_IN_CORRELATION) {
                    val match = causalRegex.find(text)
                    if (match != null) {
                        violations += "Bloqueo léxico en [CORRELACIÓN / ASOCIACIÓN]: verbo causal prohibido '${match.value}'. Use verbos asociativos (se asocia con, covaría con)."
                    }
                }
            }
            OntologicalPropositionTag.AUTHORIAL_INTERPRETATION -> {
                val hasAuthorAnchor = AUTHORIAL_ANCHOR_PATTERNS.any { it.containsMatchIn(text) }
                if (!hasAuthorAnchor) {
                    violations += "La etiqueta [INTERPRETACIÓN / POSTURA AUTORAL] exige anclaje autoral explícito (ej. 'según Autor (año)')."
                }
            }
            OntologicalPropositionTag.DEONTIC_NORM_RULE -> {
                val hasDeonticOperator = DEONTIC_OPERATOR_PATTERNS.any { it.containsMatchIn(text) }
                if (!hasDeonticOperator) {
                    violations += "La etiqueta [NORMA / REGLA DEÓNTICA] exige operador deóntico explícito (obligatorio, prohibido, facultativo, debe, plazo)."
                }
            }
            OntologicalPropositionTag.EMPIRICAL_FACT,
            OntologicalPropositionTag.THEORETICAL_HYPOTHESIS,
            OntologicalPropositionTag.FORMAL_AXIOM_PROOF,
            -> Unit
        }

        return OntologicalLintReport(
            propositionId = candidate.id,
            isValid = violations.isEmpty(),
            violations = violations,
        )
    }
}
