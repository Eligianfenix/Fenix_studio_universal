/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 03:26:15 -03:00
 * ARCHIVO: core/designsystem/src/main/kotlin/com/fenix/studio/core/designsystem/components/FenixLogo.kt
 * ================================================================================
 */

package com.fenix.studio.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fenix.studio.core.designsystem.theme.FenixTheme

/**
 * Imagotipo Vectorial Soberano de Fénix Studio (`MF-D.1`).
 * Dibuja de manera determinista mediante Canvas las alas protectoras del Ave Fénix
 * entrelazadas con las páginas abiertas del saber académico universal.
 * Cumple con costo 0 absoluto, máxima nitidez vectorial en cualquier DPI y accesibilidad.
 */
@Composable
fun FenixLogo(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    primaryColor: Color = FenixTheme.colors.primary,
    accentColor: Color = Color(0xFFFF6D00), // Fénix Ámbar Fuego
    isMonochrome: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = "Logotipo Oficial Soberano de Fénix Studio"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val width = this.size.width
            val height = this.size.height

            val primaryPaintColor = if (isMonochrome) primaryColor else primaryColor
            val flameColor = if (isMonochrome) primaryColor else accentColor

            drawFenixEmblem(
                width = width,
                height = height,
                primaryColor = primaryPaintColor,
                flameColor = flameColor,
            )
        }
    }
}

/**
 * Dibuja las alas ascendentes, el libro base y la corona de fuego del Fénix.
 */
private fun DrawScope.drawFenixEmblem(
    width: Float,
    height: Float,
    primaryColor: Color,
    flameColor: Color,
) {
    val strokeWidth = (width * 0.07f).coerceAtLeast(2f)

    // 1. Libro Abierto (Base del Conocimiento)
    val bookPath = Path().apply {
        // Página Izquierda
        moveTo(width * 0.12f, height * 0.78f)
        cubicTo(
            width * 0.28f, height * 0.70f,
            width * 0.42f, height * 0.74f,
            width * 0.50f, height * 0.85f,
        )
        // Página Derecha
        cubicTo(
            width * 0.58f, height * 0.74f,
            width * 0.72f, height * 0.70f,
            width * 0.88f, height * 0.78f,
        )
    }

    drawPath(
        path = bookPath,
        color = primaryColor,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )

    // 2. Alas Ascendentes del Fénix (Curvas de Renacimiento)
    val leftWing = Path().apply {
        moveTo(width * 0.50f, height * 0.85f)
        cubicTo(
            width * 0.30f, height * 0.55f,
            width * 0.08f, height * 0.40f,
            width * 0.15f, height * 0.15f,
        )
        cubicTo(
            width * 0.22f, height * 0.32f,
            width * 0.38f, height * 0.45f,
            width * 0.46f, height * 0.58f,
        )
    }

    val rightWing = Path().apply {
        moveTo(width * 0.50f, height * 0.85f)
        cubicTo(
            width * 0.70f, height * 0.55f,
            width * 0.92f, height * 0.40f,
            width * 0.85f, height * 0.15f,
        )
        cubicTo(
            width * 0.78f, height * 0.32f,
            width * 0.62f, height * 0.45f,
            width * 0.54f, height * 0.58f,
        )
    }

    drawPath(
        path = leftWing,
        brush = Brush.verticalGradient(
            colors = listOf(flameColor, primaryColor),
        ),
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )

    drawPath(
        path = rightWing,
        brush = Brush.verticalGradient(
            colors = listOf(flameColor, primaryColor),
        ),
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )

    // 3. Llama Central Superior (Núcleo de Innovación y Claridad Intelectual)
    val centerFlame = Path().apply {
        moveTo(width * 0.50f, height * 0.12f)
        cubicTo(
            width * 0.42f, height * 0.28f,
            width * 0.42f, height * 0.42f,
            width * 0.50f, height * 0.52f,
        )
        cubicTo(
            width * 0.58f, height * 0.42f,
            width * 0.58f, height * 0.28f,
            width * 0.50f, height * 0.12f,
        )
        close()
    }

    drawPath(
        path = centerFlame,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFFD54F), flameColor),
        ),
    )
}

/**
 * Cabecera con imagotipo y marca textual soberana ("Fénix Studio").
 */
@Composable
fun FenixBrandHeader(
    modifier: Modifier = Modifier,
    logoSize: Dp = 36.dp,
    subtitle: String? = null,
) {
    val colors = FenixTheme.colors
    val typography = MaterialTheme.typography

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FenixLogo(size = logoSize)
        Spacer(modifier = Modifier.width(10.dp))
        androidx.compose.foundation.layout.Column {
            Text(
                text = "Fénix Studio",
                style = typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.onBackground,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
