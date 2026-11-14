/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/network-ai/src/test/kotlin/com/fenix/studio/core/network/supabase/SupabaseAuthAndDataGatewayTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.network.supabase

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SupabaseAuthAndDataGatewayTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun signInWithPassword_success_returnsSession() = runTest {
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/auth/v1/token")) {
                respond(
                    content = """{"access_token":"fake_token_123","user":{"id":"user_abc_1","email":"test@gmail.com"}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond("Not Found", HttpStatusCode.NotFound)
            }
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }
        val gateway = SupabaseAuthAndDataGateway(client, json)

        val result = gateway.signInWithPassword("test@gmail.com", "pass123")

        assertTrue(result.isSuccess)
        val session = result.getOrThrow()
        assertEquals("fake_token_123", session.access_token)
        assertEquals("user_abc_1", session.user?.id)
    }

    @Test
    fun fetchProfile_success_returnsUsuarioPerfil() = runTest {
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/rest/v1/usuarios_perfiles")) {
                respond(
                    content = """[{"id":"user_abc_1","email":"test@gmail.com","role":"admin","credits":15,"user_type":"beta_tester"}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond("Not Found", HttpStatusCode.NotFound)
            }
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }
        val gateway = SupabaseAuthAndDataGateway(client, json)

        val result = gateway.fetchProfile("fake_token_123", "user_abc_1")

        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals("admin", profile.role)
        assertEquals(15, profile.credits)
        assertEquals("beta_tester", profile.user_type)
    }

    @Test
    fun redeemLibraryDownload_success_returnsEphemeralToken() = runTest {
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/rpc/canjear_descarga_biblioteca")) {
                respond(
                    content = """{"success":true,"download_token":"ephemeral_token_xyz","credits_remaining":14}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond("Not Found", HttpStatusCode.NotFound)
            }
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }
        val gateway = SupabaseAuthAndDataGateway(client, json)

        val result = gateway.redeemLibraryDownload("fake_token_123", "material_uuid_456")

        assertTrue(result.isSuccess)
        val redeem = result.getOrThrow()
        assertTrue(redeem.success)
        assertEquals("ephemeral_token_xyz", redeem.download_token)
        assertEquals(14, redeem.credits_remaining)
    }
}

