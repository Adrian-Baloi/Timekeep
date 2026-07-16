package com.newforma.timekeep

import com.newforma.timekeep.model.HaloAgentSummary

sealed interface AppUiState {
    data class SetupBlocked(
        val environmentLabel: String,
        val tenantId: String,
        val missingFields: List<String>,
        val detail: String,
    ) : AppUiState

    data class SignedOut(
        val environmentLabel: String,
        val tenantId: String,
        val discoveryLoaded: Boolean,
        val pkceSupported: Boolean,
        val tokenAuthMethods: List<String>,
        val hasClientSecret: Boolean,
        val canStartSignIn: Boolean,
        val message: String? = null,
    ) : AppUiState

    data class Loading(
        val title: String,
        val detail: String,
    ) : AppUiState

    data class Authenticated(
        val environmentLabel: String,
        val tenantId: String,
        val agent: HaloAgentSummary,
        val message: String? = null,
    ) : AppUiState
}
