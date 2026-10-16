package com.example.timekeep_work.auth

import android.net.Uri
import com.example.timekeep_work.BuildConfig

/** Tenant-specific OAuth configuration used by the native Halo sign-in flow. */
data class HaloAuthConfig(
    val resourceServerUrl: String,
    val authorizationServerUrl: String,
    val tenantId: String,
    val clientId: String,
    val redirectUri: String,
    val scopes: List<String>,
    val includeTenantOnTokenRequests: Boolean,
) {
    val discoveryUrl: String
        get() = authorizationServerUrl.trimEnd('/') + "/.well-known/openid-configuration"

    /** Adds Halo's tenant query parameter when this tenant requires it for token requests. */
    fun tokenEndpoint(discoveredEndpoint: String): Uri {
        val endpoint = Uri.parse(discoveredEndpoint)
        if (!includeTenantOnTokenRequests || tenantId.isBlank() || endpoint.getQueryParameter("tenant") != null) {
            return endpoint
        }
        return endpoint.buildUpon().appendQueryParameter("tenant", tenantId).build()
    }

    /** Reports all unsafe or incomplete settings so the UI can present one actionable error. */
    fun configurationErrors(): List<String> = buildList {
        if (!resourceServerUrl.isHttpsUrl()) add("HALO_RESOURCE_SERVER_URL must be HTTPS.")
        if (!authorizationServerUrl.isHttpsUrl()) add("HALO_AUTH_SERVER_URL must be HTTPS.")
        if (tenantId.isBlank()) add("HALO_TENANT_ID is missing.")
        if (clientId.isBlank()) add("HALO_CLIENT_ID is missing.")
        if (redirectUri.isBlank() || Uri.parse(redirectUri).scheme.isNullOrBlank()) {
            add("HALO_REDIRECT_URI must be an absolute URI.")
        }
        if (scopes.isEmpty()) add("HALO_SCOPES is missing.")
    }

    companion object {
        /** Creates the runtime configuration from Gradle-generated build constants. */
        fun fromBuildConfig(): HaloAuthConfig = HaloAuthConfig(
            resourceServerUrl = BuildConfig.HALO_RESOURCE_SERVER_URL.normalizedBaseUrl(),
            authorizationServerUrl = BuildConfig.HALO_AUTH_SERVER_URL.normalizedBaseUrl(),
            tenantId = BuildConfig.HALO_TENANT_ID.trim(),
            clientId = BuildConfig.HALO_CLIENT_ID.trim(),
            redirectUri = BuildConfig.HALO_REDIRECT_URI.trim(),
            scopes = BuildConfig.HALO_SCOPES
                .trim()
                .split(Regex("\\s+"))
                .filter(String::isNotBlank),
            includeTenantOnTokenRequests = BuildConfig.HALO_INCLUDE_TENANT_ON_TOKEN_REQUESTS,
        )
    }
}

private fun String.normalizedBaseUrl(): String = trim().trimEnd('/') + "/"

private fun String.isHttpsUrl(): Boolean = Uri.parse(this).scheme.equals("https", ignoreCase = true)
