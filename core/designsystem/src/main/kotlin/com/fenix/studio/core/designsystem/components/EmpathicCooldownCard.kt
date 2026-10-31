/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/designsystem/src/main/kotlin/com/fenix/studio/core/designsystem/components/EmpathicCooldownCard.kt
 * ================================================================================
 */

package com.fenix.studio.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fenix.studio.core.designsystem.modifiers.fenixMinTouchTarget
import com.fenix.studio.core.designsystem.theme.FenixTheme

/**
 * Estado determinista del Semáforo Empático de Enfriamiento (RN-07, DT-BAJA-02).
 * Se actualiza estrictamente a `1 Hz` (1 tick por segundo), prohibiendo animaciones a 60 FPS.
 */
@Immutable
data class EmpathicCooldownUiState(
    val isVisible: Boolean = false,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 5,
    val activeProviderName: String = "Fénix IA",
) {
    /**
     * Fracción de progreso lineal en el intervalo cerrado [0.0f, 1.0f].
     */
    val progressFraction: Float
        get() {
            if (totalSeconds <= 0) return 1.0f
            val clampedRemaining = remainingSeconds.coerceIn(0, totalSeconds)
            val elapsed = totalSeconds - clampedRemaining
            return (elapsed.toFloat() / totalSeconds.toFloat()).coerceIn(0.0f, 1.0f)
        }

    /**
     * Emite únicamente 2 anuncios para TalkBack (`LiveRegionMode.Polite`):
     * 1) Al iniciar el enfriamiento (`remainingSeconds == totalSeconds`).
     * 2) Al llegar a `0s` (`remainingSeconds == 0`), antes del reintento automático manos libres.
     * Devuelve `null` en todos los segundos intermedios para evitar saturación auditiva.
     */
    fun buildPoliteLiveAnnouncement(): String? {
        if (!isVisible || totalSeconds <= 0) return null
        return when (remainingSeconds) {
            totalSeconds ->
                "Pausa empática de red activa en $activeProviderName. " +
                    "Reintento automático manos libres en $totalSeconds segundos."
            0 ->
                "Enfriamiento completado. Reintentando envío automáticamente."
            else -> null
        }
    }
}

/**
 * Tarjeta de enfriamiento empático con cuenta regresiva a 1 Hz, silenciado de ticks en TalkBack
 * y reintento 100% automático manos libres al llegar a `0s`.
 */
@Composable
fun EmpathicCooldownCard(
    state: EmpathicCooldownUiState,
    onAutoRetryTriggered: () -> Unit,
    onSwitchProviderClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isVisible) return

    val colors = FenixTheme.colors
    val liveAnnouncement = state.buildPoliteLiveAnnouncement()

    LaunchedEffect(state.isVisible, state.remainingSeconds) {
        if (state.isVisible && state.remainingSeconds <= 0) {
            onAutoRetryTriggered()
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = colors.cooldownContainer,
        contentColor = colors.onCooldownContainer,
        border = BorderStroke(
            width = colors.borderStrokeWidth,
            color = colors.outline,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            if (liveAnnouncement != null) {
                Text(
                    text = "",
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = liveAnnouncement
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Respirando un instante (${state.activeProviderName})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onCooldownContainer,
                )

                Text(
                    text = "${state.remainingSeconds.coerceAtLeast(0)} s",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.cooldownAccent,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Tu consulta está protegida. Reanudaremos automáticamente sin que toques nada.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onCooldownContainer,
            )

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { state.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clearAndSetSemantics {},
                color = colors.cooldownAccent,
                trackColor = colors.surface,
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onSwitchProviderClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .fenixMinTouchTarget(),
                border = BorderStroke(colors.borderStrokeWidth, colors.outline),
            ) {
                Text(
                    text = "[ Conmutar a otro proveedor ahora ]",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onCooldownContainer,
                )
            }
        }
    }
}
