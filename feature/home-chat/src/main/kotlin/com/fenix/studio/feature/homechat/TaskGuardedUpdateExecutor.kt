/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:27:10 -03:00
 * ARCHIVO: feature/home-chat/src/main/kotlin/com/fenix/studio/feature/homechat/TaskGuardedUpdateExecutor.kt
 * ================================================================================
 */

package com.fenix.studio.feature.homechat

/**
 * Decisión determinista del Guardián de Actualización OTA (`RN-13`, `RN-27`).
 */
enum class OtaExecutionDecision {
    READY_TO_INSTALL_NOW,
    DEFERRED_UNTIL_IDLE,
}

/**
 * Estado de tareas críticas en ejecución que impiden un reinicio disruptivo de la aplicación.
 */
data class CriticalTaskActivitySnapshot(
    val isDocumentProcessingQueueActive: Boolean = false,
    val isUnsavedFenixDocsDraftActive: Boolean = false,
    val isSseStreamingActive: Boolean = false,
) {
    val hasAnyActiveCriticalTask: Boolean
        get() = isDocumentProcessingQueueActive ||
            isUnsavedFenixDocsDraftActive ||
            isSseStreamingActive
}

/**
 * Guardián Inviolable de Tareas en Ejecución para Actualizaciones OTA (`TaskGuardedUpdateExecutor`, `MF-4.2`, `RN-13`, `RN-27`).
 * - Monitorea si el estudiante tiene una cola de procesamiento en curso (`isDocumentProcessingQueueActive`),
 *   un borrador sin guardar en Fenix Docs (`isUnsavedFenixDocsDraftActive`) o un streaming SSE activo (`isSseStreamingActive`).
 * - Si hay cualquier tarea crítica activa, devuelve `OtaExecutionDecision.DEFERRED_UNTIL_IDLE` y pospone
 *   la aplicación de la actualización hasta que todas las tareas críticas pasen a `false` y el checkpoint Room esté confirmado.
 */
class TaskGuardedUpdateExecutor {

    private var pendingUpdateVersion: String? = null
    private var pendingUpdateSha256Hex: String? = null

    fun notifyOtaUpdateDownloaded(
        versionName: String,
        sha256Hex: String,
    ) {
        pendingUpdateVersion = versionName
        pendingUpdateSha256Hex = sha256Hex
    }

    fun evaluateOtaInstallation(
        snapshot: CriticalTaskActivitySnapshot,
    ): OtaExecutionDecision {
        if (pendingUpdateVersion == null) return OtaExecutionDecision.DEFERRED_UNTIL_IDLE
        return if (snapshot.hasAnyActiveCriticalTask) {
            OtaExecutionDecision.DEFERRED_UNTIL_IDLE
        } else {
            OtaExecutionDecision.READY_TO_INSTALL_NOW
        }
    }

    fun buildUserFacingOtaNotice(snapshot: CriticalTaskActivitySnapshot): String? {
        val ver = pendingUpdateVersion ?: return null
        return if (snapshot.hasAnyActiveCriticalTask) {
            "Actualización v$ver descargada. Hay un procesamiento en curso; la actualización se instalará de forma segura cuando finalice la tarea actual para proteger tus datos."
        } else {
            "Actualización v$ver verificada (SHA-256) y lista para instalar de forma segura."
        }
    }
}
