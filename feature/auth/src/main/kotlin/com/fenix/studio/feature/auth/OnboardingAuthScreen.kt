/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 03:36:30 -03:00
 * ARCHIVO: feature/auth/src/main/kotlin/com/fenix/studio/feature/auth/OnboardingAuthScreen.kt
 * ================================================================================
 */

package com.fenix.studio.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fenix.studio.core.designsystem.components.FenixBrandHeader
import com.fenix.studio.core.designsystem.modifiers.fenixMinTouchTarget
import com.fenix.studio.core.designsystem.theme.FenixTheme
import com.fenix.studio.core.security.quota.RemoteUserPermissionsProfile

/**
 * Pantalla `P-01`: Bienvenida, Accesibilidad Inicial, Login Opcional, Contrato TyC v1.1 (`MF-4.1`),
 * y Postulación al **Programa Fenix Beta Testers** (sujeta a cuenta autenticada en Supabase y aprobación del creador).
 *
 * Microfase MF-D.3: Integración de FenixBrandHeader, escape rápido [ ✕ ], y tarjetas de selección limpias.
 */
@Composable
fun OnboardingAuthScreen(
    state: OnboardingAuthState,
    onMarkTycScrolledToBottom: () -> Unit,
    onToggleTycAcceptance: (Boolean) -> Unit,
    onToggleBetaTesterTelemetryConsent: (Boolean) -> Unit = {},
    onSelectEntryMode: (AuthEntryMode) -> Unit,
    onProceedClick: () -> Unit,
    onQuickEscapeClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = FenixTheme.colors
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = colors.background,
        contentColor = colors.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Barra superior accesible con botón de escape rápido en la esquina superior izquierda
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onQuickEscapeClick?.invoke() ?: onProceedClick() },
                    modifier = Modifier
                        .size(48.dp)
                        .fenixMinTouchTarget(),
                ) {
                    Text(
                        text = "✕",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = "Bienvenido a Fenix Studio",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Emblema e identidad institucional universal
            FenixBrandHeader(
                subtitle = "Accesibilidad Universal y Procesamiento Académico",
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Tu entorno soberano para estudiar, procesar textos académicos y organizar asignaturas con inteligencia artificial accesible y ética.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )

            // Contrato de Términos y Condiciones v1.1 (RN-16 / RN-17)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.surface,
                    contentColor = colors.onSurface,
                ),
                border = BorderStroke(colors.borderStrokeWidth, colors.outline),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Términos y Condiciones (Versión ${state.currentRequiredTycVersion})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. Soberanía de Datos (Ley 25.326): Tus apuntes, documentos y claves privadas residen de forma local y cifrada en tu dispositivo.\n" +
                            "2. Encuadre Deontológico (RN-17): Fénix Studio es una herramienta de estudio y asistencia pedagógica; no reemplaza el patrocinio legal, el dictamen pericial ni el diagnóstico de salud profesional.\n" +
                            "3. Transparencia Algorítmica: Las citas y síntesis son fieles al material provisto por el estudiante sin alucinaciones intencionales.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onMarkTycScrolledToBottom,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fenixMinTouchTarget(),
                        border = BorderStroke(colors.borderStrokeWidth, colors.primary),
                    ) {
                        Text(
                            text = if (state.hasScrolledToBottomOfTyc) {
                                "✓ Lectura completa confirmada"
                            } else {
                                "Confirmar lectura hasta el final del contrato"
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fenixMinTouchTarget(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = state.acceptedTycVersion == state.currentRequiredTycVersion,
                            onCheckedChange = onToggleTycAcceptance,
                            enabled = state.hasScrolledToBottomOfTyc,
                            modifier = Modifier.fenixMinTouchTarget(),
                        )
                        Text(
                            text = "He leído y acepto los Términos y Condiciones v${state.currentRequiredTycVersion} y el marco deontológico.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }

            // Opciones de Ingreso (Tarjetas limpias y accesibles)
            Text(
                text = "Modalidad de Uso",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )

            // Opción 1: Estudiante Libre Soberano
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectEntryMode(AuthEntryMode.SOVEREIGN_LOCAL_GUEST_100_FUNCTIONAL) },
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.selectedEntryMode == AuthEntryMode.SOVEREIGN_LOCAL_GUEST_100_FUNCTIONAL) {
                        colors.propositionPrimaryTheoryContainer
                    } else {
                        colors.surface
                    },
                    contentColor = colors.onSurface,
                ),
                border = BorderStroke(
                    colors.borderStrokeWidth,
                    if (state.selectedEntryMode == AuthEntryMode.SOVEREIGN_LOCAL_GUEST_100_FUNCTIONAL) {
                        colors.primary
                    } else {
                        colors.outline
                    },
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.selectedEntryMode == AuthEntryMode.SOVEREIGN_LOCAL_GUEST_100_FUNCTIONAL,
                        onClick = { onSelectEntryMode(AuthEntryMode.SOVEREIGN_LOCAL_GUEST_100_FUNCTIONAL) },
                        modifier = Modifier.fenixMinTouchTarget(),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Continuar como Estudiante Libre",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "100% Funcional sin cuenta ni registro. Privacidad y soberanía total en tu equipo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }

            // Opción 2: Sincronización Opcional
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectEntryMode(AuthEntryMode.OPTIONAL_CLOUD_SYNC) },
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.selectedEntryMode == AuthEntryMode.OPTIONAL_CLOUD_SYNC) {
                        colors.propositionPrimaryTheoryContainer
                    } else {
                        colors.surface
                    },
                    contentColor = colors.onSurface,
                ),
                border = BorderStroke(
                    colors.borderStrokeWidth,
                    if (state.selectedEntryMode == AuthEntryMode.OPTIONAL_CLOUD_SYNC) {
                        colors.primary
                    } else {
                        colors.outline
                    },
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.selectedEntryMode == AuthEntryMode.OPTIONAL_CLOUD_SYNC,
                        onClick = { onSelectEntryMode(AuthEntryMode.OPTIONAL_CLOUD_SYNC) },
                        modifier = Modifier.fenixMinTouchTarget(),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Iniciar Sesión Opcional",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Sincroniza tus aulas y notas entre dispositivos con cifrado de extremo a extremo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }

            // Opción 3: Postulación Programa Beta Testers
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.surface,
                    contentColor = colors.onSurface,
                ),
                border = BorderStroke(colors.borderStrokeWidth, colors.outline),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .fenixMinTouchTarget(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.hasAcceptedAnonymousTelemetryConsent,
                        onCheckedChange = onToggleBetaTesterTelemetryConsent,
                        modifier = Modifier.fenixMinTouchTarget(),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Postular a ${RemoteUserPermissionsProfile.PROGRAM_OFFICIAL_NAME}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Rol actual: ${state.activeRoleName} · Estado: ${state.betaTesterApplicationStatus.name}. Sujeto a aprobación remota por el creador.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Botón de ingreso principal
            Button(
                onClick = onProceedClick,
                enabled = state.isProceedEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .fenixMinTouchTarget(),
            ) {
                Text(
                    text = "Ingresar a Fenix Studio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
