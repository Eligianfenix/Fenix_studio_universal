/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:40:50 -03:00
 * ARCHIVO: feature/settings/src/main/kotlin/com/fenix/studio/feature/settings/SettingsAndByokVaultViewModel.kt
 * ================================================================================
 */

package com.fenix.studio.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fenix.studio.core.database.entity.ProviderModelEntity
import com.fenix.studio.core.designsystem.theme.FenixThemeMode
import com.fenix.studio.core.designsystem.theme.FenixTouchTargetProfile
import com.fenix.studio.core.network.catalog.AiProviderCatalog
import com.fenix.studio.core.network.catalog.ProviderConnectionProbe
import com.fenix.studio.core.network.discovery.DynamicModelDiscoveryService
import com.fenix.studio.core.security.archive.FenixPackageManifest
import com.fenix.studio.core.security.archive.FenixSovereignArchiveManager
import com.fenix.studio.core.security.keystore.AndroidHardwareKeyProvider
import com.fenix.studio.core.security.keystore.FenixKeystoreVault
import com.fenix.studio.core.security.keystore.JvmMemoryMasterKeyProvider
import com.fenix.studio.core.security.keystore.SharedPreferencesEncryptedSlotStore
import com.fenix.studio.core.security.quota.BetaTesterApplicationStatus
import com.fenix.studio.core.security.quota.RemoteUserPermissionsProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.OutputStream

/**
 * ViewModel para `P-03` (`P-03b..e`, incluyendo **Programa Fenix Beta Testers** y Telemetría Anónima)
 * y `P-04` (Bóveda BYOK y Proveedor `"Fénix IA"` activo de fábrica, `MF-4.3`, `MF-4.4`).
 */
