/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-02 18:19:00 -03:00
 * ARCHIVO: app/src/main/kotlin/com/fenix/studio/MainActivity.kt
 * ================================================================================
 */

package com.fenix.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.fenix.studio.core.designsystem.components.FenixEmberSplashOverlay
import com.fenix.studio.core.designsystem.theme.FenixTheme
import com.fenix.studio.navigation.FenixRootNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

/**
 * Actividad Principal de Fénix Studio (`MF-8.5`).
 * Punto de entrada del sistema con pantalla de carga cinemática de brasas y anclaje de navegación.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FenixTheme {
                var showSplash by rememberSaveable { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    if (showSplash) {
                        // Espera a que el primer fotograma de la animación esté realmente en pantalla
                        withFrameNanos { }
                        delay(4500L)
                        showSplash = false
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    FenixRootNavHost()
                    FenixEmberSplashOverlay(visible = showSplash)
                }
            }
        }
    }
}
