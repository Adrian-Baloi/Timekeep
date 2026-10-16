package com.example.timekeep_work.screen

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timekeep_work.Agent
import com.example.timekeep_work.auth.AuthenticationRequiredException
import com.example.timekeep_work.auth.HaloAuthConfigurationException
import com.example.timekeep_work.auth.PkceRequiredException
import com.example.timekeep_work.auth.PublicClientRejectedException
import com.example.timekeep_work.data.AppContainer
import com.example.timekeep_work.network.dto.toAgent
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** Authentication states that determine whether the protected navigation graph is available. */
sealed interface AuthUiState {
    data object Checking : AuthUiState
    data object SignedOut : AuthUiState
    data class Authenticated(val agent: Agent) : AuthUiState
}

/** Coordinates browser authorization, session restoration, and current-technician loading. */
class AuthViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Checking)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // Channels carry one-time effects; replaying them after recomposition would relaunch the browser
    // or display an already-consumed message.
    private val authorizationIntentChannel = Channel<Intent>(Channel.BUFFERED)
    val authorizationIntents = authorizationIntentChannel.receiveAsFlow()

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    private var signInJob: Job? = null
    private var authorizationInProgress = false

    init {
        // Repository invalidation can originate from any authenticated API request.
        viewModelScope.launch {
            container.authRepository.sessionInvalidated.collect {
                val wasAuthenticated = _uiState.value is AuthUiState.Authenticated
                container.accessTokenProvider.clear()
                _uiState.value = AuthUiState.SignedOut
                if (wasAuthenticated) {
                    messageChannel.send("Your Halo session expired. Sign in again.")
                }
            }
        }
        viewModelScope.launch { restoreSession() }
    }

    fun startSignIn() {
        if (authorizationInProgress || signInJob?.isActive == true) return
        authorizationInProgress = true
        _uiState.value = AuthUiState.Checking
        signInJob = viewModelScope.launch {
            runCatching { container.authRepository.createAuthorizationIntent() }
                .onSuccess { intent -> authorizationIntentChannel.send(intent) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    authorizationInProgress = false
                    _uiState.value = AuthUiState.SignedOut
                    messageChannel.send(error.userFacingMessage())
                }
        }
    }

    fun handleAuthorizationResult(data: Intent?) {
        authorizationInProgress = false
        signInJob?.cancel()
        signInJob = viewModelScope.launch {
            if (data == null) {
                _uiState.value = AuthUiState.SignedOut
                messageChannel.send("Halo sign-in was cancelled.")
                return@launch
            }

            _uiState.value = AuthUiState.Checking
            runCatching {
                val accessToken = container.authRepository.exchangeAuthorizationResult(data)
                container.accessTokenProvider.update(accessToken)
                loadCurrentAgent()
            }.onSuccess { agent ->
                _uiState.value = AuthUiState.Authenticated(agent)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                container.accessTokenProvider.clear()
                _uiState.value = AuthUiState.SignedOut
                messageChannel.send(error.userFacingMessage())
            }
        }
    }

    fun signOut() {
        authorizationInProgress = false
        signInJob?.cancel()
        container.authRepository.clearSession()
        container.accessTokenProvider.clear()
        _uiState.value = AuthUiState.SignedOut
    }

    private suspend fun restoreSession() {
        val errors = container.authRepository.configurationErrors()
        if (errors.isNotEmpty()) {
            _uiState.value = AuthUiState.SignedOut
            messageChannel.send(errors.joinToString(" "))
            return
        }
        if (!container.authRepository.hasStoredSession()) {
            _uiState.value = AuthUiState.SignedOut
            return
        }

        runCatching { loadCurrentAgent() }
            .onSuccess { agent -> _uiState.value = AuthUiState.Authenticated(agent) }
            .onFailure { error ->
                if (error is CancellationException) throw error
                container.accessTokenProvider.clear()
                _uiState.value = AuthUiState.SignedOut
                messageChannel.send(error.userFacingMessage())
            }
    }

    /** Verifies the session with Agent/me and retries once after forcing a token refresh. */
    private suspend fun loadCurrentAgent(): Agent {
        updateFreshToken(forceRefresh = false)
        return try {
            container.haloApi.getCurrentAgent().toAgent()
        } catch (error: HttpException) {
            if (error.code() != 401) {
                if (error.code() == 403) container.authRepository.invalidateSession()
                throw error
            }
            updateFreshToken(forceRefresh = true)
            try {
                container.haloApi.getCurrentAgent().toAgent()
            } catch (retryError: HttpException) {
                if (retryError.code() == 401 || retryError.code() == 403) {
                    container.authRepository.invalidateSession()
                    throw AuthenticationRequiredException(retryError)
                }
                throw retryError
            }
        }
    }

    private suspend fun updateFreshToken(forceRefresh: Boolean) {
        val accessToken = container.authRepository.freshAccessToken(forceRefresh)
        container.accessTokenProvider.update(accessToken)
    }

    private fun Throwable.userFacingMessage(): String = when (this) {
        is PublicClientRejectedException ->
            "Halo rejected this native token exchange. Turn off 'Use a client secret for the token request' in the Halo API application, save it, and sign in again."
        is PkceRequiredException ->
            "Halo did not advertise S256 PKCE, so this native sign-in was blocked."
        is HaloAuthConfigurationException -> message ?: "Halo sign-in is not configured."
        is AuthenticationRequiredException -> "Your Halo session expired. Sign in again."
        is ActivityNotFoundException -> "No approved external browser is available for Halo sign-in."
        is HttpException -> when (code()) {
            401 -> "Halo rejected the session. Sign in again."
            403 -> "The Halo user or API application cannot read Agent/me. Check its roles and scopes."
            else -> "Halo returned HTTP ${code()} while validating the session. Try again."
        }
        is IOException -> "Could not complete Halo sign-in. Check the connection and try again."
        else -> "Could not complete Halo sign-in. Try again."
    }

    class Factory(
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AuthViewModel::class.java))
            return AuthViewModel(container) as T
        }
    }
}
