package com.example.timekeep_work

import com.example.timekeep_work.scanner.IdentifierNormalizationResult
import com.example.timekeep_work.scanner.IdentifierNormalizer
import com.example.timekeep_work.scanner.IdentifierRules
import com.example.timekeep_work.scanner.InvalidIdentifierReason
import com.example.timekeep_work.scanner.KnownPayloadRule
import com.example.timekeep_work.scanner.KnownQrUrlRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentifierNormalizerTest {
    @Test
    fun `plain serial is trimmed and normalized with locale-independent uppercase`() {
        val result = IdentifierNormalizer().normalize("  pf4x91q2  ")

        assertSuccess("PF4X91Q2", result)
    }

    @Test
    fun `only an explicitly approved prefix is removed`() {
        val normalizer = IdentifierNormalizer(
            IdentifierRules(approvedPrefixes = listOf("SERIAL:")),
        )

        assertSuccess("PF4X91Q2", normalizer.normalize("serial: pf4x91q2"))
        assertInvalid(
            InvalidIdentifierReason.UnsupportedFormat,
            IdentifierNormalizer().normalize("serial: pf4x91q2"),
        )
    }

    @Test
    fun `serial is extracted only from an explicitly known QR URL`() {
        val normalizer = IdentifierNormalizer(
            IdentifierRules(
                knownQrUrls = listOf(
                    KnownQrUrlRule(
                        host = "inventory.example",
                        pathPrefix = "/assets/",
                        serialQueryParameter = "serial",
                    ),
                ),
            ),
        )

        assertSuccess(
            "PF4X91Q2",
            normalizer.normalize("https://inventory.example/assets/view?serial=pf4x91q2"),
        )
        assertInvalid(
            InvalidIdentifierReason.UnsupportedFormat,
            normalizer.normalize("https://unapproved.example/assets/view?serial=pf4x91q2"),
        )
    }

    @Test
    fun `serial is extracted only from an explicitly known structured payload key`() {
        val normalizer = IdentifierNormalizer(
            IdentifierRules(knownPayloads = listOf(KnownPayloadRule(serialKey = "SERIAL"))),
        )

        assertSuccess("PF4X91Q2", normalizer.normalize("ASSET=NF-10482;SERIAL=pf4x91q2"))
        assertInvalid(
            InvalidIdentifierReason.UnsupportedFormat,
            IdentifierNormalizer().normalize("ASSET=NF-10482;SERIAL=pf4x91q2"),
        )
    }

    @Test
    fun `empty and malformed values are rejected`() {
        val normalizer = IdentifierNormalizer()

        assertInvalid(InvalidIdentifierReason.Empty, normalizer.normalize("   "))
        assertInvalid(InvalidIdentifierReason.Empty, normalizer.normalize(null))
        assertInvalid(InvalidIdentifierReason.UnsupportedFormat, normalizer.normalize("serial with spaces"))
        assertInvalid(InvalidIdentifierReason.UnsupportedFormat, normalizer.normalize("{\"serial\":\"PF4X91Q2\"}"))
    }

    private fun assertSuccess(expected: String, actual: IdentifierNormalizationResult) {
        assertTrue(actual is IdentifierNormalizationResult.Success)
        actual as IdentifierNormalizationResult.Success
        assertEquals(expected, actual.identifier.value)
    }

    private fun assertInvalid(expected: InvalidIdentifierReason, actual: IdentifierNormalizationResult) {
        assertTrue(actual is IdentifierNormalizationResult.Invalid)
        actual as IdentifierNormalizationResult.Invalid
        assertEquals(expected, actual.reason)
    }
}
