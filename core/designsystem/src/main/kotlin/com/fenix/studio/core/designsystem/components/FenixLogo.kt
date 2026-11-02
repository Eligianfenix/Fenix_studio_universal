/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-02 17:55:00 -03:00
 * ARCHIVO: core/designsystem/src/main/kotlin/com/fenix/studio/core/designsystem/components/FenixLogo.kt
 * ================================================================================
 */

package com.fenix.studio.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fenix.studio.core.designsystem.R
import com.fenix.studio.core.designsystem.theme.FenixTheme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Logotipo Oficial de Fénix Studio usando la imagen real `logo.png`.
 */
@Composable
fun FenixLogo(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    primaryColor: Color = FenixTheme.colors.primary,
    accentColor: Color = Color(0xFFFF6D00),
    isMonochrome: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .semantics {
                contentDescription = "Logotipo Oficial Soberano de Fénix Studio"
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.fenix_official_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
        )
    }
}

/**
 * Cabecera con imagotipo oficial y marca textual ("Fénix Studio").
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
        Column {
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

private data class EmberParticle(
    val angleRad: Float,
    val speedFactor: Float,
    val phaseOffset: Float,
    val radiusPx: Float,
    val swayAmp: Float,
    val swayFreq: Float,
    val colorType: Int,
)

/**
 * Pantalla de Carga Cinemática Inicial de Fénix Studio:
 * Muestra el `logo.png` central con partículas de brasas incandescentes saliendo disparadas
 * desde la imagen hacia toda la pantalla y el título "FÉNIX STUDIO" debajo con letras de fuego.
 */
@Composable
fun FenixEmberSplashOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)),
        modifier = modifier.fillMaxSize(),
    ) {
        val particles = remember {
            val rng = Random(2026)
            List(140) {
                EmberParticle(
                    angleRad = rng.nextFloat() * 6.2831855f,
                    speedFactor = 0.10f + rng.nextFloat() * 0.18f,
                    phaseOffset = rng.nextFloat(),
                    radiusPx = 2.2f + rng.nextFloat() * 5.2f,
                    swayAmp = 4f + rng.nextFloat() * 12f,
                    swayFreq = 0.6f + rng.nextFloat() * 1.2f,
                    colorType = rng.nextInt(4),
                )
            }
        }

        var elapsedSeconds by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) {
            val startNanos = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                elapsedSeconds = (now - startNanos) / 1_000_000_000f
            }
        }

        val infiniteTransition = rememberInfiniteTransition(label = "fire_splash")
        val logoScale by infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "logo_pulse",
        )
        val flameShimmer by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "flame_shimmer",
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF050200)),
            contentAlignment = Alignment.Center,
        ) {
            // Lienzo a pantalla completa de brasas saliendo disparadas desde el centro del Fénix
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width * 0.5f
                val cy = size.height * 0.44f
                val maxRadius = size.height.coerceAtLeast(size.width) * 0.68f
                val innerSpawnRadius = 75.dp.toPx()

                // Resplandor ígneo radial detrás del logo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x66FF6D00),
                            Color(0x33D50000),
                            Color.Transparent,
                        ),
                        center = Offset(cx, cy),
                        radius = 180.dp.toPx(),
                    ),
                    radius = 180.dp.toPx(),
                    center = Offset(cx, cy),
                )

                particles.forEach { p ->
                    val progress = ((elapsedSeconds * p.speedFactor + p.phaseOffset) % 1f)
                    val dist = innerSpawnRadius + progress * (maxRadius - innerSpawnRadius)
                    val perpAngle = p.angleRad + 1.5707964f
                    val sway = sin(progress * p.swayFreq * 6.28318f) * p.swayAmp * progress

                    val px = cx + cos(p.angleRad) * dist + cos(perpAngle) * sway
                    val py = cy + sin(p.angleRad) * dist + sin(perpAngle) * sway

                    val prevDist = (dist - 10f).coerceAtLeast(innerSpawnRadius)
                    val tailX = cx + cos(p.angleRad) * prevDist + cos(perpAngle) * sway * 0.85f
                    val tailY = cy + sin(p.angleRad) * prevDist + sin(perpAngle) * sway * 0.85f

                    val alpha = (1f - progress * 0.85f).coerceIn(0f, 1f)
                    val emberColor = when (p.colorType) {
                        0 -> Color(0xFFFFF59D) // Blanco/Amarillo incandescente
                        1 -> Color(0xFFFFB300) // Oro fuego
                        2 -> Color(0xFFFF6D00) // Naranja brasa
                        else -> Color(0xFFFF3D00) // Rojo brasa
                    }

                    // Estela de la brasa disparada
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(emberColor.copy(alpha = 0f), emberColor.copy(alpha = alpha * 0.85f)),
                            start = Offset(tailX, tailY),
                            end = Offset(px, py),
                        ),
                        start = Offset(tailX, tailY),
                        end = Offset(px, py),
                        strokeWidth = p.radiusPx * 0.9f,
                        cap = StrokeCap.Round,
                    )

                    // Núcleo brillante de la brasa
                    drawCircle(
                        color = emberColor.copy(alpha = alpha),
                        radius = p.radiusPx * (1f - progress * 0.45f),
                        center = Offset(px, py),
                    )
                }
            }

            // Imagen Central del Fénix + "FÉNIX STUDIO" con letras de fuego debajo
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.fenix_official_logo),
                    contentDescription = "Fénix Studio",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(220.dp)
                        .scale(logoScale)
                        .clip(CircleShape),
                )

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "FÉNIX STUDIO",
                    style = TextStyle(
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 4.sp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFFF59D),
                                Color(0xFFFFB300),
                                Color(0xFFFF3D00),
                                Color(0xFFD50000),
                            ),
                        ),
                        shadow = Shadow(
                            color = Color(0xFFFF6D00).copy(alpha = 0.75f + 0.25f * flameShimmer),
                            offset = Offset(0f, 0f),
                            blurRadius = 26f + 10f * flameShimmer,
                        ),
                    ),
                )
            }
        }
    }
}
