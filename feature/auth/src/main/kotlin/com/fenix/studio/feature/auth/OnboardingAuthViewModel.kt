/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-01 19:30:00 -03:00
 * ARCHIVO: feature/auth/src/main/kotlin/com/fenix/studio/feature/auth/OnboardingAuthViewModel.kt
 * ================================================================================
 */

package com.fenix.studio.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fenix.studio.core.network.supabase.SupabaseAuthAndDataGateway
import com.fenix.studio.core.security.quota.BetaTesterApplicationStatus
import com.fenix.studio.core.security.quota.RemoteUserPermissionsProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel determinista para `P-01` (`MF-4.1`).
 * Garantiza el cumplimiento de `RN-01` (ingreso local 100% funcional sin cuenta obligatoria),
 * `RN-16` (bloqueo y re-aceptación ante cambio de versión de TyC), `RN-17` (descargo deontológico y Ley 25.326),
 * autenticación opcional en Supabase y postulación al **Programa Fenix Beta Testers** sin permitir
 * jamás la auto-atribución local del rol `"beta_tester"`.
 */
class OnboardingAuthViewModel(
    initialStoredTycVersion: String? = null,
    private val supabaseGateway: SupabaseAuthAndDataGateway? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnboardingAuthState(
            hasScrolledToBottomOfTyc = initialStoredTycVersion == OnboardingAuthState.CURRENT_TYC_VERSION,
            acceptedTycVersion = initialStoredTycVersion,
            deontologicalNoticeAccepted = initialStoredTycVersion == OnboardingAuthState.CURRENT_TYC_VERSION,
        ),
    )
    val uiState: StateFlow<OnboardingAuthState> = _uiState.asStateFlow()

    fun onTycScrolledToBottom() {
        _uiState.update { it.copy(hasScrolledToBottomOfTyc = true) }
    }

    fun onToggleTycAcceptance(
        checked: Boolean,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        _uiState.update { current ->
            if (!current.hasScrolledToBottomOfTyc && checked) {
                current
            } else {
                current.copy(
                    acceptedTycVersion = if (checked) current.currentRequiredTycVersion else null,
                    acceptedTimestampEpochMs = if (checked) nowEpochMs else null,
                    deontologicalNoticeAccepted = checked,
                )
            }
        }
    }

    fun onSelectEntryMode(mode: AuthEntryMode) {
        _uiState.update { it.copy(selectedEntryMode = mode) }
    }

    fun onEmailInputChanged(email: String) {
        _uiState.update { it.copy(emailInput = email, authErrorMessage = null) }
    }

    fun onPasswordInputChanged(password: String) {
        _uiState.update { it.copy(passwordInput = password, authErrorMessage = null) }
    }

    fun onFullNameInputChanged(fullName: String) {
        _uiState.update { it.copy(fullNameInput = fullName, authErrorMessage = null) }
    }

    fun onToggleSignUpMode(isSignUp: Boolean) {
        _uiState.update { it.copy(isSignUpMode = isSignUp, authErrorMessage = null) }
    }

    fun clearAuthMessages() {
        _uiState.update { it.copy(authErrorMessage = null, authSuccessMessage = null) }
    }

    /**
     * Inicia sesión en Supabase y sincroniza perfil y créditos.
     */
    fun signInWithSupabase(deviceId: String = "") {
        val gateway = supabaseGateway ?: run {
            _uiState.update { it.copy(authErrorMessage = "Servicio de autenticación no inicializado.") }
            return
        }
        val email = _uiState.value.emailInput.trim()
        val pass = _uiState.value.passwordInput.trim()

        if (email.isBlank() || pass.isBlank()) {
            _uiState.update { it.copy(authErrorMessage = "Por favor ingrese email y contraseña.") }
            return
        }

        _uiState.update { it.copy(isAuthenticating = true, authErrorMessage = null, authSuccessMessage = null) }

        viewModelScope.launch {
            gateway.signInWithPassword(email, pass).fold(
                onSuccess = { session ->
                    val userId = session.user?.id ?: ""
                    val token = session.access_token

                    gateway.fetchProfile(token, userId).fold(
                        onSuccess = { profile ->
                            if (deviceId.isNotBlank() && profile.device_id.isNullOrBlank()) {
                                gateway.bindDevice(token, userId, deviceId)
                            }

                            val roleName = when {
                                profile.role == "admin" -> RemoteUserPermissionsProfile.ROLE_ADMIN
                                profile.user_type == "beta_tester" -> RemoteUserPermissionsProfile.ROLE_BETA_TESTER
                                else -> RemoteUserPermissionsProfile.ROLE_STUDENT_FREE
                            }

                            _uiState.update { current ->
                                current.copy(
                                    isAuthenticating = false,
                                    supabaseUserId = userId,
                                    activeRoleName = roleName,
                                    credits = profile.credits,
                                    userType = profile.user_type,
                                    isApprovedByCreatorInSupabase = profile.role == "admin" || profile.user_type == "beta_tester" || profile.credits > 0,
                                    isBetaTesterProgramOpen = true,
                                    authSuccessMessage = "Sesión iniciada con éxito (${profile.credits} créditos disponibles).",
                                    authErrorMessage = null,
                                )
                            }
                        },
                        onFailure = {
                            _uiState.update {
                                it.copy(
                                    isAuthenticating = false,
                                    supabaseUserId = userId,
                                    authSuccessMessage = "Sesión iniciada.",
                                    authErrorMessage = null,
                                )
                            }
                        }
                    )
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isAuthenticating = false,
                            authErrorMessage = "Error al iniciar sesión: ${err.message ?: "Credenciales inválidas"}",
                        )
                    }
                }
            )
        }
    }

    /**
     * Registra un nuevo usuario en Supabase con modo normal o beta tester.
     */
    fun signUpWithSupabase(isBeta: Boolean = false, deviceId: String = "") {
        val gateway = supabaseGateway ?: run {
            _uiState.update { it.copy(authErrorMessage = "Servicio de autenticación no inicializado.") }
            return
        }
        val email = _uiState.value.emailInput.trim()
        val pass = _uiState.value.passwordInput.trim()
        val name = _uiState.value.fullNameInput.trim().ifEmpty { "Estudiante Fénix" }

        if (email.isBlank() || pass.isBlank()) {
            _uiState.update { it.copy(authErrorMessage = "Por favor complete email y contraseña.") }
            return
        }

        _uiState.update { it.copy(isAuthenticating = true, authErrorMessage = null, authSuccessMessage = null) }

        viewModelScope.launch {
            gateway.signUp(email, pass, name, isBeta).fold(
                onSuccess = { session ->
                    val userId = session.user?.id ?: ""
                    val token = session.access_token

                    if (token.isNotBlank() && userId.isNotBlank()) {
                        gateway.fetchProfile(token, userId).fold(
                            onSuccess = { profile ->
                                if (deviceId.isNotBlank()) {
                                    gateway.bindDevice(token, userId, deviceId)
                                }
                                val roleName = if (isBeta) RemoteUserPermissionsProfile.ROLE_BETA_TESTER else RemoteUserPermissionsProfile.ROLE_STUDENT_FREE
                                _uiState.update { current ->
                                    current.copy(
                                        isAuthenticating = false,
                                        supabaseUserId = userId,
                                        activeRoleName = roleName,
                                        credits = profile.credits,
                                        userType = profile.user_type,
                                        isApprovedByCreatorInSupabase = isBeta,
                                        authSuccessMessage = "¡Cuenta creada exitosamente! Créditos asignados: ${profile.credits}.",
                                        authErrorMessage = null,
                                    )
                                }
                            },
                            onFailure = {
                                _uiState.update {
                                    it.copy(
                                        isAuthenticating = false,
                                        supabaseUserId = userId,
                                        authSuccessMessage = "Cuenta creada.",
                                        authErrorMessage = null,
                                    )
                                }
                            }
                        )
                    } else {
                        _uiState.update {
                            it.copy(
                                isAuthenticating = false,
                                authSuccessMessage = "Registro completado exitosamente.",
                                authErrorMessage = null,
                            )
                        }
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isAuthenticating = false,
                            authErrorMessage = "Error en el registro: ${err.message ?: "Verifique los datos ingresados"}",
                        )
                    }
                }
            )
        }
    }

    /**
     * Sincroniza el perfil autenticado proveniente de Supabase (`Fenix Admin`).
     * El rol `"beta_tester"` únicamente puede ingresar desde un perfil aprobado por el creador en Supabase.
     */
    fun onSyncVerifiedSupabaseProfile(
        profile: RemoteUserPermissionsProfile,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        _uiState.update {
            val verifiedRole = if (!profile.supabaseUserId.isNullOrBlank() && profile.isApprovedByCreatorInSupabase) {
                profile.roleName
            } else {
                RemoteUserPermissionsProfile.ROLE_STUDENT_FREE
            }
            it.copy(
                supabaseUserId = profile.supabaseUserId,
                activeRoleName = verifiedRole,
                isApprovedByCreatorInSupabase = profile.isApprovedByCreatorInSupabase,
                isBetaTesterProgramOpen = profile.isBetaTesterProgramEnabledByAdmin,
                hasAcceptedAnonymousTelemetryConsent = profile.hasAcceptedAnonymousTelemetryConsent,
                betaTesterExpirationEpochMs = profile.betaTesterExpirationEpochMs,
                betaTesterApplicationStatus = profile.resolveBetaTesterStatus(nowEpochMs),
            )
        }
    }

    /**
     * Registra la aceptación o revocación del consentimiento de telemetría técnica anónima (sin datos personales)
     * para postularse al **Programa Fenix Beta Testers**.
     * Regla 5 (Seguridad de Acceso): Este método NUNCA auto-atribuye el rol `"beta_tester"`; si el usuario no
     * cuenta con aprobación remota del creador en Supabase, queda en estado `PENDING_ADMIN_APPROVAL`.
     */
    fun onToggleBetaTesterTelemetryConsent(accepted: Boolean) {
        _uiState.update { current ->
            val nextStatus = when {
                !accepted -> BetaTesterApplicationStatus.NOT_APPLIED
                !current.supabaseUserId.isNullOrBlank() &&
                    current.isApprovedByCreatorInSupabase &&
                    current.activeRoleName == RemoteUserPermissionsProfile.ROLE_BETA_TESTER &&
                    current.isBetaTesterProgramOpen -> BetaTesterApplicationStatus.APPROVED_ACTIVE
                else -> BetaTesterApplicationStatus.PENDING_ADMIN_APPROVAL
            }
            current.copy(
                hasAcceptedAnonymousTelemetryConsent = accepted,
                betaTesterApplicationStatus = nextStatus,
            )
        }
    }

    /**
     * Si el contrato legal se actualiza a una nueva versión (`RN-16`), invalida la aceptación previa
     * hasta que el estudiante lea y acepte la nueva versión.
     */
    fun onRequiredTycVersionUpdated(newRequiredVersion: String) {
        _uiState.update { current ->
            val stillValid = current.acceptedTycVersion == newRequiredVersion
            current.copy(
                currentRequiredTycVersion = newRequiredVersion,
                hasScrolledToBottomOfTyc = stillValid,
                deontologicalNoticeAccepted = stillValid,
                hasCompletedOnboarding = stillValid && current.hasCompletedOnboarding,
            )
        }
    }

    fun onProceedToHomeChat(): Boolean {
        val current = _uiState.value
        if (!current.isProceedEnabled) return false
        _uiState.update { it.copy(hasCompletedOnboarding = true) }
        return true
    }
}
