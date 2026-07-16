package com.newforma.timekeep.auth

import kotlinx.serialization.Serializable

@Serializable
data class OidcDiscoveryDocument(
    val issuer: String = "",
    val authorization_endpoint: String = "",
    val token_endpoint: String = "",
    val end_session_endpoint: String? = null,
    val revocation_endpoint: String? = null,
    val code_challenge_methods_supported: List<String> = emptyList(),
    val token_endpoint_auth_methods_supported: List<String> = emptyList(),
    val scopes_supported: List<String> = emptyList(),
) {
    val authorizationEndpoint: String
        get() = authorization_endpoint

    val tokenEndpoint: String
        get() = token_endpoint

    val tokenEndpointAuthMethods: List<String>
        get() = token_endpoint_auth_methods_supported

    val pkceS256Supported: Boolean
        get() = code_challenge_methods_supported.any { it.equals("S256", ignoreCase = true) }

    val tokenNeedsClientSecret: Boolean
        get() = token_endpoint_auth_methods_supported.isNotEmpty() &&
            token_endpoint_auth_methods_supported.none { it.equals("none", ignoreCase = true) }
}
