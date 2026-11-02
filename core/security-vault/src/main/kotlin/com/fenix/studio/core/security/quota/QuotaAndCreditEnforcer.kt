/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:38:30 -03:00
 * ARCHIVO: core/security-vault/src/main/kotlin/com/fenix/studio/core/security/quota/QuotaAndCreditEnforcer.kt
 * ================================================================================
 */

package com.fenix.studio.core.security.quota

import com.fenix.studio.core.security.keystore.FenixKeystoreVault
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Catálogo inicial de las 9 herramientas instrumentadas por `QuotaAndCreditEnforcer` (`RN-11`, `RN-12`).
 * Nota de arquitectura: Los valores `bootstrap*` actúan únicamente como *seed* inicial offline antes de
 * la primera sincronización; todos los límites, cuotas, costos en créditos, máximo de llamadas API
 * y modelos asignados son gobernables dinámicamente en vivo desde la segunda app **`Fenix Admin`**.
 */
enum class FenixToolId(
    val wireKey: String,
    val bootstrapDailyFreeQuota: Int,
    val bootstrapCreditCostAfterFreeQuota: Int,
    val bootstrapMaxApiCallsPerExecution: Int,
    val bootstrapMaxOutputTokens: Int,
) {
    INITIAL_CHAT("initial_chat", 50, 1, 3, 4_096),
    SYLLABUS_PROCESSOR("syllabus_processor", 5, 5, 50, 16_384),
    STANDALONE_DOCUMENT_PROCESSOR("standalone_doc_processor", 10, 2, 12, 8_192),
    GUIDED_PROJECT_PROCESSOR("guided_project_processor", 5, 4, 24, 12_288),
    FENIX_DOCS_EDITOR("fenix_docs_editor", 30, 1, 5, 8_192),
    COOPERATIVE_TUTOR_AGENT("cooperative_tutor_agent", 40, 1, 4, 4_096),
    DEEP_RESEARCH_AGENT("deep_research_agent", 10, 3, 15, 16_384),
    VIDEO_CAPSULE_GENERATOR("video_capsule_generator", 3, 5, 8, 8_192),
    PPTX_DECK_GENERATOR("pptx_deck_generator", 5, 3, 8, 8_192),
}

/**
 * Estado de postulación o membresía en el **Programa Fenix Beta Testers**.
 */
enum class BetaTesterApplicationStatus {
    NOT_APPLIED,
    PENDING_ADMIN_APPROVAL,
    APPROVED_ACTIVE,
    EXPIRED_OR_REVOKED,
}

/**
 * Regla remota de gobernanza por herramienta proveniente de `global_tool_configs` (`Fenix Admin`).
 * Permite modificar en caliente desde `Fenix Admin` si la herramienta está encendida, su cuota diaria gratuita,
 * el costo en créditos, el tope de llamadas API por ejecución, los tokens máximos, el modelo asignado
 * y la bandera de permiso requerida, sin actualizar la APK de Fenix Studio.
 */
@Serializable
data class RemoteToolConfigRule(
    val toolId: FenixToolId,
    val toolKey: String = toolId.wireKey,
    val isEnabled: Boolean = true,
    val dailyFreeLimit: Int = toolId.bootstrapDailyFreeQuota,
    val creditCostPerExtraUse: Int = toolId.bootstrapCreditCostAfterFreeQuota,
    val maxApiCallsPerExecution: Int = toolId.bootstrapMaxApiCallsPerExecution,
    val maxOutputTokens: Int = toolId.bootstrapMaxOutputTokens,
    val assignedModelId: String? = null,
    val requiredPermissionFlag: String? = null,
)

/**
 * Perfil dinámico de permisos y rol del usuario administrado desde `Fenix Admin` en Supabase.
 *
 * Regla Inviolable de Seguridad de Acceso (Regla 5):
 * Un usuario JAMÁS puede auto-atribuirse el rol `"beta_tester"` desde la interfaz local.
 * El privilegio del **Programa Fenix Beta Testers** exige obligatoriamente:
 * 1. Registro autenticado en Supabase (`!supabaseUserId.isNullOrBlank()`).
 * 2. Aprobación explícita otorgada por el creador en Supabase (`isApprovedByCreatorInSupabase == true` y `roleName == ROLE_BETA_TESTER`).
 * 3. Firma `HMAC-SHA256` local verificada por `FenixKeystoreVault`.
 * 4. Consentimiento activo de telemetría técnica anónima (`hasAcceptedAnonymousTelemetryConsent == true`) y vigencia temporal.
 */
