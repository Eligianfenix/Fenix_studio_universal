/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:41:10 -03:00
 * ARCHIVO: feature/settings/src/main/kotlin/com/fenix/studio/feature/settings/SettingsScreen.kt
 * ================================================================================
 */

package com.fenix.studio.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fenix.studio.core.designsystem.modifiers.fenixMinTouchTarget
import com.fenix.studio.core.designsystem.theme.FenixTheme
import com.fenix.studio.core.designsystem.theme.FenixThemeMode
import com.fenix.studio.core.designsystem.theme.FenixTouchTargetProfile
import com.fenix.studio.core.security.quota.RemoteUserPermissionsProfile

/**
 * Pantalla `P-03` + subvistas `P-03b` (Accesibilidad Universal), `P-03c` (Permisos granulares,
 * **Programa Fenix Beta Testers** con Telemetría Técnica Anónima y Servidor MCP Local),
 * `P-03d` (Entorno Linux Pasivo 0 MB en reposo) y `P-03e` (Respaldo Soberano `.fenix`).
 */
@Composable
fun SettingsScreen(
    state: SettingsAndVaultUiState,
    onSelectThemeMode: (FenixThemeMode) -> Unit,
    onToggleOpenDyslexicFont: (Boolean) -> Unit,
    onSelectTouchTargetProfile: (FenixTouchTargetProfile) -> Unit,
    onToggleAnonymousTelemetryConsent: (Boolean) -> Unit = {},
    onOpenByokVault: () -> Unit,
    currentPitch: Float = 0.82f,
    currentRate: Float = 0.92f,
    selectedVoiceId: String = "male_mature_rioplatense",
    onChangePitch: (Float) -> Unit = {},
    onChangeRate: (Float) -> Unit = {},
    onSelectVoice: (String) -> Unit = {},
    onTestVoiceSample: () -> Unit = {},
    azureApiKey: String = "",
    azureRegion: String = "eastus",
    onSaveAzureKey: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val colors = FenixTheme.colors
    val typography = MaterialTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Ajustes y Accesibilidad Universal (P-03)",
            style = typography.titleMedium,
            color = colors.onBackground,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(colors.borderStrokeWidth, colors.outline, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "P-03b · Modos Visuales y Tipografía Inclusiva",
                style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
            )
            FenixThemeMode.entries.forEach { mode ->
                val isSelected = state.accessibilityConfig.themeMode == mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) colors.surface else colors.background)
                        .border(
                            width = colors.borderStrokeWidth,
                            color = if (isSelected) colors.primary else colors.outline,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable { onSelectThemeMode(mode) }
                        .fenixMinTouchTarget()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = mode.name,
                        style = typography.bodyMedium,
                        color = colors.onSurface,
                    )
                    Text(
                        text = if (isSelected) "ACTIVO" else "SELECCIONAR",
                        style = typography.labelMedium,
                        color = if (isSelected) colors.primary else colors.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.background)
                    .clickable { onToggleOpenDyslexicFont(!state.accessibilityConfig.useOpenDyslexicFont) }
                    .fenixMinTouchTarget()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Tipografía OpenDyslexic (Interlineado 1.6x)",
                    style = typography.bodyMedium,
                    color = colors.onBackground,
                )
                Text(
                    text = if (state.accessibilityConfig.useOpenDyslexicFont) "ENCENDIDO" else "APAGADO",
                    style = typography.labelMedium,
                    color = colors.primary,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.background)
                    .clickable {
                        val next = if (state.accessibilityConfig.touchTargetProfile == FenixTouchTargetProfile.STANDARD) {
                            FenixTouchTargetProfile.MACRO_LARGE
                        } else {
                            FenixTouchTargetProfile.STANDARD
                        }
                        onSelectTouchTargetProfile(next)
                    }
                    .fenixMinTouchTarget()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Diana Táctil Motriz (${state.accessibilityConfig.touchTargetProfile.minSize.value.toInt()} dp)",
                    style = typography.bodyMedium,
                    color = colors.onBackground,
                )
                Text(
                    text = "CAMBIAR",
                    style = typography.labelMedium,
                    color = colors.primary,
                )
            }
        }

        // P-03f · Configuración de Voz y Narrador (TTS)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(colors.borderStrokeWidth, colors.outline, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "P-03f · Narrador y Voz Académica (TTS)",
                style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
            )
            Text(
                text = "Configuración de lectura en voz alta para apuntes y exportación de audiolibros a Costo $0.",
                style = typography.bodySmall,
                color = colors.onSurfaceVariant,
            )

            val voiceOptions = listOf(
                "male_mature_rioplatense" to "Hombre Maduro (Narrador Profesional - Rioplatense)",
                "male_classic_neutral" to "Hombre Clásico (Narrador Neutro)",
                "female_rioplatense" to "Mujer Rioplatense (Locución Profesional)",
                "female_neutral" to "Mujer Neutra (Locución Académica)",
                "device_default" to "Voz por Defecto del Dispositivo",
            )

            voiceOptions.forEach { (voiceId, label) ->
                val isSelected = selectedVoiceId == voiceId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) colors.surface else colors.background)
                        .border(
                            width = colors.borderStrokeWidth,
                            color = if (isSelected) colors.primary else colors.outline,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable { onSelectVoice(voiceId) }
                        .fenixMinTouchTarget()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (isSelected) "ACTIVO" else "ELEGIR",
                        style = typography.labelSmall,
                        color = if (isSelected) colors.primary else colors.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Tono de Voz: ${String.format(java.util.Locale.US, "%.2f", currentPitch)}x ${if (currentPitch < 0.85f) "(Grave y Maduro)" else if (currentPitch > 1.05f) "(Agudo)" else "(Neutro)"}",
                style = typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
            )
            Slider(
                value = currentPitch,
                onValueChange = onChangePitch,
                valueRange = 0.6f..1.4f,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Velocidad de Narración: ${String.format(java.util.Locale.US, "%.2f", currentRate)}x ${if (currentRate < 0.95f) "(Pausado y Solemne)" else if (currentRate > 1.1f) "(Rápido)" else "(Normal)"}",
                style = typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
            )
            Slider(
                value = currentRate,
                onValueChange = onChangeRate,
                valueRange = 0.75f..1.5f,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = onTestVoiceSample,
                modifier = Modifier
                    .fillMaxWidth()
                    .fenixMinTouchTarget(),
            ) {
                Text("🔊 Escuchar Muestra de Narrador", style = typography.labelMedium)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Sub-bloque Clave de Azure Speech
            val uriHandler = LocalUriHandler.current
            var keyInput by remember(azureApiKey) { mutableStateOf(azureApiKey) }
            var regionInput by remember(azureRegion) { mutableStateOf(azureRegion) }

            Text(
                text = "Clave API de Narrador Neuronal (Azure Speech F0 Gratis)",
                style = typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.primary,
            )
            Text(
                text = "500.000 caracteres mensuales gratuitos en nivel F0 a Costo $0 sin cargos.",
                style = typography.bodySmall,
                color = colors.onSurfaceVariant,
            )

            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("Clave API (Opcional)") },
                placeholder = { Text("Ej: 4a2b8c9d...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = regionInput,
                onValueChange = { regionInput = it },
                label = { Text("Región (Por defecto: eastus)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onSaveAzureKey(keyInput.trim(), regionInput.trim().ifBlank { "eastus" }) },
                    modifier = Modifier
                        .weight(1f)
                        .fenixMinTouchTarget(),
                ) {
                    Text("Guardar Clave", style = typography.labelSmall)
                }

                OutlinedButton(
                    onClick = { uriHandler.openUri("https://portal.azure.com/#create/Microsoft.CognitiveServicesSpeechServices") },
                    modifier = Modifier.fenixMinTouchTarget(),
                    border = BorderStroke(colors.borderStrokeWidth, colors.outline),
                ) {
                    Text("Obtener Clave ➔", style = typography.labelSmall)
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.surface,
            contentColor = colors.onSurface,
            border = BorderStroke(colors.borderStrokeWidth, colors.primary),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "P-03c · ${RemoteUserPermissionsProfile.PROGRAM_OFFICIAL_NAME}",
                    style = typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                )
                Text(
                    text = "Rol verificado en Supabase: ${state.activeRoleName} · Estado: ${state.betaTesterApplicationStatus.name} · Vigencia EpochMs: ${state.betaTesterExpirationEpochMs}",
                    style = typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fenixMinTouchTarget(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.hasAcceptedAnonymousTelemetryConsent,
                        onCheckedChange = onToggleAnonymousTelemetryConsent,
                        modifier = Modifier.fenixMinTouchTarget(),
                    )
                    Text(
                        text = "Acepto compartir telemetría técnica 100% libre de datos personales (Ley 25.326). La activación del rol beta_tester requiere cuenta registrada y aprobación del creador en Supabase.",
                        style = typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(colors.borderStrokeWidth, colors.outline, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "P-03d · Garantía de Privacidad y Sandbox Linux Pasivo",
                style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
            )
            Text(
                text = "Cero Telemetría en Reposo: ${if (state.zeroTelemetryVerified) "ACTIVO (0 envíos)" else "TELEMETRÍA TÉCNICA ANÓNIMA AUTORIZADA"}",
                style = typography.bodyMedium,
                color = colors.primary,
            )
            Text(
                text = "Sandbox Linux Pasivo: ${state.passiveSandboxState.activeRamUsageMb} MB RAM en reposo",
                style = typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.primary)
                .clickable(onClick = onOpenByokVault)
                .fenixMinTouchTarget()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Abrir Bóveda BYOK y Fénix IA (P-04)",
                style = typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.onPrimary,
            )
        }
    }
}
