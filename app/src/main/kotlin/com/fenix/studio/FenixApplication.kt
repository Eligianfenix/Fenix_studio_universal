/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:38:15 -03:00
 * ARCHIVO: app/src/main/kotlin/com/fenix/studio/FenixApplication.kt
 * ================================================================================
 */

package com.fenix.studio

import android.app.Application
import com.fenix.studio.crash.SanitizedCrashReporter
import dagger.hilt.android.HiltAndroidApp

/**
 * Aplicación raíz de Fénix Studio con inyección Hilt y Guardián de Fallos Sanitizado (`MF-8.5`).
 */
@HiltAndroidApp
class FenixApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        SanitizedCrashReporter.install(appVersion = BuildConfig.VERSION_NAME)
    }
}
