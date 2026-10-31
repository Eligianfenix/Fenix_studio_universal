/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:40:35 -03:00
 * ARCHIVO: feature/settings/src/main/kotlin/com/fenix/studio/feature/settings/SettingsAndVaultModels.kt
 * ================================================================================
 */

package com.fenix.studio.feature.settings

import androidx.compose.runtime.Immutable
import com.fenix.studio.core.database.entity.ProviderModelEntity
import com.fenix.studio.core.designsystem.theme.FenixAccessibilityConfig
import com.fenix.studio.core.network.model.AiProviderConfig
import com.fenix.studio.core.security.quota.BetaTesterApplicationStatus
import com.fenix.studio.core.security.quota.RemoteUserPermissionsProfile

/**
 * Estado visual de salud de cada una de las 6 ranuras en la Bóveda BYOK (`P-04`).
 */
enum class ByokSlotVisualStatus {
    HEALTHY,
    COOLDOWN,
    EMPTY,
}

/**
 * Representación de una ranura (`0..5`) en `P-04` sin exponer jamás la clave en texto plano.
 */
@Immutable
data class ByokSlotUiModel(
    val slotIndex: Int,
    val status: ByokSlotVisualStatus,
    val maskedKeyHint: String = "Vacío",
)

/**
 * Estado del Entorno Linux Pasivo (`P-03d`): 0 MB de RAM consumidos en reposo hasta invocación explícita.
 */
@Immutable
data class PassiveLinuxSandboxState(
    val isRunning: Boolean = false,
    val activeRamUsageMb: Int = 0,
)

/**
 * Estado unificado de `P-03` (`P-03b` Accesibilidad, `P-03c` Permisos/MCP y **Programa Fenix Beta Testers**,
 * `P-03d` Sandbox Pasivo, `P-03e` Respaldo Soberano `.fenix` y Cuotas) y `P-04` (Bóveda Criptográfica BYOK y Selector `"Fénix IA"`).
 */
@Immutable
data class SettingsAndVaultUiState(
    val accessibilityConfig: FenixAccessibilityConfig = FenixAccessibilityConfig(),
    val ttsSpeechRateMultiplier: Float = 1.0f,
    val isLsaPipEnabled: Boolean = true,
    val zeroTelemetryVerified: Boolean = true,
    val supabaseUserId: String? = null,
    val activeRoleName: String = RemoteUserPermissionsProfile.ROLE_STUDENT_FREE,
    val isApprovedByCreatorInSupabase: Boolean = false,
    val isBetaTesterProgramEnabledByAdmin: Boolean = false,
    val hasAcceptedAnonymousTelemetryConsent: Boolean = false,
    val betaTesterExpirationEpochMs: Long = 0L,
    val betaTesterDailyQuotaPerTool: Int = 999,
    val betaTesterApplicationStatus: BetaTesterApplicationStatus = BetaTesterApplicationStatus.NOT_APPLIED,
    val passiveSandboxState: PassiveLinuxSandboxState = PassiveLinuxSandboxState(isRunning = false, activeRamUsageMb = 0),
    val activeProvider: AiProviderConfig = AiProviderConfig.createDefaultFenixIaProvider(),
    val byokSlots: List<ByokSlotUiModel> = (0 until 6).map { idx ->
        ByokSlotUiModel(slotIndex = idx, status = ByokSlotVisualStatus.EMPTY)
    },
    val discoveredModels: List<ProviderModelEntity> = emptyList(),
    val lastExportedArchiveSanitized: Boolean? = null,
)
