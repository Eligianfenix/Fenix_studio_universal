/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/designsystem/src/main/kotlin/com/fenix/studio/core/designsystem/components/AuditedPropositionCalloutCard.kt
 * ================================================================================
 */

package com.fenix.studio.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fenix.studio.core.designsystem.theme.FenixTheme

/**
 * Política de Tres Estados Epistémicos del Protocolo Fénix v3.0 (Blindaje Anti-Alucinación, DT-ALTA-03).
 */
enum class FenixEpistemicState(
    val badgeLabel: String,
    val headerText: String,
    val isPrimarySource: Boolean,
) {
    FUENTE(
        badgeLabel = "[FUENTE]",
        headerText = "[ \uD83D\uDCD8 TEORÍA PRINCIPAL ]",
        isPrimarySource = true,
    ),
    INFERENCIA(
        badgeLabel = "[INFERENCIA]",
        headerText = "[ \uD83D\uDCCE APORTE COMPLEMENTARIO ]",
        isPrimarySource = false,
    ),
    EXTERNO(
        badgeLabel = "[EXTERNO]",
        headerText = "[ \uD83D\uDCCE APORTE COMPLEMENTARIO ]",
        isPrimarySource = false,
    ),
}

/**
 * Las 6 etiquetas del Desambiguador Ontológico de Proposiciones del Motor Fénix v3.0.
 */
enum class FenixOntologicalTag(val rawTag: String) {
    EMPIRICAL_FACT("[HECHO / DATO EMPÍRICO]"),
    CORRELATION_ASSOCIATION("[CORRELACIÓN / ASOCIACIÓN]"),
    THEORETICAL_HYPOTHESIS("[HIPÓTESIS / MODELO TEÓRICO]"),
    AUTHORIAL_INTERPRETATION("[INTERPRETACIÓN / POSTURA AUTORAL]"),
    FORMAL_AXIOM_PROOF("[DEMOSTRACIÓN / AXIOMA FORMAL]"),
    DEONTIC_NORM_RULE("[NORMA / REGLA DEÓNTICA]"),
}

/**
 * Modelo de presentación inmutable para una proposición académica auditada.
 */
@Immutable
data class AuditedPropositionUiModel(
    val id: String,
    val epistemicState: FenixEpistemicState,
    val ontologicalTag: FenixOntologicalTag,
    val propositionText: String,
    val sourceCitation: String? = null,
)

/**
 * Tarjeta de proposición auditada (DT-ALTA-03) con diferenciación cromática y no-cromática:
 * - Teoría Principal (`[FUENTE]`): barra lateral `6.dp` `#1A73E8`, prefijo `[ 📘 TEORÍA PRINCIPAL ]`, tipografía normal.
 * - Aporte Complementario (`[INFERENCIA]` / `[EXTERNO]`): barra lateral `6.dp` `#FA903E`, prefijo `[ 📎 APORTE COMPLEMENTARIO ]`, tipografía itálica.
 * - Bordes explícitos (`2.dp` en modo OLED Alto Contraste).
 */
@Composable
fun AuditedPropositionCalloutCard(
    model: AuditedPropositionUiModel,
    modifier: Modifier = Modifier,
) {
    val colors = FenixTheme.colors
    val isPrimary = model.epistemicState.isPrimarySource

    val accentBarColor = if (isPrimary) {
        colors.propositionPrimaryTheoryAccent
    } else {
        colors.propositionComplementaryAccent
    }

    val containerColor = if (isPrimary) {
        colors.propositionPrimaryTheoryContainer
    } else {
        colors.propositionComplementaryContainer
    }

    val contentColor = if (isPrimary) {
        colors.onPropositionPrimaryTheoryContainer
    } else {
        colors.onPropositionComplementaryContainer
    }

    val bodyFontStyle = if (isPrimary) FontStyle.Normal else FontStyle.Italic

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(
            width = colors.borderStrokeWidth,
            color = colors.outline,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(accentBarColor),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { heading() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${model.epistemicState.headerText} ${model.epistemicState.badgeLabel}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = model.ontologicalTag.rawTag,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = model.propositionText,
                    style = MaterialTheme.typography.bodyLarge.copy(fontStyle = bodyFontStyle),
                    color = contentColor,
                )

                if (!model.sourceCitation.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = model.sourceCitation,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = contentColor,
                    )
                }
            }
        }
    }
}