@Serializable
data class RemoteUserPermissionsProfile(
    val userId: String = "anonymous_local",
    val supabaseUserId: String? = null,
    val roleName: String = ROLE_STUDENT_FREE,
    val isApprovedByCreatorInSupabase: Boolean = false,
    val isAccountActive: Boolean = true,
    val canAccessCreatorPool: Boolean = true,
    val grantedPermissions: Set<String> = emptySet(),
    val isBetaTesterProgramEnabledByAdmin: Boolean = false,
    val betaTesterExpirationEpochMs: Long = 0L,
    val betaTesterDailyQuotaPerTool: Int = 999,
    val hasAcceptedAnonymousTelemetryConsent: Boolean = false,
) {
    fun toCanonicalRolePayload(): String =
        "${supabaseUserId.orEmpty()}|$roleName|$isApprovedByCreatorInSupabase|$isBetaTesterProgramEnabledByAdmin|$betaTesterExpirationEpochMs|$betaTesterDailyQuotaPerTool"

    /**
     * Verifica todas las precondiciones de seguridad del **Programa Fenix Beta Testers**.
     */
    fun isEligibleForBetaTesterPrivilege(nowEpochMs: Long): Boolean =
        isAccountActive &&
            !supabaseUserId.isNullOrBlank() &&
            isApprovedByCreatorInSupabase &&
            roleName == ROLE_BETA_TESTER &&
            isBetaTesterProgramEnabledByAdmin &&
            hasAcceptedAnonymousTelemetryConsent &&
            nowEpochMs < betaTesterExpirationEpochMs

    fun resolveBetaTesterStatus(nowEpochMs: Long): BetaTesterApplicationStatus = when {
        isEligibleForBetaTesterPrivilege(nowEpochMs) -> BetaTesterApplicationStatus.APPROVED_ACTIVE
        roleName == ROLE_BETA_TESTER && isApprovedByCreatorInSupabase && nowEpochMs >= betaTesterExpirationEpochMs ->
            BetaTesterApplicationStatus.EXPIRED_OR_REVOKED
        hasAcceptedAnonymousTelemetryConsent && !supabaseUserId.isNullOrBlank() && !isApprovedByCreatorInSupabase ->
            BetaTesterApplicationStatus.PENDING_ADMIN_APPROVAL
        hasAcceptedAnonymousTelemetryConsent ->
            BetaTesterApplicationStatus.PENDING_ADMIN_APPROVAL
        else -> BetaTesterApplicationStatus.NOT_APPLIED
    }

    companion object {
        const val PROGRAM_OFFICIAL_NAME: String = "Programa Fenix Beta Testers"
        const val ROLE_STUDENT_FREE: String = "student_free"
        const val ROLE_BETA_TESTER: String = "beta_tester"
        const val ROLE_ADMIN: String = "admin"
    }
}

/**
 * Instantánea integral de gobernanza remota emitida por `Fenix Admin`.
 */
@Serializable
data class FenixAdminGovernanceSnapshot(
    val schemaVersion: Int = 1,
    val syncIntervalMs: Long = QuotaAndCreditEnforcer.BOOTSTRAP_SYNC_INTERVAL_MS,
    val toolRules: List<RemoteToolConfigRule>,
    val userPermissions: RemoteUserPermissionsProfile = RemoteUserPermissionsProfile(),
    val verifiedRemoteCredits: Int,
    val syncEpochMs: Long,
)

/**
 * Registro local firmado criptográficamente en hardware (`HMAC-SHA256`) para impedir manipulación
 * del contador diario o del saldo de créditos en el dispositivo.
 */
data class SignedQuotaLedgerEntry(
    val toolId: FenixToolId,
    val utcDayKey: String,
    val usedCountToday: Int,
    val remainingCredits: Int,
    val hmacSha256Signature: String,
) {
    fun toCanonicalPayload(): String =
        "${toolId.wireKey}|$utcDayKey|$usedCountToday|$remainingCredits"
}

/**
 * Resultado determinista de la verificación de acceso a una herramienta.
 */
sealed interface QuotaEnforcementDecision {
    data class AllowedFreeQuota(
        val toolId: FenixToolId,
        val usedTodayAfterExecution: Int,
        val dailyFreeLimit: Int,
        val maxApiCallsPerExecution: Int,
        val maxOutputTokens: Int,
        val assignedModelId: String?,
    ) : QuotaEnforcementDecision

