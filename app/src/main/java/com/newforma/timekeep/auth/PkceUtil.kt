package com.newforma.timekeep.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object PkceUtil {
    private val secureRandom = SecureRandom()

    fun generateCodeVerifier(): String = base64Url(randomBytes(32))

    fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return base64Url(digest)
    }

    fun randomState(): String = base64Url(randomBytes(24))

    private fun randomBytes(length: Int): ByteArray = ByteArray(length).also(secureRandom::nextBytes)

    private fun base64Url(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
