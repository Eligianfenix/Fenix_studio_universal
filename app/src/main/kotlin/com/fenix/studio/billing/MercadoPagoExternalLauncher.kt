/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:37:30 -03:00
 * ARCHIVO: app/src/main/kotlin/com/fenix/studio/billing/MercadoPagoExternalLauncher.kt
 * ================================================================================
 */

package com.fenix.studio.billing

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Lanzador Oficial Externo de Mercado Pago a Costo 0 (`MF-8.5`).
 * Permite recarga opcional de créditos de procesamiento en la nube abriendo el checkout oficial
 * en el navegador web del usuario o en la app de Mercado Pago (`Intent.ACTION_VIEW`),
 * evitando de forma estricta embeber bibliotecas binarias o SDKs pesados dentro del APK de Fénix Studio.
 */
object MercadoPagoExternalLauncher {

    const val BASE_CHECKOUT_URL = "https://www.mercadopago.com.ar/checkout/v1/redirect"

    /**
     * Construye de manera determinista la URL de pago con referencia externa sin rastreo ni telemetría invasiva.
     */
    fun buildCheckoutUrl(
        preferenceId: String,
        externalReference: String = "fenix_topup",
    ): String {
        val sanitizedPreferenceId = URLEncoder.encode(preferenceId.trim(), StandardCharsets.UTF_8.name())
        val sanitizedRef = URLEncoder.encode(externalReference.trim(), StandardCharsets.UTF_8.name())
        return "$BASE_CHECKOUT_URL?pref_id=$sanitizedPreferenceId&external_reference=$sanitizedRef"
    }

    /**
     * Lanza la pasarela externa vía Android Intent estándar.
     */
    fun launchExternalCheckout(
        context: Context,
        preferenceId: String,
        externalReference: String = "fenix_topup",
    ): Boolean {
        return try {
            val targetUrl = buildCheckoutUrl(preferenceId, externalReference)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
