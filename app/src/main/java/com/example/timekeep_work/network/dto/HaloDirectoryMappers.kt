package com.example.timekeep_work.network.dto

import com.example.timekeep_work.AssignableUser
import com.example.timekeep_work.AssetSite
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull

data class HaloUserPage(
    val recordCount: Long,
    val users: List<HaloUserListDto>,
)

data class HaloSitePage(
    val recordCount: Long,
    val sites: List<HaloSiteListDto>,
)

fun Json.decodeUserPage(payload: JsonElement): HaloUserPage {
    val response = decodePage<HaloUserSearchResponseDto>(payload, "user search")
    val recordCount = response.recordCount.validRecordCount("user search")
    return HaloUserPage(
        recordCount = recordCount,
        users = response.users ?: emptyListIfNoRecords(recordCount, "users", "user search"),
    )
}

fun Json.decodeSitePage(payload: JsonElement): HaloSitePage {
    val response = decodePage<HaloSiteSearchResponseDto>(payload, "site list")
    val recordCount = response.recordCount.validRecordCount("site list")
    return HaloSitePage(
        recordCount = recordCount,
        sites = response.sites ?: emptyListIfNoRecords(recordCount, "sites", "site list"),
    )
}

/** Returns null for inactive or unusable records so callers can safely use mapNotNull. */
fun HaloUserListDto.toAssignableUser(): AssignableUser? {
    if (inactive == true) return null
    val userId = id?.takeIf { it > 0 } ?: return null
    val displayName = name.clean()
        ?: listOfNotNull(firstname.clean(), surname.clean()).joinToString(" ").ifBlank { null }
        ?: emailaddress.clean()
        ?: return null
    return AssignableUser(
        id = userId,
        name = displayName,
        email = emailaddress.clean(),
        isAgent = linkedAgentId?.let { it > 0 } == true,
    )
}

/** Returns null for inactive sites or records missing the identity required by the domain model. */
fun HaloSiteListDto.toAssetSite(): AssetSite? {
    if (inactive == true) return null
    val siteId = id?.takeIf { it > 0 } ?: return null
    val siteName = name.clean() ?: return null
    return AssetSite(
        id = siteId,
        name = siteName,
        clientName = clientName.clean(),
        clientId = (clientId as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 },
    )
}

private inline fun <reified T> Json.decodePage(payload: JsonElement, label: String): T = try {
    decodeFromJsonElement(payload)
} catch (error: SerializationException) {
    throw error
} catch (error: IllegalArgumentException) {
    throw SerializationException("Halo $label response did not match the tenant schema.", error)
}

private fun Long?.validRecordCount(label: String): Long =
    this?.takeIf { it >= 0 }
        ?: throw SerializationException("Halo $label response has no valid record_count.")

private fun <T> emptyListIfNoRecords(recordCount: Long, field: String, label: String): List<T> =
    if (recordCount == 0L) emptyList()
    else throw SerializationException("Halo $label response omitted $field for a non-empty result.")

private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)
