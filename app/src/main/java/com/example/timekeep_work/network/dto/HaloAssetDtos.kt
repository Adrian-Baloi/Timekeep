package com.example.timekeep_work.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire representation of the tenant's Device_View asset response. */
@Serializable
data class HaloAssetDto(
    val id: Int? = null,
    @SerialName("inventory_number") val inventoryNumber: String? = null,
    @SerialName("key_field") val keyField: String? = null,
    @SerialName("key_field2") val keyField2: String? = null,
    @SerialName("key_field3") val keyField3: String? = null,
    @SerialName("key_field_name") val keyFieldName: String? = null,
    @SerialName("key_field2_name") val keyField2Name: String? = null,
    @SerialName("key_field3_name") val keyField3Name: String? = null,
    @SerialName("manufacturer_name") val manufacturerName: String? = null,
    @SerialName("assettype_name") val assetTypeName: String? = null,
    @SerialName("item_name") val itemName: String? = null,
    val username: String? = null,
    @SerialName("status_name") val statusName: String? = null,
    @SerialName("site_name") val siteName: String? = null,
    @SerialName("changehistory_last_updated") val changeHistoryLastUpdated: String? = null,
)

@Serializable
data class HaloAssetSearchResponseDto(
    @SerialName("page_no") val pageNumber: Int? = null,
    @SerialName("page_size") val pageSize: Int? = null,
    @SerialName("record_count") val recordCount: Long? = null,
    val assets: List<HaloAssetDto>? = null,
)

/** Minimal write payload accepted by Halo's bulk Asset endpoint. */
@Serializable
data class HaloAssetUpdateDto(
    val id: Int,
    @SerialName("site_id") val siteId: Int,
    val users: List<HaloAssetUserUpdateDto>,
)

@Serializable
data class HaloAssetUserUpdateDto(
    val id: Int,
)
