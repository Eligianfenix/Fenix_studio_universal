/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:37:55 -03:00
 * ARCHIVO: feature/settings/src/main/kotlin/com/fenix/studio/feature/settings/ByokVaultScreen.kt
 * ================================================================================
 */

package com.fenix.studio.feature.settings

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fenix.studio.core.designsystem.modifiers.fenixMinTouchTarget
import com.fenix.studio.core.designsystem.theme.FenixTheme

/**
 * Pantalla `P-04` (Bóveda Criptográfica BYOK y Selector de Proveedor `"Fénix IA (Gratuito Asistido)"`
 * activo de fábrica según `RN-25`, con 6 ranuras de rotación adaptativa y botón de Autodescubrimiento).
 */
@Composable
fun ByokVaultScreen(
    state: SettingsAndVaultUiState,
    onRemoveSlot: (Int) -> Unit,
    onDiscoverModelsClick: () -> Unit,
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
            text = "Bóveda Criptográfica BYOK (P-04)",
            style = typography.titleMedium,
            color = colors.onBackground,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(colors.borderStrokeWidth, colors.primary, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Proveedor Activo de Fábrica (RN-25)",
                style = typography.labelMedium,
                color = colors.primary,
            )
            Text(
                text = state.activeProvider.displayName,
                style = typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface,
            )
            Text(
                text = "El motor asistido gratuito opera de inmediato sin exigir API Keys externas.",
                style = typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }

        Text(
            text = "Pool de 6 Ranuras BYOK Opcionales (AES-256-GCM)",
            style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onBackground,
        )

        state.byokSlots.forEach { slot ->
            val statusColor = when (slot.status) {
                ByokSlotVisualStatus.HEALTHY -> colors.primary
                ByokSlotVisualStatus.COOLDOWN -> colors.cooldownAccent
                ByokSlotVisualStatus.EMPTY -> colors.onSurfaceVariant
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .border(colors.borderStrokeWidth, colors.outline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Ranura #${slot.slotIndex + 1} · ${slot.status.name}",
                        style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = statusColor,
                    )
                    Text(
                        text = "Estado Criptográfico: ${slot.maskedKeyHint}",
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (slot.status != ByokSlotVisualStatus.EMPTY) {
                    Text(
                        text = "LIMPIAR",
                        style = typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.error,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onRemoveSlot(slot.slotIndex) }
                            .fenixMinTouchTarget()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(colors.borderStrokeWidth, colors.primary, RoundedCornerShape(12.dp))
                .clickable(onClick = onDiscoverModelsClick)
                .fenixMinTouchTarget()
                .padding(14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Autodescubrir Modelos en Vivo (/models) · Detectados: ${state.discoveredModels.size}",
                style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.primary,
            )
        }
    }
}