    /**
     * Decisión otorgada exclusivamente a usuarios autenticados en Supabase y aprobados por el creador
     * en el **Programa Fenix Beta Testers** con consentimiento activo de telemetría técnica anónima.
     */
    data class AllowedBetaTesterPrivilege(
        val toolId: FenixToolId,
        val usedTodayAfterExecution: Int,
        val testerQuotaLimit: Int,
        val expiresAtEpochMs: Long,
        val maxApiCallsPerExecution: Int = 50,
        val maxOutputTokens: Int = 16_384,
        val assignedModelId: String? = null,
    ) : QuotaEnforcementDecision

    data class AllowedViaCredits(
        val toolId: FenixToolId,
        val creditsDeducted: Int,
        val remainingCreditsAfterExecution: Int,
        val maxApiCallsPerExecution: Int,
        val maxOutputTokens: Int,
        val assignedModelId: String?,
    ) : QuotaEnforcementDecision

    data class DeniedToolDisabledByAdmin(
        val toolId: FenixToolId,
    ) : QuotaEnforcementDecision

    data class DeniedByUserPermission(
        val toolId: FenixToolId,
        val missingPermissionOrReason: String,
    ) : QuotaEnforcementDecision

    data class DeniedQuotaExhausted(
        val toolId: FenixToolId,
        val dailyFreeLimit: Int,
        val requiredCredits: Int,
        val currentCredits: Int,
        val nextResetUtcLabel: String = "00:00 UTC",
    ) : QuotaEnforcementDecision

    data class DeniedLedgerTamperingDetected(
        val toolId: FenixToolId,
    ) : QuotaEnforcementDecision
}

/**
 * Guardián local de cuotas gratuitas, privilegios del **Programa Fenix Beta Testers**, créditos, modelos
 * por herramienta y permisos dinámicos preparado para administración en tiempo real desde **`Fenix Admin`** (`MF-1.3`, `RN-11`, `RN-12`).
 */
