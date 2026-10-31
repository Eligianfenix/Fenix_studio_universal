/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/database/src/main/kotlin/com/fenix/studio/core/database/model/DatabaseEnums.kt
 * ================================================================================
 */

package com.fenix.studio.core.database.model

/**
 * Las 3 modalidades soberanas de creación y procesamiento de aulas (RN-18, RN-19, RN-20).
 */
enum class ClassroomModality {
    CATEDRA_PROGRAMA,
    PROYECTO_GUIADO,
    DOCUMENTO_AISLADO,
}

/**
 * Estado del ciclo de vida del aula.
 * `SEALED_EPHEMERAL` aplica a documentos aislados procesados sin cátedra (`P-05b`, RN-19).
 */
enum class ClassroomLifecycleState {
    ACTIVE,
    SEALED_EPHEMERAL,
    ARCHIVED,
}

/**
 * Estado operativo del procesamiento asíncrono para unidades, bloques y checkpoints (DT-ALTA-02).
 */
enum class ProcessingStatus {
    PENDING,
    QUEUED,
    OCR_IN_PROGRESS,
    SYNTHESIS_IN_PROGRESS,
    COMPLETED,
    PAUSED_CHECKPOINT,
    FAILED,
}

/**
 * Rol jerárquico del documento dentro del embudo de ingesta (Doctrina Troncal 1.0x vs Complementario 0.75x).
 */
enum class MaterialRoleType(val defaultPriorityWeight: Float) {
    SYLLABUS_RECTOR(1.0f),
    PRIMARY_DOCTRINE(1.0f),
    COMPLEMENTARY_SUPPORT(0.75f),
    PRACTICAL_CASUISTRY(0.85f),
}

/**
 * Secciones canónicas del Protocolo Fénix v2.1 / v3.0.
 */
enum class ProtocolSectionType {
    PART_0_INVENTORY,
    PART_A_EXPOSITORY_SYNTHESIS,
    PART_B_LITERAL_QUOTES,
    PART_C_APPLIED_CASES,
    PART_D_ACTIVE_RECALL_15Q,
    PART_E_COMPARATIVE_TABLES,
    PART_F_TECHNICAL_GLOSSARY,
}

/**
 * Estados epistémicos persistidos en base de datos para trazabilidad anti-alucinación (DT-ALTA-03).
 */
enum class EpistemicStateDb {
    FUENTE,
    INFERENCIA,
    EXTERNO,
}

/**
 *Las 6 categorías ontológicas de proposición persistidas en base de datos.
 */
enum class OntologicalCategoryDb {
    EMPIRICAL_FACT,
    CORRELATION_ASSOCIATION,
    THEORETICAL_HYPOTHESIS,
    AUTHORIAL_INTERPRETATION,
    FORMAL_AXIOM_PROOF,
    DEONTIC_NORM_RULE,
}
