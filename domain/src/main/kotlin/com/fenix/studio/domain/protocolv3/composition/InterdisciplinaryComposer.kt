/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: domain/src/main/kotlin/com/fenix/studio/domain/protocolv3/composition/InterdisciplinaryComposer.kt
 * ================================================================================
 */

package com.fenix.studio.domain.protocolv3.composition

import kotlin.math.roundToInt

/**
 * Los 6 Perfiles Disciplinares Canónicos (Taxonomía Biglan 1973 / Becher 1989) del Motor Fénix v3.0.
 * Operan por naturaleza epistemológica del material, sin atarse a facultades ni carreras fijas.
 */
enum class CanonicalDisciplinaryProfile(
    val code: String,
    val canonicalDescription: String,
    val specializedInjectionDirective: String,
) {
    P1_HERM(
        code = "P1-HERM",
        canonicalDescription = "Hermenéutico-Argumentativo",
        specializedInjectionDirective = "Genealogía conceptual, tensiones interautorales y matriz de posturas.",
    ),
    P2_EMP(
        code = "P2-EMP",
        canonicalDescription = "Empírico-Experimental",
        specializedInjectionDirective = "Variables operacionales, control experimental, magnitudes SI y leyes empíricas.",
    ),
    P3_FORM(
        code = "P3-FORM",
        canonicalDescription = "Formal-Deductivo",
        specializedInjectionDirective = "Definiciones axiomáticas, cuantificadores explícitos, invariantes y demostraciones.",
    ),
    P4_CLIN(
        code = "P4-CLIN",
        canonicalDescription = "Clínico-Diagnóstico",
        specializedInjectionDirective = "Fisiopatología/etiología, semiología, diagnóstico diferencial y signos de alarma.",
    ),
    P5_NORM(
        code = "P5-NORM",
        canonicalDescription = "Normativo-Deóntico",
        specializedInjectionDirective = "Operadores deónticos (obligatorio/prohibido/facultativo), supuestos de hecho y plazos.",
    ),
    P6_PROY(
        code = "P6-PROY",
        canonicalDescription = "Proyectual y de Diseño",
        specializedInjectionDirective = "Requerimientos, restricciones de contorno, arquitectura modular y validación.",
    ),
}

/**
 * Resultado del Álgebra de Composición Interdisciplinaria ($\sum w_i = 1.0$).
 */
data class ComposedProfilePlan(
    val normalizedWeights: Map<CanonicalDisciplinaryProfile, Double>,
    val primaryProfile: CanonicalDisciplinaryProfile,
    val isPrimaryDominant: Boolean,
    val injectedSecondaryProfiles: List<CanonicalDisciplinaryProfile>,
    val activeRecallQuestionDistribution: Map<CanonicalDisciplinaryProfile, Int>,
)

/**
 * Compositor algebraico interdisciplinario del Protocolo Fénix v3.0 (`MF-3.3`).
 * - Normaliza cualquier vector de pesos de entrada para garantizar $\sum_{i=1}^6 w_i = 1.0$.
 * - **Regla de Dominancia Estructural:** Si $w_p \ge 0.65$, el perfil primario gobierna íntegramente las Partes A y F.
 * - **Regla de Inyección Modular:** Todo perfil secundario con $w_s \ge 0.25$ inyecta sus estructuras especializadas
 *   y recibe asignación proporcional dentro de las 15 preguntas de Active Recall (Parte D).
 */
object InterdisciplinaryComposer {

    const val DOMINANCE_THRESHOLD: Double = 0.65
    const val SECONDARY_INJECTION_THRESHOLD: Double = 0.25
    const val TOTAL_ACTIVE_RECALL_QUESTIONS: Int = 15

    fun composePlan(
        rawWeights: Map<CanonicalDisciplinaryProfile, Double>,
    ): ComposedProfilePlan {
        val positiveWeights = CanonicalDisciplinaryProfile.entries.associateWith { profile ->
            (rawWeights[profile] ?: 0.0).coerceAtLeast(0.0)
        }
        val sum = positiveWeights.values.sum()

        val normalized: Map<CanonicalDisciplinaryProfile, Double> = if (sum <= 1e-9) {
            CanonicalDisciplinaryProfile.entries.associateWith {
                if (it == CanonicalDisciplinaryProfile.P1_HERM) 1.0 else 0.0
            }
        } else {
            positiveWeights.mapValues { (_, v) -> v / sum }
        }

        val ordered = normalized.entries.sortedWith(
            compareByDescending<Map.Entry<CanonicalDisciplinaryProfile, Double>> { it.value }
                .thenBy { it.key.ordinal },
        )

        val primaryEntry = ordered.first()
        val isDominant = primaryEntry.value >= DOMINANCE_THRESHOLD

        val injectedSecondaries = ordered
            .drop(1)
            .filter { it.value >= SECONDARY_INJECTION_THRESHOLD }
            .map { it.key }

        val distribution = distributeActiveRecallQuestions(normalized, primaryEntry.key)

        return ComposedProfilePlan(
            normalizedWeights = normalized,
            primaryProfile = primaryEntry.key,
            isPrimaryDominant = isDominant,
            injectedSecondaryProfiles = injectedSecondaries,
            activeRecallQuestionDistribution = distribution,
        )
    }

    private fun distributeActiveRecallQuestions(
        normalizedWeights: Map<CanonicalDisciplinaryProfile, Double>,
        primaryProfile: CanonicalDisciplinaryProfile,
    ): Map<CanonicalDisciplinaryProfile, Int> {
        val activeProfiles = normalizedWeights.filter { it.value > 0.01 }
        if (activeProfiles.isEmpty()) {
            return mapOf(primaryProfile to TOTAL_ACTIVE_RECALL_QUESTIONS)
        }

        val mutableCounts = mutableMapOf<CanonicalDisciplinaryProfile, Int>()
        var allocated = 0
        for ((profile, weight) in activeProfiles) {
            val count = (weight * TOTAL_ACTIVE_RECALL_QUESTIONS).roundToInt()
            if (count > 0) {
                mutableCounts[profile] = count
                allocated += count
            }
        }

        val delta = TOTAL_ACTIVE_RECALL_QUESTIONS - allocated
        if (delta != 0) {
            val currentPrimary = mutableCounts[primaryProfile] ?: 0
            mutableCounts[primaryProfile] = (currentPrimary + delta).coerceAtLeast(1)
        }

        return mutableCounts.toMap()
    }
}
