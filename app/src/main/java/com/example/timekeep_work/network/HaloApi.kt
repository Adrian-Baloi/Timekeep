package com.example.timekeep_work.network

import com.example.timekeep_work.network.dto.HaloAssetUpdateDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface HaloApi {
    // The classic HaloITSM route and filters must be checked against the target tenant's /api/swagger.
    @GET("Asset")
    suspend fun findAssets(
        @Query("search") identifier: String,
        @Query("pageinate") paginate: Boolean = true,
        @Query("page_size") pageSize: Int = 50,
        @Query("page_no") pageNumber: Int = 1,
    ): JsonElement

    // Halo write endpoints commonly accept an array. Confirm this exact schema in tenant Swagger.
    @POST("Asset")
    suspend fun updateAsset(
        @Body request: List<HaloAssetUpdateDto>,
    ): JsonElement
}
