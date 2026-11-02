/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/network-ai/src/main/kotlin/com/fenix/studio/core/network/supabase/SupabaseAuthAndDataGateway.kt
 * ================================================================================
 */

package com.fenix.studio.core.network.supabase

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SupabaseAuthSession(
    val access_token: String,
    val token_type: String = "bearer",
    val expires_in: Long = 3600,
    val refresh_token: String? = null,
    val user: SupabaseAuthUser? = null,
)

@Serializable
data class SupabaseAuthUser(
    val id: String,
    val email: String? = null,
)

@Serializable
data class UsuarioPerfilRemote(
    val id: String,
    val email: String,
    val display_name: String? = null,
    val role: String = "user",
    val user_type: String = "normal",
    val credits: Int = 5,
    val processor_usage_weekly: Int = 0,
    val device_id: String? = null,
    val beta_status: String = "no_aplica",
    val last_beta_form_date: String? = null,
)

@Serializable
data class AnuncianteRemote(
    val id: String,
    val local_name: String,
    val product_service_type: String,
    val tier: String = "bronce",
    val anuncio_1: String? = null,
    val anuncio_2: String? = null,
    val anuncio_3: String? = null,
    val is_active: Boolean = true,
)

@Serializable
data class RedeemDownloadResult(
    val success: Boolean,
    val download_token: String? = null,
    val expires_at: String? = null,
    val credits_remaining: Int = 0,
)

/**
 * Pasarela central de red para la integración de Fénix Studio con Supabase.
 * Diseñada bajo Costo 0, Ktor de alto rendimiento y cumplimiento estricto de RLS.
 */
@Singleton
class SupabaseAuthAndDataGateway @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
) {
    companion object {
        const val BASE_URL = "https://vhuscwlftjmukvqdtyab.supabase.co"
        const val ANON_KEY = "sb_publishable_NRLCgOJkYMyU6Zx8MYSvfA_Eo0UNSIZ"
    }

    private fun commonHeaders(builder: io.ktor.client.request.HttpRequestBuilder, userToken: String? = null) {
        builder.header("apikey", ANON_KEY)
        val token = userToken ?: ANON_KEY
        builder.header("Authorization", "Bearer $token")
    }

    /**
     * Iniciar sesión con email y contraseña.
     */
    suspend fun signInWithPassword(email: String, pass: String): Result<SupabaseAuthSession> = runCatching {
        val response = httpClient.post("$BASE_URL/auth/v1/token?grant_type=password") {
            contentType(ContentType.Application.Json)
            commonHeaders(this)
            setBody(mapOf("email" to email, "password" to pass))
        }
        if (!response.status.isSuccess()) {
            error("Error de autenticación (${response.status.value})")
        }
        json.decodeFromString<SupabaseAuthSession>(response.body<String>())
    }

    /**
     * Registrar nuevo usuario con asignación automática de rol y créditos según sea beta tester o normal.
     */
    suspend fun signUp(email: String, pass: String, fullName: String, isBeta: Boolean): Result<SupabaseAuthSession> = runCatching {
        val payload = mapOf(
            "email" to email,
            "password" to pass,
            "data" to mapOf("full_name" to fullName, "is_beta" to isBeta)
        )
        val response = httpClient.post("$BASE_URL/auth/v1/signup") {
            contentType(ContentType.Application.Json)
            commonHeaders(this)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            error("Error de registro (${response.status.value})")
        }
        json.decodeFromString<SupabaseAuthSession>(response.body<String>())
    }

    /**
     * Consultar el perfil y balance de créditos del usuario.
     */
    suspend fun fetchProfile(userToken: String, userId: String): Result<UsuarioPerfilRemote> = runCatching {
        val response = httpClient.get("$BASE_URL/rest/v1/usuarios_perfiles?id=eq.$userId&select=*") {
            commonHeaders(this, userToken)
        }
        val list = json.decodeFromString<List<UsuarioPerfilRemote>>(response.body<String>())
        list.firstOrNull() ?: error("Perfil no encontrado")
    }

    /**
     * Vincular el identificador único de hardware del teléfono al perfil del usuario.
     */
    suspend fun bindDevice(userToken: String, userId: String, deviceId: String): Result<Unit> = runCatching {
        httpClient.patch("$BASE_URL/rest/v1/usuarios_perfiles?id=eq.$userId") {
            contentType(ContentType.Application.Json)
            commonHeaders(this, userToken)
            setBody(mapOf("device_id" to deviceId))
        }
    }

    /**
     * Obtener el catálogo de anunciantes activos para mostrar en la app.
     */
    suspend fun fetchActiveAdvertisers(): Result<List<AnuncianteRemote>> = runCatching {
        val response = httpClient.get("$BASE_URL/rest/v1/anunciantes?is_active=eq.true&select=*") {
            commonHeaders(this)
        }
        json.decodeFromString<List<AnuncianteRemote>>(response.body<String>())
    }

    /**
     * Canjear créditos por un token efímero de descarga de cátedra en la biblioteca.
     */
    suspend fun redeemLibraryDownload(userToken: String, materialId: String): Result<RedeemDownloadResult> = runCatching {
        val response = httpClient.post("$BASE_URL/rest/v1/rpc/canjear_descarga_biblioteca") {
            contentType(ContentType.Application.Json)
            commonHeaders(this, userToken)
            setBody(mapOf("p_material_id" to materialId))
        }
        json.decodeFromString<RedeemDownloadResult>(response.body<String>())
    }

    /**
     * Enviar comprobante de transferencia con los datos extraídos por el OCR local.
     */
    suspend fun submitTransferPayment(
        userToken: String,
        userId: String,
        amountArs: Double,
        creditsRequested: Int,
        titularOrigen: String,
        nroOperacion: String,
        ocrRawText: String,
    ): Result<Unit> = runCatching {
        val payload = mapOf(
            "user_id" to userId,
            "amount_ars" to amountArs.toString(),
            "credits_added" to creditsRequested.toString(),
            "titular_origen" to titularOrigen,
            "comprobante_nro_operacion" to nroOperacion,
            "ocr_raw_text" to ocrRawText,
            "status" to "pending"
        )
        httpClient.post("$BASE_URL/rest/v1/transacciones_recargas") {
            contentType(ContentType.Application.Json)
            commonHeaders(this, userToken)
            setBody(payload)
        }
    }
}

