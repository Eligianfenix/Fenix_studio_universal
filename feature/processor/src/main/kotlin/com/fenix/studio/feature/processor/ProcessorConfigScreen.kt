/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:54:20 -03:00
 * ARCHIVO: feature/processor/src/main/kotlin/com/fenix/studio/feature/processor/ProcessorConfigScreen.kt
 * ================================================================================
 */

package com.fenix.studio.feature.processor

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fenix.studio.core.database.model.ClassroomModality
import com.fenix.studio.core.designsystem.modifiers.fenixMinTouchTarget
import com.fenix.studio.core.designsystem.theme.FenixTheme

/**
 * Pantalla `P-05` (Configuración del Procesador Académico con 3 Modalidades Soberanas:
 * - `CATEDRA_PROGRAMA` (`RN-18`, exige `programa_year`),
 * - `DOCUMENTO_AISLADO` (`RN-19`, sin programa oficial),
 * - `PROYECTO_GUIADO` (`RN-20`, tesis, casos prácticos, investigación libre)).
 */
@Composable
fun ProcessorConfigScreen(
    state: ProcessorWizardUiState,
    onSelectModality: (ClassroomModality) -> Unit,
    onTitleChanged: (String) -> Unit,
    onProgramaYearChanged: (String) -> Unit,
    onConfirmConfiguration: () -> Unit,
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
            text = "Configuración del Procesador Académico (P-05)",
            style = typography.titleMedium,
            color = colors.onBackground,
        )

        Text(
            text = "Selecciona una de las 3 Modalidades Soberanas de Estudio",
            style = typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )

        ClassroomModality.entries.forEach { modality ->
            val isSelected = state.modality == modality
            val label = when (modality) {
                ClassroomModality.CATEDRA_PROGRAMA -> "1. Cátedra Oficial (Con Programa Anual · RN-18)"
                ClassroomModality.DOCUMENTO_AISLADO -> "2. Estudio Libre de Documento Aislado (Sin Programa · RN-19)"
                ClassroomModality.PROYECTO_GUIADO -> "3. Proyecto o Investigación Guiada (Tesis / Casos · RN-20)"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) colors.surface else colors.background)
                    .border(
                        width = if (isSelected) 2.dp else colors.borderStrokeWidth,
                        color = if (isSelected) colors.primary else colors.outline,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelectModality(modality) }
                    .fenixMinTouchTarget()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Denominación del Espacio Curricular, Libro o Proyecto (Definido por el Usuario)",
                style = typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            BasicTextField(
                value = state.subjectOrProjectTitle,
                onValueChange = onTitleChanged,
                textStyle = typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .border(colors.borderStrokeWidth, colors.outline, RoundedCornerShape(10.dp))
                    .fenixMinTouchTarget()
                    .padding(14.dp),
            )
        }

        if (state.modality == ClassroomModality.CATEDRA_PROGRAMA) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Año de Vigencia del Programa Oficial (Obligatorio RN-18)",
                    style = typography.labelMedium,
                    color = colors.primary,
                )
                BasicTextField(
                    value = state.programaYearInput,
                    onValueChange = onProgramaYearChanged,
                    textStyle = typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface)
                        .border(colors.borderStrokeWidth, colors.primary, RoundedCornerShape(10.dp))
                        .fenixMinTouchTarget()
                        .padding(14.dp),
                )
            }
        }

        state.validationErrorMessage?.let { errorMsg ->
            Text(
                text = errorMsg,
                style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.error,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.primary)
                .clickable(onClick = onConfirmConfiguration)
                .fenixMinTouchTarget()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Continuar a Matriz Curricular (P-05b)",
                style = typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.onPrimary,
            )
        }
    }
}
