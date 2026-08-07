package com.example.timekeep_work.network.dto

import com.example.timekeep_work.Asset
import com.example.timekeep_work.AssetUpdate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

fun HaloAssetDto.toAppModel(): Asset {
    val assetId = id?.takeIf { it > 0 }
        ?: throw SerializationException("Halo asset response has no valid id.")
    val serial = firstText(serialNumber, serialNumberAlternative, keyField)
        ?: throw SerializationException("Halo asset response has no serial identifier.")
    return Asset(
        id = assetId,
        hostname = firstText(hostname, name),
        serialNumber = serial,
        assetTag = firstText(assetTag, inventoryNumber),
        manufacturer = firstText(manufacturerName, manufacturer),
        model = firstText(modelName, assetTypeName),
        assignedUser = firstText(username, userName),
        status = statusName?.trim()?.takeIf(String::isNotEmpty),
        location = firstText(siteName, locationName),
    )
}

fun AssetUpdate.toHaloDto(): HaloAssetUpdateDto = HaloAssetUpdateDto(
    id = assetId,
    hostname = hostname?.trim()?.takeIf(String::isNotEmpty),
    serialNumber = serialNumber?.trim()?.takeIf(String::isNotEmpty),
    assetTag = assetTag?.trim()?.takeIf(String::isNotEmpty),
    assignedUserId = assignedUserId,
    statusId = statusId,
    statusName = statusName?.trim()?.takeIf(String::isNotEmpty),
    locationId = locationId,
)

fun Json.decodeAssetList(payload: JsonElement): List<HaloAssetDto> {
    val elements = when (payload) {
        is JsonArray -> payload
        is JsonObject -> payload.firstArray("assets", "items", "records")
            ?: if (payload.isExplicitlyEmpty()) JsonArray(emptyList()) else JsonArray(listOf(payload))
        else -> throw SerializationException("Halo asset response is neither an object nor an array.")
    }
    return elements.map { decodeFromJsonElement<HaloAssetDto>(it) }
}

fun Json.decodeUpdatedAsset(payload: JsonElement): HaloAssetDto =
    decodeAssetList(payload).firstOrNull()
        ?: throw SerializationException("Halo asset update response is empty.")

private fun JsonObject.firstArray(vararg names: String): JsonArray? =
    names.firstNotNullOfOrNull { name -> this[name] as? JsonArray }

private fun JsonObject.isExplicitlyEmpty(): Boolean {
    if (isEmpty()) return true
    val count = listOf("count", "record_count", "total").firstNotNullOfOrNull { name ->
        (this[name] as? JsonPrimitive)?.content?.toIntOrNull()
    }
    return count == 0
}

private fun firstText(vararg values: String?): String? =
    values.firstNotNullOfOrNull { value -> value?.trim()?.takeIf(String::isNotEmpty) }
