/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-02 19:49:00 -03:00
 * ARCHIVO: feature/settings/src/main/kotlin/com/fenix/studio/feature/settings/GetCreditsScreen.kt
 * ================================================================================
 */

package com.fenix.studio.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fenix.studio.core.designsystem.modifiers.fenixMinTouchTarget
import com.fenix.studio.core.designsystem.theme.FenixTheme

data class CreditPackageOption(
    val amount: Int,
    val title: String,
    val subtitle: String,
    val badge: String? = null,
)

@Composable
fun GetCreditsScreen(
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = FenixTheme.colors
    val typography = MaterialTheme.typography
    var selectedPackage by remember { mutableStateOf<CreditPackageOption?>(null) }

    val packages = remember {
        listOf(
            CreditPackageOption(
                amount = 1,
                title = "Comprar 1 Crédito",
                subtitle = "Ideal para procesar una unidad académica o consulta puntual.",
            ),
            CreditPackageOption(
                amount = 3,
                title = "Comprar 3 Créditos",
                subtitle = "Paquete ágil para parciales y síntesis de varias unidades.",
            ),
            CreditPackageOption(
                amount = 5,
                title = "Comprar 5 Créditos",
                subtitle = "Pensado para preparar una materia completa o investigación.",
                badge = "RECOMENDADO",
            ),
            CreditPackageOption(
                amount = 10,
                title = "Comprar 10 Créditos",
                subtitle = "Capacidad extendida para múltiples cátedras y trabajos finales.",
                badge = "MÁXIMO RENDIMIENTO",
            ),
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "⚡ Conseguir Créditos",
                style = typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.primary,
            )
            OutlinedButton(
                onClick = onBackClick,
                modifier = Modifier.fenixMinTouchTarget(),
                border = BorderStroke(colors.borderStrokeWidth, colors.outline),
            ) {
                Text("Volver", style = typography.labelMedium)
            }
        }

        Text(
            text = "Amplía tus capacidades de procesamiento académico seleccionando un paquete de créditos:",
            style = typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(4.dp))

        packages.forEach { pkg ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedPackage = pkg }
                    .fenixMinTouchTarget(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.surface,
                    contentColor = colors.onSurface,
                ),
                border = BorderStroke(
                    width = if (pkg.badge != null) 2.dp else colors.borderStrokeWidth,
                    color = if (pkg.badge != null) colors.primary else colors.outline,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (pkg.badge != null) {
                            Text(
                                text = pkg.badge,
                                style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.primary,
                            )
                        }
                        Text(
                            text = pkg.title,
                            style = typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface,
                        )
                        Text(
                            text = pkg.subtitle,
                            style = typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.primary)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "+${pkg.amount} ⚡",
                            style = typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.onPrimary,
                        )
                    }
                }
            }
        }
    }

    selectedPackage?.let { pkg ->
        AlertDialog(
            onDismissRequest = { selectedPackage = null },
            title = {
                Text(
                    text = pkg.title,
                    style = typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.primary,
                )
            },
            text = {
                Text(
                    text = "Has seleccionado el paquete '${pkg.title}' (+${pkg.amount} créditos de procesamiento académico).",
                    style = typography.bodyMedium,
                    color = colors.onSurface,
                )
            },
            confirmButton = {
                Button(
                    onClick = { selectedPackage = null },
                    modifier = Modifier.fenixMinTouchTarget(),
                ) {
                    Text("Continuar", style = typography.labelMedium)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { selectedPackage = null },
                    modifier = Modifier.fenixMinTouchTarget(),
                    border = BorderStroke(colors.borderStrokeWidth, colors.outline),
                ) {
                    Text("Cancelar", style = typography.labelMedium)
                }
            },
        )
    }
}
