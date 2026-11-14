/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:34:30 -03:00
 * ARCHIVO: core/security-vault/src/main/kotlin/com/fenix/studio/core/security/telemetry/AnonymousTelemetryManager.kt
 * ================================================================================
 */

package com.fenix.studio.core.security.telemetry

import com.fenix.studio.core.security.quota.FenixToolId
import kotlinx.serialization.Serializable
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Las 6 categorías exhaustivas de telemetría técnica anónima (100% libre de PII / datos personales)
 * recolectadas cuando un usuario (especialmente con rol `beta_tester`) otorga su consentimiento explícito.
 */
@Serializable
enum class TelemetryCategory {
    TOOL_EXECUTION_METRIC,
    API_AND_ROUTER_METRIC,
    DOCUMENT_ENGINE_METRIC,
    DEVICE_RESOURCE_HEALTH_METRIC,
    NAVIGATION_AND_UX_METRIC,
    SANITIZED_ERROR_REPORT,
}

/**
 * Evento de telemetría técnica estrictamente desprovisto de identificadores personales (Ley 25.326).
 */
@Serializable
data class AnonymousTelemetryEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val category: TelemetryCategory,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val attributes: Map<String, String>,
)

/**
 * Colector centralizado de telemetría técnica anónima para el Programa de `beta_tester` y diagnóstico
 * de `Fenix Admin`.
 *
 * Garantías de Soberanía y Privacidad (Ley 25.326):
 * 1. Solo encola o emite eventos si `isTelemetryActive()` es `true` (`hasAcceptedAnonymousTelemetryConsent == true`).
 * 2. Aplica sanitización determinista por expresiones regulares (`sanitizePiiAndSecrets`) sobre todas las
 *    cadenas y trazas técnicas, redactando emails, rutas de archivos personales (`/Users/...`, `/home/...`, `C:\...`),
 *    documentos PDF/DOCX con nombres propios y tokens (`sk-...`, `Bearer ...`).
 */
