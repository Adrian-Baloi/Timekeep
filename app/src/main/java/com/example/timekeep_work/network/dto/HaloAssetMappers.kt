package com.example.timekeep_work.network.dto

import com.example.timekeep_work.Asset
import com.example.timekeep_work.AssetUpdate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

data class HaloAssetPage(
    val pageNumber: Int,
    val pageSize: Int,
    val recordCount: Long,
    val assets: List<HaloAssetDto>,
)

/**
 * Maps tenant-configurable key fields into the app's stable asset fields.
 * Labels are inspected instead of assuming key_field always means the same thing in every tenant.
 */
fun HaloAssetDto.toAppModel(matchedIdentifier: String? = null): Asset {
    val assetId = id?.takeIf { it > 0 }
        ?: throw SerializationException("Halo asset response has no valid id.")
    val keyFields = keyFields()
    val serial = keyFields.valueForLabel("serial")
    val deviceName = keyFields.valueForLabel("hostname", "host name", "device name", "computer name")
        ?: keyFields.firstValueExcept(serial)
    val model = keyFields.valueForLabel("model") ?: itemName?.clean()
    val lookupIdentifier = matchedIdentifier?.clean()
        ?: serial
        ?: inventoryNumber?.clean()
        ?: keyFields.firstValueExcept(null)
        ?: "Halo asset #$assetId"
    return Asset(
        id = assetId,
        hostname = deviceName,
        serialNumber = serial,
        assetTag = inventoryNumber?.clean(),
        manufacturer = manufacturerName?.clean(),
        model = model,
        assignedUser = username?.clean(),
        status = statusName?.clean(),
        location = siteName?.clean(),
        lastUpdated = changeHistoryLastUpdated?.clean(),
        matchedIdentifier = lookupIdentifier,
    )
}

/** Confirms an exact identifier match after Halo's broader server-side search. */
fun HaloAssetDto.matchesIdentifier(identifier: String): Boolean =
    identifierValues().any { it.equals(identifier, ignoreCase = true) }

/** Converts one app action into the tenant's asset-change workflow ticket batch. */
fun AssetUpdate.toHaloTicketDtos(): List<HaloAssetChangeTicketDto> = when (this) {
    is AssetUpdate.Deploy -> listOf(
        assetChangeTicket(
            actionValue = DEPLOYING_VALUE_ID,
            additionalFields = listOf(
                HaloTicketCustomFieldDto(ACCOUNT_NUMBER_FIELD_ID, user.id.toString()),
            ),
        ),
    )

    // The native Halo request omits account-number field 289 when returning an asset.
    is AssetUpdate.ReturnToInventory -> listOf(
        assetChangeTicket(actionValue = RETURNING_TO_INVENTORY_VALUE_ID),
    )
}

private fun AssetUpdate.assetChangeTicket(
    actionValue: Int,
    additionalFields: List<HaloTicketCustomFieldDto> = emptyList(),
): HaloAssetChangeTicketDto = HaloAssetChangeTicketDto(
    ticketTypeId = ASSET_CHANGE_TICKET_TYPE_ID,
    assetNumber = assetId,
    assets = listOf(HaloTicketAssetDto(id = assetId)),
    clientId = site.clientId,
    siteId = site.id,
    customFields = buildList {
        add(HaloTicketCustomFieldDto(ACTION_FIELD_ID, actionValue.toString()))
        addAll(additionalFields)
        add(HaloTicketCustomFieldDto(OFFICE_LOCATION_FIELD_ID, site.id.toString()))
    },
)

private const val ASSET_CHANGE_TICKET_TYPE_ID = 79
private const val ACTION_FIELD_ID = 564
private const val ACCOUNT_NUMBER_FIELD_ID = 289
private const val OFFICE_LOCATION_FIELD_ID = 404
private const val DEPLOYING_VALUE_ID = 1
private const val RETURNING_TO_INVENTORY_VALUE_ID = 2

/** Decodes a page and rejects incomplete non-empty responses before they reach the UI. */
fun Json.decodeAssetPage(payload: JsonElement): HaloAssetPage {
    val response = try {
        decodeFromJsonElement<HaloAssetSearchResponseDto>(payload)
    } catch (error: SerializationException) {
        throw error
    } catch (error: IllegalArgumentException) {
        throw SerializationException("Halo asset search response did not match Device_View.", error)
    }
    val recordCount = response.recordCount?.takeIf { it >= 0 }
        ?: throw SerializationException("Halo asset search response has no valid record_count.")
    val pageNumber = response.pageNumber?.takeIf { it > 0 } ?: 1
    val pageSize = response.pageSize?.takeIf { it > 0 } ?: response.assets?.size ?: 0
    val assets = response.assets ?: if (recordCount == 0L) {
        emptyList()
    } else {
        throw SerializationException("Halo asset search response omitted assets for a non-empty result.")
    }
    return HaloAssetPage(
        pageNumber = pageNumber,
        pageSize = pageSize,
        recordCount = recordCount,
        assets = assets,
    )
}

private data class LabeledValue(
    val label: String?,
    val value: String,
)

private fun HaloAssetDto.keyFields(): List<LabeledValue> = listOf(
    LabeledValue(keyFieldName.clean(), keyField.clean().orEmpty()),
    LabeledValue(keyField2Name.clean(), keyField2.clean().orEmpty()),
    LabeledValue(keyField3Name.clean(), keyField3.clean().orEmpty()),
).filter { it.value.isNotEmpty() }

private fun HaloAssetDto.identifierValues(): List<String> = buildList {
    inventoryNumber.clean()?.let(::add)
    keyField.clean()?.let(::add)
    keyField2.clean()?.let(::add)
    keyField3.clean()?.let(::add)
}

private fun List<LabeledValue>.valueForLabel(vararg expectedLabels: String): String? =
    firstNotNullOfOrNull { field ->
        val normalizedLabel = field.label?.lowercase() ?: return@firstNotNullOfOrNull null
        field.value.takeIf { expectedLabels.any(normalizedLabel::contains) }
    }

private fun List<LabeledValue>.firstValueExcept(excluded: String?): String? =
    firstOrNull { !it.value.equals(excluded, ignoreCase = true) }?.value

private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)
