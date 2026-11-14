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
