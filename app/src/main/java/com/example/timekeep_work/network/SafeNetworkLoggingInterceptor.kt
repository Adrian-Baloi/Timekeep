package com.example.timekeep_work.network

import android.content.Context
import android.util.Log
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response

/**
 * Writes only method, sanitized route, status, and duration to Logcat.
 * Query values, request headers and bodies, exception messages, and stack traces are never logged.
 * Failed Asset writes additionally include a bounded, redacted Halo response and redirect hosts.
 */
class SafeNetworkLoggingInterceptor(
    private val diagnosticSink: FailureDiagnosticSink = FailureDiagnosticSink { },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val route = request.url.encodedPath.sanitizedRoute()
        val startedAt = System.nanoTime()

        return try {
            chain.proceed(request).also { response ->
                val durationMs = elapsedMilliseconds(startedAt)
                Log.d(LOG_TAG, "${request.method} $route -> ${response.code} (${durationMs}ms)")
                if (request.isAssetUpdate() && !response.isSuccessful) {
                    val diagnostic = response.assetFailureDiagnostics(request.url.host)
                    Log.w(LOG_TAG, diagnostic)
                    runCatching { diagnosticSink.record(diagnostic) }
                }
            }
        } catch (error: Throwable) {
            val durationMs = elapsedMilliseconds(startedAt)
            Log.w(LOG_TAG, "${request.method} $route -> transport failure (${durationMs}ms)")
            throw error
        }
    }

    private fun elapsedMilliseconds(startedAt: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

    private fun String.sanitizedRoute(): String {
        val segments = split('/').filter(String::isNotBlank)
        if (segments.isEmpty()) return "/"
        return segments.joinToString(separator = "/", prefix = "/") { segment ->
            when {
                segment.all(Char::isDigit) -> "{id}"
                segment.length > MAX_SAFE_SEGMENT_LENGTH -> "{value}"
                else -> segment
            }
        }
    }

    private fun okhttp3.Request.isAssetUpdate(): Boolean =
        method.equals("POST", ignoreCase = true) &&
            url.encodedPath.trimEnd('/').endsWith("/Asset", ignoreCase = true)

    private fun Response.assetFailureDiagnostics(originHost: String): String {
        val redirectTrace = redirectTrace()
        val finalHost = request.url.host
        val authentication = header("WWW-Authenticate")
            ?.let(HaloFailureSanitizer::sanitizeHeader)
            ?: "<absent>"
        val correlation = headers.names()
            .filter(HaloFailureSanitizer::isCorrelationHeader)
            .sorted()
            .joinToString(separator = ", ") { name ->
                "$name=${HaloFailureSanitizer.sanitizeHeader(header(name).orEmpty())}"
            }
            .ifBlank { "<absent>" }
        val body = peekSanitizedBody(body?.contentType())

        return buildString {
            append("Halo Asset write failure: status=")
            append(code)
            append(" originHost=")
            append(originHost)
            append(" finalHost=")
            append(finalHost)
            append(" crossHost=")
            append(!originHost.equals(finalHost, ignoreCase = true))
            append(" redirects=")
            append(redirectTrace)
            append(" wwwAuthenticate=")
            append(authentication)
            append(" correlation=")
            append(correlation)
            append(" responseBody=")
            append(body)
        }
    }

    private fun Response.redirectTrace(): String {
        val responses = generateSequence(this) { it.priorResponse }
            .toList()
            .asReversed()
        if (responses.size == 1) return "<none>"
        return responses.joinToString(separator = " -> ") { response ->
            "${response.code}@${response.request.url.host}"
        }
    }

    private fun Response.peekSanitizedBody(contentType: MediaType?): String {
        if (!HaloFailureSanitizer.isTextual(contentType)) return "<non-text body omitted>"
        return runCatching {
            HaloFailureSanitizer.sanitizeBody(peekBody(MAX_ERROR_BODY_BYTES).string())
        }.getOrDefault("<unavailable>")
    }

    private companion object {
        const val LOG_TAG = "TimekeepNetwork"
        const val MAX_SAFE_SEGMENT_LENGTH = 48
        const val MAX_ERROR_BODY_BYTES = 4_096L
    }
}

