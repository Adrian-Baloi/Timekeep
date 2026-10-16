package com.example.timekeep_work.data

import com.example.timekeep_work.Asset
import com.example.timekeep_work.AssignableUser
import com.example.timekeep_work.AssetSite
import com.example.timekeep_work.AssetManagementSites
import com.example.timekeep_work.AssetUpdate

/** In-memory repository used by previews, demonstrations, and deterministic local testing. */
class FakeAssetRepository(
    initialAssets: List<Asset> = demoAssets(),
) : AssetRepository {
    private val assetsById = initialAssets.associateBy(Asset::id).toMutableMap()
    private val users = listOf(
        AssignableUser(201, "Maya Chen", "maya.chen@example.com"),
        AssignableUser(202, "Jordan Lee", "jordan.lee@example.com", isAgent = true),
    )
    private val sites = listOf(
        AssetSite(301, "Boston Office"),
        AssetSite(302, "Montreal Office"),
        AssetSite(303, "Remote | CA"),
    )

    override suspend fun findByIdentifier(identifier: String): List<Asset> {
        val normalized = identifier.trim()
        if (normalized.isBlank()) {
            throw AssetRepositoryException(AssetDataError.ValidationFailure)
        }

        // These reserved values make the empty, multiple-result, and failure screens easy to demo.
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

    override suspend fun searchAssignableUsers(query: String): List<AssignableUser> {
        val normalized = query.trim()
        if (normalized.length < MIN_USER_QUERY_LENGTH) {
            throw AssetRepositoryException(AssetDataError.ValidationFailure)
        }
        return users.filter { user ->
            user.name.contains(normalized, ignoreCase = true) ||
                user.email?.contains(normalized, ignoreCase = true) == true
        }
    }

    override suspend fun getActiveSites(): List<AssetSite> = AssetManagementSites.restrict(sites)

    override suspend fun updateAsset(request: AssetUpdate) {
        val asset = assetsById[request.assetId]
            ?: throw AssetRepositoryException(AssetDataError.NotFound)
        val updated = when (request) {
            is AssetUpdate.Deploy -> asset.copy(
                assignedUser = request.user.name,
                location = request.site.name,
            )

            is AssetUpdate.ReturnToInventory -> asset.copy(
                assignedUser = null,
                location = request.site.name,
            )
        }
        assetsById[request.assetId] = updated
    }

    companion object {
        private const val MIN_USER_QUERY_LENGTH = 4

        /** Initial records intentionally share a serial number to exercise candidate selection. */
        fun demoAssets(): List<Asset> = listOf(
            Asset(101, "TOR-LT-0142", "PF4X91Q2", "NF-10482", "Lenovo", "ThinkPad T14 Gen 4", "Maya Chen", "In use", "Toronto - 14th floor"),
            Asset(102, "TOR-LT-0198", "PF4X91Q2", "NF-10811", "Lenovo", "ThinkPad T14 Gen 3", "Unassigned", "In stock", "Toronto - IT storage"),
            Asset(103, null, "PF4X91Q2", "NF-10903", "Lenovo", "ThinkPad T14 Gen 4", "Jordan Lee", "Repair", "Toronto - Service desk"),
        )
    }
}
