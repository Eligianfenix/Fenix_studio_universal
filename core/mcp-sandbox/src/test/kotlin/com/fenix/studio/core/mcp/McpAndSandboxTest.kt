/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:36:35 -03:00
 * ARCHIVO: core/mcp-sandbox/src/test/kotlin/com/fenix/studio/core/mcp/McpAndSandboxTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.mcp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class McpAndSandboxTest {

    @Test
    fun `academic mcp manager registers exactly 20 connectors and enforces 3-tier HITL policy`() {
        val manager = AcademicMcpConnectorManager()
        val connectors = manager.getAllConnectors()
        assertEquals(20, connectors.size, "El catálogo debe contener exactamente 20 conectores MCP académicos")

        // Unicidad de IDs
        val uniqueIds = connectors.map { it.connectorId }.toSet()
        assertEquals(20, uniqueIds.size)

        // Verificamos nivel 1: READ_ONLY_AUTO (aprobación automática)
        val readReq = McpInvocationRequest(
            connectorId = "mcp-zotero",
            toolName = "search_items",
            arguments = mapOf("query" to "Metodología"),
            isUserConsentGranted = false,
        )
        val readResult = manager.executeMcpTool(readReq)
        assertTrue(readResult is McpInvocationResult.Success)

        // Verificamos nivel 2: WRITE_CONFIRMATION_REQUIRED (bloqueo sin consentimiento)
        val writeReqWithoutConsent = McpInvocationRequest(
            connectorId = "mcp-obsidian",
            toolName = "create_atomic_note",
            arguments = mapOf("title" to "Apunte 1"),
            isUserConsentGranted = false,
        )
        val writeBlocked = manager.executeMcpTool(writeReqWithoutConsent)
        assertTrue(writeBlocked is McpInvocationResult.AwaitingUserConsent)
        assertEquals(HitlPolicyLevel.WRITE_CONFIRMATION_REQUIRED, (writeBlocked as McpInvocationResult.AwaitingUserConsent).requiredPolicy)

        // Con consentimiento: éxito
        val writeWithConsent = manager.executeMcpTool(writeReqWithoutConsent.copy(isUserConsentGranted = true))
        assertTrue(writeWithConsent is McpInvocationResult.Success)

        // Verificamos nivel 3: DESTRUCTIVE_STRICT_CONSENT (bloqueo de borrado sin consentimiento)
        val deleteReq = McpInvocationRequest(
            connectorId = "mcp-fenix-vault",
            toolName = "wipe_all_keys",
            arguments = emptyMap(),
            isUserConsentGranted = false,
        )
        val deleteBlocked = manager.executeMcpTool(deleteReq)
        assertTrue(deleteBlocked is McpInvocationResult.AwaitingUserConsent)
        assertEquals(HitlPolicyLevel.DESTRUCTIVE_STRICT_CONSENT, (deleteBlocked as McpInvocationResult.AwaitingUserConsent).requiredPolicy)

        val deleteWithConsent = manager.executeMcpTool(deleteReq.copy(isUserConsentGranted = true))
        assertTrue(deleteWithConsent is McpInvocationResult.Success)
    }

    @Test
    fun `passive linux sandbox lifecycle ensures strictly 0 MB ram at rest and clean 1-click uninstall`() {
        val tempDir = Files.createTempDirectory("fenix_sandbox_test").toFile()
        val sandboxManager = PassiveLinuxSandboxManager(sandboxRootDir = tempDir)

        // Estado inicial
        assertEquals(SandboxState.UNINSTALLED, sandboxManager.uiState.value.state)
        assertEquals(0, sandboxManager.getPassiveRamUsageMb())

        // 1. Instalación
        val installed = sandboxManager.installPassiveSandbox()
        assertTrue(installed)
        assertEquals(SandboxState.IDLE_PASSIVE_0MB, sandboxManager.uiState.value.state)
        assertEquals(0, sandboxManager.getPassiveRamUsageMb(), "En reposo debe consumir estrictamente 0 MB de RAM")
        assertTrue(sandboxManager.uiState.value.installedStorageBytes > 0)

        // 2. Ejecución activa bajo demanda
        val output = sandboxManager.executePassiveCommand("python main.py")
        assertTrue(output.contains("Python 3.12"))
        // Al terminar, el proceso finaliza y vuelve a reposo de 0 MB
        assertEquals(SandboxState.IDLE_PASSIVE_0MB, sandboxManager.uiState.value.state)
        assertEquals(0, sandboxManager.getPassiveRamUsageMb(), "Al finalizar el comando, la RAM debe volver a 0 MB")

        // 3. Desinstalación limpia en un solo clic
        val uninstalled = sandboxManager.uninstallCleanly()
        assertTrue(uninstalled)
        assertEquals(SandboxState.UNINSTALLED, sandboxManager.uiState.value.state)
        assertEquals(0L, sandboxManager.uiState.value.installedStorageBytes)
        assertEquals(0, sandboxManager.getPassiveRamUsageMb())
        assertFalse(tempDir.exists(), "El directorio debe eliminarse por completo")
    }
}
