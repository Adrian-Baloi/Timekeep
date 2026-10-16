package com.example.timekeep_work.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.NoClientAuthentication
import net.openid.appauth.ResponseTypeValues
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Owns the Halo OAuth authorization-code flow, token refresh, and persisted AppAuth state.
 *
 * Browser launching and UI messaging remain in [com.example.timekeep_work.screen.AuthViewModel];
 * this class deals only with protocol and session concerns.
 */
class HaloAuthRepository(
    context: Context,
    val config: HaloAuthConfig,
    private val stateStore: AuthStateStore,
    private val json: Json,
    private val okHttpClient: OkHttpClient,
) {
    private val authorizationService = AuthorizationService(context.applicationContext)
    // AppAuth callbacks and UI actions may touch session state concurrently.
    private val stateLock = Any()
    private var cachedState: AuthState? = null
    private var cachedDiscovery: HaloDiscoveryDocument? = null

    private val _sessionInvalidated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits when a protected request proves that the stored session is no longer usable. */
    val sessionInvalidated: SharedFlow<Unit> = _sessionInvalidated.asSharedFlow()

    fun configurationErrors(): List<String> = config.configurationErrors()

    fun hasStoredSession(): Boolean = loadState()?.isAuthorized == true

    /** Builds the external-browser request after validating configuration and S256 support. */
    suspend fun createAuthorizationIntent(): Intent {
        val errors = configurationErrors()
        if (errors.isNotEmpty()) throw HaloAuthConfigurationException(errors.joinToString(" "))

        val discovery = discover()
        if (!discovery.supportsS256Pkce) {
            throw PkceRequiredException()
        }

        // Halo advertises the token authentication methods supported by the server,
        // not the authentication setting of this individual API application. A native
        // client must still attempt a public PKCE exchange without a client secret.

        val serviceConfiguration = AuthorizationServiceConfiguration(
            Uri.parse(discovery.authorizationEndpoint),
            config.tokenEndpoint(discovery.tokenEndpoint),
            null,
            discovery.endSessionEndpoint?.takeIf(String::isNotBlank)?.let(Uri::parse),
        )
        val request = AuthorizationRequest.Builder(
            serviceConfiguration,
            config.clientId,
            ResponseTypeValues.CODE,
            Uri.parse(config.redirectUri),
        )
            .setScopes(config.scopes)
            .build()

        if (request.codeVerifierChallengeMethod != AuthorizationRequest.CODE_CHALLENGE_METHOD_S256) {
            throw PkceRequiredException()
        }
        return authorizationService.getAuthorizationRequestIntent(request)
    }

    /** Exchanges the browser redirect's authorization code for the first access token. */
    suspend fun exchangeAuthorizationResult(data: Intent): String {
        val response = AuthorizationResponse.fromIntent(data)
        val authorizationError = AuthorizationException.fromIntent(data)
        if (response == null) {
            clearSession()
            throw HaloAuthorizationException(
                if (authorizationError == null) "Halo sign-in was cancelled." else "Halo authorization failed.",
                authorizationError,
            )
        }

        val state = AuthState().apply { update(response, null) }
        persistState(state)
        val tokenRequest = response.createTokenExchangeRequest()

        return suspendCancellableCoroutine { continuation ->
            authorizationService.performTokenRequest(
                tokenRequest,
                NoClientAuthentication.INSTANCE,
            ) { tokenResponse, tokenError ->
                if (!continuation.isActive) return@performTokenRequest
                state.update(tokenResponse, tokenError)
                val accessToken = state.accessToken
                if (tokenResponse != null && !accessToken.isNullOrBlank()) {
                    persistState(state)
                    continuation.resume(accessToken)
                } else {
                    clearSession()
                    continuation.resumeWithException(
                        if (tokenError.isPublicClientRejection()) {
                            PublicClientRejectedException(tokenError)
                        } else {
                            HaloAuthorizationException("Halo authorization-code exchange failed.", tokenError)
                        },
                    )
                }
            }
        }
    }

    /** Returns a valid token, asking AppAuth to refresh it when needed or explicitly requested. */
    suspend fun freshAccessToken(forceRefresh: Boolean = false): String {
        val state = loadState() ?: throw AuthenticationRequiredException()
        if (forceRefresh) state.setNeedsTokenRefresh(true)

        return suspendCancellableCoroutine { continuation ->
            state.performActionWithFreshTokens(
                authorizationService,
                NoClientAuthentication.INSTANCE,
                emptyMap(),
            ) { accessToken, _, error ->
                if (!continuation.isActive) return@performActionWithFreshTokens
                if (error == null && !accessToken.isNullOrBlank()) {
                    persistState(state)
                    continuation.resume(accessToken)
                } else {
                    invalidateSession()
                    continuation.resumeWithException(
                        AuthenticationRequiredException(error),
                    )
                }
            }
        }
    }

    fun clearSession() {
        synchronized(stateLock) {
            cachedState = null
            stateStore.clear()
        }
    }

    /** Clears local state and tells observers to return the user to the sign-in flow. */
    fun invalidateSession() {
        clearSession()
        _sessionInvalidated.tryEmit(Unit)
    }

    /** Loads and validates discovery once for the lifetime of this repository. */
    private suspend fun discover(): HaloDiscoveryDocument {
        cachedDiscovery?.let { return it }
        val document = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(config.discoveryUrl)
                .header("Accept", "application/json")
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Halo discovery returned HTTP ${response.code}.")
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) throw IOException("Halo discovery returned an empty response.")
                json.decodeFromString<HaloDiscoveryDocument>(body)
            }
        }
        if (!document.authorizationEndpoint.isHttpsUrl() || !document.tokenEndpoint.isHttpsUrl()) {
            throw HaloAuthConfigurationException("Halo discovery must return HTTPS authorization and token endpoints.")
        }
        cachedDiscovery = document
        return document
    }

    private fun loadState(): AuthState? = synchronized(stateLock) {
        cachedState ?: stateStore.read()?.also { cachedState = it }
    }

    private fun persistState(state: AuthState) {
        synchronized(stateLock) {
            stateStore.write(state)
            cachedState = state
        }
    }
}

class HaloAuthConfigurationException(message: String) : IOException(message)

class PublicClientRejectedException(cause: Throwable? = null) : IOException(
    "Halo rejected the public token exchange. Disable 'Use a client secret for the token request' for this native API application, or use a backend-for-frontend.",
    cause,
)

class PkceRequiredException : IOException(
    "This Halo tenant did not advertise S256 PKCE, so native sign-in was blocked.",
)

class HaloAuthorizationException(message: String, cause: Throwable? = null) : IOException(message, cause)

class AuthenticationRequiredException(cause: Throwable? = null) : IOException(
    "The Halo session is missing, expired, or could not be refreshed.",
    cause,
)

private fun String.isHttpsUrl(): Boolean = Uri.parse(this).scheme.equals("https", ignoreCase = true)

private fun AuthorizationException?.isPublicClientRejection(): Boolean =
    this?.error.equals("invalid_client", ignoreCase = true) ||
        this?.error.equals("unauthorized_client", ignoreCase = true)
