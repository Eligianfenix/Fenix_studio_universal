/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:39:10 -03:00
 * ARCHIVO: core/security-vault/src/test/kotlin/com/fenix/studio/core/security/SecurityVaultAndQuotaEnforcerTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.security

import com.fenix.studio.core.security.archive.FenixPackageManifest
import com.fenix.studio.core.security.archive.FenixSovereignArchiveManager
import com.fenix.studio.core.security.keystore.FenixKeystoreVault
import com.fenix.studio.core.security.keystore.JvmMemoryMasterKeyProvider
import com.fenix.studio.core.security.quota.BetaTesterApplicationStatus
import com.fenix.studio.core.security.quota.FenixAdminGovernanceSnapshot
import com.fenix.studio.core.security.quota.FenixToolId
import com.fenix.studio.core.security.quota.QuotaAndCreditEnforcer
import com.fenix.studio.core.security.quota.QuotaEnforcementDecision
import com.fenix.studio.core.security.quota.RemoteToolConfigRule
import com.fenix.studio.core.security.quota.RemoteUserPermissionsProfile
import com.fenix.studio.core.security.telemetry.AnonymousTelemetryManager
import com.fenix.studio.core.security.telemetry.TelemetryCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.AEADBadTagException

class SecurityVaultAndQuotaEnforcerTest {

    @Test
    fun `FenixKeystoreVault encrypts and decrypts 6 BYOK slots with AES-256-GCM and rejects AAD substitution`() {
        val vault = FenixKeystoreVault(JvmMemoryMasterKeyProvider())

        for (slotIdx in 0 until FenixKeystoreVault.MAX_SLOTS_PER_PROVIDER) {
            val rawSecret = "test_byok_secret_slot_$slotIdx"
            val encrypted = vault.storeApiKey("gemini", slotIdx, rawSecret)

            assertFalse(encrypted.toString().contains(rawSecret), "toString() must redact plaintext keys")
            assertFalse(encrypted.ciphertextBase64.contains(rawSecret))
            assertEquals(rawSecret, vault.getDecryptedApiKey("gemini", slotIdx))
        }

        assertEquals(6, vault.listConfiguredSlots("gemini").size)

        val slot0 = vault.listConfiguredSlots("gemini").first { it.slotIndex == 0 }
        assertThrows(AEADBadTagException::class.java) {
            vault.decryptApiKey(slot0, expectedProviderId = "gemini", expectedSlotIndex = 1)
        }
    }

    @Test
    fun `FenixSovereignArchiveManager streams encrypted fenix packages and sanitizes private notes for community`() {
        val keyProvider = JvmMemoryMasterKeyProvider()
        val archiveKey = keyProvider.getOrCreateAes256Key("archive_test_key")
        val manager = FenixSovereignArchiveManager()

        val rawManifest = FenixPackageManifest(
            catedraId = "cat-900",
            displayTitle = "Cátedra General de Prueba",
            modalityName = "CATEDRA_PROGRAMA",
            programaYear = 2026,
            simHash64 = 987654321L,
            isSanitizedForCommunity = true,
            ownerUserId = "student-private-id-42",
            privateUserNotesJson = """[{"note":"Apunte personal privado"}]""",
            protocolBlocksJson = """[{"blockId":"b1","text":"Síntesis verificada"}]""",
            exportedAtEpochMs = 1_700_000_000_000L,
        )

        val output = ByteArrayOutputStream()
        manager.exportArchiveToStream(rawManifest, output, optionalAesKey = archiveKey)

        val restored = manager.importArchiveFromStream(
            ByteArrayInputStream(output.toByteArray()),
            optionalAesKey = archiveKey,
        )

        assertEquals("cat-900", restored.catedraId)
        assertTrue(restored.isSanitizedForCommunity)
        assertNull(restored.ownerUserId)
        assertNull(restored.privateUserNotesJson)
        assertTrue(restored.protocolBlocksJson.contains("Síntesis verificada"))
    }

