/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:38:25 -03:00
 * ARCHIVO: app/src/main/kotlin/com/fenix/studio/MainActivity.kt
 * ================================================================================
 */

package com.fenix.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fenix.studio.core.designsystem.theme.FenixTheme
import com.fenix.studio.navigation.FenixRootNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * Actividad Principal de Fénix Studio (`MF-8.5`).
 * Punto de entrada del sistema con inicialización de tema accesible y anclaje de navegación.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FenixTheme {
                FenixRootNavHost()
            }
        }
    }
}
