package com.newforma.timekeep.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthSession(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochSeconds: Long,
    val tokenType: String,
    val scope: String?,
) {
    fun expiresSoon(
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
        skewSeconds: Long = 60,
    ): Boolean = expiresAtEpochSeconds <= nowEpochSeconds + skewSeconds
}

@Serializable
data class PendingAuthorizationRequest(
    val state: String,
    val codeVerifier: String?,
    val createdAtEpochSeconds: Long,
)

data class AuthorizationLaunch(
    val url: String,
)
