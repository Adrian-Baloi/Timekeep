package com.example.timekeep_work

import com.example.timekeep_work.auth.HaloDiscoveryDocument
import com.example.timekeep_work.network.dto.toAgent
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloAuthModelsTest {
    @Test
    fun `S256 PKCE discovery is recognized`() {
        val discovery = HaloDiscoveryDocument(
            authorizationEndpoint = "https://tenant.example/auth/authorize",
            tokenEndpoint = "https://tenant.example/auth/token",
            codeChallengeMethodsSupported = listOf("S256"),
            tokenEndpointAuthMethodsSupported = listOf("client_secret_basic", "client_secret_post"),
        )

        assertTrue(discovery.supportsS256Pkce)
    }

    @Test
    fun `Agent me payload maps to the existing Agent model`() {
        val payload = Json.parseToJsonElement(
            """{"id":36,"firstname":"Adrian","surname":"Baloi","email":"agent@example.com"}""",
        )

        assertEquals(
            Agent(name = "Adrian Baloi", email = "agent@example.com", id = 36),
            payload.toAgent(),
        )
    }
}
