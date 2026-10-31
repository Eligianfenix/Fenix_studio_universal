/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:38:45 -03:00
 * ARCHIVO: app/src/test/kotlin/com/fenix/studio/RootNavigationIntegrationTest.kt
 * ================================================================================
 */

package com.fenix.studio

import com.fenix.studio.billing.MercadoPagoExternalLauncher
import com.fenix.studio.crash.SanitizedCrashReporter
import com.fenix.studio.navigation.FenixDestinations
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RootNavigationIntegrationTest {

    @Test
    fun `fenix root navigation graph contains all 10 canonical destinations without collisions`() {
        val destinations = listOf(
            FenixDestinations.P01_ONBOARDING,
            FenixDestinations.P02_HOME_CHAT,
            FenixDestinations.P03_SETTINGS,
            FenixDestinations.P04_BYOK_VAULT,
            FenixDestinations.P05_PROCESSOR_CONFIG,
            FenixDestinations.P05B_CURRICULAR_MATRIX,
            FenixDestinations.P06_PROCESSING_MONITOR,
            FenixDestinations.P07_CLASSROOMS,
            FenixDestinations.P08_CLASSROOM_DETAIL,
            FenixDestinations.P09_FENIX_DOCS,
            FenixDestinations.P10_COMMUNITY,
        )

        assertEquals(11, destinations.size)
        val uniqueDestinations = destinations.toSet()
        assertEquals(11, uniqueDestinations.size, "No debe haber colisiones en las rutas de navegación")

        destinations.forEach { dest ->
            assertTrue(dest.isNotBlank())
            assertTrue(dest.startsWith("p0") || dest.startsWith("p1"))
        }
    }

    @Test
    fun `sanitized crash reporter purges personal data tokens and user paths forensically`() {
        val sampleDirtyTrace = """
            java.lang.IllegalArgumentException: Error procesando archivo /data/user/0/com.fenix.studio/cache/apunte.pdf
            at com.fenix.studio.engine.Processor.run(Processor.kt:42)
            Caused by: AuthError: Authorization header: Bearer sk-ant-api03-abcdef123456789
            User email: student_private@unsj.edu.ar with password=SuperSecretPassword123
            Local path: /home/eli_cortez/fenix_studio/build/tmp
        """.trimIndent()

        val sanitized = SanitizedCrashReporter.sanitizeText(sampleDirtyTrace)

        // Verificación forense: Cero PII
        assertFalse(sanitized.contains("/data/user/0/com.fenix.studio"))
        assertTrue(sanitized.contains("/data/user/[REDACTED]"))

        assertFalse(sanitized.contains("/home/eli_cortez"))
        assertTrue(sanitized.contains("/home/[REDACTED]"))

        assertFalse(sanitized.contains("student_private@unsj.edu.ar"))
        assertTrue(sanitized.contains("[EMAIL_REDACTED]"))

        assertFalse(sanitized.contains("sk-ant-api03-abcdef123456789"))
        assertTrue(sanitized.contains("[TOKEN_REDACTED]"))

        assertFalse(sanitized.contains("SuperSecretPassword123"))
        assertTrue(sanitized.contains("[PASSWORD_REDACTED]"))

        // Se preserva la traza técnica y número de línea
        assertTrue(sanitized.contains("IllegalArgumentException"))
        assertTrue(sanitized.contains("Processor.kt:42"))

        val mockThrowable = RuntimeException("Fallo simulado en prueba unitaria")
        val crashData = SanitizedCrashReporter.createSanitizedReport(mockThrowable, "3.0.0")
        assertNotNull(crashData)
        assertEquals("RuntimeException", crashData.exceptionClass)

        val telegramPayload = SanitizedCrashReporter.formatTelegramPayload(crashData)
        assertTrue(telegramPayload.contains("🚨 *Reporte Técnico de Fallo Sanitizado — Fénix Studio*"))
        assertTrue(telegramPayload.contains("Versión Fénix:* 3.0.0"))
    }

    @Test
    fun `mercadopago external launcher constructs safe clean url without embedded sdks`() {
        val preferenceId = "pref_998877665544"
        val ref = "topup_student_100_tokens"

        val checkoutUrl = MercadoPagoExternalLauncher.buildCheckoutUrl(
            preferenceId = preferenceId,
            externalReference = ref,
        )

        assertTrue(checkoutUrl.startsWith(MercadoPagoExternalLauncher.BASE_CHECKOUT_URL))
        assertTrue(checkoutUrl.contains("pref_id=$preferenceId"))
        assertTrue(checkoutUrl.contains("external_reference=$ref"))

        // Regla 1: Cero rastreo invasivo o SDKs pesados
        assertFalse(checkoutUrl.contains("facebook"))
        assertFalse(checkoutUrl.contains("google_analytics"))
        assertFalse(checkoutUrl.contains("mixpanel"))
    }
}
