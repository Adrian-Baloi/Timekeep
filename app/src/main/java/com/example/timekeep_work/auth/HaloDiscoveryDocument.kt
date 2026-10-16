package com.example.timekeep_work.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Subset of the OpenID Connect discovery document required by this app. */
@Serializable
data class HaloDiscoveryDocument(
    @SerialName("authorization_endpoint")
    val authorizationEndpoint: String = "",
    @SerialName("token_endpoint")
    val tokenEndpoint: String = "",
    @SerialName("end_session_endpoint")
    val endSessionEndpoint: String? = null,
    @SerialName("code_challenge_methods_supported")
    val codeChallengeMethodsSupported: List<String> = emptyList(),
    @SerialName("token_endpoint_auth_methods_supported")
    val tokenEndpointAuthMethodsSupported: List<String> = emptyList(),
) {
    val supportsS256Pkce: Boolean
        get() = codeChallengeMethodsSupported.any { it.equals("S256", ignoreCase = true) }
}
