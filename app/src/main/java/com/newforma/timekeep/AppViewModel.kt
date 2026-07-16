package com.newforma.timekeep

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.newforma.timekeep.auth.OidcDiscoveryDocument
import com.newforma.timekeep.model.AuthSession
import com.newforma.timekeep.network.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val config = container.config
    private var discovery: OidcDiscoveryDocument? = null

    private val _uiState = MutableStateFlow<AppUiState>(
        AppUiState.Loading(
            title = "Checking Halo access",
            detail = "Loading tenant configuration and current session state.",
        ),
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _launchUrl = MutableStateFlow<String?>(null)
    val launchUrl: StateFlow<String?> = _launchUrl.asStateFlow()

    init {
        viewModelScope.launch {
            bootstrap(forceDiscoveryRefresh = false, stickyMessage = null)
        }
    }

    fun retry() {
        viewModelScope.launch {
            bootstrap(
                forceDiscoveryRefresh = true,
                stickyMessage = "Auth prerequisites refreshed.",
            )
        }
    }

    fun onSignInClicked() {
        viewModelScope.launch {
            val missing = config.missingFields()
            if (missing.isNotEmpty()) {
                _uiState.value = blockedState(missing)
                return@launch
            }

            val currentDiscovery = ensureDiscovery(forceRefresh = true) ?: return@launch
            if (currentDiscovery.tokenNeedsClientSecret && config.clientSecret.isBlank()) {
                _uiState.value = signedOutState(
                    "This tenant currently advertises client-secret token auth only. Add HALO_CLIENT_SECRET for sandbox testing or move exchange server-side.",
                )
                return@launch
            }

            _uiState.value = AppUiState.Loading(
                title = "Opening Halo sign-in",
                detail = "Launching the browser-based OAuth flow.",
            )
            val launch = container.authRepository.createAuthorizationLaunch(currentDiscovery)
            _launchUrl.value = launch.url
        }
    }

    fun onAuthorizationUrlLaunched() {
        _launchUrl.value = null
        if (_uiState.value is AppUiState.Loading) {
            _uiState.value = signedOutState(
                "Browser launched. Complete sign-in there, or return here to try again.",
            )
        }
    }

    fun handleAuthorizationCallback(uri: Uri) {
        viewModelScope.launch {
            val currentDiscovery = ensureDiscovery(forceRefresh = false) ?: return@launch
            _uiState.value = AppUiState.Loading(
                title = "Finishing sign-in",
                detail = "Exchanging the authorization code and validating /Agent/me.",
            )

            runCatching {
                val session = container.authRepository.exchangeAuthorizationCode(uri, currentDiscovery)
                container.authRepository.writeSession(session)
                container.haloApiClient.getCurrentAgent(session.accessToken)
            }.onSuccess { agent ->
                _uiState.value = AppUiState.Authenticated(
                    environmentLabel = config.environmentLabel,
                    tenantId = config.tenantId,
                    agent = agent,
                    message = "Authenticated through Halo and validated with /Agent/me.",
                )
            }.onFailure { error ->
                container.authRepository.clearAll()
                _uiState.value = signedOutState(
                    "Halo sign-in failed: ${error.userFacingMessage()}",
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            container.authRepository.clearAll()
            _uiState.value = signedOutState("Local session cleared.")
        }
    }

    private suspend fun bootstrap(
        forceDiscoveryRefresh: Boolean,
        stickyMessage: String?,
    ) {
        val missing = config.missingFields()
        if (missing.isNotEmpty()) {
            _uiState.value = blockedState(missing)
            return
        }

        val currentDiscovery = ensureDiscovery(forceRefresh = forceDiscoveryRefresh)
        val storedSession = container.authRepository.readSession()
        if (storedSession == null) {
            if (currentDiscovery == null && stickyMessage == null) {
                return
            }
            _uiState.value = signedOutState(stickyMessage)
            return
        }

        validateStoredSession(
            session = storedSession,
            stickyMessage = stickyMessage,
        )
    }

    private suspend fun validateStoredSession(
        session: AuthSession,
        stickyMessage: String?,
    ) {
        _uiState.value = AppUiState.Loading(
            title = "Validating session",
            detail = "Calling /Agent/me before unlocking the authenticated shell.",
        )

        runCatching {
            container.haloApiClient.getCurrentAgent(session.accessToken)
        }.onSuccess { agent ->
            _uiState.value = AppUiState.Authenticated(
                environmentLabel = config.environmentLabel,
                tenantId = config.tenantId,
                agent = agent,
                message = stickyMessage,
            )
        }.onFailure { error ->
            if (error is ApiException && error.statusCode == 401 && !session.refreshToken.isNullOrBlank()) {
                attemptRefresh(session, stickyMessage)
                return
            }
            if (error is ApiException && (error.statusCode == 401 || error.statusCode == 403)) {
                container.authRepository.clearAll()
                _uiState.value = signedOutState(
                    "Stored session is no longer allowed to bootstrap /Agent/me. Sign in again.",
                )
                return
            }
            _uiState.value = signedOutState(
                stickyMessage ?: "Could not validate the stored session: ${error.userFacingMessage()}",
            )
        }
    }

    private suspend fun attemptRefresh(
        expiredSession: AuthSession,
        stickyMessage: String?,
    ) {
        val currentDiscovery = ensureDiscovery(forceRefresh = false) ?: return
        _uiState.value = AppUiState.Loading(
            title = "Refreshing session",
            detail = "The stored token failed once, so the app is attempting a single refresh.",
        )

        runCatching {
            val refreshed = container.authRepository.refreshSession(currentDiscovery, expiredSession)
            container.authRepository.writeSession(refreshed)
            container.haloApiClient.getCurrentAgent(refreshed.accessToken)
        }.onSuccess { agent ->
            _uiState.value = AppUiState.Authenticated(
                environmentLabel = config.environmentLabel,
                tenantId = config.tenantId,
                agent = agent,
                message = stickyMessage ?: "Stored session refreshed successfully.",
            )
        }.onFailure {
            container.authRepository.clearAll()
            _uiState.value = signedOutState(
                "The stored session expired and refresh did not succeed. Sign in again.",
            )
        }
    }

    private suspend fun ensureDiscovery(forceRefresh: Boolean): OidcDiscoveryDocument? {
        discovery?.takeUnless { forceRefresh }?.let { return it }
        val loaded = runCatching {
            container.authRepository.fetchDiscovery(forceRefresh = forceRefresh)
        }.getOrElse { error ->
            _uiState.value = signedOutState(
                "Could not load tenant auth metadata: ${error.userFacingMessage()}",
            )
            return null
        }
        discovery = loaded
        return loaded
    }

    private fun blockedState(missingFields: List<String>): AppUiState.SetupBlocked {
        return AppUiState.SetupBlocked(
            environmentLabel = config.environmentLabel,
            tenantId = config.tenantId,
            missingFields = missingFields,
            detail = "Add the missing Gradle properties or environment variables, then retry the auth shell.",
        )
    }

    private fun signedOutState(message: String?): AppUiState.SignedOut {
        val currentDiscovery = discovery
        val clientSecretRequired = currentDiscovery?.tokenNeedsClientSecret == true
        return AppUiState.SignedOut(
            environmentLabel = config.environmentLabel,
            tenantId = config.tenantId,
            discoveryLoaded = currentDiscovery != null,
            pkceSupported = currentDiscovery?.pkceS256Supported == true,
            tokenAuthMethods = currentDiscovery?.tokenEndpointAuthMethods.orEmpty(),
            hasClientSecret = config.clientSecret.isNotBlank(),
            canStartSignIn = currentDiscovery != null && (!clientSecretRequired || config.clientSecret.isNotBlank()),
            message = message,
        )
    }

    private fun Throwable.userFacingMessage(): String {
        return when (this) {
            is ApiException -> {
                val body = responseBody?.trim()?.take(180)
                if (body.isNullOrBlank()) {
                    "HTTP $statusCode"
                } else {
                    "HTTP $statusCode: $body"
                }
            }
            else -> message?.trim().orEmpty().ifBlank { javaClass.simpleName }
        }
    }

    class Factory(
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AppViewModel::class.java)) {
                "Unsupported ViewModel class: ${modelClass.name}"
            }
            return AppViewModel(container) as T
        }
    }
}
