/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:39:30 -03:00
 * ARCHIVO: feature/auth/src/main/kotlin/com/fenix/studio/feature/auth/OnboardingAuthState.kt
 * ================================================================================
 */

package com.fenix.studio.feature.auth

import androidx.compose.runtime.Immutable
import com.fenix.studio.core.security.quota.BetaTesterApplicationStatus
import com.fenix.studio.core.security.quota.RemoteUserPermissionsProfile

/**
 * Modalidades de ingreso en `P-01` (`RN-01`).
 * El modo local invitado (`SOVEREIGN_LOCAL_GUEST_100_FUNCTIONAL`) ofrece el 100% de las funciones
 * sin obligar al estudiante a crear una cuenta en la nube.
 */
enum class AuthEntryMode(val displayLabel: String) {
    SOVEREIGN_LOCAL_GUEST_100_FUNCTIONAL("Ingreso Local Soberano (100% Funcional sin Cuenta)"),
    OPTIONAL_CLOUD_SYNC("Sincronización Opcional en la Nube"),
}

/**
 * Estado inmutable de la pantalla `P-01` (Bienvenida, Accesibilidad Inicial, Contrato TyC v1.1 `RN-16`/`RN-17`
 * y postulación al **Programa Fenix Beta Testers** sujeta a cuenta autenticada en Supabase y aprobación del creador).
 */
@Immutable
data class OnboardingAuthState(
    val currentRequiredTycVersion: String = CURRENT_TYC_VERSION,
    val hasScrolledToBottomOfTyc: Boolean = false,
    val acceptedTycVersion: String? = null,
    val acceptedTimestampEpochMs: Long? = null,
    val deontologicalNoticeAccepted: Boolean = false,
    val selectedEntryMode: AuthEntryMode = AuthEntryMode.SOVEREIGN_LOCAL_GUEST_100_FUNCTIONAL,
    val hasCompletedOnboarding: Boolean = false,
    val supabaseUserId: String? = null,
    val activeRoleName: String = RemoteUserPermissionsProfile.ROLE_STUDENT_FREE,
    val isApprovedByCreatorInSupabase: Boolean = false,
    val isBetaTesterProgramOpen: Boolean = false,
    val hasAcceptedAnonymousTelemetryConsent: Boolean = false,
    val betaTesterExpirationEpochMs: Long = 0L,
    val betaTesterApplicationStatus: BetaTesterApplicationStatus = BetaTesterApplicationStatus.NOT_APPLIED,
) {
    /**
     * Habilita el avance hacia `P-02` únicamente cuando el estudiante leyó el contrato,
     * aceptó la versión vigente (`CURRENT_TYC_VERSION = "1.1"`) y el aviso deontológico (`RN-16`, `RN-17`).
     */
    val isProceedEnabled: Boolean
        get() = hasScrolledToBottomOfTyc &&
            acceptedTycVersion == currentRequiredTycVersion &&
            deontologicalNoticeAccepted

    /**
     * El privilegio activo del **Programa Fenix Beta Testers** exige cuenta registrada en Supabase,
     * aprobación remota del creador (`isApprovedByCreatorInSupabase == true`), rol `"beta_tester"`
     * y consentimiento activo de telemetría técnica anónima.
     */
    val isBetaTesterPrivilegeActive: Boolean
        get() = !supabaseUserId.isNullOrBlank() &&
            isApprovedByCreatorInSupabase &&
            activeRoleName == RemoteUserPermissionsProfile.ROLE_BETA_TESTER &&
            isBetaTesterProgramOpen &&
            hasAcceptedAnonymousTelemetryConsent &&
            betaTesterExpirationEpochMs > 0L

    companion object {
        const val CURRENT_TYC_VERSION: String = "1.1"
    }
}