class QuotaAndCreditEnforcer(
    private val keystoreVault: FenixKeystoreVault,
) {
    private val remoteRules = ConcurrentHashMap<FenixToolId, RemoteToolConfigRule>()
    private val dynamicRulesByKey = ConcurrentHashMap<String, RemoteToolConfigRule>()
    private val signedLedgers = ConcurrentHashMap<FenixToolId, SignedQuotaLedgerEntry>()

    @Volatile
    private var userPermissionsProfile: RemoteUserPermissionsProfile = RemoteUserPermissionsProfile()

    @Volatile
    private var userPermissionsRoleSignature: String = signRoleProfile(userPermissionsProfile)

    @Volatile
    private var dynamicSyncIntervalMs: Long = BOOTSTRAP_SYNC_INTERVAL_MS

    @Volatile
    private var creditBalance: Int = 0

    @Volatile
    private var creditBalanceSignature: String = signCreditBalance(0)

    @Volatile
    private var lastRemoteSyncEpochMs: Long = 0L

    init {
        FenixToolId.entries.forEach { tool ->
            val bootstrapRule = RemoteToolConfigRule(toolId = tool)
            remoteRules[tool] = bootstrapRule
            dynamicRulesByKey[tool.wireKey] = bootstrapRule
        }
    }

    /**
     * Determina si corresponde ejecutar una sincronización periódica con Supabase,
     * respetando el intervalo dinámico gobernado desde `Fenix Admin` (`dynamicSyncIntervalMs`).
     */
    fun shouldRunPeriodicSync(nowEpochMs: Long): Boolean {
        if (lastRemoteSyncEpochMs <= 0L) return true
        return (nowEpochMs - lastRemoteSyncEpochMs) >= dynamicSyncIntervalMs
    }

    fun getCurrentSyncIntervalMs(): Long = dynamicSyncIntervalMs

    fun getActiveUserPermissionsProfile(): RemoteUserPermissionsProfile = userPermissionsProfile

    fun getEffectiveRuleForTool(toolId: FenixToolId): RemoteToolConfigRule =
        remoteRules[toolId] ?: RemoteToolConfigRule(toolId = toolId)

    /**
     * Registra únicamente el consentimiento local de telemetría técnica anónima (`P-01` / `P-03`).
     * Por seguridad de acceso (Regla 5), este método JAMÁS otorga ni modifica `roleName`,
     * `supabaseUserId` ni `isApprovedByCreatorInSupabase`.
     */
    @Synchronized
    fun updateUserTelemetryConsent(accepted: Boolean) {
        userPermissionsProfile = userPermissionsProfile.copy(
            hasAcceptedAnonymousTelemetryConsent = accepted,
        )
    }

    /**
     * Aplica en vivo una instantánea completa de gobernanza enviada desde `Fenix Admin` (Supabase)
     * y firma criptográficamente con `HMAC-SHA256` tanto el rol del usuario como el saldo de créditos.
     */
    @Synchronized
    fun applyAdminGovernanceSnapshot(snapshot: FenixAdminGovernanceSnapshot) {
        dynamicSyncIntervalMs = snapshot.syncIntervalMs.coerceAtLeast(MIN_SAFE_SYNC_INTERVAL_MS)
        userPermissionsProfile = snapshot.userPermissions
        userPermissionsRoleSignature = signRoleProfile(snapshot.userPermissions)
        snapshot.toolRules.forEach { rule ->
            remoteRules[rule.toolId] = rule
            dynamicRulesByKey[rule.toolKey] = rule
        }
        creditBalance = snapshot.verifiedRemoteCredits.coerceAtLeast(0)
        creditBalanceSignature = signCreditBalance(creditBalance)
        lastRemoteSyncEpochMs = snapshot.syncEpochMs
    }

    /**
     * Sobrecarga compatible para aplicar reglas actualizadas y saldo de créditos verificado.
     */
    @Synchronized
    fun applyRemoteSyncSnapshot(
        updatedRules: List<RemoteToolConfigRule>,
        verifiedRemoteCredits: Int,
        syncEpochMs: Long,
    ) {
        applyAdminGovernanceSnapshot(
            FenixAdminGovernanceSnapshot(
                syncIntervalMs = dynamicSyncIntervalMs,
                toolRules = updatedRules,
                userPermissions = userPermissionsProfile,
                verifiedRemoteCredits = verifiedRemoteCredits,
                syncEpochMs = syncEpochMs,
            ),
        )
    }

    /**
     * Evalúa integridad HMAC del rol y créditos, permisos de usuario (`Fenix Admin`), switch ON/OFF,
     * privilegio verificado del **Programa Fenix Beta Testers**, cuota diaria gratuita y créditos disponibles.
     */
    @Synchronized
    fun evaluateAndConsumeQuota(
        toolId: FenixToolId,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): QuotaEnforcementDecision {
        // 1. Verificar integridad del saldo global de créditos y del perfil de rol contra adulteración local
        if (!keystoreVault.verifyHmacSha256("CREDITS|$creditBalance", creditBalanceSignature)) {
            return QuotaEnforcementDecision.DeniedLedgerTamperingDetected(toolId)
        }
        val currentProfile = userPermissionsProfile
        if (!keystoreVault.verifyHmacSha256("ROLE|${currentProfile.toCanonicalRolePayload()}", userPermissionsRoleSignature)) {
            return QuotaEnforcementDecision.DeniedLedgerTamperingDetected(toolId)
        }

        // 2. Verificar estado de la cuenta y permisos dinámicos gobernados desde Fenix Admin
        if (!currentProfile.isAccountActive) {
            return QuotaEnforcementDecision.DeniedByUserPermission(
                toolId = toolId,
                missingPermissionOrReason = "Cuenta suspendida o desactivada desde Fenix Admin.",
            )
        }

        val rule = remoteRules[toolId] ?: RemoteToolConfigRule(toolId)
        if (!rule.isEnabled) {
            return QuotaEnforcementDecision.DeniedToolDisabledByAdmin(toolId)
        }

        val requiredPerm = rule.requiredPermissionFlag
        if (!requiredPerm.isNullOrBlank() && !currentProfile.grantedPermissions.contains(requiredPerm)) {
            return QuotaEnforcementDecision.DeniedByUserPermission(
                toolId = toolId,
                missingPermissionOrReason = "Se requiere el permiso '$requiredPerm' gobernado desde Fenix Admin.",
            )
        }

        val currentUtcDay = formatUtcDayKey(nowEpochMs)
        val existingEntry = signedLedgers[toolId]

        val currentUsedToday = if (existingEntry != null) {
            if (!verifyLedgerEntry(existingEntry)) {
                return QuotaEnforcementDecision.DeniedLedgerTamperingDetected(toolId)
            }
            if (existingEntry.utcDayKey == currentUtcDay) existingEntry.usedCountToday else 0
        } else {
            0
        }

        // 3. Privilegio del Programa Fenix Beta Testers (exige cuenta Supabase + aprobación del creador + telemetría aceptada + vigencia)
        if (currentProfile.isEligibleForBetaTesterPrivilege(nowEpochMs)) {
            val testerLimit = currentProfile.betaTesterDailyQuotaPerTool.coerceAtLeast(rule.dailyFreeLimit)
            if (currentUsedToday < testerLimit) {
                val nextUsed = currentUsedToday + 1
                signedLedgers[toolId] = createSignedEntry(toolId, currentUtcDay, nextUsed, creditBalance)
                return QuotaEnforcementDecision.AllowedBetaTesterPrivilege(
                    toolId = toolId,
                    usedTodayAfterExecution = nextUsed,
                    testerQuotaLimit = testerLimit,
                    expiresAtEpochMs = currentProfile.betaTesterExpirationEpochMs,
                    maxApiCallsPerExecution = rule.maxApiCallsPerExecution,
                    maxOutputTokens = rule.maxOutputTokens,
                    assignedModelId = rule.assignedModelId,
                )
            }
        }

        // 4. Dentro del cupo diario gratuito dinámico fijado desde Fenix Admin
        if (currentUsedToday < rule.dailyFreeLimit) {
            val nextUsed = currentUsedToday + 1
            signedLedgers[toolId] = createSignedEntry(toolId, currentUtcDay, nextUsed, creditBalance)
            return QuotaEnforcementDecision.AllowedFreeQuota(
                toolId = toolId,
                usedTodayAfterExecution = nextUsed,
                dailyFreeLimit = rule.dailyFreeLimit,
                maxApiCallsPerExecution = rule.maxApiCallsPerExecution,
                maxOutputTokens = rule.maxOutputTokens,
                assignedModelId = rule.assignedModelId,
            )
        }

        // 5. Superado el cupo gratuito: verificar créditos disponibles según el costo dinámico de Fenix Admin
        if (creditBalance >= rule.creditCostPerExtraUse) {
            val nextCredits = creditBalance - rule.creditCostPerExtraUse
            val nextUsed = currentUsedToday + 1
            creditBalance = nextCredits
            creditBalanceSignature = signCreditBalance(nextCredits)
            signedLedgers[toolId] = createSignedEntry(toolId, currentUtcDay, nextUsed, nextCredits)
            return QuotaEnforcementDecision.AllowedViaCredits(
                toolId = toolId,
                creditsDeducted = rule.creditCostPerExtraUse,
                remainingCreditsAfterExecution = nextCredits,
                maxApiCallsPerExecution = rule.maxApiCallsPerExecution,
                maxOutputTokens = rule.maxOutputTokens,
                assignedModelId = rule.assignedModelId,
            )
        }

        return QuotaEnforcementDecision.DeniedQuotaExhausted(
            toolId = toolId,
            dailyFreeLimit = rule.dailyFreeLimit,
            requiredCredits = rule.creditCostPerExtraUse,
            currentCredits = creditBalance,
        )
    }

    fun restoreLedgerEntryFromStorage(entry: SignedQuotaLedgerEntry) {
        signedLedgers[entry.toolId] = entry
    }

    fun getSignedLedgerEntry(toolId: FenixToolId): SignedQuotaLedgerEntry? = signedLedgers[toolId]

    fun verifyLedgerEntry(entry: SignedQuotaLedgerEntry): Boolean =
        keystoreVault.verifyHmacSha256(entry.toCanonicalPayload(), entry.hmacSha256Signature)

    private fun createSignedEntry(
        toolId: FenixToolId,
        utcDayKey: String,
        usedCountToday: Int,
        remainingCredits: Int,
    ): SignedQuotaLedgerEntry {
        val canonical = "${toolId.wireKey}|$utcDayKey|$usedCountToday|$remainingCredits"
        val signature = keystoreVault.computeHmacSha256Base64(canonical)
        return SignedQuotaLedgerEntry(
            toolId = toolId,
            utcDayKey = utcDayKey,
            usedCountToday = usedCountToday,
            remainingCredits = remainingCredits,
            hmacSha256Signature = signature,
        )
    }

    private fun signCreditBalance(credits: Int): String =
        keystoreVault.computeHmacSha256Base64("CREDITS|$credits")

    private fun signRoleProfile(profile: RemoteUserPermissionsProfile): String =
        keystoreVault.computeHmacSha256Base64("ROLE|${profile.toCanonicalRolePayload()}")

    companion object {
        /**
         * Valor bootstrap inicial de sincronización (6 horas = 4x/día) antes de recibir
         * la configuración dinámica gobernada desde `Fenix Admin`.
         */
        const val BOOTSTRAP_SYNC_INTERVAL_MS: Long = 6L * 60L * 60L * 1000L
        const val PERIODIC_SYNC_INTERVAL_MS: Long = BOOTSTRAP_SYNC_INTERVAL_MS
        private const val MIN_SAFE_SYNC_INTERVAL_MS: Long = 60_000L

        fun formatUtcDayKey(epochMs: Long): String {
            val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC)
            return DateTimeFormatter.ISO_LOCAL_DATE.format(zdt)
        }
    }
}