    @Test
    fun `QuotaAndCreditEnforcer enforces free daily quota credit fallback HMAC tamper detection and dynamic Fenix Admin governance`() {
        val vault = FenixKeystoreVault(JvmMemoryMasterKeyProvider())
        val enforcer = QuotaAndCreditEnforcer(vault)
        val nowMs = 1_730_000_000_000L

        assertTrue(enforcer.shouldRunPeriodicSync(nowMs))

        val customSyncIntervalMs = 2L * 60L * 60L * 1000L
        val adminSnapshot = FenixAdminGovernanceSnapshot(
            schemaVersion = 2,
            syncIntervalMs = customSyncIntervalMs,
            toolRules = listOf(
                RemoteToolConfigRule(
                    toolId = FenixToolId.VIDEO_CAPSULE_GENERATOR,
                    isEnabled = true,
                    dailyFreeLimit = 1,
                    creditCostPerExtraUse = 5,
                    maxApiCallsPerExecution = 14,
                    maxOutputTokens = 12_000,
                    assignedModelId = "admin-governed-model-v3",
                    requiredPermissionFlag = "perm_video_capsule",
                ),
            ),
            userPermissions = RemoteUserPermissionsProfile(
                userId = "user-101",
                roleName = "scholarship_verified",
                isAccountActive = true,
                canAccessCreatorPool = true,
                grantedPermissions = setOf("perm_video_capsule"),
            ),
            verifiedRemoteCredits = 7,
            syncEpochMs = nowMs,
        )
        enforcer.applyAdminGovernanceSnapshot(adminSnapshot)

        assertEquals(customSyncIntervalMs, enforcer.getCurrentSyncIntervalMs())
        assertFalse(enforcer.shouldRunPeriodicSync(nowMs + 60 * 60 * 1000L))
        assertTrue(enforcer.shouldRunPeriodicSync(nowMs + customSyncIntervalMs))

        val firstDecision = enforcer.evaluateAndConsumeQuota(FenixToolId.VIDEO_CAPSULE_GENERATOR, nowMs)
        assertTrue(firstDecision is QuotaEnforcementDecision.AllowedFreeQuota)
        val allowedFree = firstDecision as QuotaEnforcementDecision.AllowedFreeQuota
        assertEquals("admin-governed-model-v3", allowedFree.assignedModelId)
        assertEquals(14, allowedFree.maxApiCallsPerExecution)
        assertEquals(12_000, allowedFree.maxOutputTokens)

        val secondDecision = enforcer.evaluateAndConsumeQuota(FenixToolId.VIDEO_CAPSULE_GENERATOR, nowMs + 1_000L)
        assertTrue(secondDecision is QuotaEnforcementDecision.AllowedViaCredits)
        assertEquals(2, (secondDecision as QuotaEnforcementDecision.AllowedViaCredits).remainingCreditsAfterExecution)

        val thirdDecision = enforcer.evaluateAndConsumeQuota(FenixToolId.VIDEO_CAPSULE_GENERATOR, nowMs + 2_000L)
        assertTrue(thirdDecision is QuotaEnforcementDecision.DeniedQuotaExhausted)

        enforcer.applyAdminGovernanceSnapshot(
            adminSnapshot.copy(
                userPermissions = adminSnapshot.userPermissions.copy(grantedPermissions = emptySet()),
                syncEpochMs = nowMs + 2_500L,
            ),
        )
        val permissionDenied = enforcer.evaluateAndConsumeQuota(FenixToolId.VIDEO_CAPSULE_GENERATOR, nowMs + 2_600L)
        assertTrue(permissionDenied is QuotaEnforcementDecision.DeniedByUserPermission)

        enforcer.applyAdminGovernanceSnapshot(adminSnapshot.copy(syncEpochMs = nowMs + 2_700L))
        val legitimateEntry = enforcer.getSignedLedgerEntry(FenixToolId.VIDEO_CAPSULE_GENERATOR)!!
        enforcer.restoreLedgerEntryFromStorage(legitimateEntry.copy(usedCountToday = 0))

        val tamperedDecision = enforcer.evaluateAndConsumeQuota(FenixToolId.VIDEO_CAPSULE_GENERATOR, nowMs + 3_000L)
        assertTrue(tamperedDecision is QuotaEnforcementDecision.DeniedLedgerTamperingDetected)
    }