class SettingsAndByokVaultViewModel(
    private val keystoreVault: FenixKeystoreVault = FenixKeystoreVault(JvmMemoryMasterKeyProvider()),
    private val archiveManager: FenixSovereignArchiveManager = FenixSovereignArchiveManager(),
    private val discoveryService: DynamicModelDiscoveryService? = null,
    private val providerProbe: ProviderConnectionProbe = ProviderConnectionProbe(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsAndVaultUiState())
    val uiState: StateFlow<SettingsAndVaultUiState> = _uiState.asStateFlow()

    init {
        refreshProviderCards()
    }

    fun onSelectThemeMode(mode: FenixThemeMode) {
        _uiState.update { current ->
            current.copy(
                accessibilityConfig = current.accessibilityConfig.copy(themeMode = mode),
            )
        }
    }

    fun onToggleOpenDyslexicFont(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                accessibilityConfig = current.accessibilityConfig.copy(useOpenDyslexicFont = enabled),
            )
        }
    }

    fun onSelectTouchTargetProfile(profile: FenixTouchTargetProfile) {
        _uiState.update { current ->
            current.copy(
                accessibilityConfig = current.accessibilityConfig.copy(touchTargetProfile = profile),
            )
        }
    }

    /**
     * Sincroniza desde `Fenix Admin` (Supabase) el perfil verificado de rol (`beta_tester` / `student_free`),
     * la aprobación explícita del creador (`isApprovedByCreatorInSupabase`) y la vigencia temporal.
     */
    fun onSyncBetaTesterProfile(
        profile: RemoteUserPermissionsProfile,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        val verifiedRole = if (!profile.supabaseUserId.isNullOrBlank() && profile.isApprovedByCreatorInSupabase) {
            profile.roleName
        } else {
            RemoteUserPermissionsProfile.ROLE_STUDENT_FREE
        }
        _uiState.update { current ->
            current.copy(
                supabaseUserId = profile.supabaseUserId,
                activeRoleName = verifiedRole,
                isApprovedByCreatorInSupabase = profile.isApprovedByCreatorInSupabase,
                isBetaTesterProgramEnabledByAdmin = profile.isBetaTesterProgramEnabledByAdmin,
                hasAcceptedAnonymousTelemetryConsent = profile.hasAcceptedAnonymousTelemetryConsent,
                betaTesterExpirationEpochMs = profile.betaTesterExpirationEpochMs,
                betaTesterDailyQuotaPerTool = profile.betaTesterDailyQuotaPerTool,
                betaTesterApplicationStatus = profile.resolveBetaTesterStatus(nowEpochMs),
                zeroTelemetryVerified = !profile.hasAcceptedAnonymousTelemetryConsent,
            )
        }
    }

    /**
     * Permite otorgar o revocar el consentimiento de telemetría técnica anónima (sin PII) para postularse
     * al **Programa Fenix Beta Testers**.
     * Regla 5 (Seguridad de Acceso): Este método NUNCA auto-atribuye el rol `"beta_tester"`; si el usuario no
     * fue aprobado en Supabase por el creador, permanece con rol `"student_free"` y estado `PENDING_ADMIN_APPROVAL`.
     */
    fun onToggleAnonymousTelemetryConsent(accepted: Boolean) {
        _uiState.update { current ->
            val nextStatus = when {
                !accepted -> BetaTesterApplicationStatus.NOT_APPLIED
                !current.supabaseUserId.isNullOrBlank() &&
                    current.isApprovedByCreatorInSupabase &&
                    current.activeRoleName == RemoteUserPermissionsProfile.ROLE_BETA_TESTER &&
                    current.isBetaTesterProgramEnabledByAdmin -> BetaTesterApplicationStatus.APPROVED_ACTIVE
                else -> BetaTesterApplicationStatus.PENDING_ADMIN_APPROVAL
            }
            current.copy(
                hasAcceptedAnonymousTelemetryConsent = accepted,
                betaTesterApplicationStatus = nextStatus,
                zeroTelemetryVerified = !accepted,
            )
        }
    }

    /**
     * Almacena una clave BYOK cifrada en `FenixKeystoreVault` en el slot `[0..5]` sin exponer el texto plano en el estado UI.
     */
    fun onSaveByokKeyInSlot(
        providerId: String,
        slotIndex: Int,
        plaintextKey: String,
    ) {
        val trimmed = plaintextKey.trim()
        if (trimmed.isEmpty()) return
        keystoreVault.storeApiKey(providerId, slotIndex, trimmed)
        val suffix = trimmed.takeLast(4).padStart(8, '*')
        refreshSlotsForProvider(providerId, updatedSlotIndex = slotIndex, maskedHint = suffix)
        refreshProviderCards()
        _uiState.update { it.copy(providerProbe = null) }
    }

    fun onRemoveByokKeySlot(providerId: String, slotIndex: Int) {
        keystoreVault.removeApiKeySlot(providerId, slotIndex)
        refreshSlotsForProvider(providerId, updatedSlotIndex = null, maskedHint = null)
        refreshProviderCards()
        _uiState.update { it.copy(providerProbe = null) }
    }

    /**
     * Prueba la conexión real contra el proveedor usando la clave de la ranura indicada.
     * No consume cuota de generación: consulta `GET /models`.
     */
    fun onProbeProvider(providerId: String, slotIndex: Int = 0) {
        val spec = AiProviderCatalog.byId(providerId) ?: return
        _uiState.update { it.copy(probingProviderId = providerId) }

        viewModelScope.launch {
            val apiKey = if (spec.keyless) {
                ""
            } else {
                keystoreVault.getDecryptedApiKey(providerId, slotIndex).orEmpty()
            }
            val result = providerProbe.probe(spec, apiKey)
            _uiState.update { current ->
                current.copy(
                    probingProviderId = null,
                    providerProbe = ProviderProbeUiModel(
                        providerId = providerId,
                        status = result.status,
                        message = result.message,
                        modelsFound = result.modelsFound,
                        sampleModels = result.sampleModels,
                    ),
                )
            }
        }
    }

    private fun refreshProviderCards() {
        val cards = AiProviderCatalog.providers.map { spec ->
            val configured = if (spec.keyless) emptyList() else {
                keystoreVault.listConfiguredSlots(spec.id).map { it.slotIndex }
            }
            ProviderCardUiModel(
                providerId = spec.id,
                displayName = spec.displayName,
                ringLabel = spec.ring.label,
                isKeyless = spec.keyless,
                configuredSlots = configured.size,
                configuredSlotIndices = configured,
                defaultModelId = spec.defaultModelId,
                notes = spec.notes,
                endpointVerified = spec.endpointVerified,
            )
        }
        _uiState.update { it.copy(providerCards = cards) }
    }

    suspend fun onTriggerDynamicModelDiscovery(
        providerId: String,
        baseUrl: String,
        slotIndex: Int = 0,
    ): List<ProviderModelEntity> {
        val service = discoveryService ?: return emptyList()
        val key = keystoreVault.getDecryptedApiKey(providerId, slotIndex).orEmpty()
        val discovered = service.discoverAndPersistModels(
            providerId = providerId,
            baseUrl = baseUrl,
            apiKey = key,
        )
        _uiState.update { it.copy(discoveredModels = discovered) }
        return discovered
    }

    fun onExportSovereignArchive(
        manifest: FenixPackageManifest,
        outputStream: OutputStream,
    ) {
        archiveManager.exportArchiveToStream(manifest, outputStream)
        _uiState.update { it.copy(lastExportedArchiveSanitized = manifest.isSanitizedForCommunity) }
    }

    private fun refreshSlotsForProvider(
        providerId: String,
        updatedSlotIndex: Int?,
        maskedHint: String?,
    ) {
        val configuredIndices = keystoreVault.listConfiguredSlots(providerId).map { it.slotIndex }.toSet()
        _uiState.update { current ->
            val updatedList = current.byokSlots.map { slot ->
                when {
                    slot.slotIndex !in configuredIndices ->
                        slot.copy(status = ByokSlotVisualStatus.EMPTY, maskedKeyHint = "Vacío")
                    slot.slotIndex == updatedSlotIndex && maskedHint != null ->
                        slot.copy(status = ByokSlotVisualStatus.HEALTHY, maskedKeyHint = maskedHint)
                    else ->
                        slot.copy(status = ByokSlotVisualStatus.HEALTHY)
                }
            }
            current.copy(byokSlots = updatedList)
        }
    }

    companion object {
        /**
         * Instancia de producción: llave maestra en el `AndroidKeyStore` real y sobres
         * cifrados persistidos en disco, para que las claves sobrevivan al cierre.
         */
        fun persistent(context: android.content.Context): SettingsAndByokVaultViewModel =
            SettingsAndByokVaultViewModel(
                keystoreVault = FenixKeystoreVault(
                    masterKeyProvider = AndroidHardwareKeyProvider(),
                    slotStore = SharedPreferencesEncryptedSlotStore(context.applicationContext),
                ),
            )
    }
}
