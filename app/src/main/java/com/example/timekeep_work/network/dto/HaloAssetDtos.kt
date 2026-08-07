package com.example.timekeep_work.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HaloAssetDto(
    val id: Int? = null,
    val name: String? = null,
    val hostname: String? = null,
    @SerialName("serial_number") val serialNumber: String? = null,
    @SerialName("serial_no") val serialNumberAlternative: String? = null,
    @SerialName("inventory_number") val inventoryNumber: String? = null,
    @SerialName("asset_tag") val assetTag: String? = null,
    @SerialName("key_field") val keyField: String? = null,
    @SerialName("manufacturer_name") val manufacturerName: String? = null,
    val manufacturer: String? = null,
    @SerialName("model_name") val modelName: String? = null,
    @SerialName("assettype_name") val assetTypeName: String? = null,
    val username: String? = null,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("status_name") val statusName: String? = null,
    @SerialName("site_name") val siteName: String? = null,
    @SerialName("location_name") val locationName: String? = null,
)

@Serializable
data class HaloAssetUpdateDto(
    val id: Int,
    val hostname: String? = null,
    @SerialName("serial_number") val serialNumber: String? = null,
    @SerialName("inventory_number") val assetTag: String? = null,
    @SerialName("user_id") val assignedUserId: Int? = null,
    @SerialName("status_id") val statusId: Int? = null,
    @SerialName("status_name") val statusName: String? = null,
    @SerialName("site_id") val locationId: Int? = null,
)
