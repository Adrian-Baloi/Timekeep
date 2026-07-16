package com.newforma.timekeep.auth

import com.newforma.timekeep.BuildConfig

private fun String.normalizedBaseUrl(): String = trim().trimEnd('/')

data class HaloEnvironmentConfig(
    val environmentLabel: String,
    val resourceServerUrl: String,
    val authServerUrl: String,
    val tenantId: String,
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    val scopes: String,
    val usePkce: Boolean,
    val includeTenantOnTokenRequests: Boolean,
) {
    val scopeList: List<String> = scopes
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

    fun missingFields(): List<String> {
        val missing = mutableListOf<String>()
        if (resourceServerUrl.isBlank()) missing += "HALO_RESOURCE_SERVER_URL"
        if (authServerUrl.isBlank()) missing += "HALO_AUTH_SERVER_URL"
        if (tenantId.isBlank()) missing += "HALO_TENANT_ID"
        if (clientId.isBlank()) missing += "HALO_CLIENT_ID"
        if (redirectUri.isBlank()) missing += "HALO_REDIRECT_URI"
        if (scopeList.isEmpty()) missing += "HALO_SCOPES"
        return missing
    }

    companion object {
        fun fromBuildConfig(): HaloEnvironmentConfig {
            return HaloEnvironmentConfig(
                environmentLabel = BuildConfig.HALO_ENVIRONMENT_LABEL.ifBlank { "Halo Environment" },
                resourceServerUrl = BuildConfig.HALO_RESOURCE_SERVER_URL.normalizedBaseUrl(),
                authServerUrl = BuildConfig.HALO_AUTH_SERVER_URL.normalizedBaseUrl(),
                tenantId = BuildConfig.HALO_TENANT_ID.trim(),
                clientId = BuildConfig.HALO_CLIENT_ID.trim(),
                clientSecret = BuildConfig.HALO_CLIENT_SECRET.trim(),
                redirectUri = BuildConfig.HALO_REDIRECT_URI.trim(),
                scopes = BuildConfig.HALO_SCOPES.trim(),
                usePkce = BuildConfig.HALO_USE_PKCE,
                includeTenantOnTokenRequests = BuildConfig.HALO_INCLUDE_TENANT_ON_TOKEN_REQUESTS,
            )
        }
    }
}