fun interface FailureDiagnosticSink {
    fun record(diagnostic: String)
}

/** Keeps the latest sanitized asset-write diagnostic in app-private storage. */
class PrivateFailureDiagnosticStore(context: Context) : FailureDiagnosticSink {
    private val applicationContext = context.applicationContext

    override fun record(diagnostic: String) {
        applicationContext.openFileOutput(FILE_NAME, Context.MODE_PRIVATE)
            .bufferedWriter(Charsets.UTF_8)
            .use { writer ->
                writer.appendLine("recordedAtEpochMs=${System.currentTimeMillis()}")
                writer.append(diagnostic)
            }
    }

    companion object {
        const val FILE_NAME = "last_halo_asset_failure.txt"
    }
}

/** Centralizes redaction so Logcat and stored diagnostics follow the same privacy rules. */
internal object HaloFailureSanitizer {
    private const val MAX_HEADER_LENGTH = 1_024
    private const val MAX_BODY_LENGTH = 2_048

    private val sensitiveJsonValue = Regex(
        pattern = """(?i)(\"(?:token|access_?token|refresh_?token|id_?token|authorization|authorization_?code|password|secret|client_?secret|email|username|user|asset(?:_?tag)?|serial(?:_?number)?)\"\s*:\s*)(\"(?:\\.|[^\"])*\"|[^,}\s]+)""",
    )
    private val bearerValue = Regex(
        """(?i)\bBearer\s+(?!error\b|realm\b|scope\b)[A-Za-z0-9._~+\-/]{16,}=*""",
    )
    private val jwtValue = Regex("""\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b""")
    private val sensitiveQueryValue = Regex(
        """(?i)([?&](?:access_?token|refresh_?token|id_?token|token|code|secret)=)[^&#\s]+""",
    )
    private val emailValue = Regex("""\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b""", RegexOption.IGNORE_CASE)
    private val assetTagValue = Regex("""\bWLT-[A-Z0-9-]+\b""", RegexOption.IGNORE_CASE)
    private val htmlTag = Regex("""<[^>]+>""")
    private val whitespace = Regex("""\s+""")

    fun sanitizeHeader(value: String): String = sanitize(value, MAX_HEADER_LENGTH, stripHtml = false)

    fun sanitizeBody(value: String): String =
        sanitize(value, MAX_BODY_LENGTH, stripHtml = true).ifBlank { "<empty>" }

    fun isCorrelationHeader(name: String): Boolean {
        val normalized = name.lowercase().replace("_", "-")
        return normalized in setOf(
            "request-id",
            "x-request-id",
            "x-requestid",
            "correlation-id",
            "x-correlation-id",
            "x-correlationid",
            "x-halo-request-id",
            "x-halo-correlation-id",
            "traceparent",
        )
    }

    fun isTextual(contentType: MediaType?): Boolean {
        if (contentType == null) return true
        return contentType.type.equals("text", ignoreCase = true) ||
            contentType.subtype.contains("json", ignoreCase = true) ||
            contentType.subtype.contains("xml", ignoreCase = true) ||
            contentType.subtype.contains("problem", ignoreCase = true)
    }

    private fun sanitize(value: String, maxLength: Int, stripHtml: Boolean): String {
        var sanitized = value
        if (stripHtml) sanitized = sanitized.replace(htmlTag, " ")
        sanitized = sanitized
            .replace(sensitiveJsonValue) { match -> "${match.groupValues[1]}\"<redacted>\"" }
            .replace(bearerValue, "Bearer <redacted>")
            .replace(jwtValue, "<redacted-jwt>")
            .replace(sensitiveQueryValue) { match -> "${match.groupValues[1]}<redacted>" }
            .replace(emailValue, "<redacted-email>")
            .replace(assetTagValue, "<redacted-asset-tag>")
            .replace(whitespace, " ")
            .trim()
        if (sanitized.length <= maxLength) return sanitized
        return sanitized.take(maxLength) + "...[truncated]"
    }
}
