/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/designsystem/src/main/kotlin/com/fenix/studio/core/designsystem/modifiers/FenixAccessibilityModifiers.kt
 * ================================================================================
 */

package com.fenix.studio.core.designsystem.modifiers

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.Dp
import com.fenix.studio.core.designsystem.theme.LocalFenixTouchTargetSize

private const val SYNTHETIC_DENSITY_STAR = "\u2726"

/**
 * Modelo inmutable de presentación para puntos y subpuntos por densidad teórica (`N.N.a ✦`).
 */
@Immutable
data class FenixSubpointUiModel(
    val displayCode: String,
    val title: String,
    val isSyntheticDensityNode: Boolean,
)

/**
 * Formatea la descripción hablada para lectores de pantalla (TalkBack),
 * eliminando el glifo `✦` (`\u2726`) para impedir la lectura literal "cuatro puntas estrella negra"
 * y anteponiendo la categoría semántica `"Subpunto temático"` (DT-BAJA-01).
 */
fun formatSubpointSpokenDescription(subpoint: FenixSubpointUiModel): String {
    val sanitizedCode = subpoint.displayCode
        .replace(SYNTHETIC_DENSITY_STAR, "")
        .trim()
    val sanitizedTitle = subpoint.title
        .replace(SYNTHETIC_DENSITY_STAR, "")
        .trim()

    val isSubpoint = subpoint.isSyntheticDensityNode ||
        subpoint.displayCode.contains(SYNTHETIC_DENSITY_STAR) ||
        subpoint.title.contains(SYNTHETIC_DENSITY_STAR)

    val prefix = if (isSubpoint) "Subpunto temático" else "Punto temático"
    return if (sanitizedCode.isNotEmpty()) {
        "$prefix $sanitizedCode: $sanitizedTitle"
    } else {
        "$prefix: $sanitizedTitle"
    }
}

/**
 * Modificador semántico que reemplaza la lectura cruda de `✦` por una locución académica limpia
 * y marca el nodo como encabezado navegable por gestos de TalkBack.
 */
fun Modifier.fenixSubpointAccessibility(subpoint: FenixSubpointUiModel): Modifier {
    val spokenText = formatSubpointSpokenDescription(subpoint)
    return this.clearAndSetSemantics {
        contentDescription = spokenText
        heading()
    }
}

/**
 * Garantiza que cualquier control interactivo cumpla el tamaño mínimo de zona táctil (Macro-UI)
 * provisto por `LocalFenixTouchTargetSize` (>= 48.dp, 56.dp o 64.dp).
 */
@Composable
fun Modifier.fenixMinTouchTarget(overrideMinSize: Dp? = null): Modifier {
    val minTargetSize = overrideMinSize ?: LocalFenixTouchTargetSize.current
    return this.defaultMinSize(
        minWidth = minTargetSize,
        minHeight = minTargetSize,
    )
}
