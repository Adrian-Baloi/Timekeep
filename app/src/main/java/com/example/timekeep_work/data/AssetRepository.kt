package com.example.timekeep_work.data

import com.example.timekeep_work.Asset
import com.example.timekeep_work.AssignableUser
import com.example.timekeep_work.AssetSite
import com.example.timekeep_work.AssetUpdate

/** Data boundary for asset lookup and management, independent of the Halo transport. */
interface AssetRepository {
    suspend fun findByIdentifier(identifier: String): List<Asset>

    suspend fun searchAssignableUsers(query: String): List<AssignableUser>

    suspend fun getActiveSites(): List<AssetSite>

    suspend fun updateAsset(request: AssetUpdate)
}
