/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:37:05 -03:00
 * ARCHIVO: app/src/main/kotlin/com/fenix/studio/crash/SanitizedCrashReporter.kt
 * ================================================================================
 */

package com.fenix.studio.crash

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fenix.studio.core.designsystem.theme.FenixTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Reporte técnico forense de error 100% sanitizado de datos personales (PII, `MF-8.5`).
 */
data class SanitizedCrashData(
    val exceptionClass: String,
    val exceptionMessage: String,
    val sanitizedStackTrace: String,
    val appVersion: String,
    val androidSdk: Int,
    val deviceModel: String,
    val timestampEpochMs: Long,
)

/**
 * Guardián de Fallos y Reportero Forense Sanitizado a Costo 0 (`MF-8.5`).
 * Atrapa excepciones no capturadas a nivel raíz del hilo principal y secundario,
 * eliminando tokens, correos o rutas locales antes de permitir el envío voluntario
 * al canal oficial del creador (Eli Gian Cortez Leonardi) para diagnóstico pericial.
 */
object SanitizedCrashReporter {

    private val _lastCrashFlow = MutableStateFlow<SanitizedCrashData?>(null)
    val lastCrashFlow: StateFlow<SanitizedCrashData?> = _lastCrashFlow.asStateFlow()

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun install(appVersion: String = "3.0.0") {
        if (defaultHandler != null) return
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashData = createSanitizedReport(throwable, appVersion)
                _lastCrashFlow.value = crashData
            } catch (_: Throwable) {
                // Silenciado defensivo para no entrar en bucle de fallo
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    /**
     * Sanitiza el stacktrace removiendo rutas de usuario, emails, tokens de autenticación y contraseñas.
     */
    fun sanitizeText(raw: String): String {
        return raw
            // Sanitizar rutas de usuario en Linux / Android
            .replace(Regex("/data/user/\\d+/[a-zA-Z0-9_.-]+"), "/data/user/[REDACTED]")
            .replace(Regex("/home/[a-zA-Z0-9_.-]+"), "/home/[REDACTED]")
            .replace(Regex("/Users/[a-zA-Z0-9_.-]+"), "/Users/[REDACTED]")
            // Sanitizar tokens y credenciales
            .replace(Regex("(?i)(bearer\\s+)[A-Za-z0-9_.-]+"), "$1[TOKEN_REDACTED]")
            .replace(Regex("(?i)(key=)[A-Za-z0-9_.-]+"), "$1[KEY_REDACTED]")
            .replace(Regex("(?i)(password=)[^&\\s]+"), "$1[PASSWORD_REDACTED]")
            // Sanitizar correos electrónicos
            .replace(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"), "[EMAIL_REDACTED]")
    }

    fun createSanitizedReport(throwable: Throwable, appVersion: String): SanitizedCrashData {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val rawTrace = sw.toString()

        return SanitizedCrashData(
            exceptionClass = throwable.javaClass.simpleName,
            exceptionMessage = sanitizeText(throwable.message ?: "Sin mensaje detallado"),
            sanitizedStackTrace = sanitizeText(rawTrace),
            appVersion = appVersion,
            androidSdk = Build.VERSION.SDK_INT,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            timestampEpochMs = System.currentTimeMillis(),
        )
    }

    fun formatTelegramPayload(data: SanitizedCrashData): String {
        return buildString {
            append("🚨 *Reporte Técnico de Fallo Sanitizado — Fénix Studio*\n")
            append("• *Excepción:* `${data.exceptionClass}`\n")
            append("• *Mensaje:* `${data.exceptionMessage}`\n")
            append("• *Versión Fénix:* ${data.appVersion}\n")
            append("• *Dispositivo:* ${data.deviceModel} (SDK ${data.androidSdk})\n")
            append("• *Fecha:* ${data.timestampEpochMs}\n\n")
            append("*Stacktrace Sanitizado (0% PII):*\n```\n")
            append(data.sanitizedStackTrace.take(1500))
            append("\n```")
        }
    }

    fun dismissLastCrash() {
        _lastCrashFlow.value = null
    }
}

/**
 * Diálogo Compose accesible y no intrusivo ante fallos no capturados (`MF-8.5`).
 */
@Composable
fun SanitizedCrashDialog(
    crashData: SanitizedCrashData,
    onSendVoluntaryReportClick: (SanitizedCrashData) -> Unit,
    onDismissClick: () -> Unit,
) {
    val colors = FenixTheme.colors
    val typography = MaterialTheme.typography

    AlertDialog(
        onDismissRequest = onDismissClick,
        title = {
            Text(
                text = "Fénix Studio — Notificación de Fallo",
                style = typography.titleMedium,
                color = colors.onSurface,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Ocurrió una interrupción imprevista. El sistema ha protegido tus datos locales.",
                    style = typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = "Detalle Técnico Sanitizado:",
                    style = typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.background)
                        .border(colors.borderStrokeWidth, colors.outline, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                ) {
                    Text(
                        text = "${crashData.exceptionClass}: ${crashData.exceptionMessage}",
                        style = typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = colors.error,
                    )
                }
                Text(
                    text = "Puedes enviar este reporte técnico de forma voluntaria y 100% anónima para ayudar a mejorar la plataforma.",
                    style = typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSendVoluntaryReportClick(crashData) }) {
                Text("[ Enviar Reporte Anónimo ]")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismissClick) {
                Text("[ Continuar ]")
            }
        },
    )
}
