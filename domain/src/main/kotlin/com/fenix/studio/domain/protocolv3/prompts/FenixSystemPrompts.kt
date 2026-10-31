/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: domain/src/main/kotlin/com/fenix/studio/domain/protocolv3/prompts/FenixSystemPrompts.kt
 * ================================================================================
 */

package com.fenix.studio.domain.protocolv3.prompts

import com.fenix.studio.domain.protocolv3.composition.ComposedProfilePlan

/**
 * Generador de System Prompts modulares del Protocolo Fénix v3.0 (`MF-3.3`).
 * 100% libre de nombres de universidades, facultades, carreras o cátedras fijas (Regla Inviolable 2 y 3).
 */
object FenixSystemPrompts {

    /**
     * Construye el System Prompt de síntesis académica parametrizado dinámicamente por el `ComposedProfilePlan`
     * y los datos de contexto ingresados por el estudiante.
     */
    fun buildProtocolV3SynthesisPrompt(
        classroomTitle: String,
        unitNumber: Int,
        unitTitle: String,
        composedPlan: ComposedProfilePlan,
    ): String {
        val secondaryDirectives = if (composedPlan.injectedSecondaryProfiles.isEmpty()) {
            "Sin inyección secundaria (w_s < 0.25)."
        } else {
            composedPlan.injectedSecondaryProfiles.joinToString(separator = " | ") { profile ->
                "${profile.code}: ${profile.specializedInjectionDirective}"
            }
        }

        return buildString {
            appendLine("SISTEMA: MOTOR DE PROCESAMIENTO ACADÉMICO MODULAR FÉNIX v3.0")
            appendLine("CONTEXTO DEL USUARIO: Aula '$classroomTitle' — Unidad $unitNumber: '$unitTitle'")
            appendLine("PERFIL PRIMARIO DOMINANTE: ${composedPlan.primaryProfile.code} (${composedPlan.primaryProfile.canonicalDescription})")
            appendLine("DIRECTIVA PRIMARIA: ${composedPlan.primaryProfile.specializedInjectionDirective}")
            appendLine("INYECCIÓN INTERDISCIPLINARIA SECUNDARIA: $secondaryDirectives")
            appendLine("REGLAS INVIOLABLES DE SALIDA:")
            appendLine("1. Mapeo 1:1 obligatorio entre el inventario de la Parte 0 y el desarrollo expositivo de la Parte A.")
            appendLine("2. Etiquetado ontológico estricto en cada proposición ([HECHO / DATO EMPÍRICO], [CORRELACIÓN / ASOCIACIÓN], [HIPÓTESIS / MODELO TEÓRICO], [INTERPRETACIÓN / POSTURA AUTORAL], [DEMOSTRACIÓN / AXIOMA FORMAL], [NORMA / REGLA DEÓNTICA]).")
            appendLine("3. Segregación epistémica obligatoria: [FUENTE] exclusivo para Partes A, B y E; [INFERENCIA] para aplicaciones pedagógicas en Partes C y D; [EXTERNO] prohibido en la doctrina troncal.")
            appendLine("4. Parte D: exactamente 15 preguntas de Active Recall distribuidas según: ${composedPlan.activeRecallQuestionDistribution}.")
            appendLine("5. Parte F: Glosario técnico-académico riguroso extraído de las fuentes.")
        }.trim()
    }
}
