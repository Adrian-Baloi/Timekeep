package com.example.timekeep_work.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Minimal request that starts Newforma's Halo asset-change workflow. */
@Serializable
data class HaloAssetChangeTicketDto(
    @SerialName("tickettype_id") val ticketTypeId: Int,
    @SerialName("asset_number") val assetNumber: Int,
    val assets: List<HaloTicketAssetDto>,
    @SerialName("client_id") val clientId: Int? = null,
    @SerialName("site_id") val siteId: Int,
    @SerialName("customfields") val customFields: List<HaloTicketCustomFieldDto>,
)

/** Minimal Device_List reference that creates Halo's ticket-to-asset relationship. */
@Serializable
data class HaloTicketAssetDto(
    val id: Int,
)

/** String-form value matching the custom-field representation sent by Halo's web client. */
@Serializable
data class HaloTicketCustomFieldDto(
    val id: Int,
    val value: String,
)
