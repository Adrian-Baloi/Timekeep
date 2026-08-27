package com.example.timekeep_work.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire model for a Halo user/agent candidate returned by the Users endpoint. */
@Serializable
data class HaloUserListDto(
    val id: Int? = null,
    val name: String? = null,
    val firstname: String? = null,
    val surname: String? = null,
    val emailaddress: String? = null,
    val inactive: Boolean? = null,
    @SerialName("linked_agent_id") val linkedAgentId: Int? = null,
)

@Serializable
data class HaloUserSearchResponseDto(
    @SerialName("page_no") val pageNumber: Int? = null,
    @SerialName("page_size") val pageSize: Int? = null,
    @SerialName("record_count") val recordCount: Long? = null,
    val users: List<HaloUserListDto>? = null,
)

/** Wire model for a Halo site returned by the Site endpoint. */
@Serializable
data class HaloSiteListDto(
    val id: Int? = null,
    val name: String? = null,
    @SerialName("client_name") val clientName: String? = null,
    val inactive: Boolean? = null,
)

@Serializable
data class HaloSiteSearchResponseDto(
    @SerialName("page_no") val pageNumber: Int? = null,
    @SerialName("page_size") val pageSize: Int? = null,
    @SerialName("record_count") val recordCount: Long? = null,
    val sites: List<HaloSiteListDto>? = null,
)
