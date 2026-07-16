package com.newforma.timekeep.network

import com.newforma.timekeep.auth.HaloEnvironmentConfig
import com.newforma.timekeep.model.HaloAgentSummary
import kotlinx.serialization.json.jsonObject

class HaloApiClient(
    private val config: HaloEnvironmentConfig,
    private val httpClient: HaloHttpClient,
) {
    suspend fun getCurrentAgent(accessToken: String): HaloAgentSummary {
        val payload = httpClient.getJson(
            url = "${config.resourceServerUrl}/Agent/me",
            bearerToken = accessToken,
        )
        return payload.jsonObject.toHaloAgentSummary()
    }
}