    @Test
    fun `Programa Fenix Beta Testers prevents local self-attribution and grants privileges only after Supabase Creator approval`() {
        val vault = FenixKeystoreVault(JvmMemoryMasterKeyProvider())
        val enforcer = QuotaAndCreditEnforcer(vault)
        val nowMs = 1_730_000_000_000L
        val threeMonthsLaterMs = nowMs + 90L * 24L * 60L * 60L * 1000L

        // Configuramos cuota gratuita estándar en 1 para probar que sin aprobación del creador NO hay privilegio beta_tester
        enforcer.applyAdminGovernanceSnapshot(
            FenixAdminGovernanceSnapshot(
                toolRules = FenixToolId.entries.map { tool ->
                    RemoteToolConfigRule(toolId = tool, dailyFreeLimit = 1, creditCostPerExtraUse = 10)
                },
                userPermissions = RemoteUserPermissionsProfile(
                    userId = "local-student",
                    supabaseUserId = "supa-uid-777",
                    roleName = RemoteUserPermissionsProfile.ROLE_STUDENT_FREE,
                    isApprovedByCreatorInSupabase = false,
                    isBetaTesterProgramEnabledByAdmin = true,
                    betaTesterExpirationEpochMs = threeMonthsLaterMs,
                ),
                verifiedRemoteCredits = 0,
                syncEpochMs = nowMs,
            ),
        )

        // El usuario marca el consentimiento de telemetría en la UI local, pero aún NO fue aprobado por el creador en Supabase
        enforcer.updateUserTelemetryConsent(accepted = true)
        assertEquals(
            BetaTesterApplicationStatus.PENDING_ADMIN_APPROVAL,
            enforcer.getActiveUserPermissionsProfile().resolveBetaTesterStatus(nowMs),
        )

        // 1er uso entra por cuota gratuita normal (AllowedFreeQuota, NO AllowedBetaTesterPrivilege)
        val firstStandard = enforcer.evaluateAndConsumeQuota(FenixToolId.SYLLABUS_PROCESSOR, nowMs)
        assertTrue(firstStandard is QuotaEnforcementDecision.AllowedFreeQuota)

        // 2do uso es rechazado porque NO tiene aprobación remota de beta_tester en Supabase
        val secondDenied = enforcer.evaluateAndConsumeQuota(FenixToolId.SYLLABUS_PROCESSOR, nowMs + 100L)
        assertTrue(secondDenied is QuotaEnforcementDecision.DeniedQuotaExhausted)

        // Ahora el creador aprueba al usuario en Supabase desde Fenix Admin (roleName = beta_tester, isApprovedByCreatorInSupabase = true)
        enforcer.applyAdminGovernanceSnapshot(
            FenixAdminGovernanceSnapshot(
                toolRules = FenixToolId.entries.map { tool ->
                    RemoteToolConfigRule(toolId = tool, dailyFreeLimit = 1, creditCostPerExtraUse = 10)
                },
                userPermissions = RemoteUserPermissionsProfile(
                    userId = "local-student",
                    supabaseUserId = "supa-uid-777",
                    roleName = RemoteUserPermissionsProfile.ROLE_BETA_TESTER,
                    isApprovedByCreatorInSupabase = true,
                    isBetaTesterProgramEnabledByAdmin = true,
                    betaTesterExpirationEpochMs = threeMonthsLaterMs,
                    betaTesterDailyQuotaPerTool = 500,
                    hasAcceptedAnonymousTelemetryConsent = true,
                ),
                verifiedRemoteCredits = 0,
                syncEpochMs = nowMs + 500L,
            ),
        )

        // Con aprobación de Supabase + consentimiento de telemetría activo + plazo vigente, recibe AllowedBetaTesterPrivilege en las 9 herramientas
        FenixToolId.entries.forEach { tool ->
            val decision = enforcer.evaluateAndConsumeQuota(tool, nowMs + 1_000L)
            assertTrue(decision is QuotaEnforcementDecision.AllowedBetaTesterPrivilege, "Expected AllowedBetaTesterPrivilege for $tool")
        }

        // Si el usuario revoca el consentimiento de telemetría anónima o vence el plazo de varios meses, pierde el privilegio automáticamente
        enforcer.updateUserTelemetryConsent(accepted = false)
        val afterConsentRevoked = enforcer.evaluateAndConsumeQuota(FenixToolId.SYLLABUS_PROCESSOR, nowMs + 2_000L)
        assertTrue(afterConsentRevoked is QuotaEnforcementDecision.DeniedQuotaExhausted)

        enforcer.updateUserTelemetryConsent(accepted = true)
        val afterExpiration = enforcer.evaluateAndConsumeQuota(FenixToolId.SYLLABUS_PROCESSOR, threeMonthsLaterMs + 1_000L)
        // Al cambiar de día UTC en threeMonthsLaterMs usa su 1 cuota gratuita estándar y la siguiente se agota (ya no tiene privilegio tester)
        assertTrue(afterExpiration is QuotaEnforcementDecision.AllowedFreeQuota)
        val afterExpirationSecond = enforcer.evaluateAndConsumeQuota(FenixToolId.SYLLABUS_PROCESSOR, threeMonthsLaterMs + 2_000L)
        assertTrue(afterExpirationSecond is QuotaEnforcementDecision.DeniedQuotaExhausted)
    }

