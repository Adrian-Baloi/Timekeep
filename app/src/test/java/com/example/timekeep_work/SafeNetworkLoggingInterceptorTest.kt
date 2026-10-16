package com.example.timekeep_work

import com.example.timekeep_work.network.HaloFailureSanitizer
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeNetworkLoggingInterceptorTest {
    @Test
    fun authenticationHeaderKeepsScopeErrorAndRedactsBearerValue() {
        val sanitized = HaloFailureSanitizer.sanitizeHeader(
            "Bearer token-value-that-must-be-redacted, error=\"insufficient_scope\", scope=\"edit:tickets\"",
        )

        assertFalse(sanitized.contains("token-value-that-must-be-redacted"))
        assertTrue(sanitized.contains("insufficient_scope"))
        assertTrue(sanitized.contains("edit:tickets"))
    }

    @Test
    fun standardAuthenticationChallengeKeepsErrorName() {
        val sanitized = HaloFailureSanitizer.sanitizeHeader(
            "Bearer error=\"invalid_token\", error_description=\"The token is expired\"",
        )

        assertTrue(sanitized.contains("error=\"invalid_token\""))
        assertTrue(sanitized.contains("The token is expired"))
    }

    @Test
    fun errorBodyRedactsCredentialsAccountAndAssetIdentity() {
        val sanitized = HaloFailureSanitizer.sanitizeBody(
            """
                {
                  "error": "invalid_token",
                  "error_description": "Cannot update WLT-123 for person@example.com",
                  "access_token": "eyJhbGciOiJIUzI1NiJ9.payload.signature",
                  "account_number": "ACCOUNT-42",
                  "serial_number": "SERIAL-123"
                }
            """.trimIndent(),
        )

        assertTrue(sanitized.contains("invalid_token"))
        assertFalse(sanitized.contains("WLT-123"))
        assertFalse(sanitized.contains("person@example.com"))
        assertFalse(sanitized.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertFalse(sanitized.contains("ACCOUNT-42"))
        assertFalse(sanitized.contains("SERIAL-123"))
    }

    @Test
    fun onlyKnownTextualMediaTypesAreCaptured() {
        assertTrue(HaloFailureSanitizer.isTextual("application/problem+json".toMediaType()))
        assertTrue(HaloFailureSanitizer.isTextual("text/plain".toMediaType()))
        assertFalse(HaloFailureSanitizer.isTextual("application/octet-stream".toMediaType()))
    }

    @Test
    fun correlationHeaderAllowlistIsNarrow() {
        assertTrue(HaloFailureSanitizer.isCorrelationHeader("X-Request-ID"))
        assertTrue(HaloFailureSanitizer.isCorrelationHeader("traceparent"))
        assertFalse(HaloFailureSanitizer.isCorrelationHeader("Authorization"))
        assertFalse(HaloFailureSanitizer.isCorrelationHeader("Set-Cookie"))
    }

    @Test
    fun emptyBodyHasStableDiagnostic() {
        assertEquals("<empty>", HaloFailureSanitizer.sanitizeBody(" \n "))
    }
}
