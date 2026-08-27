package com.example.timekeep_work.network

import com.example.timekeep_work.network.dto.HaloAssetUpdateDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Retrofit description of the small Halo REST surface used by the app. */
interface HaloApi {
    // Read-only bootstrap call, verified in the supplied Timekeep-dev reference project.
    // Reconfirm it against the target HaloITSM tenant's /api/swagger after tenant upgrades.
    @GET("Agent/me")
    suspend fun getCurrentAgent(): JsonElement

    // Confirmed against the DEV tenant's /api/swagger/v2/swagger.json on 2026-08-10.
    // `search` is intentionally broad because serials can be configured in key_field1-3.
    @GET("Asset")
    suspend fun findAssets(
        @Query("search") identifier: String,
        @Query("pageinate") paginate: Boolean = true,
        @Query("page_size") pageSize: Int = 50,
        @Query("page_no") pageNumber: Int = 1,
        @Query("includeuser") includeUser: Boolean = true,
        @Query("includechangehistorylastupdated") includeLastUpdated: Boolean = true,
    ): JsonElement

    // Confirmed against the production tenant's OpenAPI 3.0.1 document on 2026-08-11.
    // With exclude_agents=false, linked agents and end users are returned as Users_List records.
    @GET("Users")
    suspend fun searchAssignableUsers(
        @Query("search") query: String,
        @Query("pageinate") paginate: Boolean = true,
        @Query("page_size") pageSize: Int = 25,
        @Query("page_no") pageNumber: Int = 1,
        @Query("includeactive") includeActive: Boolean = true,
        @Query("includeinactive") includeInactive: Boolean = false,
        @Query("exclude_agents") excludeAgents: Boolean = false,
        @Query("listagentuserfirst") listAgentUserFirst: Boolean = true,
    ): JsonElement

    // Confirmed against the production tenant's OpenAPI 3.0.1 document on 2026-08-11.
    @GET("Site")
    suspend fun getActiveSites(
        @Query("pageinate") paginate: Boolean = true,
        @Query("page_size") pageSize: Int = 100,
        @Query("page_no") pageNumber: Int = 1,
        @Query("includeactive") includeActive: Boolean = true,
        @Query("includeinactive") includeInactive: Boolean = false,
        @Query("order") order: String = "name",
    ): JsonElement

    // The tenant schema requires Device.users for assignment and Device.site_id for location.
    // A minimal body can update by Halo id without GET /Asset/{id}; the response is not used as a refetch.
    @POST("Asset")
    suspend fun updateAsset(
        @Body request: List<HaloAssetUpdateDto>,
    ): JsonElement
}