    @Test
    fun `AnonymousTelemetryManager captures all 6 technical telemetry categories and redacts PII and secrets`() {
        val telemetry = AnonymousTelemetryManager(initialConsentAccepted = false)
        assertFalse(telemetry.recordToolExecutionMetric(FenixToolId.INITIAL_CHAT, 120L, "STAGE_1", true))
        assertTrue(telemetry.peekPendingEvents().isEmpty())

        telemetry.setTelemetryConsent(true)
        assertTrue(telemetry.recordToolExecutionMetric(FenixToolId.INITIAL_CHAT, 120L, "STAGE_1", true))
        assertTrue(telemetry.recordApiAndRouterMetric("fenix_ia_assisted", "model-x", 240L, 200, 0, false))
        assertTrue(telemetry.recordDocumentEngineMetric(24, "CLEAN_DIGITAL", 5, 1, 0, "DOCX", 310L))
        assertTrue(telemetry.recordDeviceResourceHealthMetric(190, 0, 14.2f, "NOMINAL"))
        assertTrue(telemetry.recordNavigationAndUxMetric("P-02", "P-05", "HIGH_CONTRAST_OLED", true, true, 12))
        assertTrue(
            telemetry.recordSanitizedErrorReport(
                moduleName = "document-engine",
                errorCode = "ERR_PARSE",
                rawTechnicalStacktrace = "Failed reading /home/student_user/apunte_personal.pdf for user@example.org with token sk-1234567890abcdef",
            ),
        )

        val events = telemetry.drainPendingEventsForUpload()
        assertEquals(6, events.size)
        assertEquals(TelemetryCategory.entries.toSet(), events.map { it.category }.toSet())

        val errorEvent = events.first { it.category == TelemetryCategory.SANITIZED_ERROR_REPORT }
        val stacktrace = errorEvent.attributes.getValue("sanitized_stacktrace")
        assertFalse(stacktrace.contains("student_user"))
        assertFalse(stacktrace.contains("apunte_personal.pdf"))
        assertFalse(stacktrace.contains("user@example.org"))
        assertFalse(stacktrace.contains("sk-1234567890abcdef"))
        assertTrue(stacktrace.contains("[REDACTED_EMAIL]"))
        assertTrue(stacktrace.contains("[REDACTED_SECRET]"))
    }
}
