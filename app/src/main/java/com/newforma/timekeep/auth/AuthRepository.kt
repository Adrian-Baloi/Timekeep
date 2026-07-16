package com.newforma.timekeep.auth

import android.net.Uri
import com.newforma.timekeep.model.AuthSession
import com.newforma.timekeep.model.AuthorizationLaunch
import com.newforma.timekeep.model.PendingAuthorizationRequest
import com.newforma.timekeep.network.HaloHttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

class AuthRepository(
    private val config: HaloEnvironmentConfig,
    private val sessionStore: SessionStore,
    private val httpClient: HaloHttpClient,
    private val json: Json,
) {
    private var cachedDiscovery: OidcDiscoveryDocument? = null

    suspend fun fetchDiscovery(forceRefresh: Boolean = false): OidcDiscoveryDocument {
        if (!forceRefresh) {
            cachedDiscovery?.let { return it }
        }
        val document = json.decodeFromJsonElement<OidcDiscoveryDocument>(
            httpClient.getJson("${config.authServerUrl}/.well-known/openid-configuration"),
        )
        require(document.authorizationEndpoint.isNotBlank()) {
            "OIDC discovery did not return an authorization endpoint."
        }
        require(document.tokenEndpoint.isNotBlank()) {
            "OIDC discovery did not return a token endpoint."
        }
        cachedDiscovery = document
        return document
    }

    fun readSession(): AuthSession? = sessionStore.readSession()

    fun writeSession(session: AuthSession) {
        sessionStore.writeSession(session)
    }

    fun clearAll() {
        sessionStore.clearAll()
    }

    suspend fun createAuthorizationLaunch(discovery: OidcDiscoveryDocument): AuthorizationLaunch {
        val state = PkceUtil.randomState()
        val codeVerifier = if (config.usePkce && discovery.pkceS256Supported) {
            PkceUtil.generateCodeVerifier()
        } else {
            null
        }
        sessionStore.writePendingAuthorization(
            PendingAuthorizationRequest(
                state = state,
                codeVerifier = codeVerifier,
                createdAtEpochSeconds = System.currentTimeMillis() / 1000,
            ),
        )

        val request = Uri.parse(discovery.authorizationEndpoint).buildUpon()
            .appendQueryParameter("client_id", config.clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", config.redirectUri)
            .appendQueryParameter("scope", config.scopeList.joinToString(" "))
            .appendQueryParameter("state", state)
            .apply {
                if (codeVerifier != null) {
                    appendQueryParameter("code_challenge_method", "S256")
                    appendQueryParameter(
                        "code_challenge",
                        PkceUtil.generateCodeChallenge(codeVerifier),
                    )
                }
            }
            .build()
            .toString()

        return AuthorizationLaunch(request)
    }

    suspend fun exchangeAuthorizationCode(
        callbackUri: Uri,
        discovery: OidcDiscoveryDocument,
    ): AuthSession {
        val error = callbackUri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            sessionStore.clearPendingAuthorization()
            val description = callbackUri.getQueryParameter("error_description")
            throw IllegalStateException(
                listOfNotNull("Halo returned $error", description).joinToString(": "),
            )
        }

        val pending = sessionStore.readPendingAuthorization()
            ?: throw IllegalStateException("No pending Halo authorization state was found.")
        val returnedState = callbackUri.getQueryParameter("state")
        if (returnedState != pending.state) {
            sessionStore.clearPendingAuthorization()
            throw IllegalStateException("The Halo auth callback state did not match the local pending request.")
        }

        val code = callbackUri.getQueryParameter("code")
            ?: throw IllegalStateException("Halo did not return an authorization code.")

        val body = linkedMapOf(
            "grant_type" to "authorization_code",
            "client_id" to config.clientId,
            "code" to code,
            "redirect_uri" to config.redirectUri,
        )
        pending.codeVerifier?.let { body["code_verifier"] = it }
        appendClientSecretIfRequired(discovery, body)

        val tokenResponse = json.decodeFromJsonElement<TokenResponse>(
            httpClient.postForm(buildTokenUrl(discovery), body),
        )
        sessionStore.clearPendingAuthorization()
        return tokenResponse.toSession(previousRefreshToken = null)
    }

    suspend fun refreshSession(
        discovery: OidcDiscoveryDocument,
        session: AuthSession,
    ): AuthSession {
        val refreshToken = session.refreshToken
            ?: throw IllegalStateException("No refresh token is stored for this session.")

        val body = linkedMapOf(
            "grant_type" to "refresh_token",
            "client_id" to config.clientId,
            "refresh_token" to refreshToken,
        )
        appendClientSecretIfRequired(discovery, body)

        val tokenResponse = json.decodeFromJsonElement<TokenResponse>(
            httpClient.postForm(buildTokenUrl(discovery), body),
        )
        return tokenResponse.toSession(previousRefreshToken = refreshToken)
    }

    private fun appendClientSecretIfRequired(
        discovery: OidcDiscoveryDocument,
        body: MutableMap<String, String>,
    ) {
        if (discovery.tokenNeedsClientSecret && config.clientSecret.isBlank()) {
            throw IllegalStateException(
                "The tenant OIDC metadata currently advertises client-secret token auth only.",
            )
        }
        if (config.clientSecret.isNotBlank()) {
            body["client_secret"] = config.clientSecret
        }
    }

    private fun buildTokenUrl(discovery: OidcDiscoveryDocument): String {
        if (!config.includeTenantOnTokenRequests) {
            return discovery.tokenEndpoint
        }
        return Uri.parse(discovery.tokenEndpoint).buildUpon()
            .appendQueryParameter("tenant", config.tenantId)
            .build()
            .toString()
    }

    @Serializable
    private data class TokenResponse(
        val access_token: String = "",
        val token_type: String = "Bearer",
        val expires_in: Long = 0,
        val refresh_token: String? = null,
        val scope: String? = null,
    ) {
        fun toSession(previousRefreshToken: String?): AuthSession {
            val nowEpochSeconds = System.currentTimeMillis() / 1000
            return AuthSession(
                accessToken = access_token,
                refreshToken = refresh_token ?: previousRefreshToken,
                expiresAtEpochSeconds = nowEpochSeconds + expires_in,
                tokenType = token_type.ifBlank { "Bearer" },
                scope = scope,
            )
        }
    }
}
