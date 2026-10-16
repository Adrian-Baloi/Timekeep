package com.example.timekeep_work.scanner

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

@JvmInline
value class NormalizedIdentifier internal constructor(val value: String) {
    init {
        require(value.isNotBlank())
    }

    override fun toString(): String = value
}

enum class InvalidIdentifierReason {
    Empty,
    UnsupportedFormat,
}

sealed interface IdentifierNormalizationResult {
    data class Success(val identifier: NormalizedIdentifier) : IdentifierNormalizationResult

    data class Invalid(val reason: InvalidIdentifierReason) : IdentifierNormalizationResult
}

data class IdentifierRules(
    val serialPattern: Regex = Regex("^[A-Z0-9][A-Z0-9._-]{0,63}$"),
    val approvedPrefixes: List<String> = emptyList(),
    val knownQrUrls: List<KnownQrUrlRule> = emptyList(),
    val knownPayloads: List<KnownPayloadRule> = emptyList(),
)

/** Describes an explicitly trusted QR URL and where its identifier is stored. */
data class KnownQrUrlRule(
    val host: String,
    val serialQueryParameter: String,
    val pathPrefix: String = "/",
    val scheme: String = "https",
)

/** Describes an explicitly trusted delimited label payload such as `serial=ABC123;...`. */
data class KnownPayloadRule(
    val serialKey: String,
    val pairSeparator: Char = ';',
    val keyValueSeparator: Char = '=',
)

/**
 * Pure identifier normalization shared by scanned and manually entered values.
 *
 * Prefix, URL, and structured-payload rules are allow lists. The production MVP leaves
 * those lists empty until a real organizational label payload has been inspected.
 */
class IdentifierNormalizer(
    private val rules: IdentifierRules = IdentifierRules(),
) {
    /** Produces the canonical uppercase lookup value, or a reason the input was rejected. */
    fun normalize(rawValue: String?): IdentifierNormalizationResult {
        var candidate = rawValue?.trim().orEmpty()
        if (candidate.isEmpty()) return IdentifierNormalizationResult.Invalid(InvalidIdentifierReason.Empty)

        if (candidate.isHttpUrl()) {
            candidate = extractKnownUrl(candidate)
                ?: return IdentifierNormalizationResult.Invalid(InvalidIdentifierReason.UnsupportedFormat)
        } else {
            extractKnownPayload(candidate)?.let { candidate = it }
            candidate = removeApprovedPrefix(candidate)
        }

        candidate = candidate.trim()
        if (candidate.isEmpty()) return IdentifierNormalizationResult.Invalid(InvalidIdentifierReason.Empty)

        val normalized = candidate.uppercase(Locale.ROOT)
        if (!rules.serialPattern.matches(normalized)) {
            return IdentifierNormalizationResult.Invalid(InvalidIdentifierReason.UnsupportedFormat)
        }
        return IdentifierNormalizationResult.Success(NormalizedIdentifier(normalized))
    }

    private fun extractKnownUrl(value: String): String? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val rule = rules.knownQrUrls.firstOrNull { candidate ->
            uri.scheme.equals(candidate.scheme, ignoreCase = true) &&
                uri.host.equals(candidate.host, ignoreCase = true) &&
                uri.path.orEmpty().startsWith(candidate.pathPrefix)
        } ?: return null

        return uri.rawQuery.orEmpty()
            .split('&')
            .asSequence()
            .mapNotNull { pair ->
                val parts = pair.split('=', limit = 2)
                if (parts.size != 2) null else decode(parts[0]) to decode(parts[1])
            }
            .firstOrNull { (key, _) -> key.equals(rule.serialQueryParameter, ignoreCase = true) }
            ?.second
    }

    private fun extractKnownPayload(value: String): String? = rules.knownPayloads
        .asSequence()
        .mapNotNull { rule ->
            value.split(rule.pairSeparator)
                .asSequence()
                .mapNotNull { pair ->
                    val parts = pair.split(rule.keyValueSeparator, limit = 2)
                    if (parts.size != 2) null else parts[0].trim() to parts[1].trim()
                }
                .firstOrNull { (key, _) -> key.equals(rule.serialKey, ignoreCase = true) }
                ?.second
        }
        .firstOrNull()

    private fun removeApprovedPrefix(value: String): String {
        // Prefer the longest match when approved prefixes overlap.
        val prefix = rules.approvedPrefixes
            .sortedByDescending(String::length)
            .firstOrNull { value.startsWith(it, ignoreCase = true) }
            ?: return value
        return value.drop(prefix.length)
    }

    private fun decode(value: String): String? = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrNull()
}

private fun String.isHttpUrl(): Boolean =
    startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)