class AnonymousTelemetryManager(
    initialConsentAccepted: Boolean = false,
) {
    @Volatile
    private var consentAccepted: Boolean = initialConsentAccepted

    private val pendingEvents = CopyOnWriteArrayList<AnonymousTelemetryEvent>()

    fun isTelemetryActive(): Boolean = consentAccepted

    fun setTelemetryConsent(accepted: Boolean) {
        consentAccepted = accepted
        if (!accepted) {
            pendingEvents.clear()
        }
    }

    /**
     * 1. `TOOL_EXECUTION_METRIC`: Herramienta ejecutada, duración ms, etapa del Protocolo v3.0 y éxito/fallo.
     */
    fun recordToolExecutionMetric(
        toolId: FenixToolId,
        durationMs: Long,
        protocolStage: String,
        succeeded: Boolean,
    ): Boolean = enqueueIfConsented(
        category = TelemetryCategory.TOOL_EXECUTION_METRIC,
        rawAttributes = mapOf(
            "tool_key" to toolId.wireKey,
            "duration_ms" to durationMs.toString(),
            "protocol_stage" to protocolStage,
            "succeeded" to succeeded.toString(),
        ),
    )

    /**
     * 2. `API_AND_ROUTER_METRIC`: Proveedor, modelo dinámico usado, latencia TTFT ms, HTTP 429/cooldown, slot usado.
     */
    fun recordApiAndRouterMetric(
        providerId: String,
        modelId: String,
        ttftLatencyMs: Long,
        httpStatusCode: Int,
        slotIndex: Int,
        enteredCooldown: Boolean,
    ): Boolean = enqueueIfConsented(
        category = TelemetryCategory.API_AND_ROUTER_METRIC,
        rawAttributes = mapOf(
            "provider_id" to providerId,
            "model_id" to modelId,
            "ttft_latency_ms" to ttftLatencyMs.toString(),
            "http_status" to httpStatusCode.toString(),
            "slot_index" to slotIndex.toString(),
            "entered_cooldown" to enteredCooldown.toString(),
        ),
    )

    /**
     * 3. `DOCUMENT_ENGINE_METRIC`: Páginas escaneadas, veredicto `PreIngestionGuardian`, distribución de umbrales
     * del matcher difuso y tiempo de exportación `.docx`/`.pdf`/`.pptx`/`.mp4`.
     */
    fun recordDocumentEngineMetric(
        scannedPagesCount: Int,
        preIngestionVerdict: String,
        highConfidenceMatches: Int,
        mediumConfidenceMatches: Int,
        orphanCount: Int,
        exportFormat: String,
        exportDurationMs: Long,
    ): Boolean = enqueueIfConsented(
        category = TelemetryCategory.DOCUMENT_ENGINE_METRIC,
        rawAttributes = mapOf(
            "scanned_pages" to scannedPagesCount.toString(),
            "pre_ingestion_verdict" to preIngestionVerdict,
            "matches_green" to highConfidenceMatches.toString(),
            "matches_amber" to mediumConfidenceMatches.toString(),
            "matches_orphan" to orphanCount.toString(),
            "export_format" to exportFormat,
            "export_duration_ms" to exportDurationMs.toString(),
        ),
    )

    /**
     * 4. `DEVICE_RESOURCE_HEALTH_METRIC`: Consumo RAM MB, nivel `onTrimMemory`, FPS/latencia UI y estado térmico/batería.
     */
    fun recordDeviceResourceHealthMetric(
        ramUsedMb: Int,
        trimMemoryLevel: Int,
        uiFrameP95Ms: Float,
        thermalStatusLabel: String,
    ): Boolean = enqueueIfConsented(
        category = TelemetryCategory.DEVICE_RESOURCE_HEALTH_METRIC,
        rawAttributes = mapOf(
            "ram_used_mb" to ramUsedMb.toString(),
            "trim_memory_level" to trimMemoryLevel.toString(),
            "ui_frame_p95_ms" to uiFrameP95Ms.toString(),
            "thermal_status" to thermalStatusLabel,
        ),
    )

    /**
     * 5. `NAVIGATION_AND_UX_METRIC`: Transiciones `P-01`..`P-10`, uso de accesibilidad `HighContrastOledAAA`,
     * TTS, LSA `[ 🤟 ]` y FSRS v6.
     */
    fun recordNavigationAndUxMetric(
        fromScreenCode: String,
        toScreenCode: String,
        activeThemeMode: String,
        ttsActive: Boolean,
        lsaPipActive: Boolean,
        fsrsCardsReviewed: Int,
    ): Boolean = enqueueIfConsented(
        category = TelemetryCategory.NAVIGATION_AND_UX_METRIC,
        rawAttributes = mapOf(
            "from_screen" to fromScreenCode,
            "to_screen" to toScreenCode,
            "theme_mode" to activeThemeMode,
            "tts_active" to ttsActive.toString(),
            "lsa_pip_active" to lsaPipActive.toString(),
            "fsrs_cards_reviewed" to fsrsCardsReviewed.toString(),
        ),
    )

    /**
     * 6. `SANITIZED_ERROR_REPORT`: Stacktrace técnico con sanitizador regex automático que redacta emails,
     * rutas de archivos personales y claves/tokens antes de encolar el reporte.
     */
    fun recordSanitizedErrorReport(
        moduleName: String,
        errorCode: String,
        rawTechnicalStacktrace: String,
    ): Boolean = enqueueIfConsented(
        category = TelemetryCategory.SANITIZED_ERROR_REPORT,
        rawAttributes = mapOf(
            "module_name" to moduleName,
            "error_code" to errorCode,
            "sanitized_stacktrace" to sanitizePiiAndSecrets(rawTechnicalStacktrace),
        ),
    )

    fun drainPendingEventsForUpload(): List<AnonymousTelemetryEvent> {
        if (!consentAccepted) {
            pendingEvents.clear()
            return emptyList()
        }
        val snapshot = pendingEvents.toList()
        pendingEvents.clear()
        return snapshot
    }

    fun peekPendingEvents(): List<AnonymousTelemetryEvent> =
        if (consentAccepted) pendingEvents.toList() else emptyList()

    private fun enqueueIfConsented(
        category: TelemetryCategory,
        rawAttributes: Map<String, String>,
    ): Boolean {
        if (!consentAccepted) return false
        val sanitizedMap = rawAttributes.mapValues { (_, v) -> sanitizePiiAndSecrets(v) }
        pendingEvents.add(
            AnonymousTelemetryEvent(
                category = category,
                attributes = sanitizedMap,
            ),
        )
        return true
    }

    companion object {
        private val EMAIL_REGEX = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")
        private val SECRET_KEY_REGEX = Regex("\\b(sk-[A-Za-z0-9_\\-]{8,}|AIza[A-Za-z0-9_\\-]{12,}|Bearer\\s+[A-Za-z0-9._\\-]+)")
        private val UNIX_USER_PATH_REGEX = Regex("(/home/|/Users/|/storage/emulated/0/)[^\\s:,\"]+")
        private val WINDOWS_USER_PATH_REGEX = Regex("[A-Za-z]:\\\\Users\\\\[^\\s:,\"]+")
        private val PERSONAL_DOC_FILENAME_REGEX = Regex("\\b[A-Za-z0-9_\\-]+\\.(pdf|docx|pptx|fenix)\\b", RegexOption.IGNORE_CASE)

        /**
         * Redacta cualquier dirección de correo, token API, ruta de directorio personal o nombre de documento
         * del estudiante para garantizar 0% de PII en los envíos de telemetría técnica.
         */
        fun sanitizePiiAndSecrets(rawText: String): String =
            rawText
                .replace(EMAIL_REGEX, "[REDACTED_EMAIL]")
                .replace(SECRET_KEY_REGEX, "[REDACTED_SECRET]")
                .replace(UNIX_USER_PATH_REGEX, "[REDACTED_PATH]")
                .replace(WINDOWS_USER_PATH_REGEX, "[REDACTED_WIN_PATH]")
                .replace(PERSONAL_DOC_FILENAME_REGEX, "[REDACTED_DOC_FILE]")
    }
}
