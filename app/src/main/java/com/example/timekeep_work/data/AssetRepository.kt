package com.example.timekeep_work.data

import com.example.timekeep_work.Asset
import java.io.IOException

interface AssetRepository {
    suspend fun findAssets(identifier: String): List<Asset>

    suspend fun getAsset(assetId: Int): Asset?

    suspend fun updateStatus(assetId: Int, status: String): Asset
}

class DemoAssetRepository : AssetRepository {
    private val assetsById = initialAssets().associateBy(Asset::id).toMutableMap()

    override suspend fun findAssets(identifier: String): List<Asset> {
        return when (identifier.trim().uppercase()) {
            "NONE" -> emptyList()
            "MULTI" -> assetsById.values.toList()
            "ERROR" -> throw IOException("Can't reach Halo ITSM. Check your connection and try again.")
            else -> assetsById.values
                .filter { it.serialNumber.equals(identifier.trim(), ignoreCase = true) }
                .take(1)
        }
    }

    override suspend fun getAsset(assetId: Int): Asset? = assetsById[assetId]

    override suspend fun updateStatus(assetId: Int, status: String): Asset {
        val asset = assetsById[assetId] ?: error("Asset $assetId was not found.")
        return asset.copy(status = status).also { assetsById[assetId] = it }
    }

    private fun initialAssets() = listOf(
        Asset(101, "TOR-LT-0142", "PF4X91Q2", "NF-10482", "Lenovo", "ThinkPad T14 Gen 4", "Maya Chen", "In use", "Toronto – 14th floor"),
        Asset(102, "TOR-LT-0198", "PF4X91Q2", "NF-10811", "Lenovo", "ThinkPad T14 Gen 3", "Unassigned", "In stock", "Toronto – IT storage"),
        Asset(103, null, "PF4X91Q2", "NF-10903", "Lenovo", "ThinkPad T14 Gen 4", "Jordan Lee", "Repair", "Toronto – Service desk"),
    )
}
