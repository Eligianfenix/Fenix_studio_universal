/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-14 19:45:00 -03:00
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fenix.studio.core.designsystem.modifiers.fenixMinTouchTarget
import com.fenix.studio.core.designsystem.theme.FenixTheme
import com.fenix.studio.core.network.catalog.ProviderProbeStatus

/**
 * Pantalla `P-04`: catálogo de proveedores y bóveda BYOK real.
 *
 * - Los proveedores **sin clave** se muestran primero y se pueden probar de inmediato.
 * - Cada proveedor con clave acepta hasta 6 ranuras cifradas en el Android Keystore.
 * - "Probar conexión" consulta `GET /models`: no gasta cuota de generación.
 */
@Composable
fun ByokVaultScreen(
    state: SettingsAndVaultUiState,
    onSaveKey: (providerId: String, slotIndex: Int, plaintextKey: String) -> Unit,
    onRemoveSlot: (providerId: String, slotIndex: Int) -> Unit,
    onProbe: (providerId: String, slotIndex: Int) -> Unit,
    onDiscoverModelsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FenixTheme.colors
    val typography = MaterialTheme.typography

    var expandedProviderId by remember { mutableStateOf<String?>(null) }
    var keyDraft by remember { mutableStateOf("") }
    var selectedSlot by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Proveedores de IA y Bóveda BYOK (P-04)",
            style = typography.titleMedium,
            color = colors.onBackground,
        )
        Text(
            text = "La app funciona sin configurar nada con los proveedores sin clave. " +
                "Si agregás tus propias claves, el router las usa primero por calidad y cuota, " +
                "y vuelve a los keyless si algo falla.",
            style = typography.bodySmall,
            color = colors.onSurfaceVariant,
        )

        ProviderSectionTitle("Sin clave · listo para usar", colors.onSurface)
        state.providerCards.filter { it.isKeyless }.forEach { card ->
            ProviderCardHeader(
                card = card,
                expanded = false,
                isProbing = state.probingProviderId == card.providerId,
                probe = state.providerProbe?.takeIf { it.providerId == card.providerId },
                onClick = { onProbe(card.providerId, 0) },
            )
        }

        ProviderSectionTitle("Con tu clave · ${state.providerCards.count { !it.isKeyless }} proveedores", colors.onSurface)
        state.providerCards.filterNot { it.isKeyless }.forEach { card ->
            val isExpanded = expandedProviderId == card.providerId
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .border(
                        width = if (isExpanded) 2.dp else colors.borderStrokeWidth,
                        color = if (isExpanded) colors.primary else colors.outline,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedProviderId = if (isExpanded) null else card.providerId
                            keyDraft = ""
                            selectedSlot = 0
                        }
                        .fenixMinTouchTarget(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = card.displayName,
                            style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface,
                        )
                        Text(
                            text = "${card.ringLabel} · ${keySummary(card)}",
                            style = typography.bodySmall,
                            color = if (card.configuredSlots > 0) colors.primary else colors.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = if (isExpanded) "CERRAR" else "CONFIGURAR",
                        style = typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary,
                    )
                }

                if (isExpanded) {
                    Text(
                        text = card.notes,
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        text = "Modelo sugerido: ${card.defaultModelId ?: "se descubre en vivo"}",
                        style = typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                    if (!card.endpointVerified) {
                        Text(
                            text = "URL reportada por la comunidad: si falla, corregila en una versión futura desde Proveedor propio.",
                            style = typography.bodySmall,
                            color = colors.cooldownAccent,
                        )
                    }

                    Text(
                        text = "Ranura de la clave",
                        style = typography.labelMedium,
                        color = colors.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0 until 6).forEach { index ->
                            val isSelected = selectedSlot == index
                            val isConfigured = index in card.configuredSlotIndices
                            OutlinedButton(
                                onClick = { selectedSlot = index },
                                modifier = Modifier.fenixMinTouchTarget(),
                            ) {
                                Text(
                                    text = if (isConfigured) "${index + 1}●" else "${index + 1}",
                                    style = typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) colors.primary else colors.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = keyDraft,
                        onValueChange = { keyDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Clave API de ${card.displayName}") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (keyDraft.isNotBlank()) {
                                    onSaveKey(card.providerId, selectedSlot, keyDraft)
                                    keyDraft = ""
                                }
                            },
                            enabled = keyDraft.isNotBlank(),
                            modifier = Modifier.fenixMinTouchTarget(),
                        ) {
                            Text("Guardar cifrada")
                        }
                        OutlinedButton(
                            onClick = { onProbe(card.providerId, selectedSlot) },
                            enabled = state.probingProviderId == null,
                            modifier = Modifier.fenixMinTouchTarget(),
                        ) {
                            if (state.probingProviderId == card.providerId) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 6.dp))
                            }
                            Text("Probar conexión")
                        }
                        if (selectedSlot in card.configuredSlotIndices) {
                            OutlinedButton(
                                onClick = { onRemoveSlot(card.providerId, selectedSlot) },
                                modifier = Modifier.fenixMinTouchTarget(),
                            ) {
                                Text("Borrar", color = colors.error)
                            }
                        }
                    }

                    state.providerProbe
                        ?.takeIf { it.providerId == card.providerId }
                        ?.let { probe ->
                            Text(
                                text = probe.message,
                                style = typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = probeColor(probe.status),
                            )
                            if (probe.sampleModels.isNotEmpty()) {
                                Text(
                                    text = probe.sampleModels.joinToString(" · "),
                                    style = typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
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
                text = "Autodescubrir modelos en vivo (/models) · Detectados: ${state.discoveredModels.size}",
                style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.primary,
            )
        }
    }
}

@Composable
private fun ProviderSectionTitle(
    text: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        color = color,
    )
}

@Composable
private fun ProviderCardHeader(
    card: ProviderCardUiModel,
    expanded: Boolean,
    isProbing: Boolean,
    probe: ProviderProbeUiModel?,
    onClick: () -> Unit,
) {
    val colors = FenixTheme.colors
    val typography = MaterialTheme.typography

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(colors.borderStrokeWidth, colors.primary, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.displayName,
                    style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface,
                )
                Text(
                    text = "${card.ringLabel} · ${card.defaultModelId ?: "modelo automático"}",
                    style = typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onClick,
                enabled = !isProbing,
                modifier = Modifier.fenixMinTouchTarget(),
            ) {
                if (isProbing) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 6.dp))
                }
                Text(if (isProbing) "Probando…" else "Probar")
            }
        }
        Text(
            text = card.notes,
            style = typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        probe?.let {
            Text(
                text = it.message,
                style = typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = probeColor(it.status),
            )
        }
    }
}

private fun keySummary(card: ProviderCardUiModel): String = when {
    card.configuredSlots == 0 -> "Sin clave cargada"
    card.configuredSlots == 1 -> "1 clave cargada"
    else -> "${card.configuredSlots} claves cargadas"
}

@Composable
private fun probeColor(status: ProviderProbeStatus): androidx.compose.ui.graphics.Color {
    val colors = FenixTheme.colors
    return when (status) {
        ProviderProbeStatus.OK -> colors.primary
        ProviderProbeStatus.RATE_LIMITED -> colors.cooldownAccent
        ProviderProbeStatus.NO_KEY,
        ProviderProbeStatus.NOT_CONFIGURED,
        -> colors.onSurfaceVariant
        else -> colors.error
    }
}
