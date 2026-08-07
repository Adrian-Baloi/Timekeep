package com.example.timekeep_work.data

import com.example.timekeep_work.Asset
import com.example.timekeep_work.AssetUpdate

interface AssetRepository {
    suspend fun findByIdentifier(identifier: String): List<Asset>

    suspend fun updateAsset(request: AssetUpdate): Asset
}
