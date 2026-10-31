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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
