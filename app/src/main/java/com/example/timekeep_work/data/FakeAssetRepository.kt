package com.example.timekeep_work.data

import com.example.timekeep_work.Asset
import com.example.timekeep_work.AssetUpdate

class FakeAssetRepository(
    initialAssets: List<Asset> = demoAssets(),
) : AssetRepository {
    private val assetsById = initialAssets.associateBy(Asset::id).toMutableMap()

    override suspend fun findByIdentifier(identifier: String): List<Asset> {
        val normalized = identifier.trim()
        if (normalized.isBlank()) {
            throw AssetRepositoryException(AssetDataError.ValidationFailure)
        }

        return when (normalized.uppercase()) {
            "NONE" -> emptyList()
            "MULTI" -> assetsById.values.toList()
            "ERROR" -> throw AssetRepositoryException(AssetDataError.NoNetwork)
            else -> assetsById.values.filter { asset ->
                asset.serialNumber.equals(normalized, ignoreCase = true) ||
                    asset.assetTag.equals(normalized, ignoreCase = true) ||
                    asset.hostname.equals(normalized, ignoreCase = true)
            }
        }
    }

    override suspend fun updateAsset(request: AssetUpdate): Asset {
        if (!request.hasChanges) {
            throw AssetRepositoryException(AssetDataError.ValidationFailure)
        }
        val asset = assetsById[request.assetId]
            ?: throw AssetRepositoryException(AssetDataError.NotFound)
        return asset.copy(
            hostname = request.hostname ?: asset.hostname,
            serialNumber = request.serialNumber ?: asset.serialNumber,
            assetTag = request.assetTag ?: asset.assetTag,
            assignedUser = request.assignedUserId?.let { "User #$it" } ?: asset.assignedUser,
            status = request.statusName ?: request.statusId?.let { "Status #$it" } ?: asset.status,
            location = request.locationId?.let { "Location #$it" } ?: asset.location,
        ).also { assetsById[request.assetId] = it }
    }

    companion object {
        fun demoAssets(): List<Asset> = listOf(
            Asset(101, "TOR-LT-0142", "PF4X91Q2", "NF-10482", "Lenovo", "ThinkPad T14 Gen 4", "Maya Chen", "In use", "Toronto - 14th floor"),
            Asset(102, "TOR-LT-0198", "PF4X91Q2", "NF-10811", "Lenovo", "ThinkPad T14 Gen 3", "Unassigned", "In stock", "Toronto - IT storage"),
            Asset(103, null, "PF4X91Q2", "NF-10903", "Lenovo", "ThinkPad T14 Gen 4", "Jordan Lee", "Repair", "Toronto - Service desk"),
        )
    }
}
