/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:53:20 -03:00
 * ARCHIVO: feature/settings/src/test/kotlin/com/fenix/studio/feature/settings/SettingsAndByokVaultViewModelTest.kt
 * ================================================================================
 */

package com.fenix.studio.feature.settings

import com.fenix.studio.core.designsystem.theme.FenixThemeMode
import com.fenix.studio.core.designsystem.theme.FenixTouchTargetProfile
import com.fenix.studio.core.network.model.AiProviderConfig
import com.fenix.studio.core.security.keystore.FenixKeystoreVault
import com.fenix.studio.core.security.keystore.JvmMemoryMasterKeyProvider
import com.fenix.studio.core.security.quota.BetaTesterApplicationStatus
import com.fenix.studio.core.security.quota.RemoteUserPermissionsProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsAndByokVaultViewModelTest {

    @Test
    fun `default provider is Fenix IA Assisted Free Mode RN-25 and passive sandbox uses 0 MB`() {
        val viewModel = SettingsAndByokVaultViewModel()
        val state = viewModel.uiState.value

        assertEquals(AiProviderConfig.DEFAULT_FENIX_IA_PROVIDER_ID, state.activeProvider.providerId)
        assertTrue(state.activeProvider.isDefaultAssistedPool)
        assertEquals(0, state.passiveSandboxState.activeRamUsageMb)
        assertFalse(state.passiveSandboxState.isRunning)
    }

    @Test
    fun `storing 6 BYOK keys encrypts in vault and never leaks plaintext into UI state`() {
        val vault = FenixKeystoreVault(JvmMemoryMasterKeyProvider())
        val viewModel = SettingsAndByokVaultViewModel(keystoreVault = vault)
        val rawSecret = "sk-live-super-secret-value-9876"

        viewModel.onSaveByokKeyInSlot(
            providerId = "custom_openai_compatible",
            slotIndex = 2,
            plaintextKey = rawSecret,
        )

        val slotUi = viewModel.uiState.value.byokSlots[2]
        assertEquals(ByokSlotVisualStatus.HEALTHY, slotUi.status)
        assertFalse(slotUi.maskedKeyHint.contains("super-secret"))
        assertTrue(slotUi.maskedKeyHint.endsWith("9876"))
        assertEquals(rawSecret, vault.getDecryptedApiKey("custom_openai_compatible", 2))
    }

    @Test
    fun `accessibility mode and Programa Fenix Beta Testers prevent self-attribution until Supabase approval`() {
        val viewModel = SettingsAndByokVaultViewModel()
        viewModel.onSelectThemeMode(FenixThemeMode.HIGH_CONTRAST_OLED)
        viewModel.onToggleOpenDyslexicFont(true)
        viewModel.onSelectTouchTargetProfile(FenixTouchTargetProfile.MACRO_LARGE)

        val config = viewModel.uiState.value.accessibilityConfig
        assertEquals(FenixThemeMode.HIGH_CONTRAST_OLED, config.themeMode)
        assertTrue(config.useOpenDyslexicFont)
        assertEquals(FenixTouchTargetProfile.MACRO_LARGE, config.touchTargetProfile)

        // Si el usuario acepta telemetría localmente sin estar aprobado por el creador en Supabase, NO obtiene beta_tester
        viewModel.onToggleAnonymousTelemetryConsent(true)
        assertEquals(RemoteUserPermissionsProfile.ROLE_STUDENT_FREE, viewModel.uiState.value.activeRoleName)
        assertEquals(BetaTesterApplicationStatus.PENDING_ADMIN_APPROVAL, viewModel.uiState.value.betaTesterApplicationStatus)

        // Cuando Supabase sincroniza el perfil aprobado por el creador, recibe el rol beta_tester
        viewModel.onSyncBetaTesterProfile(
            profile = RemoteUserPermissionsProfile(
                userId = "usr-beta-01",
                supabaseUserId = "supa-approved-99",
                roleName = RemoteUserPermissionsProfile.ROLE_BETA_TESTER,
                isApprovedByCreatorInSupabase = true,
                isBetaTesterProgramEnabledByAdmin = true,
                betaTesterExpirationEpochMs = 1_800_000_000_000L,
                betaTesterDailyQuotaPerTool = 500,
                hasAcceptedAnonymousTelemetryConsent = true,
            ),
            nowEpochMs = 1_730_000_000_000L,
        )
        assertEquals(RemoteUserPermissionsProfile.ROLE_BETA_TESTER, viewModel.uiState.value.activeRoleName)
        assertEquals(BetaTesterApplicationStatus.APPROVED_ACTIVE, viewModel.uiState.value.betaTesterApplicationStatus)
        assertTrue(viewModel.uiState.value.hasAcceptedAnonymousTelemetryConsent)
        assertFalse(viewModel.uiState.value.zeroTelemetryVerified)
    }
}
